package com.example.foz.data

import android.content.Context
import android.database.Cursor
import android.provider.CalendarContract
import com.example.foz.model.CalendarEventInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CalendarRepository(private val context: Context) {

    suspend fun getUpcomingEvents(days: Int = 7, limit: Int = 20): List<CalendarEventInfo> {
        return withContext(Dispatchers.IO) {
            val results = mutableListOf<CalendarEventInfo>()
            var cursor: Cursor? = null
            try {
                val now = System.currentTimeMillis()
                val end = now + days * 24L * 60L * 60L * 1000L

                val projection = arrayOf(
                    CalendarContract.Instances.EVENT_ID,
                    CalendarContract.Instances.TITLE,
                    CalendarContract.Instances.BEGIN,
                    CalendarContract.Instances.END,
                    CalendarContract.Instances.ALL_DAY,
                    CalendarContract.Instances.EVENT_LOCATION
                )

                val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
                    .appendPath(now.toString())
                    .appendPath(end.toString())
                    .build()

                cursor = context.contentResolver.query(
                    uri,
                    projection,
                    null,
                    null,
                    "${CalendarContract.Instances.BEGIN} ASC"
                )

                cursor?.use { c ->
                    val idIndex = c.getColumnIndex(CalendarContract.Instances.EVENT_ID)
                    val titleIndex = c.getColumnIndex(CalendarContract.Instances.TITLE)
                    val beginIndex = c.getColumnIndex(CalendarContract.Instances.BEGIN)
                    val endIndex = c.getColumnIndex(CalendarContract.Instances.END)
                    val allDayIndex = c.getColumnIndex(CalendarContract.Instances.ALL_DAY)
                    val locationIndex = c.getColumnIndex(CalendarContract.Instances.EVENT_LOCATION)
                    while (c.moveToNext() && results.size < limit) {
                        val begin = c.getLong(beginIndex)
                        val endMs = c.getLong(endIndex)
                        val title = c.getString(titleIndex) ?: continue
                        results.add(
                            CalendarEventInfo(
                                eventId = c.getLong(idIndex),
                                title = title,
                                begin = begin,
                                end = if (endMs > begin) endMs else begin,
                                allDay = c.getInt(allDayIndex) != 0,
                                location = c.getString(locationIndex)?.takeIf { it.isNotBlank() }
                            )
                        )
                    }
                }
            } catch (_: SecurityException) {
                // READ_CALENDAR not granted
            } catch (_: Exception) {
            } finally {
                cursor?.close()
            }
            results
        }
    }
}
