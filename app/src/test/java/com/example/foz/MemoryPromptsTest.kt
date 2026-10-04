package com.example.foz

import com.example.foz.memory.MemoryFact
import com.example.foz.memory.MemoryPrompts
import com.example.foz.memory.MemoryStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryPromptsTest {

    private fun facts(vararg texts: String) = texts.map { MemoryFact("id_$it", it, 0L) }

    @Test
    fun `memorySection is null when everything is empty`() {
        val empty = MemoryStore.SUBJECTS.associateWith { emptyList<MemoryFact>() }
        assertNull(MemoryPrompts.memorySection(empty))
    }

    @Test
    fun `memorySection injects core facts and recall index`() {
        val bySubject = MemoryStore.SUBJECTS.associateWith { emptyList<MemoryFact>() }
            .toMutableMap()
        bySubject["profile"] = facts("Name is Ana", "Lives in Foz")
        bySubject["work"] = facts("Works as a dev")
        val section = MemoryPrompts.memorySection(bySubject)!!
        assertTrue(section.contains("Name is Ana"))
        assertTrue(section.contains("recall_memory"))
        assertTrue(section.contains("work, family"))
        // Non-core facts are NOT injected inline.
        assertFalse(section.contains("Works as a dev"))
    }

    @Test
    fun `extractionPrompt includes known facts and both turns`() {
        val bySubject = mapOf("preferences" to facts("Prefers dark theme"))
        val prompt = MemoryPrompts.extractionPrompt(
            MemoryPrompts.existingSummary(bySubject),
            "I only use dark mode",
            "Got it, dark theme set."
        )
        assertTrue(prompt.contains("[preferences] Prefers dark theme"))
        assertTrue(prompt.contains("User: I only use dark mode"))
        assertTrue(prompt.contains("Assistant: Got it, dark theme set."))
    }

    @Test
    fun `parseExtraction handles bare array`() {
        val out = MemoryPrompts.parseExtraction(
            """[{"subject":"profile","text":"Name is Ana"}]"""
        )
        assertEquals(listOf("profile" to "Name is Ana"), out)
    }

    @Test
    fun `parseExtraction handles fenced output and pair entries`() {
        val out = MemoryPrompts.parseExtraction(
            "```json\n[[\"work\", \"Works as a dev\"]]\n```"
        )
        assertEquals(listOf("work" to "Works as a dev"), out)
    }

    @Test
    fun `parseExtraction drops unknown subjects duplicates and overlong`() {
        val longFact = "y".repeat(200)
        val out = MemoryPrompts.parseExtraction(
            """[
                {"subject":"shoes","text":"Red sneakers"},
                {"subject":"profile","text":"Name is Ana"},
                ["profile","Name is Ana"],
                {"subject":"health","text":"$longFact"},
                {"subject":"finance","text":"Uses Nubank"}
            ]"""
        )
        assertEquals(listOf("profile" to "Name is Ana", "finance" to "Uses Nubank"), out)
    }

    @Test
    fun `parseExtraction caps at five and survives garbage`() {
        val many = (1..8).joinToString(",") {
            """{"subject":"places","text":"Place $it"}"""
        }
        assertEquals(5, MemoryPrompts.parseExtraction("[$many]").size)
        assertTrue(MemoryPrompts.parseExtraction("I will not remember anything.").isEmpty())
        assertTrue(MemoryPrompts.parseExtraction("[{\"subject\":\"profile\"}").isEmpty())
    }

    @Test
    fun `existingSummary is blank for empty store`() {
        val empty = MemoryStore.SUBJECTS.associateWith { emptyList<MemoryFact>() }
        assertEquals("", MemoryPrompts.existingSummary(empty))
    }
}
