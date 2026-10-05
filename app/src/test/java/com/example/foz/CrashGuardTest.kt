package com.example.foz

import com.example.foz.manager.CrashGuard
import org.junit.Assert.assertEquals
import org.junit.Test

class CrashGuardTest {

    @Test
    fun `no crash recorded classifies as none`() {
        assertEquals(
            CrashGuard.Recovery.NONE,
            CrashGuard.classify(nowMs = 10_000L, crashTimestampMs = 0L)
        )
        assertEquals(
            CrashGuard.Recovery.NONE,
            CrashGuard.classify(nowMs = 10_000L, crashTimestampMs = -5L)
        )
    }

    @Test
    fun `recent crash activates safe mode`() {
        assertEquals(
            CrashGuard.Recovery.SAFE_MODE,
            CrashGuard.classify(nowMs = 61_000L, crashTimestampMs = 60_000L)
        )
        assertEquals(
            CrashGuard.Recovery.SAFE_MODE,
            CrashGuard.classify(nowMs = 60_000L + CrashGuard.SAFE_MODE_WINDOW_MS, crashTimestampMs = 60_000L)
        )
    }

    @Test
    fun `crash within warning window classifies as warning`() {
        assertEquals(
            CrashGuard.Recovery.WARNING,
            CrashGuard.classify(
                nowMs = 60_000L + CrashGuard.SAFE_MODE_WINDOW_MS + 1,
                crashTimestampMs = 60_000L
            )
        )
    }

    @Test
    fun `old crash classifies as none`() {
        assertEquals(
            CrashGuard.Recovery.NONE,
            CrashGuard.classify(
                nowMs = 60_000L + CrashGuard.WARNING_WINDOW_MS + 1,
                crashTimestampMs = 60_000L
            )
        )
    }
}
