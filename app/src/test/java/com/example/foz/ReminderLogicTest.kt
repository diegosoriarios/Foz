package com.example.foz

import com.example.foz.data.Reminder
import com.example.foz.data.ReminderRepository
import com.example.foz.reminder.ReminderTime
import java.time.LocalDateTime
import java.time.Month
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderLogicTest {

    private val zone = ZoneId.of("America/Sao_Paulo")
    private val now = LocalDateTime.of(2026, Month.OCTOBER, 3, 12, 0)
    private val nowMillis = now.atZone(zone).toInstant().toEpochMilli()

    // ----- ReminderTime.compute -----

    @Test
    fun `missing or invalid time returns null`() {
        assertNull(ReminderTime.compute(null, null, nowMillis, zone))
        assertNull(ReminderTime.compute(null, "9h30", nowMillis, zone))
        assertNull(ReminderTime.compute(null, "25:00", nowMillis, zone))
    }

    @Test
    fun `invalid date returns null`() {
        assertNull(ReminderTime.compute("tomorrow", "09:00", nowMillis, zone))
    }

    @Test
    fun `absurd far future date returns null`() {
        assertNull(ReminderTime.compute("2035-01-01", "09:00", nowMillis, zone))
    }

    @Test
    fun `past time today rolls to tomorrow`() {
        val trigger = ReminderTime.compute(null, "09:00", nowMillis, zone)
        assertEquals(LocalDateTime.of(2026, Month.OCTOBER, 4, 9, 0), trigger)
    }

    @Test
    fun `time exactly now rolls to tomorrow`() {
        val trigger = ReminderTime.compute(null, "12:00", nowMillis, zone)
        assertEquals(LocalDateTime.of(2026, Month.OCTOBER, 4, 12, 0), trigger)
    }

    @Test
    fun `future time today keeps today`() {
        val trigger = ReminderTime.compute(null, "18:30", nowMillis, zone)
        assertEquals(LocalDateTime.of(2026, Month.OCTOBER, 3, 18, 30), trigger)
    }

    @Test
    fun `single digit hour accepted`() {
        val trigger = ReminderTime.compute(null, "9:05", nowMillis, zone)
        assertEquals(LocalDateTime.of(2026, Month.OCTOBER, 4, 9, 5), trigger)
    }

    @Test
    fun `explicit past date rolls to tomorrow`() {
        val trigger = ReminderTime.compute("2026-10-01", "09:00", nowMillis, zone)
        assertEquals(LocalDateTime.of(2026, Month.OCTOBER, 4, 9, 0), trigger)
    }

    @Test
    fun `explicit future date is respected`() {
        val trigger = ReminderTime.compute("2026-12-25", "08:15", nowMillis, zone)
        assertEquals(LocalDateTime.of(2026, Month.DECEMBER, 25, 8, 15), trigger)
    }

    // ----- ReminderRepository JSON (companion) -----

    @Test
    fun `json round trip preserves reminders`() {
        val reminders = listOf(
            Reminder("rem_1", "Call mom", 1_000L, fired = false),
            Reminder("rem_2", "Take pills", 2_000L, fired = true)
        )
        val parsed = ReminderRepository.parseReminders(ReminderRepository.toJson(reminders))
        assertEquals(reminders, parsed)
    }

    @Test
    fun `corrupt blank or null json yields empty list`() {
        assertTrue(ReminderRepository.parseReminders("{{{").isEmpty())
        assertTrue(ReminderRepository.parseReminders("").isEmpty())
        assertTrue(ReminderRepository.parseReminders(null).isEmpty())
    }
}
