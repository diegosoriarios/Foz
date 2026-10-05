package com.example.foz

import com.example.foz.ai.LoadCrashPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoadCrashPolicyTest {

    @Test
    fun `clean start keeps streak and allows auto load`() {
        val decision = LoadCrashPolicy.onProcessStart(
            pendingStartTimestampMs = 0L,
            previousStreak = 0
        )
        assertEquals(0, decision.newStreak)
        assertFalse(decision.blockAutoLoad)
    }

    @Test
    fun `pending load marker bumps streak and blocks auto load`() {
        val decision = LoadCrashPolicy.onProcessStart(
            pendingStartTimestampMs = 1_000L,
            previousStreak = 0
        )
        assertEquals(1, decision.newStreak)
        assertTrue(decision.blockAutoLoad)
    }

    @Test
    fun `existing block persists across clean restarts`() {
        val decision = LoadCrashPolicy.onProcessStart(
            pendingStartTimestampMs = 0L,
            previousStreak = 1
        )
        assertEquals(1, decision.newStreak)
        assertTrue(decision.blockAutoLoad)
    }

    @Test
    fun `repeated deaths keep incrementing the streak`() {
        val decision = LoadCrashPolicy.onProcessStart(
            pendingStartTimestampMs = 1_000L,
            previousStreak = 2
        )
        assertEquals(3, decision.newStreak)
        assertTrue(decision.blockAutoLoad)
    }

    @Test
    fun `auto load allowed below threshold`() {
        assertEquals(
            LoadCrashPolicy.LoadDecision.ALLOW,
            LoadCrashPolicy.decideLoad(streak = 0, auto = true)
        )
    }

    @Test
    fun `auto load blocked at or above threshold`() {
        assertEquals(
            LoadCrashPolicy.LoadDecision.BLOCKED_BY_CRASH_STREAK,
            LoadCrashPolicy.decideLoad(streak = 1, auto = true)
        )
        assertEquals(
            LoadCrashPolicy.LoadDecision.BLOCKED_BY_CRASH_STREAK,
            LoadCrashPolicy.decideLoad(streak = 5, auto = true)
        )
    }

    @Test
    fun `manual load is never blocked`() {
        assertEquals(
            LoadCrashPolicy.LoadDecision.ALLOW,
            LoadCrashPolicy.decideLoad(streak = 3, auto = false)
        )
    }

    @Test
    fun `successful manual load resets the streak`() {
        assertEquals(0, LoadCrashPolicy.onSuccessManual(streak = 4))
    }
}
