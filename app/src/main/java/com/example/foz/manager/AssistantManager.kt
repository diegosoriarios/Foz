package com.example.foz.manager

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.res.Configuration
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
import com.example.foz.data.ContactsRepository
import com.example.foz.data.NotesRepository
import com.example.foz.data.PrefsManager
import com.example.foz.data.ReminderRepository
import com.example.foz.data.WeatherRepository
import com.example.foz.model.ModelDownloader
import com.example.foz.reminder.ReminderScheduler
import com.example.foz.voice.SpeechRecognizerManager
import com.example.foz.voice.TtsManager
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
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
    private val contactsRepository = ContactsRepository(appContext)
    private val reminderRepository = ReminderRepository(appContext)
    private val reminderScheduler = ReminderScheduler(appContext)
    private val weatherRepository = WeatherRepository()
    private val appRepository = AppRepository(
        packageManager = appContext.packageManager,
        launcherApps = appContext.getSystemService(LauncherApps::class.java)
    )
    private val toolRegistry = ToolRegistry(
        context = appContext,
        prefsManager = prefsManager,
        notesRepository = notesRepository,
        calendarRepository = calendarRepository,
        contactsRepository = contactsRepository,
        reminderRepository = reminderRepository,
        reminderScheduler = reminderScheduler,
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
    private var suspendedQuery: SuspendedQuery? = null

    @Volatile
    private var cancelRequested = false

    @Volatile
    private var keepLoaded = false

    private var idleUnloadJob: Job? = null

    @Volatile
    private var speakEnabled = true

    private val _state = MutableStateFlow(AssistantRuntimeState())
    val state: StateFlow<AssistantRuntimeState> = _state.asStateFlow()

    private val _messages = MutableStateFlow<List<AssistantMessage>>(emptyList())
    val messages: StateFlow<List<AssistantMessage>> = _messages.asStateFlow()

    private val componentCallbacks = object : ComponentCallbacks2 {
        override fun onTrimMemory(level: Int) {
            when {
                // Launcher UI hidden: release unless the user opted into residency.
                level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> unloadIfPossible(force = false)
                // System under real pressure (foreground low/critical) or background levels.
                level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> unloadIfPossible(force = true)
                level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> unloadIfPossible(force = false)
                level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> unloadIfPossible(force = true)
            }
        }

        override fun onConfigurationChanged(newConfig: Configuration) = Unit

        override fun onLowMemory() = unloadIfPossible(force = true)
    }

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
            prefsManager.assistantSpeakResponses.collect { enabled ->
                setSpeakEnabled(enabled)
            }
        }
        scope.launch {
            prefsManager.assistantKeepLoaded.collect { enabled ->
                keepLoaded = enabled
                if (enabled) {
                    cancelIdleUnload()
                } else {
                    onAssistantIdle()
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
        scope.launch {
            // Restore the last conversation once (survives process death).
            try {
                val json = prefsManager.assistantHistory.firstOrNull()
                if (!json.isNullOrBlank()) {
                    val restored = parseHistory(json)
                    if (restored.isNotEmpty()) _messages.value = restored
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Could not restore assistant history", t)
            }
        }
        scope.launch {
            _messages.collect { messages ->
                val keep = messages.filter { !it.isToolActivity }.takeLast(MAX_PERSISTED_MESSAGES)
                try {
                    prefsManager.setAssistantHistory(toHistoryJson(keep))
                } catch (_: Throwable) {
                }
            }
        }
        appContext.registerComponentCallbacks(componentCallbacks)
    }

    val modelDirectory: File
        get() = File(appContext.filesDir, MODEL_DIR).apply { mkdirs() }

    /** In-app HuggingFace download; completion marks the file as the active model. */
    val modelDownloader: ModelDownloader by lazy {
        ModelDownloader(scope, modelDirectory, DEFAULT_MODEL_FILE_NAME) { onModelDownloaded() }
    }

    private fun onModelDownloaded() {
        scope.launch {
            prefsManager.setAssistantModelFileName(DEFAULT_MODEL_FILE_NAME)
            _state.update {
                it.copy(
                    status = AssistantRuntimeModelStatus.SELECTED,
                    fileName = DEFAULT_MODEL_FILE_NAME,
                    error = null
                )
            }
        }
    }

    private fun cancelIdleUnload() {
        idleUnloadJob?.cancel()
        idleUnloadJob = null
    }

    /**
     * Arms the 3-minute idle sweep. No-op while the model should stay resident,
     * while a query is suspended waiting for permission/confirmation, or while busy.
     */
    fun onAssistantIdle() {
        val current = _state.value
        if (current.status != AssistantRuntimeModelStatus.LOADED) return
        if (current.thinking || current.isListening) return
        if (current.pendingPermission != null || current.pendingConfirmation != null) return
        if (suspendedQuery != null) return
        if (keepLoaded) return
        cancelIdleUnload()
        idleUnloadJob = scope.launch {
            delay(IDLE_UNLOAD_TIMEOUT_MS)
            unloadIfPossible(force = true)
        }
    }

    private fun unloadIfPossible(force: Boolean) {
        val current = _state.value
        if (current.status != AssistantRuntimeModelStatus.LOADED) return
        if (current.thinking || current.isListening) return
        if (!force && keepLoaded) return
        scope.launch {
            engine.close()
            cancelRequested = false
            _state.update {
                it.copy(
                    status = if (it.fileName != null) {
                        AssistantRuntimeModelStatus.SELECTED
                    } else {
                        AssistantRuntimeModelStatus.NONE
                    },
                    partialAnswer = null
                )
            }
            Log.i(TAG, "Model unloaded from memory")
        }
    }

    /** Manually releases model memory (settings action). */
    fun unloadModel() {
        cancelIdleUnload()
        unloadIfPossible(force = true)
    }

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
        modelDownloader.cancel()
        suspendedQuery = null
        cancelIdleUnload()
        scope.launch {
            prefsManager.setAssistantModelFileName(null)
            _state.update {
                it.copy(
                    status = AssistantRuntimeModelStatus.NONE,
                    fileName = null,
                    error = null,
                    pendingPermission = null,
                    pendingConfirmation = null,
                    partialAnswer = null
                )
            }
            withContext(Dispatchers.IO) {
                modelDirectory.listFiles()?.forEach { it.delete() }
            }
            _messages.value = emptyList()
        }
    }

    fun ensureModelLoaded(onError: (String) -> Unit = {}, onReady: () -> Unit = {}) {
        val current = _state.value
        if (current.status == AssistantRuntimeModelStatus.LOADED) {
            cancelIdleUnload()
            scope.launch { withContext(Dispatchers.Main) { onReady() } }
            return
        }
        if (current.status == AssistantRuntimeModelStatus.LOADING ||
            current.status == AssistantRuntimeModelStatus.COPYING
        ) {
            return
        }
        val file = modelFile()
        if (file == null) {
            onError(appContext.getString(R.string.assistant_error_no_model))
            return
        }
        cancelIdleUnload()
        scope.launch {
            _state.update { it.copy(status = AssistantRuntimeModelStatus.LOADING, error = null) }
            try {
                engine.load(appContext, file)
                _state.update { it.copy(status = AssistantRuntimeModelStatus.LOADED, error = null) }
                withContext(Dispatchers.Main) { onReady() }
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
        if (current.status != AssistantRuntimeModelStatus.LOADED ||
            current.thinking ||
            current.pendingConfirmation != null
        ) return

        tts.stop()
        _messages.value = _messages.value + AssistantMessage(isFromUser = true, text = trimmed)
        runToolLoop(
            _messages.value
                .filter { !it.isToolActivity }
                .takeLast(MAX_HISTORY_MESSAGES)
                .toMutableList()
        )
    }

    /**
     * Runs the tool-calling loop. The model may request tools; results are fed
     * back until a final answer is produced, a permission or confirmation is
     * required, or the iteration budget runs out. [resume] continues a query
     * that was suspended waiting for a permission grant or user confirmation.
     */
    private fun runToolLoop(
        loopHistory: MutableList<AssistantMessage>,
        resume: ResumeAction? = null,
        startIterations: Int = 0
    ) {
        _state.update { it.copy(thinking = true) }

        scope.launch {
            try {
                var pending = resume
                var iterations = startIterations
                while (true) {
                    if (iterations >= MAX_TOOL_ITERATIONS) {
                        appendAssistantMessage(appContext.getString(R.string.assistant_error_complex))
                        return@launch
                    }
                    iterations++

                    val forcedDispatch = pending as? ResumeAction.Dispatch
                    val toolCall: ToolCall
                    val modelText: String?
                    if (forcedDispatch != null) {
                        pending = null
                        toolCall = forcedDispatch.call
                        modelText = null
                    } else {
                        val inject = pending as? ResumeAction.Inject
                        if (inject != null) {
                            pending = null
                            loopHistory += AssistantMessage(
                                isFromUser = true,
                                text = "[TOOL_RESULT] ${inject.payload}"
                            )
                        }
                        val prompt = buildPrompt(loopHistory)
                        val output = withContext(Dispatchers.Default) {
                            engine.generateStreaming(prompt) { partial ->
                                // Never show raw tool-call JSON in the live bubble.
                                val visible = if (looksLikeToolCall(partial)) null else partial
                                _state.update { it.copy(partialAnswer = visible?.takeLast(MAX_PARTIAL_CHARS)) }
                            }
                        }
                        val cancelled = cancelRequested
                        cancelRequested = false
                        _state.update { it.copy(partialAnswer = null) }
                        val candidate = output.trim().removeSuffix("<end_of_turn>").trim()
                        if (cancelled) {
                            when {
                                candidate.isBlank() -> appendAssistantMessage(
                                    appContext.getString(R.string.assistant_generation_stopped)
                                )
                                toolRegistry.parseToolCall(candidate) != null ->
                                    appendAssistantMessage(
                                        appContext.getString(R.string.assistant_generation_stopped)
                                    )
                                else -> appendAssistantMessage(candidate)
                            }
                            return@launch
                        }
                        val parsed = toolRegistry.parseToolCall(candidate)
                        if (parsed == null) {
                            if (candidate.isBlank()) {
                                appendAssistantMessage(appContext.getString(R.string.assistant_error_generating))
                            } else {
                                appendAssistantMessage(candidate)
                            }
                            return@launch
                        }
                        toolCall = parsed
                        modelText = candidate
                    }

                    if (!toolRegistry.toolNames.contains(toolCall.tool)) {
                        loopHistory += AssistantMessage(
                            isFromUser = false,
                            text = modelText ?: JSONObject().put("tool", toolCall.tool).toString()
                        )
                        loopHistory += AssistantMessage(
                            isFromUser = true,
                            text = "[TOOL_RESULT] " + JSONObject().put("error", "Unknown tool").toString()
                        )
                        continue
                    }

                    _messages.value += AssistantMessage(
                        isFromUser = false,
                        text = friendlyToolLabel(toolCall),
                        isToolActivity = true
                    )

                    val result = withContext(Dispatchers.IO) {
                        toolRegistry.dispatch(toolCall, confirmed = forcedDispatch?.confirmed == true)
                    }

                    when (result) {
                        is ToolResult.NeedsPermission -> {
                            suspendedQuery = SuspendedQuery(loopHistory, toolCall, iterations)
                            _state.update {
                                it.copy(thinking = false, pendingPermission = result.permission)
                            }
                            return@launch
                        }

                        is ToolResult.NeedsConfirmation -> {
                            suspendedQuery = SuspendedQuery(loopHistory, toolCall, iterations)
                            _state.update {
                                it.copy(thinking = false, pendingConfirmation = result.label)
                            }
                            return@launch
                        }

                        is ToolResult.Error -> {
                            loopHistory += AssistantMessage(
                                isFromUser = false,
                                text = modelText ?: toolCallAsPromptText(toolCall)
                            )
                            loopHistory += AssistantMessage(
                                isFromUser = true,
                                text = "[TOOL_RESULT] " + JSONObject().put("error", result.message).toString()
                            )
                        }

                        is ToolResult.Success -> {
                            loopHistory += AssistantMessage(
                                isFromUser = false,
                                text = modelText ?: toolCallAsPromptText(toolCall)
                            )
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
                onAssistantIdle()
            }
        }
    }

    private fun toolCallAsPromptText(call: ToolCall): String {
        return JSONObject().put("tool", call.tool).put("arguments", call.arguments).toString()
    }

    private fun toHistoryJson(messages: List<AssistantMessage>): String {
        val array = org.json.JSONArray()
        messages.forEach { message ->
            array.put(
                JSONObject()
                    .put("u", message.isFromUser)
                    .put("t", message.text)
            )
        }
        return array.toString()
    }

    private fun parseHistory(json: String): List<AssistantMessage> {
        return try {
            val array = org.json.JSONArray(json)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                val text = obj.optString("t")
                if (text.isBlank()) {
                    null
                } else {
                    AssistantMessage(isFromUser = obj.optBoolean("u"), text = text)
                }
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun looksLikeToolCall(partial: String): Boolean {
        val stripped = partial.trim().removePrefix("```json").trim()
        return stripped.startsWith("{")
    }

    /** Stops the in-flight generation; the partial answer (if any) is kept. */
    fun stopGeneration() {
        if (_state.value.thinking) {
            cancelRequested = true
            engine.cancelGeneration()
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
     * On grant, the suspended tool call is dispatched directly — no re-run
     * of the whole question is needed.
     */
    fun onPermissionResult(granted: Boolean) {
        val suspended = suspendedQuery
        suspendedQuery = null
        _state.update { it.copy(pendingPermission = null) }
        if (suspended == null) return
        if (!granted) {
            appendAssistantMessage(appContext.getString(R.string.assistant_error_permission_denied))
            return
        }
        if (_state.value.status != AssistantRuntimeModelStatus.LOADED || _state.value.thinking) return
        runToolLoop(
            loopHistory = suspended.loopHistory,
            resume = ResumeAction.Dispatch(suspended.pendingCall, confirmed = false),
            startIterations = suspended.iterations
        )
    }

    /**
     * Called when the user answers a Yes/No confirmation chip for a risky
     * tool (hide app, delete note, clear notifications).
     */
    fun onConfirmationResult(accepted: Boolean) {
        val suspended = suspendedQuery
        suspendedQuery = null
        _state.update { it.copy(pendingConfirmation = null) }
        if (suspended == null) return
        if (_state.value.status != AssistantRuntimeModelStatus.LOADED || _state.value.thinking) return
        if (!accepted) {
            runToolLoop(
                loopHistory = suspended.loopHistory,
                resume = ResumeAction.Inject(JSONObject().put("cancelled", true).toString()),
                startIterations = suspended.iterations
            )
            return
        }
        runToolLoop(
            loopHistory = suspended.loopHistory,
            resume = ResumeAction.Dispatch(suspended.pendingCall, confirmed = true),
            startIterations = suspended.iterations
        )
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
        val name = call.arguments.optString("name")
        val query = call.arguments.optString("query")
        return when (call.tool) {
            "get_events" -> appContext.getString(R.string.tool_activity_get_events, range.ifBlank { "today" })
            "create_event" -> appContext.getString(R.string.tool_activity_create_event)
            "add_note" -> appContext.getString(R.string.tool_activity_add_note)
            "list_notes", "search_notes", "delete_note" -> appContext.getString(R.string.tool_activity_notes)
            "get_weather" -> appContext.getString(R.string.tool_activity_weather)
            "open_app" -> appContext.getString(R.string.tool_activity_open_app, name)
            "set_alarm" -> appContext.getString(R.string.tool_activity_alarm)
            "set_timer" -> appContext.getString(R.string.tool_activity_timer)
            "set_reminder", "get_reminders", "delete_reminder" ->
                appContext.getString(R.string.tool_activity_reminder)
            "set_theme" -> appContext.getString(R.string.tool_activity_theme)
            "set_ad_block" -> appContext.getString(R.string.tool_activity_ad_block)
            "pin_app" -> appContext.getString(R.string.tool_activity_pin_app, name)
            "hide_app" -> appContext.getString(R.string.tool_activity_hide_app, name)
            "rename_app" -> appContext.getString(R.string.tool_activity_rename_app, name)
            "battery" -> appContext.getString(R.string.tool_activity_battery)
            "media_control" -> appContext.getString(R.string.tool_activity_media)
            "notifications" -> appContext.getString(R.string.tool_activity_notifications)
            "web_search" -> appContext.getString(R.string.tool_activity_web_search)
            "youtube_search" -> appContext.getString(R.string.tool_activity_youtube)
            "navigate_to" -> appContext.getString(R.string.tool_activity_navigate)
            "open_url" -> appContext.getString(R.string.tool_activity_open_url)
            "send_message" -> appContext.getString(R.string.tool_activity_send_message)
            "call" -> appContext.getString(R.string.tool_activity_call)
            "search_contacts" -> appContext.getString(R.string.tool_activity_contacts)
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
        cancelIdleUnload()
        voice.startListening(
            onFinalResult = { text -> sendMessage(text) },
            onError = { _ -> }
        )
    }

    fun stopListening() {
        voice.stopListening()
        onAssistantIdle()
    }

    fun stopSpeaking() {
        tts.stop()
    }

    fun clearConversation() {
        if (_state.value.thinking) return
        tts.stop()
        suspendedQuery = null
        _state.update {
            it.copy(pendingPermission = null, pendingConfirmation = null, partialAnswer = null)
        }
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
        private const val MAX_PARTIAL_CHARS = 220
        private const val MAX_TOOL_ITERATIONS = 3
        private const val MAX_PERSISTED_MESSAGES = 20
        private const val IDLE_UNLOAD_TIMEOUT_MS = 3L * 60 * 1000

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
    val partialAnswer: String? = null,
    val isListening: Boolean = false,
    val partialText: String? = null,
    val voiceError: String? = null,
    val pendingPermission: String? = null,
    val pendingConfirmation: String? = null
)

/** A query paused mid-loop, waiting for a permission grant or confirmation. */
private data class SuspendedQuery(
    val loopHistory: MutableList<AssistantMessage>,
    val pendingCall: ToolCall,
    val iterations: Int
)

/** How a suspended query continues when the user responds. */
private sealed interface ResumeAction {
    data class Dispatch(val call: ToolCall, val confirmed: Boolean) : ResumeAction
    data class Inject(val payload: String) : ResumeAction
}
