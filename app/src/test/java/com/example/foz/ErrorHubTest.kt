package com.example.foz

import com.example.foz.ui.common.ErrorHub
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ErrorHubTest {

    @Before
    fun reset() {
        ErrorHub.clearRecent()
        ErrorHub.persistSink = null
    }

    @Test
    fun `record appends to recent buffer`() {
        ErrorHub.record("first")
        ErrorHub.record("second")
        val recent = ErrorHub.recent
        assertEquals(2, recent.size)
        assertEquals("first", recent[0].message)
        assertEquals("second", recent[1].message)
        assertTrue(recent[0].timestampMs > 0)
    }

    @Test
    fun `blank messages are ignored`() {
        ErrorHub.record("   ")
        assertTrue(ErrorHub.recent.isEmpty())
    }

    @Test
    fun `buffer is capped at max entries`() {
        repeat(ErrorHub.MAX_ENTRIES + 5) { index ->
            ErrorHub.record("error $index")
        }
        val recent = ErrorHub.recent
        assertEquals(ErrorHub.MAX_ENTRIES, recent.size)
        assertEquals("error 5", recent.first().message)
        assertEquals("error ${ErrorHub.MAX_ENTRIES + 4}", recent.last().message)
    }

    @Test
    fun `json roundtrip preserves entries`() {
        val entries = listOf(
            ErrorHub.Entry(1_000L, "load failed"),
            ErrorHub.Entry(2_000L, "download interrupted")
        )
        val restored = ErrorHub.fromJson(ErrorHub.toJson(entries))
        assertEquals(entries, restored)
    }

    @Test
    fun `malformed json yields empty list`() {
        assertTrue(ErrorHub.fromJson("not json at all").isEmpty())
        assertTrue(ErrorHub.fromJson("").isEmpty())
    }

    @Test
    fun `hydrate replaces the buffer without emitting`() {
        ErrorHub.record("transient")
        ErrorHub.hydrate(
            listOf(
                ErrorHub.Entry(10L, "persisted one"),
                ErrorHub.Entry(20L, "persisted two")
            )
        )
        val recent = ErrorHub.recent
        assertEquals(2, recent.size)
        assertEquals("persisted one", recent[0].message)
    }

    @Test
    fun `persist sink receives snapshots`() {
        var sinkCalls = 0
        var lastSize = 0
        ErrorHub.persistSink = { entries ->
            sinkCalls++
            lastSize = entries.size
        }
        ErrorHub.record("hello")
        assertEquals(1, sinkCalls)
        assertEquals(1, lastSize)
    }
}
