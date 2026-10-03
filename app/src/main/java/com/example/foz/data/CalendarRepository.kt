package com.example.foz.data

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.provider.CalendarContract.Instances
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.TimeZone

data class CalendarEventInfo(
    val id: Long,
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
    val location: String?
)

/**
 * Read/write access to the device calendar via CalendarContract.
 */
class CalendarRepository(private val context: Context) {

    fun hasReadPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun hasWritePermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    suspend fun getEvents(startMillis: Long, endMillis: Long): List<CalendarEventInfo> {
        if (!hasReadPermission()) throw SecurityException("READ_CALENDAR not granted")
        return withContext(Dispatchers.IO) {
            val projection = arrayOf(
                Instances._ID,
                Instances.TITLE,
                Instances.BEGIN,
                Instances.END,
                Instances.ALL_DAY,
                Instances.EVENT_LOCATION
            )
            val events = mutableListOf<CalendarEventInfo>()
            try {
                context.contentResolver.query(
                    Instances.CONTENT_URI,
                    projection,
                    "(" + Instances.BEGIN + " >= ? AND " + Instances.BEGIN + " <= ?)",
                    arrayOf(startMillis.toString(), endMillis.toString()),
                    Instances.BEGIN + " ASC"
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        events.add(
                            CalendarEventInfo(
                                id = cursor.getLong(0),
                                title = cursor.getString(1) ?: "",
                                startMillis = cursor.getLong(2),
                                endMillis = cursor.getLong(3),
                                allDay = cursor.getInt(4) == 1,
                                location = cursor.getString(5)
                            )
                        )
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to query calendar events", t)
                throw t
            }
            events
        }
    }

    suspend fun createEvent(
        title: String,
        startMillis: Long,
        endMillis: Long,
        allDay: Boolean = false
    ): Long {
        if (!hasWritePermission()) throw SecurityException("WRITE_CALENDAR not granted")
        return withContext(Dispatchers.IO) {
            val calendarId = firstWritableCalendarId()
                ?: throw IllegalStateException("No writable calendar found on this device")
            val values = ContentValues().apply {
                put(CalendarContract.Events.TITLE, title)
                put(CalendarContract.Events.DTSTART, startMillis)
                put(CalendarContract.Events.DTEND, endMillis)
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                put(CalendarContract.Events.ALL_DAY, if (allDay) 1 else 0)
                put(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_BUSY)
            }
            val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
                ?: throw IllegalStateException("Calendar rejected the new event")
            uri.lastPathSegment?.toLongOrNull() ?: 0L
        }
    }

    private fun firstWritableCalendarId(): Long? {
        return try {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf(CalendarContract.Calendars._ID),
                "(" + CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL + " >= ?)",
                arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString()),
                CalendarContract.Calendars._ID + " ASC"
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else null
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to find writable calendar", t)
            null
        }
    }

    companion object {
        private const val TAG = "CalendarRepository"
    }
}
