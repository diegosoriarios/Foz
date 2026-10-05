package com.example.foz.ui.common

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Central sink for user-visible runtime errors (model load, downloads,
 * voice, tools, crash recovery). Consumers:
 *  - [events]: hot flow for banner UI (snackbars).
 *  - [recent]: in-memory ring buffer for the Diagnostics screen.
 *  - [toJson]/[fromJson]: persistence via PrefsManager so the history
 *    survives process death.
 */
object ErrorHub {

    const val MAX_ENTRIES = 30

    data class Entry(val timestampMs: Long, val message: String)

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val events: SharedFlow<String> = _events.asSharedFlow()

    private val recentBuffer = ArrayList<Entry>(MAX_ENTRIES)

    /**
     * Set by the app (AssistantManager) to persist the buffer. Invoked
     * after every record; implementations must be cheap/non-blocking.
     */
    @Volatile
    var persistSink: ((List<Entry>) -> Unit)? = null

    val recent: List<Entry>
        get() = synchronized(recentBuffer) { ArrayList(recentBuffer) }

    /** Loads the persisted history at process start (no event emitted). */
    fun hydrate(entries: List<Entry>) {
        if (entries.isEmpty()) return
        synchronized(recentBuffer) {
            recentBuffer.clear()
            recentBuffer.addAll(entries.takeLast(MAX_ENTRIES))
        }
    }

    fun record(message: String) {
        if (message.isBlank()) return
        val entry = Entry(System.currentTimeMillis(), message)
        val snapshot: List<Entry>
        synchronized(recentBuffer) {
            recentBuffer.add(entry)
            if (recentBuffer.size > MAX_ENTRIES) {
                recentBuffer.removeAt(0)
            }
            snapshot = ArrayList(recentBuffer)
        }
        persistSink?.invoke(snapshot)
        _events.tryEmit(message)
    }

    fun clearRecent() {
        synchronized(recentBuffer) { recentBuffer.clear() }
    }

    fun toJson(entries: List<Entry>): String {
        val array = JSONArray()
        entries.takeLast(MAX_ENTRIES).forEach { entry ->
            array.put(
                JSONObject()
                    .put("t", entry.timestampMs)
                    .put("m", entry.message)
            )
        }
        return array.toString()
    }

    fun fromJson(json: String): List<Entry> {
        if (json.isBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val message = obj.optString("m")
                    if (message.isNotBlank()) {
                        add(Entry(obj.optLong("t", 0L), message))
                    }
                }
            }.takeLast(MAX_ENTRIES)
        } catch (_: Throwable) {
            emptyList()
        }
    }
}
