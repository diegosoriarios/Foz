package com.example.foz.manager

import android.content.Context
import java.io.File
import org.json.JSONObject

/**
 * Process-level crash safety net for the launcher.
 *
 * [install] hooks the default uncaught exception handler and records the
 * crash to `filesDir/crash_guard.json` before delegating to the system
 * handler. Only Java/Kotlin crashes land here; a native SIGSEGV kills the
 * process uncatchably and is instead caught indirectly by
 * [LoadCrashPolicy]'s load-death streak.
 *
 * On the next start [consumeStartupState] classifies the previous crash:
 *  - [Recovery.SAFE_MODE]: crash less than [SAFE_MODE_WINDOW_MS] ago — the
 *    session refuses automatic model loads (and bubble startup) so a
 *    crashing build can never boot-loop the launcher into Android's
 *    home-picker fallback.
 *  - [Recovery.WARNING]: crash within [WARNING_WINDOW_MS] — surfaces a
 *    one-time recovery notice.
 *
 * All file access is defensive: the handler itself must never throw.
 */
object CrashGuard {

    enum class Recovery { NONE, WARNING, SAFE_MODE }

    const val SAFE_MODE_WINDOW_MS = 60_000L
    const val WARNING_WINDOW_MS = 10 * 60_000L

    private const val FILE_NAME = "crash_guard.json"
    private const val MAX_STACK_CHARS = 500

    @Volatile
    private var installed = false

    @Volatile
    private var consumed: Recovery? = null

    fun install(context: Context) {
        if (installed) return
        installed = true
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                record(appContext, thread, throwable)
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** Evaluated once per process; later calls return the same value. */
    fun consumeStartupState(
        context: Context,
        nowMs: Long = System.currentTimeMillis()
    ): Recovery {
        consumed?.let { return it }
        var crashTimestampMs = 0L
        try {
            crashFile(context).let { file ->
                if (file.exists()) {
                    crashTimestampMs = JSONObject(file.readText()).optLong("timestamp", 0L)
                }
            }
        } catch (_: Throwable) {
        }
        // The file is intentionally kept (overwritten by the next crash) so
        // the Diagnostics screen can always show the most recent crash.
        val recovery = classify(nowMs, crashTimestampMs)
        consumed = recovery
        return recovery
    }

    data class LastCrash(
        val timestampMs: Long,
        val thread: String,
        val exception: String,
        val stack: String,
        val versionCode: Long
    )

    /** Parsed last-crash record for the Diagnostics screen, if any. */
    fun readLastCrash(context: Context): LastCrash? = try {
        val file = crashFile(context)
        if (!file.exists()) {
            null
        } else {
            val json = JSONObject(file.readText())
            LastCrash(
                timestampMs = json.optLong("timestamp", 0L),
                thread = json.optString("thread"),
                exception = json.optString("exception"),
                stack = json.optString("stack"),
                versionCode = json.optLong("versionCode", 0L)
            )
        }
    } catch (_: Throwable) {
        null
    }

    /** True when this session started in safe mode (consume already ran). */
    val safeModeActive: Boolean
        get() = consumed == Recovery.SAFE_MODE

    fun classify(nowMs: Long, crashTimestampMs: Long): Recovery = when {
        crashTimestampMs <= 0L -> Recovery.NONE
        nowMs - crashTimestampMs <= SAFE_MODE_WINDOW_MS -> Recovery.SAFE_MODE
        nowMs - crashTimestampMs <= WARNING_WINDOW_MS -> Recovery.WARNING
        else -> Recovery.NONE
    }

    private fun crashFile(context: Context): File =
        File(context.applicationContext.filesDir, FILE_NAME)

    private fun record(appContext: Context, thread: Thread, throwable: Throwable) {
        val json = JSONObject()
        json.put("timestamp", System.currentTimeMillis())
        json.put("thread", thread.name)
        json.put("versionCode", versionCode(appContext))
        json.put("exception", throwable.javaClass.name)
        json.put("stack", throwable.stackTraceToString().take(MAX_STACK_CHARS))
        crashFile(appContext).writeText(json.toString())
    }

    private fun versionCode(appContext: Context): Long = try {
        appContext.packageManager
            .getPackageInfo(appContext.packageName, 0)
            .longVersionCode
    } catch (_: Throwable) {
        0L
    }
}
