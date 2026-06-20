package com.example.foz.service

import android.content.ComponentName
import android.content.Intent
import android.media.session.MediaSessionManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.foz.MediaControllerManager
import com.example.foz.data.NotificationRepository
import com.example.foz.model.NotificationActionModel
import com.example.foz.model.NotificationModel

class MediaSessionListenerService : NotificationListenerService() {

    companion object {
        private var instance: MediaSessionListenerService? = null
        
        fun requestRefresh() {
            Log.d("MediaSessionListener", "Refresh requested. Instance available: ${instance != null}")
            instance?.updateActiveNotifications()
        }

        fun cancelNotification(key: String) {
            instance?.cancelNotification(key)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("MediaSessionListener", "Service started")
        instance = this
        updateActiveNotifications()
        return START_STICKY
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d("MediaSessionListener", "Listener connected")
        instance = this
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

    fun updateActiveNotifications() {
        try {
            val notifications = activeNotifications ?: emptyArray()
            Log.d("MediaSessionListener", "Updating notifications, count: ${notifications.size}")
            val models = notifications.map { sbn ->
                val extras = sbn.notification.extras
                val actions = sbn.notification.actions?.map { action ->
                    NotificationActionModel(
                        title = action.title,
                        actionIntent = action.actionIntent
                    )
                } ?: emptyList()

                NotificationModel(
                    key = sbn.key,
                    id = sbn.id,
                    packageName = sbn.packageName,
                    title = extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString(),
                    text = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString(),
                    postTime = sbn.postTime,
                    isClearable = sbn.isClearable,
                    actions = actions
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
        instance = null
    }
}
