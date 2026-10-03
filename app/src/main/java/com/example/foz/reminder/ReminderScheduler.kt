package com.example.foz.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.foz.data.Reminder
import com.example.foz.data.ReminderRepository

/**
 * Sets exact alarms for reminders when the system allows it, otherwise falls
 * back to an inexact ~5-minute window (some OEMs restrict SCHEDULE_EXACT_ALARM).
 */
class ReminderScheduler(private val context: Context) {

    private val alarmManager: AlarmManager? =
        context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    fun schedule(reminder: Reminder) {
        val am = alarmManager ?: return
        val pi = firePendingIntent(reminder.id, reminder.title) ?: return
        try {
            val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                am.canScheduleExactAlarms()
            if (exactAllowed) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.triggerAt, pi)
            } else {
                am.setWindow(
                    AlarmManager.RTC_WAKEUP,
                    reminder.triggerAt,
                    FALLBACK_WINDOW_MS,
                    pi
                )
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Could not schedule reminder alarm", t)
        }
    }

    fun cancel(reminderId: String) {
        val pi = firePendingIntent(reminderId, "") ?: return
        try {
            alarmManager?.cancel(pi)
        } catch (_: Throwable) {
        }
    }

    /** Re-arms every unfired future reminder (used after boot). */
    suspend fun rescheduleAll(repository: ReminderRepository) {
        repository.pendingReminders().forEach { schedule(it) }
    }

    private fun firePendingIntent(id: String, title: String): PendingIntent? {
        return try {
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                action = ReminderReceiver.ACTION_FIRE
                putExtra(ReminderReceiver.EXTRA_ID, id)
                putExtra(ReminderReceiver.EXTRA_TITLE, title)
            }
            PendingIntent.getBroadcast(
                context,
                id.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Could not build reminder PendingIntent", t)
            null
        }
    }

    companion object {
        private const val TAG = "ReminderScheduler"
        const val FALLBACK_WINDOW_MS = 5L * 60 * 1000
    }
}
