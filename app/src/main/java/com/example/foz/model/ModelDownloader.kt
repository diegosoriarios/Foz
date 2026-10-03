package com.example.foz.model

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Downloads the Gemma model file from HuggingFace into the app's internal
 * model directory. Supports resuming partial downloads via HTTP Range and
 * requires a user token because the Gemma repos are license-gated.
 * The token never leaves the device except in the Authorization header.
 */
class ModelDownloader(
    private val scope: CoroutineScope,
    private val destDir: File,
    private val fileName: String,
    private val url: String = DEFAULT_URL,
    private val minValidBytes: Long = MIN_VALID_BYTES,
    private val onComplete: () -> Unit = {}
) {

    sealed interface State {
        data object Idle : State
        data class Downloading(val received: Long, val total: Long) : State
        data object Done : State
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private var job: Job? = null

    private val partFile: File get() = File(destDir, "$fileName.part")
    val finalFile: File get() = File(destDir, fileName)

    /** Starts (or resumes) the download; no-op while already running. */
    fun start(token: String) {
        if (job?.isActive == true) return
        _state.value = State.Downloading(received = partLength(), total = 0L)
        job = scope.launch { run(token) }
    }

    /** Stops the download, keeping the partial file for a later resume. */
    fun cancel() {
        job?.cancel()
        job = null
        if (_state.value is State.Downloading) _state.value = State.Idle
    }

    /** Clears a terminal (Done/Failed) state back to Idle. */
    fun reset() {
        if (job?.isActive != true) _state.value = State.Idle
    }

    private fun partLength(): Long = if (partFile.exists()) partFile.length() else 0L

    private suspend fun run(token: String) = withContext(Dispatchers.IO) {
        try {
            val already = partLength()
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                // HttpURLConnection drops this header on cross-host redirects,
                // so the token is only sent to huggingface.co itself.
                if (token.isNotBlank()) setRequestProperty("Authorization", "Bearer $token")
                if (already > 0) setRequestProperty("Range", "bytes=$already-")
            }
            val code = conn.responseCode
            when {
                code == HTTP_PARTIAL && already > 0 -> download(conn, offset = already)
                code in 200..299 -> {
                    if (already > 0) partFile.delete()
                    download(conn, offset = 0L)
                }
                code == 401 || code == 403 -> {
                    conn.disconnect()
                    _state.value = State.Failed(UNAUTHORIZED_MESSAGE)
                }
                else -> {
                    conn.disconnect()
                    _state.value = State.Failed("Download failed (HTTP $code)")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = State.Failed(e.message ?: "Download failed")
        }
    }

    private suspend fun download(conn: HttpURLConnection, offset: Long) {
        val total = parseTotalLength(conn, offset)
        _state.value = State.Downloading(received = offset, total = total)
        try {
            var received = offset
            var lastReport = offset
            conn.inputStream.use { input ->
                java.io.FileOutputStream(partFile, offset > 0).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        received += read
                        if (received - lastReport >= REPORT_INTERVAL_BYTES) {
                            lastReport = received
                            _state.value = State.Downloading(received, total)
                        }
                    }
                }
            }
            conn.disconnect()
            if (total > 0 && received < total) {
                throw IllegalStateException("Connection lost — resume to continue")
            }
            if (received < minValidBytes) {
                partFile.delete()
                throw IllegalStateException("Downloaded file is too small to be a model")
            }
            val target = finalFile
            if (target.exists()) target.delete()
            if (!partFile.renameTo(target)) {
                throw IllegalStateException("Could not finalize model file")
            }
            _state.value = State.Done
            onComplete()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            conn.disconnect()
            _state.value = State.Failed(e.message ?: "Download failed")
        }
    }

    private fun parseTotalLength(conn: HttpURLConnection, offset: Long): Long {
        if (conn.responseCode == HTTP_PARTIAL) {
            contentRangeTotal(conn.getHeaderField("Content-Range"))?.let { return it }
        }
        return conn.contentLengthLong.takeIf { it > 0 }?.plus(offset) ?: 0L
    }

    companion object {
        const val DEFAULT_URL =
            "https://huggingface.co/litert-community/gemma-3-1b-it/resolve/main/gemma-3-1b-it-int4.task"
        const val UNAUTHORIZED_MESSAGE =
            "Unauthorized: check your HuggingFace token and accept the model license on its page"
        internal const val MIN_VALID_BYTES = 100L * 1024 * 1024

        /** Extracts the total size from "bytes 100-199/530000000" (or "bytes *\/530000000"). */
        internal fun contentRangeTotal(header: String?): Long? {
            if (header == null) return null
            val slash = header.lastIndexOf('/') ?: -1
            if (slash < 0 || slash == header.length - 1) return null
            return header.substring(slash + 1).trim().toLongOrNull()
        }

        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val BUFFER_SIZE = 64 * 1024
        private const val REPORT_INTERVAL_BYTES = 512L * 1024
        private const val HTTP_PARTIAL = 206
    }
}
