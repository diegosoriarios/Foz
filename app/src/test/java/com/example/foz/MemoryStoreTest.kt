package com.example.foz

import com.example.foz.memory.AesMemoryCipher
import com.example.foz.memory.MemoryFact
import com.example.foz.memory.MemoryStore
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MemoryStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun newStore() = MemoryStore(tmp.newFolder(), AesMemoryCipher())

    @Test
    fun `addFacts stores de-duplicates and trims`() {
        val store = newStore()
        assertEquals(2, store.addFacts("profile", listOf("Name is Ana", "Lives in Foz")))
        assertEquals(0, store.addFacts("profile", listOf("name is ana")))
        val facts = store.readSubject("profile")
        assertEquals(2, facts.size)
        assertEquals("Name is Ana", facts[0].text)
    }

    @Test
    fun `roundtrip preserves facts through encryption`() {
        val store = newStore()
        store.addFacts("work", listOf("Works as a dev"))
        assertTrue(
            MemoryStore.fileFor(store.dir(), "work").readBytes()
                .decodeToString().contains("dev").not()
        )
        assertEquals("Works as a dev", store.readSubject("work").first().text)
    }

    @Test
    fun `corrupt blob is quarantined not crashing`() {
        val dir = tmp.newFolder()
        val store = MemoryStore(dir, AesMemoryCipher())
        store.addFacts("health", listOf("Allergic to peanuts"))
        val file = MemoryStore.fileFor(dir, "health")
        file.writeBytes(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14))
        assertTrue(store.readSubject("health").isEmpty())
        assertTrue(File(dir, "health.mem.corrupt").exists())
    }

    @Test
    fun `deleteFact and clearSubject work`() {
        val store = newStore()
        store.addFacts("family", listOf("Sister is Carol"))
        val fact = store.readSubject("family").first()
        store.addFacts("family", listOf("Brother is Leo"))
        store.deleteFact("family", fact.id)
        assertEquals(listOf("Brother is Leo"), store.readSubject("family").map { it.text })
        store.clearSubject("family")
        assertTrue(store.readSubject("family").isEmpty())
        assertFalse(MemoryStore.fileFor(store.dir(), "family").exists())
    }

    @Test
    fun `caps facts per subject and chars per fact`() {
        val store = newStore()
        val longText = "x".repeat(300)
        store.addFacts("places", listOf(longText))
        assertEquals(MemoryStore.MAX_FACT_CHARS, store.readSubject("places").first().text.length)
        repeat(MemoryStore.MAX_FACTS_PER_SUBJECT + 5) { i ->
            store.addFacts("places", listOf("Place number $i"))
        }
        assertEquals(MemoryStore.MAX_FACTS_PER_SUBJECT, store.readSubject("places").size)
    }

    @Test
    fun `forgetEverything wipes files`() {
        val store = newStore()
        store.addFacts("finance", listOf("Uses Nubank"))
        store.addFacts("schedule", listOf("Gym at 7am"))
        store.forgetEverything()
        assertEquals(0, store.totalCount())
        assertTrue(store.dir().listFiles()?.isEmpty() ?: true)
    }

    private fun MemoryStore.dir(): File {
        val field = MemoryStore::class.java.getDeclaredField("dir")
        field.isAccessible = true
        return field.get(this) as File
    }
}
