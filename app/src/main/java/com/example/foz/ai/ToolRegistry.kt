package com.example.foz.ai

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.util.Log
import com.example.foz.data.CalendarRepository
import com.example.foz.data.NotesRepository
import com.example.foz.model.AppInfo
import com.example.foz.model.WeatherModel
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

sealed class ToolResult {
    data class Success(val data: JSONObject) : ToolResult()
    data class NeedsPermission(val permission: String) : ToolResult()
    data class Error(val message: String) : ToolResult()
}

data class ToolCall(val tool: String, val arguments: JSONObject)

/**
 * Prompt-based tool calling for the local model. The model is instructed to
 * emit {"tool": "...", "arguments": {...}} when it needs an action; results
 * are fed back as [TOOL_RESULT] user turns.
 */
class ToolRegistry(
    private val context: Context,
    private val notesRepository: NotesRepository,
    private val calendarRepository: CalendarRepository,
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

    suspend fun dispatch(call: ToolCall): ToolResult {
        return try {
            when (call.tool) {
                "get_events" -> getEvents(call.arguments)
                "create_event" -> createEvent(call.arguments)
                "add_note" -> addNote(call.arguments)
                "list_notes" -> listNotes()
                "search_notes" -> searchNotes(call.arguments)
                "delete_note" -> deleteNote(call.arguments)
                "get_weather" -> getWeather()
                "open_app" -> openApp(call.arguments)
                "set_alarm" -> setAlarm(call.arguments)
                "set_timer" -> setTimer(call.arguments)
                else -> ToolResult.Error("Unknown tool \"${call.tool}\". Available: ${toolNames.joinToString()}")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Tool ${call.tool} failed", t)
            ToolResult.Error(t.message ?: "Tool failed")
        }
    }

    private suspend fun getEvents(args: JSONObject): ToolResult {
        if (!calendarRepository.hasReadPermission()) {
            return ToolResult.NeedsPermission("READ_CALENDAR")
        }
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val start = when (args.optString("range", "today").lowercase(Locale.ROOT)) {
            "tomorrow" -> today.plusDays(1)
            "week" -> today
            else -> today
        }.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = when (args.optString("range", "today").lowercase(Locale.ROOT)) {
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

    private suspend fun deleteNote(args: JSONObject): ToolResult {
        val query = args.optString("query").trim()
        if (query.isEmpty()) return ToolResult.Error("Missing note to delete")
        val deleted = notesRepository.deleteNote(query)
        return if (deleted != null) {
            ToolResult.Success(JSONObject().put("deleted", true).put("title", deleted.title))
        } else {
            ToolResult.Error("No note matching \"$query\"")
        }
    }

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

    private suspend fun runSystemAction(intent: Intent, success: JSONObject): ToolResult {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
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
            "get_events" to (
                "List calendar events." to
                    "{\"range\": \"today\" or \"tomorrow\" or \"week\"}"
                ),
            "create_event" to (
                "Create a calendar event." to
                    "{\"title\": \"...\", \"date\": \"YYYY-MM-DD\", \"time\": \"HH:mm\", \"duration_minutes\": 60}"
                ),
            "add_note" to (
                "Save a private note on the device." to
                    "{\"title\": \"...\", \"content\": \"...\"}"
                ),
            "list_notes" to (
                "List all saved notes." to
                    "{}"
                ),
            "search_notes" to (
                "Search notes by keyword." to
                    "{\"query\": \"...\"}"
                ),
            "delete_note" to (
                "Delete a note matching a keyword." to
                    "{\"query\": \"...\"}"
                ),
            "get_weather" to (
                "Current weather." to
                    "{}"
                ),
            "open_app" to (
                "Open an installed app by name." to
                    "{\"name\": \"WhatsApp\"}"
                ),
            "set_alarm" to (
                "Set an alarm." to
                    "{\"hour\": 7, \"minute\": 30}"
                ),
            "set_timer" to (
                "Start a countdown timer in minutes." to
                    "{\"minutes\": 10}"
                )
        )
    }
}
