package com.example.foz.manager

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.net.Uri
import android.util.Log
import com.example.foz.R
import com.example.foz.ai.AssistantMessage
import com.example.foz.ai.LlmEngine
import com.example.foz.ai.MediaPipeLlmEngine
import com.example.foz.ai.ToolCall
import com.example.foz.ai.ToolRegistry
import com.example.foz.ai.ToolResult
import com.example.foz.data.AppRepository
import com.example.foz.data.CalendarRepository
import com.example.foz.data.NotesRepository
import com.example.foz.data.PrefsManager
import com.example.foz.data.WeatherRepository
import com.example.foz.voice.SpeechRecognizerManager
import com.example.foz.voice.TtsManager
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Owns the on-device assistant: model file lifecycle, engine loading and the
 * conversation state. Fully offline; no data leaves the device.
 */
class AssistantManager private constructor(private val appContext: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val engine: LlmEngine = MediaPipeLlmEngine()
    private val prefsManager = PrefsManager(appContext)
    private val voice = SpeechRecognizerManager(appContext)
    private val tts = TtsManager(appContext)
    private val notesRepository = NotesRepository(appContext)
    private val calendarRepository = CalendarRepository(appContext)
    private val weatherRepository = WeatherRepository()
    private val appRepository = AppRepository(
        packageManager = appContext.packageManager,
        launcherApps = appContext.getSystemService(LauncherApps::class.java)
    )
    private val toolRegistry = ToolRegistry(
        context = appContext,
        notesRepository = notesRepository,
        calendarRepository = calendarRepository,
        weatherProvider = {
            try {
                prefsManager.lastWeather.firstOrNull()?.let { json ->
                    weatherRepository.fromJson(json)
                }
            } catch (_: Throwable) {
                null
            }
        },
        findApp = { name -> findInstalledApp(name) },
        launchApp = { app -> launchInstalledApp(app.packageName, app.className, app.name) }
    )
    private val activityManager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager

    @Volatile
    private var pendingUserQuery: String? = null

    @Volatile
    private var speakEnabled = true

    private val _state = MutableStateFlow(AssistantRuntimeState())
    val state: StateFlow<AssistantRuntimeState> = _state.asStateFlow()

    private val _messages = MutableStateFlow<List<AssistantMessage>>(emptyList())
    val messages: StateFlow<List<AssistantMessage>> = _messages.asStateFlow()

    init {
        scope.launch {
            voice.state.collect { vs ->
                _state.update {
                    it.copy(
                        isListening = vs.isListening,
                        partialText = vs.partialText,
                        voiceError = vs.error
                    )
                }
            }
        }
        scope.launch {
            prefsManager.assistantModelFileName.collect { fileName ->
                if (fileName == null) {
                    if (_state.value.status != AssistantRuntimeModelStatus.COPYING) {
                        _state.update { it.copy(status = AssistantRuntimeModelStatus.NONE, fileName = null, error = null) }
                    }
                } else {
                    _state.update {
                        val newStatus = when (it.status) {
                            AssistantRuntimeModelStatus.COPYING -> AssistantRuntimeModelStatus.SELECTED
                            AssistantRuntimeModelStatus.NONE -> AssistantRuntimeModelStatus.SELECTED
                            else -> it.status
                        }
                        it.copy(status = newStatus, fileName = fileName, error = null)
                    }
                }
            }
        }
    }

    val modelDirectory: File
        get() = File(appContext.filesDir, MODEL_DIR).apply { mkdirs() }

    fun modelFile(): File? {
        val fileName = _state.value.fileName ?: return null
        return File(modelDirectory, fileName).takeIf { it.exists() }
    }

    fun deviceRamInfo(): Pair<Long, Long> {
        val info = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(info)
        return info.availMem to info.totalMem
    }

    /**
     * Copies the picked .task file into internal storage, then marks it as the
     * active model. Safe to call repeatedly; the newest pick wins.
     */
    fun selectModelFromUri(uri: Uri) {
        scope.launch {
            _state.update { it.copy(status = AssistantRuntimeModelStatus.COPYING, error = null) }
            try {
                val fileName = withContext(Dispatchers.IO) { copyModelToInternalStorage(uri) }
                prefsManager.setAssistantModelFileName(fileName)
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to copy model file", t)
                _state.update {
                    it.copy(
                        status = if (it.fileName != null) AssistantRuntimeModelStatus.SELECTED else AssistantRuntimeModelStatus.ERROR,
                        error = t.message ?: "Copy failed"
                    )
                }
            }
        }
    }

    private fun copyModelToInternalStorage(uri: Uri): String {
        val fileName = sanitizeFileName(queryDisplayName(uri) ?: DEFAULT_MODEL_FILE_NAME)
        val target = File(modelDirectory, fileName)
        val temp = File(modelDirectory, "$fileName.part")
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            temp.outputStream().use { output ->
                input.copyTo(output, bufferSize = DEFAULT_BUFFER_SIZE * 16)
            }
        } ?: throw IllegalStateException("Cannot open selected file")
        if (temp.length() < MIN_MODEL_BYTES) {
            temp.delete()
            throw IllegalStateException("Selected file is too small to be a model")
        }
        if (target.exists()) target.delete()
        if (!temp.renameTo(target)) {
            throw IllegalStateException("Could not finalize model file")
        }
        // Remove other model files so only the active one occupies storage.
        modelDirectory.listFiles()?.forEach { file ->
            if (file != target) file.delete()
        }
        return fileName
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun sanitizeFileName(name: String): String {
        val cleaned = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return if (cleaned.endsWith(".task")) cleaned else "$cleaned.task"
    }

    fun deleteModel() {
        engine.close()
        scope.launch {
            prefsManager.setAssistantModelFileName(null)
            _state.update {
                it.copy(
                    status = AssistantRuntimeModelStatus.NONE,
                    fileName = null,
                    error = null
                )
            }
            withContext(Dispatchers.IO) {
                modelDirectory.listFiles()?.forEach { it.delete() }
            }
            _messages.value = emptyList()
        }
    }

    fun ensureModelLoaded(onError: (String) -> Unit = {}) {
        val current = _state.value
        if (current.status == AssistantRuntimeModelStatus.LOADED ||
            current.status == AssistantRuntimeModelStatus.LOADING ||
            current.status == AssistantRuntimeModelStatus.COPYING
        ) {
            return
        }
        val file = modelFile()
        if (file == null) {
            onError(appContext.getString(R.string.assistant_error_no_model))
            return
        }
        scope.launch {
            _state.update { it.copy(status = AssistantRuntimeModelStatus.LOADING, error = null) }
            try {
                engine.load(appContext, file)
                _state.update { it.copy(status = AssistantRuntimeModelStatus.LOADED, error = null) }
            } catch (t: Throwable) {
                Log.e(TAG, "Model load failed", t)
                _state.update {
                    it.copy(
                        status = AssistantRuntimeModelStatus.ERROR,
                        error = t.message ?: "Load failed"
                    )
                }
            }
        }
    }

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val current = _state.value
        if (current.status != AssistantRuntimeModelStatus.LOADED || current.thinking) return

        tts.stop()
        _messages.value = _messages.value + AssistantMessage(isFromUser = true, text = trimmed)
        processQuery(trimmed)
    }

    /**
     * Runs the tool-calling loop for the last user message. The model may
     * request tools; results are fed back until a final answer is produced,
     * a permission is required, or the iteration budget runs out.
     */
    private fun processQuery(userText: String) {
        _state.update { it.copy(thinking = true) }

        scope.launch {
            try {
                val loopHistory = _messages.value
                    .filter { !it.isToolActivity }
                    .takeLast(MAX_HISTORY_MESSAGES)
                    .toMutableList()

                var iterations = 0
                while (true) {
                    if (iterations >= MAX_TOOL_ITERATIONS) {
                        appendAssistantMessage(appContext.getString(R.string.assistant_error_complex))
                        return@launch
                    }
                    iterations++
                    val prompt = buildPrompt(loopHistory)
                    val output = withContext(Dispatchers.Default) { engine.generate(prompt) }
                    val cleaned = output.trim().removeSuffix("<end_of_turn>").trim()
                    val toolCall = toolRegistry.parseToolCall(cleaned)

                    if (toolCall == null) {
                        if (cleaned.isBlank()) {
                            appendAssistantMessage(appContext.getString(R.string.assistant_error_generating))
                        } else {
                            appendAssistantMessage(cleaned)
                        }
                        return@launch
                    }

                    if (!toolRegistry.toolNames.contains(toolCall.tool)) {
                        loopHistory += AssistantMessage(isFromUser = false, text = cleaned)
                        loopHistory += AssistantMessage(
                            isFromUser = true,
                            text = "[TOOL_RESULT] " + JSONObject().put("error", "Unknown tool").toString()
                        )
                        continue
                    }

                    val friendlyLabel = friendlyToolLabel(toolCall)
                    _messages.value += AssistantMessage(
                        isFromUser = false,
                        text = friendlyLabel,
                        isToolActivity = true
                    )

                    val result = withContext(Dispatchers.IO) {
                        toolRegistry.dispatch(toolCall)
                    }

                    when (result) {
                        is ToolResult.NeedsPermission -> {
                            pendingUserQuery = userText
                            _state.update {
                                it.copy(thinking = false, pendingPermission = result.permission)
                            }
                            return@launch
                        }

                        is ToolResult.Error -> {
                            loopHistory += AssistantMessage(isFromUser = false, text = cleaned)
                            loopHistory += AssistantMessage(
                                isFromUser = true,
                                text = "[TOOL_RESULT] " + JSONObject().put("error", result.message).toString()
                            )
                        }

                        is ToolResult.Success -> {
                            loopHistory += AssistantMessage(isFromUser = false, text = cleaned)
                            loopHistory += AssistantMessage(
                                isFromUser = true,
                                text = "[TOOL_RESULT] " + result.data.toString()
                            )
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Query processing failed", t)
                appendAssistantMessage(appContext.getString(R.string.assistant_error_generating))
            } finally {
                _state.update { it.copy(thinking = false) }
            }
        }
    }

    private fun appendAssistantMessage(text: String) {
        _messages.value = _messages.value + AssistantMessage(isFromUser = false, text = text)
        if (speakEnabled && text.isNotBlank()) {
            tts.speak(text)
        }
    }

    /**
     * Called after a runtime permission is granted/denied mid-conversation.
     * On grant, the original question is re-run from a fresh prompt.
     */
    fun onPermissionResult(granted: Boolean) {
        val query = pendingUserQuery
        pendingUserQuery = null
        _state.update { it.copy(pendingPermission = null) }
        if (query == null) return
        if (!granted) {
            appendAssistantMessage(appContext.getString(R.string.assistant_error_permission_denied))
            return
        }
        if (_state.value.status != AssistantRuntimeModelStatus.LOADED || _state.value.thinking) return
        processQuery(query)
    }

    private suspend fun findInstalledApp(name: String): com.example.foz.model.AppInfo? {
        val normalized = name.trim().lowercase(Locale.ROOT)
        if (normalized.isEmpty()) return null
        val apps = try {
            appRepository.getLaunchableApps()
        } catch (_: Throwable) {
            return null
        }
        return apps.firstOrNull { it.name.lowercase(Locale.ROOT) == normalized }
            ?: apps.firstOrNull { it.name.lowercase(Locale.ROOT).startsWith(normalized) }
            ?: apps.firstOrNull { it.name.lowercase(Locale.ROOT).contains(normalized) }
    }

    private suspend fun launchInstalledApp(packageName: String, className: String, appName: String): Boolean {
        return withContext(Dispatchers.Main) {
            try {
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    setClassName(packageName, className)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                appContext.startActivity(intent)
                true
            } catch (t: Throwable) {
                Log.w(TAG, "Could not launch $appName", t)
                false
            }
        }
    }

    private fun friendlyToolLabel(call: ToolCall): String {
        val range = call.arguments.optString("range")
        return when (call.tool) {
            "get_events" -> appContext.getString(R.string.tool_activity_get_events, range.ifBlank { "today" })
            "create_event" -> appContext.getString(R.string.tool_activity_create_event)
            "add_note" -> appContext.getString(R.string.tool_activity_add_note)
            "list_notes", "search_notes" -> appContext.getString(R.string.tool_activity_notes)
            "delete_note" -> appContext.getString(R.string.tool_activity_notes)
            "get_weather" -> appContext.getString(R.string.tool_activity_weather)
            "open_app" -> appContext.getString(R.string.tool_activity_open_app, call.arguments.optString("name"))
            "set_alarm" -> appContext.getString(R.string.tool_activity_alarm)
            "set_timer" -> appContext.getString(R.string.tool_activity_timer)
            else -> appContext.getString(R.string.tool_activity_generic)
        }
    }

    fun setSpeakEnabled(enabled: Boolean) {
        speakEnabled = enabled
        if (!enabled) {
            tts.stop()
        }
    }

    fun startListening() {
        val current = _state.value
        if (current.status != AssistantRuntimeModelStatus.LOADED ||
            current.thinking ||
            current.isListening
        ) {
            return
        }
        tts.stop()
        voice.startListening(
            onFinalResult = { text -> sendMessage(text) },
            onError = { _ -> }
        )
    }

    fun stopListening() {
        voice.stopListening()
    }

    fun stopSpeaking() {
        tts.stop()
    }

    fun clearConversation() {
        if (_state.value.thinking) return
        tts.stop()
        _messages.value = emptyList()
    }

    private fun buildPrompt(history: List<AssistantMessage>): String {
        val builder = StringBuilder()
        builder.append("<start_of_turn>user\n")
        builder.append(buildSystemPrompt())
        builder.append("<end_of_turn>\n")
        builder.append("<start_of_turn>model\nUnderstood.<end_of_turn>\n")
        history.forEach { message ->
            val role = if (message.isFromUser) "user" else "model"
            builder.append("<start_of_turn>").append(role).append("\n")
            builder.append(message.text.trim().take(MAX_MESSAGE_CHARS))
            builder.append("<end_of_turn>\n")
        }
        builder.append("<start_of_turn>model\n")
        return builder.toString()
    }

    private fun buildSystemPrompt(): String {
        val now = LocalDateTime.now()
        val formatted = now.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy, h:mm a", Locale.getDefault()))
        val base = SYSTEM_PROMPT_TEMPLATE.format(formatted)
        return base + toolRegistry.systemPromptSection + "\n"
    }

    companion object {
        private const val TAG = "AssistantManager"
        private const val MODEL_DIR = "assistant"
        private const val DEFAULT_MODEL_FILE_NAME = "gemma-3-1b-it-int4.task"
        private const val MIN_MODEL_BYTES = 100L * 1024 * 1024 // 100 MB sanity floor
        private const val MAX_HISTORY_MESSAGES = 6
        private const val MAX_MESSAGE_CHARS = 500
        private const val MAX_TOOL_ITERATIONS = 3

        private val SYSTEM_PROMPT_TEMPLATE =
            "You are Foz Assistant, a personal assistant running fully offline inside the Foz launcher " +
                "on the user's phone. Current date and time: %s. " +
                "Answer in the same language the user writes in. Be concise and helpful. " +
                "If you are not sure about a fact, say so honestly instead of guessing.\n\n"

        @Volatile
        private var instance: AssistantManager? = null

        fun getInstance(context: Context): AssistantManager {
            return instance ?: synchronized(this) {
                instance ?: AssistantManager(context.applicationContext).also { instance = it }
            }
        }
    }
}

enum class AssistantRuntimeModelStatus {
    NONE,
    COPYING,
    SELECTED,
    LOADING,
    LOADED,
    ERROR
}

data class AssistantRuntimeState(
    val status: AssistantRuntimeModelStatus = AssistantRuntimeModelStatus.NONE,
    val fileName: String? = null,
    val error: String? = null,
    val thinking: Boolean = false,
    val isListening: Boolean = false,
    val partialText: String? = null,
    val voiceError: String? = null,
    val pendingPermission: String? = null
)
