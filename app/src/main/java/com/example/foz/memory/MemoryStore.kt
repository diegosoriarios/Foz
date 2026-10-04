package com.example.foz.memory

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

data class MemoryFact(
    val id: String,
    val text: String,
    val createdAt: Long
)

/**
 * Subject-partitioned, encrypted user memory. One AES-GCM file per subject
 * under filesDir/assistant_memory/. Decrypt failures quarantine the file
 * (renamed .corrupt) instead of crashing; "forget everything" deletes the
 * files AND destroys the cipher key.
 */
class MemoryStore(
    private val dir: File,
    private val cipher: MemoryCipher
) {

    private val idCounter = java.util.concurrent.atomic.AtomicLong(0)

    init {
        dir.mkdirs()
    }

    fun readSubject(subject: String): List<MemoryFact> {
        val file = fileFor(dir, subject)
        if (!file.exists()) return emptyList()
        return try {
            parseFacts(String(cipher.decrypt(file.readBytes())))
        } catch (_: Throwable) {
            // Tampered or unreadable (e.g. restored file without its key):
            // quarantine so the app keeps working.
            try {
                file.renameTo(File(dir, "${subject}.mem.corrupt"))
            } catch (_: Throwable) {
            }
            emptyList()
        }
    }

    fun writeSubject(subject: String, facts: List<MemoryFact>) {
        val capped = facts.takeLast(MAX_FACTS_PER_SUBJECT)
        val bytes = cipher.encrypt(toJson(capped).toByteArray(Charsets.UTF_8))
        val target = fileFor(dir, subject)
        val temp = File(dir, "${subject}.mem.tmp")
        temp.writeBytes(bytes)
        if (target.exists()) target.delete()
        if (!temp.renameTo(target)) {
            temp.delete()
            throw IllegalStateException("Could not write memory file")
        }
    }

    /** Adds de-duplicated facts; returns how many were actually stored. */
    fun addFacts(subject: String, texts: List<String>): Int {
        if (texts.isEmpty()) return 0
        val existing = readSubject(subject).toMutableList()
        val known = existing.mapTo(mutableSetOf()) { it.text.trim().lowercase() }
        var added = 0
        texts.forEach { raw ->
            val text = raw.trim().take(MAX_FACT_CHARS)
            if (text.isEmpty()) return@forEach
            if (known.add(text.lowercase())) {
                existing.add(
                    MemoryFact(
                        id = "f_${System.currentTimeMillis()}_${idCounter.incrementAndGet()}",
                        text = text,
                        createdAt = System.currentTimeMillis()
                    )
                )
                added++
            }
        }
        if (added > 0) writeSubject(subject, existing)
        return added
    }

    fun deleteFact(subject: String, factId: String) {
        val remaining = readSubject(subject).filterNot { it.id == factId }
        if (remaining.isEmpty()) clearSubject(subject) else writeSubject(subject, remaining)
    }

    fun clearSubject(subject: String) {
        fileFor(dir, subject).delete()
    }

    /** Deletes every memory file and destroys the encryption key (crypto-shred). */
    fun forgetEverything() {
        dir.listFiles()?.forEach { it.delete() }
        cipher.destroyKey()
    }

    fun totalCount(): Int = SUBJECTS.sumOf { readSubject(it).size }

    companion object {
        /** Fixed taxonomy: the model must classify facts into these. */
        val SUBJECTS = listOf(
            "profile", "preferences", "work", "family",
            "health", "finance", "schedule", "places"
        )
        const val MAX_FACTS_PER_SUBJECT = 20
        const val MAX_FACT_CHARS = 150

        fun fileFor(dir: File, subject: String) = File(dir, "$subject.mem")

        internal fun parseFacts(json: String?): List<MemoryFact> {
            if (json.isNullOrBlank()) return emptyList()
            return try {
                val array = JSONArray(json)
                (0 until array.length()).mapNotNull { i ->
                    val obj = array.optJSONObject(i) ?: return@mapNotNull null
                    MemoryFact(
                        id = obj.optString("id"),
                        text = obj.optString("text"),
                        createdAt = obj.optLong("createdAt")
                    )
                }
            } catch (_: Throwable) {
                emptyList()
            }
        }

        internal fun toJson(facts: List<MemoryFact>): String {
            val array = JSONArray()
            facts.forEach { fact ->
                array.put(
                    JSONObject()
                        .put("id", fact.id)
                        .put("text", fact.text)
                        .put("createdAt", fact.createdAt)
                )
            }
            return array.toString()
        }
    }
}
