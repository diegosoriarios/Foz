package com.example.foz.memory

import org.json.JSONArray

/** Pure prompt-building and output-parsing helpers for the memory feature. */
object MemoryPrompts {

    /** Injected into every prompt (cheap, the "who am I" core). */
    val CORE_SUBJECTS = listOf("profile", "preferences")

    private const val CORE_FACTS_PER_SUBJECT = 10
    private const val MAX_EXTRACTED_FACTS = 5

    /**
     * The always-present memory block: core subject facts plus the index of
     * subjects the model can pull with the recall_memory tool. Null when
     * memory is empty (no noise in the prompt).
     */
    fun memorySection(factsBySubject: Map<String, List<MemoryFact>>): String? {
        val hasAny = MemoryStore.SUBJECTS.any { !factsBySubject[it].isNullOrEmpty() }
        if (!hasAny) return null
        val recall = MemoryStore.SUBJECTS.filter { it !in CORE_SUBJECTS }
        return buildString {
            append("\nWhat you know about the user (learned from what they told you; use it naturally):\n")
            CORE_SUBJECTS.forEach { subject ->
                factsBySubject[subject]
                    ?.take(CORE_FACTS_PER_SUBJECT)
                    ?.forEach { append("- ${it.text}\n") }
            }
            append(
                "You can recall more remembered subjects with the recall_memory tool: " +
                    recall.joinToString(", ") + "\n"
            )
        }
    }

    fun extractionPrompt(
        existingSummary: String,
        userText: String,
        assistantText: String
    ): String {
        return buildString {
            append(
                "From the exchange below, extract durable facts about the user worth " +
                    "remembering long-term (identity, preferences, people, work, health, " +
                    "finances, recurring events, places). Ignore transient requests and " +
                    "tool outputs. If the user asks you not to remember something, " +
                    "extract nothing. Reply with ONLY a JSON array, empty [] if none: " +
                    "[{\"subject\":\"<profile|preferences|work|family|health|finance|schedule|places>\"," +
                    "\"text\":\"<short fact, max 120 chars>\"}]\n\n"
            )
            if (existingSummary.isNotBlank()) {
                append("Already known (do not repeat):\n$existingSummary\n\n")
            }
            append("User: $userText\n")
            append("Assistant: $assistantText\n")
        }
    }

    /**
     * Tolerant parser for the extraction output: accepts a bare array or a
     * fenced JSON block, object or pair entries; drops unknown subjects,
     * overlong texts and duplicates; caps the result.
     */
    fun parseExtraction(raw: String): List<Pair<String, String>> {
        val cleaned = raw
            .replace("```json", "", ignoreCase = true)
            .replace("```", "")
            .trim()
        val start = cleaned.indexOf('[')
        val end = cleaned.lastIndexOf(']')
        if (start < 0 || end <= start) return emptyList()
        val results = mutableListOf<Pair<String, String>>()
        val seen = mutableSetOf<String>()
        try {
            val array = JSONArray(cleaned.substring(start, end + 1))
            (0 until array.length()).forEach { i ->
                val entry = array.opt(i) ?: return@forEach
                var subject: String? = null
                var text: String? = null
                if (entry is org.json.JSONObject) {
                    subject = entry.optString("subject")
                    text = entry.optString("text")
                } else if (entry is JSONArray && entry.length() >= 2) {
                    subject = entry.optString(0)
                    text = entry.optString(1)
                }
                val s = subject?.trim()?.lowercase() ?: return@forEach
                val t = text?.trim() ?: return@forEach
                if (s !in MemoryStore.SUBJECTS) return@forEach
                if (t.isEmpty() || t.length > MemoryStore.MAX_FACT_CHARS) return@forEach
                if (seen.add("$s|$t")) results.add(s to t)
            }
        } catch (_: Throwable) {
            return emptyList()
        }
        return results.take(MAX_EXTRACTED_FACTS)
    }

    /** One-line-per-fact summary of what is already stored (for dedup prompts). */
    fun existingSummary(factsBySubject: Map<String, List<MemoryFact>>): String {
        return buildString {
            factsBySubject.forEach { (subject, facts) ->
                facts.forEach { fact -> append("[$subject] ${fact.text}\n") }
            }
        }.trim()
    }
}
