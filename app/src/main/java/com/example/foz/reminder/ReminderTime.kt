package com.example.foz.reminder

import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Pure reminder trigger resolution, shared by the set_reminder tool and unit
 * tests. Dates are ISO (YYYY-MM-DD), times are HH:mm 24h (H:mm also accepted).
 * A missing date means today; a trigger already in the past rolls to tomorrow.
 */
object ReminderTime {

    /** Guards against absurd future dates (model hallucination). */
    const val MAX_YEARS_AHEAD: Long = 5

    fun compute(
        dateStr: String?,
        timeStr: String?,
        nowMillis: Long,
        zone: ZoneId
    ): LocalDateTime? {
        val time = parseTime(timeStr) ?: return null
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val date = if (dateStr.isNullOrBlank()) {
            today
        } else {
            val parsed = parseDate(dateStr) ?: return null
            if (parsed.isAfter(today.plusYears(MAX_YEARS_AHEAD))) return null
            parsed
        }
        var trigger = LocalDateTime.of(date, time)
        // Roll forward to the next future occurrence (handles stale dates too).
        var guard = 0
        while (trigger.atZone(zone).toInstant().toEpochMilli() <= nowMillis) {
            trigger = trigger.plusDays(1)
            if (++guard > MAX_ROLL_DAYS) return null
        }
        return trigger
    }

    private const val MAX_ROLL_DAYS = 400

    internal fun parseDate(value: String?): LocalDate? {
        if (value.isNullOrBlank()) return null
        return try {
            LocalDate.parse(value.trim())
        } catch (_: DateTimeException) {
            null
        }
    }

    internal fun parseTime(value: String?): LocalTime? {
        if (value.isNullOrBlank()) return null
        return try {
            LocalTime.parse(value.trim())
        } catch (_: DateTimeException) {
            try {
                LocalTime.parse(value.trim(), DateTimeFormatter.ofPattern("H:mm"))
            } catch (_: DateTimeException) {
                null
            }
        }
    }
}
