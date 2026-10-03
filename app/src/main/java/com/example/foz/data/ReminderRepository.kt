package com.example.foz.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.remindersStore by preferencesDataStore(name = "assistant_reminders")

data class Reminder(
    val id: String,
    val title: String,
    val triggerAt: Long,
    val fired: Boolean = false
)

/**
 * Private, on-device reminders owned by the assistant. Stored as JSON in
 * DataStore; the alarm side lives in [com.example.foz.reminder.ReminderScheduler].
 */
class ReminderRepository(private val context: Context) {

    private val remindersKey = stringPreferencesKey("reminders_json")

    val reminders: Flow<List<Reminder>> = context.remindersStore.data.map { prefs ->
        parseReminders(prefs[remindersKey])
    }

    suspend fun addReminder(title: String, triggerAt: Long): Reminder {
        val reminder = Reminder(
            id = "rem_${System.currentTimeMillis()}",
            title = title.ifBlank { "Reminder" },
            triggerAt = triggerAt
        )
        context.remindersStore.edit { prefs ->
            val all = parseReminders(prefs[remindersKey]).toMutableList()
            all.add(reminder)
            prefs[remindersKey] = toJson(all)
        }
        return reminder
    }

    suspend fun getReminders(): List<Reminder> = reminders.first()

    suspend fun pendingReminders(): List<Reminder> =
        getReminders().filter { !it.fired && it.triggerAt > System.currentTimeMillis() }
            .sortedBy { it.triggerAt }

    suspend fun searchReminders(query: String): List<Reminder> {
        val normalized = query.trim().lowercase()
        if (normalized.isEmpty()) return getReminders()
        return getReminders().filter { it.title.lowercase().contains(normalized) }
    }

    suspend fun markFired(id: String) {
        context.remindersStore.edit { prefs ->
            val all = parseReminders(prefs[remindersKey]).map {
                if (it.id == id) it.copy(fired = true) else it
            }
            prefs[remindersKey] = toJson(all)
        }
    }

    /** Deletes the first reminder whose title matches [query]; returns it or null. */
    suspend fun deleteReminder(query: String): Reminder? {
        val target = searchReminders(query).firstOrNull() ?: return null
        context.remindersStore.edit { prefs ->
            val all = parseReminders(prefs[remindersKey]).filterNot { it.id == target.id }
            prefs[remindersKey] = toJson(all)
        }
        return target
    }

    suspend fun deleteById(id: String) {
        context.remindersStore.edit { prefs ->
            val all = parseReminders(prefs[remindersKey]).filterNot { it.id == id }
            prefs[remindersKey] = toJson(all)
        }
    }

    companion object {
        internal fun parseReminders(json: String?): List<Reminder> {
            if (json.isNullOrBlank()) return emptyList()
            return try {
                val array = JSONArray(json)
                (0 until array.length()).mapNotNull { i ->
                    val obj = array.optJSONObject(i) ?: return@mapNotNull null
                    Reminder(
                        id = obj.optString("id"),
                        title = obj.optString("title"),
                        triggerAt = obj.optLong("triggerAt"),
                        fired = obj.optBoolean("fired")
                    )
                }
            } catch (_: Throwable) {
                emptyList()
            }
        }

        internal fun toJson(reminders: List<Reminder>): String {
            val array = JSONArray()
            reminders.forEach { reminder ->
                array.put(
                    JSONObject()
                        .put("id", reminder.id)
                        .put("title", reminder.title)
                        .put("triggerAt", reminder.triggerAt)
                        .put("fired", reminder.fired)
                )
            }
            return array.toString()
        }
    }
}
