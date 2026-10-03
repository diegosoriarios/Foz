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

private val Context.assistantNotesStore by preferencesDataStore(name = "assistant_notes")

data class Note(
    val id: String,
    val title: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * Private, on-device notes owned by the assistant. Stored as JSON in DataStore.
 */
class NotesRepository(private val context: Context) {

    private val notesKey = stringPreferencesKey("notes_json")

    val notes: Flow<List<Note>> = context.assistantNotesStore.data.map { prefs ->
        parseNotes(prefs[notesKey])
    }

    suspend fun addNote(title: String, content: String): Note {
        val now = System.currentTimeMillis()
        val note = Note(
            id = "note_$now",
            title = title.ifBlank { "Note" },
            content = content,
            createdAt = now,
            updatedAt = now
        )
        context.assistantNotesStore.edit { prefs ->
            val all = parseNotes(prefs[notesKey]).toMutableList()
            all.add(0, note)
            prefs[notesKey] = toJson(all)
        }
        return note
    }

    suspend fun getAllNotes(): List<Note> = notes.first()

    suspend fun searchNotes(query: String): List<Note> {
        val normalized = query.trim().lowercase()
        if (normalized.isEmpty()) return getAllNotes()
        return getAllNotes().filter {
            it.title.lowercase().contains(normalized) ||
                it.content.lowercase().contains(normalized)
        }
    }

    suspend fun deleteNote(query: String): Note? {
        val matches = searchNotes(query)
        val target = matches.firstOrNull() ?: return null
        context.assistantNotesStore.edit { prefs ->
            val all = parseNotes(prefs[notesKey]).filterNot { it.id == target.id }
            prefs[notesKey] = toJson(all)
        }
        return target
    }

    private fun parseNotes(json: String?): List<Note> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                Note(
                    id = obj.optString("id"),
                    title = obj.optString("title"),
                    content = obj.optString("content"),
                    createdAt = obj.optLong("createdAt"),
                    updatedAt = obj.optLong("updatedAt")
                )
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun toJson(notes: List<Note>): String {
        val array = JSONArray()
        notes.forEach { note ->
            array.put(
                JSONObject()
                    .put("id", note.id)
                    .put("title", note.title)
                    .put("content", note.content)
                    .put("createdAt", note.createdAt)
                    .put("updatedAt", note.updatedAt)
            )
        }
        return array.toString()
    }
}
