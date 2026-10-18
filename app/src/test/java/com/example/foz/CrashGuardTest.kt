package com.example.foz

import com.example.foz.manager.CrashGuard
import org.junit.Assert.assertEquals
import org.junit.Test

class CrashGuardTest {

    @Test
    fun `java and native crashes map to crashed`() {
        assertEquals(
            CrashGuard.ExitKind.CRASHED,
            CrashGuard.exitReasonKind(android.app.ApplicationExitInfo.REASON_CRASH)
        )
        assertEquals(
            CrashGuard.ExitKind.CRASHED,
            CrashGuard.exitReasonKind(android.app.ApplicationExitInfo.REASON_CRASH_NATIVE)
        )
    }

    @Test
    fun `lmkd kill maps to low memory`() {
        assertEquals(
            CrashGuard.ExitKind.LOW_MEMORY,
            CrashGuard.exitReasonKind(android.app.ApplicationExitInfo.REASON_LOW_MEMORY)
        )
    }

    @Test
    fun `anr and signaled map to their kinds`() {
        assertEquals(
            CrashGuard.ExitKind.ANR,
            CrashGuard.exitReasonKind(android.app.ApplicationExitInfo.REASON_ANR)
        )
        assertEquals(
            CrashGuard.ExitKind.SIGNALED,
            CrashGuard.exitReasonKind(android.app.ApplicationExitInfo.REASON_SIGNALED)
        )
    }

    @Test
    fun `user stops map to user kind`() {
        assertEquals(
            CrashGuard.ExitKind.USER,
            CrashGuard.exitReasonKind(android.app.ApplicationExitInfo.REASON_USER_REQUESTED)
        )
        assertEquals(
            CrashGuard.ExitKind.USER,
            CrashGuard.exitReasonKind(android.app.ApplicationExitInfo.REASON_USER_STOPPED)
        )
    }

    @Test
    fun `unknown codes map to other`() {
        assertEquals(CrashGuard.ExitKind.OTHER, CrashGuard.exitReasonKind(-1))
        assertEquals(CrashGuard.ExitKind.OTHER, CrashGuard.exitReasonKind(999))
    }

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
