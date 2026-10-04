package com.example.foz.ai

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.AlarmClock
import android.util.Log
import com.example.foz.R
import com.example.foz.data.CalendarRepository
import com.example.foz.data.ContactsRepository
import com.example.foz.data.NotificationRepository
import com.example.foz.data.NotesRepository
import com.example.foz.data.PrefsManager
import com.example.foz.data.ReminderRepository
import com.example.foz.memory.MemoryStore
import com.example.foz.model.AppInfo
import com.example.foz.model.WeatherModel
import com.example.foz.reminder.ReminderScheduler
import com.example.foz.reminder.ReminderTime
import com.example.foz.service.MediaSessionListenerService
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

sealed class ToolResult {
    data class Success(val data: JSONObject) : ToolResult()
    data class NeedsPermission(val permission: String) : ToolResult()
    data class NeedsConfirmation(val label: String) : ToolResult()
    data class Error(val message: String) : ToolResult()
}

data class ToolCall(val tool: String, val arguments: JSONObject)

/**
 * Prompt-based tool calling for the local model. The model is instructed to
 * emit {"tool": "...", "arguments": {...}} when it needs an action; results
 * are fed back as [TOOL_RESULT] user turns. Destructive tools return
 * [ToolResult.NeedsConfirmation] on first dispatch and only act when
 * [dispatch] is called again with confirmed=true.
 */
