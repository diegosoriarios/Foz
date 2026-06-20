package com.example.foz.service

import android.content.ComponentName
import android.content.Intent
import android.media.session.MediaSessionManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.foz.MediaControllerManager
import com.example.foz.data.NotificationRepository
import com.example.foz.model.NotificationModel

class MediaSessionListenerService : NotificationListenerService() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("MediaSessionListener", "Service started")
        return START_STICKY
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d("MediaSessionListener", "Listener connected")
        updateMediaSessions()
        updateActiveNotifications()

        val mediaSessionManager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
        val componentName = ComponentName(this, MediaSessionListenerService::class.java)
        
        mediaSessionManager.addOnActiveSessionsChangedListener({ updatedSessions ->
            Log.d("MediaSessionListener", "Active sessions changed: ${updatedSessions?.size}")
            MediaControllerManager.getInstance(applicationContext).updateSessions(updatedSessions)
        }, componentName)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        updateActiveNotifications()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        updateActiveNotifications()
    }

    private fun updateMediaSessions() {
        val mediaSessionManager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
        val componentName = ComponentName(this, MediaSessionListenerService::class.java)
        val sessions = mediaSessionManager.getActiveSessions(componentName)
        Log.d("MediaSessionListener", "Found ${sessions.size} active sessions")
        MediaControllerManager.getInstance(applicationContext).updateSessions(sessions)
    }

    private fun updateActiveNotifications() {
        try {
            val activeNotifications = activeNotifications
            val models = activeNotifications.map { sbn ->
                val extras = sbn.notification.extras
                NotificationModel(
                    id = sbn.id,
                    packageName = sbn.packageName,
                    title = extras.getString(android.app.Notification.EXTRA_TITLE),
                    text = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString(),
                    postTime = sbn.postTime,
                    isClearable = sbn.isClearable
                )
            }
            NotificationRepository.getInstance().updateNotifications(models)
        } catch (e: Exception) {
            Log.e("MediaSessionListener", "Error updating notifications", e)
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d("MediaSessionListener", "Listener disconnected")
    }
}
