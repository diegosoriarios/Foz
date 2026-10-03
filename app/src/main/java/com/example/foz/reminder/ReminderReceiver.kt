package com.example.foz.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.foz.R
import com.example.foz.data.ReminderRepository
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Fires the reminder notification when the alarm goes off, and handles the
 * notification's Snooze (+10 min) and Dismiss actions.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repository = ReminderRepository(appContext)
                when (intent.action) {
                    ACTION_FIRE -> {
                        repository.markFired(id)
                        if (canPostNotifications(appContext)) {
                            showNotification(appContext, id, title)
                        }
                    }
                    ACTION_SNOOZE -> {
                        dismissNotification(appContext, id)
                        val scheduler = ReminderScheduler(appContext)
                        val snoozed = repository.addReminder(
                            title = title,
                            triggerAt = System.currentTimeMillis() + SNOOZE_DELAY_MS
                        )
                        scheduler.schedule(snoozed)
                    }
                    ACTION_DISMISS -> {
                        dismissNotification(appContext, id)
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Reminder handling failed", t)
            } finally {
                pending.finish()
            }
        }
    }

    private fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun showNotification(context: Context, id: String, title: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.reminder_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            )
        )
        val contentIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(
                context,
                0,
                it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
        val snoozeIntent = PendingIntent.getBroadcast(
            context,
            id.hashCode() + 1,
            Intent(context, ReminderReceiver::class.java).apply {
                action = ACTION_SNOOZE
                putExtra(EXTRA_ID, id)
                putExtra(EXTRA_TITLE, title)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val dismissIntent = PendingIntent.getBroadcast(
            context,
            id.hashCode() + 2,
            Intent(context, ReminderReceiver::class.java).apply {
                action = ACTION_DISMISS
                putExtra(EXTRA_ID, id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
            .setContentTitle(context.getString(R.string.reminder_notif_title))
            .setContentText(title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(title))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .addAction(0, context.getString(R.string.reminder_snooze), snoozeIntent)
            .addAction(0, context.getString(R.string.reminder_dismiss), dismissIntent)
            .build()
        manager.notify(id.hashCode(), notification)
    }

    private fun dismissNotification(context: Context, id: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        manager.cancel(id.hashCode())
    }

    companion object {
        private const val TAG = "ReminderReceiver"
        const val CHANNEL_ID = "assistant_reminders"
        const val ACTION_FIRE = "com.example.foz.reminder.FIRE"
        const val ACTION_SNOOZE = "com.example.foz.reminder.SNOOZE"
        const val ACTION_DISMISS = "com.example.foz.reminder.DISMISS"
        const val EXTRA_ID = "reminder_id"
        const val EXTRA_TITLE = "reminder_title"
        val SNOOZE_DELAY_MS = TimeUnit.MINUTES.toMillis(10)
    }
}