class ToolRegistry(
    private val context: Context,
    private val prefsManager: PrefsManager,
    private val notesRepository: NotesRepository,
    private val calendarRepository: CalendarRepository,
    private val contactsRepository: ContactsRepository,
    private val reminderRepository: ReminderRepository,
    private val reminderScheduler: ReminderScheduler,
    private val memoryStore: MemoryStore,
    private val weatherProvider: suspend () -> WeatherModel?,
    private val findApp: suspend (String) -> AppInfo?,
    private val launchApp: suspend (AppInfo) -> Boolean
) {

    val toolNames: Set<String> = TOOLS.keys

    val systemPromptSection: String
        get() = buildString {
            appendLine("You can use tools to help the user. To use a tool, respond with ONLY a JSON object, no other text, in this exact format:")
            appendLine("{\"tool\": \"tool_name\", \"arguments\": {\"param\": \"value\"}}")
            appendLine("Available tools:")
            TOOLS.forEach { (name, spec) ->
                appendLine("- $name: ${spec.first} arguments: ${spec.second}")
            }
            appendLine("Rules:")
            appendLine("- Use a tool ONLY when needed to answer or act.")
            appendLine("- After receiving a [TOOL_RESULT], answer the user in their own language using that result.")
            appendLine("- If no tool is needed, answer normally WITHOUT using JSON.")
            appendLine("- Dates must use format YYYY-MM-DD and times HH:mm (24h). Today is given in the date above.")
        }

    /** Extracts a tool call from model output; null means "plain answer". */
    fun parseToolCall(output: String): ToolCall? = parseToolCallStatic(output)

    suspend fun dispatch(call: ToolCall, confirmed: Boolean = false): ToolResult {
        return try {
            when (call.tool) {
                "get_events" -> getEvents(call.arguments)
                "create_event" -> createEvent(call.arguments)
                "add_note" -> addNote(call.arguments)
                "list_notes" -> listNotes()
                "search_notes" -> searchNotes(call.arguments)
                "delete_note" -> deleteNote(call.arguments, confirmed)
                "get_weather" -> getWeather()
                "open_app" -> openApp(call.arguments)
                "set_alarm" -> setAlarm(call.arguments)
                "set_timer" -> setTimer(call.arguments)
                "set_reminder" -> setReminder(call.arguments)
                "get_reminders" -> getReminders()
                "delete_reminder" -> deleteReminder(call.arguments, confirmed)
                "recall_memory" -> recallMemory(call.arguments)
                "set_theme" -> setTheme(call.arguments)
                "set_ad_block" -> setAdBlock(call.arguments)
                "pin_app" -> pinApp(call.arguments)
                "hide_app" -> hideApp(call.arguments, confirmed)
                "rename_app" -> renameApp(call.arguments)
                "battery" -> batteryLevel()
                "media_control" -> mediaControl(call.arguments)
                "notifications" -> notifications(call.arguments, confirmed)
                "web_search" -> webSearch(call.arguments)
                "youtube_search" -> youtubeSearch(call.arguments)
                "navigate_to" -> navigateTo(call.arguments)
                "open_url" -> openUrl(call.arguments)
                "send_message" -> sendMessage(call.arguments)
                "call" -> callContact(call.arguments)
                "search_contacts" -> searchContacts(call.arguments)
                else -> ToolResult.Error("Unknown tool \"${call.tool}\". Available: ${toolNames.joinToString()}")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Tool ${call.tool} failed", t)
            ToolResult.Error(t.message ?: "Tool failed")
        }
    }

    // ---------- Calendar ----------

    private suspend fun getEvents(args: JSONObject): ToolResult {
        if (!calendarRepository.hasReadPermission()) {
            return ToolResult.NeedsPermission("READ_CALENDAR")
        }
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val range = args.optString("range", "today").lowercase(Locale.ROOT)
        val start = when (range) {
            "tomorrow" -> today.plusDays(1)
            "week" -> today
            else -> today
        }.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = when (range) {
            "tomorrow" -> today.plusDays(2)
            "week" -> today.plusDays(7)
            else -> today.plusDays(1)
        }.atStartOfDay(zone).toInstant().toEpochMilli()

        val events = calendarRepository.getEvents(start, end)
        val array = JSONArray()
        val fmt = DateTimeFormatter.ofPattern("EEE d MMM HH:mm", Locale.getDefault())
        events.forEach { event ->
            val obj = JSONObject()
                .put("title", event.title)
                .put(
                    "start",
                    if (event.allDay) {
                        "all day " + java.time.Instant.ofEpochMilli(event.startMillis)
                            .atZone(zone).toLocalDate().format(
                                DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
                            )
                    } else {
                        java.time.Instant.ofEpochMilli(event.startMillis).atZone(zone)
                            .toLocalDateTime().format(fmt)
                    }
                )
            if (event.location?.isNotBlank() == true) obj.put("location", event.location)
            array.put(obj)
        }
        return ToolResult.Success(
            JSONObject()
                .put("events", array)
                .put("count", events.size)
        )
    }

    private suspend fun createEvent(args: JSONObject): ToolResult {
        if (!calendarRepository.hasWritePermission()) {
            return ToolResult.NeedsPermission("WRITE_CALENDAR")
        }
        val title = args.optString("title").trim()
        if (title.isEmpty()) return ToolResult.Error("Missing event title")
        val date = parseDate(args.optString("date"))
            ?: return ToolResult.Error("Invalid date \"${args.optString("date")}\". Use YYYY-MM-DD.")
        val time = parseTime(args.optString("time"))
            ?: return ToolResult.Error("Invalid time \"${args.optString("time")}\". Use HH:mm 24h.")
        val durationMinutes = if (args.has("duration_minutes")) {
            args.optLong("duration_minutes", 60L).coerceIn(5L, 24L * 60)
        } else 60L
        val start = LocalDateTime.of(date, time)
        val end = start.plusMinutes(durationMinutes)
        val zone = ZoneId.systemDefault()
        val id = calendarRepository.createEvent(
            title = title,
            startMillis = start.atZone(zone).toInstant().toEpochMilli(),
            endMillis = end.atZone(zone).toInstant().toEpochMilli()
        )
        return ToolResult.Success(
            JSONObject()
                .put("created", true)
                .put("event_id", id)
                .put("title", title)
                .put("when", start.format(DateTimeFormatter.ofPattern("EEE d MMM HH:mm", Locale.getDefault())))
        )
    }

    // ---------- Notes ----------

    private suspend fun addNote(args: JSONObject): ToolResult {
        val title = args.optString("title", "Note").trim()
        val content = args.optString("content").trim()
        if (content.isEmpty() && title.isEmpty()) return ToolResult.Error("Empty note")
        val note = notesRepository.addNote(title.ifEmpty { "Note" }, content)
        return ToolResult.Success(
            JSONObject().put("saved", true).put("title", note.title)
        )
    }

    private suspend fun listNotes(): ToolResult {
        val notes = notesRepository.getAllNotes()
        val array = JSONArray()
        notes.take(MAX_LIST_RESULTS).forEach { note ->
            array.put(
                JSONObject()
                    .put("title", note.title)
                    .put("content", note.content.take(120))
            )
        }
        return ToolResult.Success(
            JSONObject().put("notes", array).put("count", notes.size)
        )
    }

    private suspend fun searchNotes(args: JSONObject): ToolResult {
        val query = args.optString("query").trim()
        if (query.isEmpty()) return ToolResult.Error("Missing search query")
        val notes = notesRepository.searchNotes(query)
        val array = JSONArray()
        notes.take(MAX_LIST_RESULTS).forEach { note ->
            array.put(
                JSONObject()
                    .put("title", note.title)
                    .put("content", note.content.take(120))
            )
        }
        return ToolResult.Success(
            JSONObject().put("notes", array).put("count", notes.size)
        )
    }

    private suspend fun deleteNote(args: JSONObject, confirmed: Boolean): ToolResult {
        val query = args.optString("query").trim()
        if (query.isEmpty()) return ToolResult.Error("Missing note to delete")
        if (!confirmed) {
            return ToolResult.NeedsConfirmation(context.getString(R.string.confirm_delete_note, query))
        }
        val deleted = notesRepository.deleteNote(query)
        return if (deleted != null) {
            ToolResult.Success(JSONObject().put("deleted", true).put("title", deleted.title))
        } else {
            ToolResult.Error("No note matching \"$query\"")
        }
    }

    // ---------- Weather ----------

    private suspend fun getWeather(): ToolResult {
        val weather = weatherProvider()
            ?: return ToolResult.Error("No weather data available yet. Weather refreshes automatically.")
        return ToolResult.Success(
            JSONObject()
                .put("temperature_c", weather.temperature)
                .put("condition", weather.condition)
                .put("city", weather.location)
                .put("wind_kmh", weather.windSpeed)
                .put("humidity_pct", weather.humidity)
        )
    }

    // ---------- Apps ----------

    private suspend fun openApp(args: JSONObject): ToolResult {
        val name = args.optString("name").trim()
        if (name.isEmpty()) return ToolResult.Error("Missing app name")
        val app = findApp(name) ?: return ToolResult.Error("No installed app matches \"$name\"")
        val launched = launchApp(app)
        return if (launched) {
            ToolResult.Success(JSONObject().put("opened", app.name))
        } else {
            ToolResult.Error("Could not open ${app.name}")
        }
    }

    private suspend fun pinApp(args: JSONObject): ToolResult {
        val name = args.optString("name").trim()
        if (name.isEmpty()) return ToolResult.Error("Missing app name")
        val app = findApp(name) ?: return ToolResult.Error("No installed app matches \"$name\"")
        val pinned = args.optBoolean("pinned", true)
        prefsManager.setAppPinned(app.packageName, pinned)
        return ToolResult.Success(
            JSONObject().put("app", app.name).put("pinned", pinned)
        )
    }

    private suspend fun hideApp(args: JSONObject, confirmed: Boolean): ToolResult {
        val name = args.optString("name").trim()
        if (name.isEmpty()) return ToolResult.Error("Missing app name")
        val app = findApp(name) ?: return ToolResult.Error("No installed app matches \"$name\"")
        val hidden = args.optBoolean("hidden", true)
        if (hidden && !confirmed) {
            return ToolResult.NeedsConfirmation(context.getString(R.string.confirm_hide_app, app.name))
        }
        prefsManager.setAppHidden(app.packageName, hidden)
        return ToolResult.Success(
            JSONObject().put("app", app.name).put("hidden", hidden)
        )
    }

    private suspend fun renameApp(args: JSONObject): ToolResult {
        val name = args.optString("name").trim()
        val newName = args.optString("new_name").trim()
        if (name.isEmpty()) return ToolResult.Error("Missing app name")
        if (newName.isEmpty()) return ToolResult.Error("Missing new_name")
        val app = findApp(name) ?: return ToolResult.Error("No installed app matches \"$name\"")
        prefsManager.setCustomAppName(app.packageName, newName)
        return ToolResult.Success(
            JSONObject().put("app", app.name).put("renamed_to", newName)
        )
    }

    // ---------- Reminders ----------

    private suspend fun setReminder(args: JSONObject): ToolResult {
        val title = args.optString("title").trim()
        if (title.isEmpty()) return ToolResult.Error("Missing reminder title")
        val zone = ZoneId.systemDefault()
        val trigger = ReminderTime.compute(
            dateStr = args.optString("date").trim().ifEmpty { null },
            timeStr = args.optString("time"),
            nowMillis = System.currentTimeMillis(),
            zone = zone
        ) ?: return ToolResult.Error(
            "Invalid date \"${args.optString("date")}\" or time \"${args.optString("time")}\". " +
                "Use date YYYY-MM-DD and time HH:mm 24h."
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return ToolResult.NeedsPermission(Manifest.permission.POST_NOTIFICATIONS)
        }
        val triggerAt = trigger.atZone(zone).toInstant().toEpochMilli()
        val reminder = reminderRepository.addReminder(title, triggerAt)
        reminderScheduler.schedule(reminder)
        return ToolResult.Success(
            JSONObject()
                .put("reminder_set", true)
                .put("title", reminder.title)
                .put("when", trigger.format(DateTimeFormatter.ofPattern("EEE d MMM HH:mm", Locale.getDefault())))
        )
    }

    private suspend fun getReminders(): ToolResult {
        val pending = reminderRepository.pendingReminders()
        val array = JSONArray()
        val fmt = DateTimeFormatter.ofPattern("EEE d MMM HH:mm", Locale.getDefault())
        pending.forEach { reminder ->
            array.put(
                JSONObject()
                    .put("title", reminder.title)
                    .put(
                        "when",
                        java.time.Instant.ofEpochMilli(reminder.triggerAt).atZone(ZoneId.systemDefault())
                            .toLocalDateTime().format(fmt)
                    )
            )
        }
        return ToolResult.Success(
            JSONObject().put("reminders", array).put("count", pending.size)
        )
    }

    private suspend fun deleteReminder(args: JSONObject, confirmed: Boolean): ToolResult {
        val query = args.optString("query").trim()
        if (query.isEmpty()) return ToolResult.Error("Missing reminder to delete")
        if (!confirmed) {
            return ToolResult.NeedsConfirmation(context.getString(R.string.confirm_delete_reminder, query))
        }
        val deleted = reminderRepository.deleteReminder(query)
            ?: return ToolResult.Error("No reminder matching \"$query\"")
        reminderScheduler.cancel(deleted.id)
        return ToolResult.Success(JSONObject().put("deleted", true).put("title", deleted.title))
    }

    // ---------- Memory ----------

    private suspend fun recallMemory(args: JSONObject): ToolResult {
        val subject = args.optString("subject").trim().lowercase(Locale.ROOT)
        if (subject !in MemoryStore.SUBJECTS) {
            return ToolResult.Error(
                "Unknown memory subject \"$subject\". Use: ${MemoryStore.SUBJECTS.joinToString()}"
            )
        }
        val facts = memoryStore.readSubject(subject)
        val array = JSONArray()
        facts.forEach { array.put(it.text) }
        return ToolResult.Success(
            JSONObject()
                .put("subject", subject)
                .put("facts", array)
                .put("count", facts.size)
        )
    }

    // ---------- Launcher settings ----------

    private suspend fun setTheme(args: JSONObject): ToolResult {
        val mode = args.optString("mode").trim().lowercase(Locale.ROOT)
        if (mode !in setOf("dark", "light", "system")) {
            return ToolResult.Error("Invalid mode. Use dark, light or system.")
        }
        prefsManager.setThemeMode(mode)
        return ToolResult.Success(JSONObject().put("theme_set", mode))
    }

    private suspend fun setAdBlock(args: JSONObject): ToolResult {
        val enabled = args.optBoolean("enabled", true)
        prefsManager.setAdBlockEnabled(enabled)
        return ToolResult.Success(
            JSONObject()
                .put("ad_block", enabled)
                .put(
                    "note",
                    if (enabled) "System may show a VPN permission prompt." else "Ad blocker stopped."
                )
        )
    }

    private suspend fun batteryLevel(): ToolResult {
        return withContext(Dispatchers.Main) {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                ?: return@withContext ToolResult.Error("Battery service unavailable")
            val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            if (level in 1..100) {
                ToolResult.Success(JSONObject().put("battery_percent", level))
            } else {
                ToolResult.Error("Could not read battery level")
            }
        }
    }

    private suspend fun mediaControl(args: JSONObject): ToolResult {
        val action = args.optString("action").trim().lowercase(Locale.ROOT)
        val controller = com.example.foz.MediaControllerManager.getInstance(context)
        return withContext(Dispatchers.Main) {
            when (action) {
                "play" -> { controller.play(); JSONObject().put("playing", true) }
                "pause" -> { controller.pause(); JSONObject().put("paused", true) }
                "next" -> { controller.next(); JSONObject().put("skipped", "next") }
                "previous" -> { controller.previous(); JSONObject().put("skipped", "previous") }
                else -> null
            }?.let { ToolResult.Success(it) }
                ?: ToolResult.Error("Invalid action. Use play, pause, next or previous.")
        }
    }

    private suspend fun notifications(args: JSONObject, confirmed: Boolean): ToolResult {
        val action = args.optString("action", "read").trim().lowercase(Locale.ROOT)
        val all = NotificationRepository.getInstance().notifications.value
        return when (action) {
            "read" -> {
                val array = JSONArray()
                all.take(MAX_LIST_RESULTS).forEach { n ->
                    val appName = try {
                        context.packageManager.getApplicationLabel(
                            context.packageManager.getApplicationInfo(n.packageName, 0)
                        ).toString()
                    } catch (_: Throwable) {
                        n.packageName
                    }
                    array.put(
                        JSONObject()
                            .put("app", appName)
                            .put("title", n.title?.toString()?.take(60) ?: "")
                            .put("text", n.text?.toString()?.take(80) ?: "")
                    )
                }
                ToolResult.Success(
                    JSONObject().put("notifications", array).put("count", all.size)
                )
            }
            "clear" -> {
                if (!confirmed) {
                    return ToolResult.NeedsConfirmation(context.getString(R.string.confirm_clear_notifications))
                }
                withContext(Dispatchers.Main) {
                    all.filter { it.isClearable }.forEach { MediaSessionListenerService.cancelNotification(it.key) }
                }
                ToolResult.Success(JSONObject().put("cleared", true))
            }
            else -> ToolResult.Error("Invalid action. Use read or clear.")
        }
    }

    // ---------- Web / deep links ----------

    private suspend fun webSearch(args: JSONObject): ToolResult {
        val query = args.optString("query").trim()
        if (query.isEmpty()) return ToolResult.Error("Missing search query")
        return startViewIntent(
            Uri.parse("https://www.google.com/search?q=" + Uri.encode(query)),
            JSONObject().put("searching_web", query)
        )
    }

    private suspend fun youtubeSearch(args: JSONObject): ToolResult {
        val query = args.optString("query").trim()
        if (query.isEmpty()) return ToolResult.Error("Missing search query")
        return startViewIntent(
            Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(query)),
            JSONObject().put("searching_youtube", query)
        )
    }

    private suspend fun navigateTo(args: JSONObject): ToolResult {
        val place = args.optString("place").trim()
        if (place.isEmpty()) return ToolResult.Error("Missing place")
        return startViewIntent(
            Uri.parse("geo:0,0?q=" + Uri.encode(place)),
            JSONObject().put("navigating_to", place)
        )
    }

    private suspend fun openUrl(args: JSONObject): ToolResult {
        var url = args.optString("url").trim()
        if (url.isEmpty()) return ToolResult.Error("Missing url")
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://$url"
        }
        return startViewIntent(Uri.parse(url), JSONObject().put("opened_url", url))
    }

    private suspend fun sendMessage(args: JSONObject): ToolResult {
        val app = args.optString("app", "whatsapp").trim().lowercase(Locale.ROOT)
        val text = args.optString("text").trim()
        val phoneRaw = args.optString("phone").trim()
        val contact = args.optString("contact").trim()
        if (text.isEmpty()) return ToolResult.Error("Missing message text")

        var number = phoneRaw.filter { it.isDigit() || it == '+' }
        var resolvedName = ""
        if (number.isBlank() && contact.isNotBlank()) {
            if (!contactsRepository.hasPermission()) {
                return ToolResult.NeedsPermission("READ_CONTACTS")
            }
            val matches = contactsRepository.findPhoneNumbers(contact, limit = 1)
            if (matches.isEmpty()) return ToolResult.Error("No contact matching \"$contact\"")
            resolvedName = matches.first().name
            number = matches.first().phoneNumber.filter { it.isDigit() || it == '+' }
        }

        return when (app) {
            "whatsapp" -> {
                val uri = if (number.isNotBlank()) {
                    Uri.parse("https://wa.me/${number.trimStart('+')}?text=" + Uri.encode(text))
                } else {
                    Uri.parse("https://api.whatsapp.com/send?text=" + Uri.encode(text))
                }
                startViewIntent(uri, messageSuccess(app, resolvedName.ifBlank { number }, text))
            }
            "sms" -> {
                if (number.isBlank()) return ToolResult.Error("SMS needs a phone or contact")
                runSystemAction(
                    Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")).apply {
                        putExtra("sms_body", text)
                    },
                    messageSuccess("sms", resolvedName.ifBlank { number }, text)
                )
            }
            else -> ToolResult.Error("Invalid app. Use whatsapp or sms.")
        }
    }

    private suspend fun callContact(args: JSONObject): ToolResult {
        val phoneRaw = args.optString("phone").trim()
        val contact = args.optString("contact").trim()
        var number = phoneRaw.filter { it.isDigit() || it == '+' }
        if (number.isBlank() && contact.isNotBlank()) {
            if (!contactsRepository.hasPermission()) {
                return ToolResult.NeedsPermission("READ_CONTACTS")
            }
            val matches = contactsRepository.findPhoneNumbers(contact, limit = 1)
            if (matches.isEmpty()) return ToolResult.Error("No contact matching \"$contact\"")
            number = matches.first().phoneNumber.filter { it.isDigit() || it == '+' }
        }
        if (number.isBlank()) return ToolResult.Error("Missing phone or contact")
        return runSystemAction(
            Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")),
            JSONObject().put("dialer_opened", true).put("number", number)
        )
    }

    private suspend fun searchContacts(args: JSONObject): ToolResult {
        val query = args.optString("query").trim()
        if (query.isEmpty()) return ToolResult.Error("Missing search query")
        if (!contactsRepository.hasPermission()) {
            return ToolResult.NeedsPermission("READ_CONTACTS")
        }
        val matches = contactsRepository.findPhoneNumbers(query)
        val array = JSONArray()
        matches.forEach { match ->
            array.put(JSONObject().put("name", match.name).put("phone", match.phoneNumber))
        }
        return ToolResult.Success(
            JSONObject().put("contacts", array).put("count", matches.size)
        )
    }

    // ---------- Alarms ----------

    private suspend fun setAlarm(args: JSONObject): ToolResult {
        val hour = args.optInt("hour", -1)
        val minute = args.optInt("minute", -1)
        if (hour !in 0..23 || minute !in 0..59) {
            return ToolResult.Error("Invalid alarm time. hour 0-23, minute 0-59.")
        }
        return runSystemAction(
            Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            },
            JSONObject().put("alarm_set", true).put("time", String.format(Locale.US, "%02d:%02d", hour, minute))
        )
    }

    private suspend fun setTimer(args: JSONObject): ToolResult {
        val minutes = args.optInt("minutes", -1)
        if (minutes <= 0 || minutes > 24 * 60) {
            return ToolResult.Error("Invalid timer duration in minutes.")
        }
        return runSystemAction(
            Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, minutes * 60)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            },
            JSONObject().put("timer_set", true).put("minutes", minutes)
        )
    }

    // ---------- Helpers ----------

    private suspend fun startViewIntent(uri: Uri, success: JSONObject): ToolResult {
        return runSystemAction(Intent(Intent.ACTION_VIEW, uri), success)
    }

    private fun messageSuccess(app: String, destination: String, text: String): JSONObject {
        return JSONObject()
            .put("compose_opened", true)
            .put("app", app)
            .put("to", destination)
            .put("text", text.take(120))
            .put("note", "Message is pre-filled; the user sends it.")
    }

    private suspend fun runSystemAction(intent: Intent, success: JSONObject): ToolResult {
        return withContext(Dispatchers.Main) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                ToolResult.Success(success)
            } catch (_: Throwable) {
                ToolResult.Error("No app on this device can handle that action")
            }
        }
    }

    private fun parseDate(value: String): LocalDate? {
        return try {
            LocalDate.parse(value.trim())
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private fun parseTime(value: String): LocalTime? {
        return try {
            LocalTime.parse(value.trim())
        } catch (_: DateTimeParseException) {
            try {
                LocalTime.parse(value.trim(), DateTimeFormatter.ofPattern("H:mm"))
            } catch (_: Throwable) {
                null
            }
        }
    }

    companion object {
        private const val TAG = "ToolRegistry"
        private const val MAX_LIST_RESULTS = 10

        /** Extracts a tool call from model output; null means "plain answer". */
        fun parseToolCallStatic(output: String): ToolCall? {
            val cleaned = output
                .replace("```json", "")
                .replace("```", "")
                .trim()
            val start = cleaned.indexOf('{')
            if (start < 0) return null
            val end = cleaned.lastIndexOf('}')
            if (end <= start) return null
            return try {
                val obj = JSONObject(cleaned.substring(start, end + 1))
                val tool = obj.optString("tool").trim()
                if (tool.isEmpty()) null else ToolCall(tool, obj.optJSONObject("arguments") ?: JSONObject())
            } catch (_: Throwable) {
                null
            }
        }

        private val TOOLS: Map<String, Pair<String, String>> = linkedMapOf(
            "get_events" to ("List calendar events." to "{\"range\": \"today\"|\"tomorrow\"|\"week\"}"),
            "create_event" to ("Create calendar event." to "{\"title\": \"...\", \"date\": \"YYYY-MM-DD\", \"time\": \"HH:mm\", \"duration_minutes\": 60}"),
            "add_note" to ("Save a private note." to "{\"title\": \"...\", \"content\": \"...\"}"),
            "list_notes" to ("List all notes." to "{}"),
            "search_notes" to ("Search notes." to "{\"query\": \"...\"}"),
            "delete_note" to ("Delete a note." to "{\"query\": \"...\"}"),
            "get_weather" to ("Current weather." to "{}"),
            "open_app" to ("Open an installed app." to "{\"name\": \"WhatsApp\"}"),
            "set_alarm" to ("Set an alarm." to "{\"hour\": 7, \"minute\": 30}"),
            "set_timer" to ("Start a timer (minutes)." to "{\"minutes\": 10}"),
            "set_reminder" to ("Set a reminder notification." to "{\"title\": \"...\", \"time\": \"HH:mm\", \"date\": \"YYYY-MM-DD\"}"),
            "get_reminders" to ("List pending reminders." to "{}"),
            "delete_reminder" to ("Delete a reminder." to "{\"query\": \"...\"}"),
            "set_theme" to ("Change launcher theme." to "{\"mode\": \"dark\"|\"light\"|\"system\"}"),
            "set_ad_block" to ("Toggle ad blocker." to "{\"enabled\": true}"),
            "pin_app" to ("Pin/unpin app to favorites." to "{\"name\": \"...\", \"pinned\": true}"),
            "hide_app" to ("Hide/unhide app from drawer." to "{\"name\": \"...\", \"hidden\": true}"),
            "rename_app" to ("Rename an app." to "{\"name\": \"...\", \"new_name\": \"...\"}"),
            "battery" to ("Battery level." to "{}"),
            "media_control" to ("Control music." to "{\"action\": \"play\"|\"pause\"|\"next\"|\"previous\"}"),
            "notifications" to ("Read or clear notifications." to "{\"action\": \"read\"|\"clear\"}"),
            "web_search" to ("Google search." to "{\"query\": \"...\"}"),
            "youtube_search" to ("YouTube search." to "{\"query\": \"...\"}"),
            "navigate_to" to ("Maps navigation." to "{\"place\": \"...\"}"),
            "open_url" to ("Open a website." to "{\"url\": \"example.com\"}"),
            "send_message" to ("Open WhatsApp/SMS with pre-filled message (user sends)." to "{\"app\": \"whatsapp\"|\"sms\", \"text\": \"...\", \"contact\": \"...\"}"),
            "call" to ("Open dialer with number." to "{\"contact\": \"...\"}"),
            "search_contacts" to ("Look up contact numbers." to "{\"query\": \"...\"}"),
            "recall_memory" to ("Recall remembered facts about the user." to "{\"subject\": \"profile\"|\"preferences\"|\"work\"|\"family\"|\"health\"|\"finance\"|\"schedule\"|\"places\"}")
        )
    }
}
