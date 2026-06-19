package com.example.foz.service

import android.content.ComponentName
import android.content.Intent
import android.media.session.MediaSessionManager
import android.service.notification.NotificationListenerService
import android.util.Log
import com.example.foz.MediaControllerManager

class MediaSessionListenerService : NotificationListenerService() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("MediaSessionListener", "Service started")
        return START_STICKY
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d("MediaSessionListener", "Listener connected")
        val mediaSessionManager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
        val componentName = ComponentName(this, MediaSessionListenerService::class.java)
        
        val sessions = mediaSessionManager.getActiveSessions(componentName)
        Log.d("MediaSessionListener", "Found ${sessions.size} active sessions")
        MediaControllerManager.getInstance(applicationContext).updateSessions(sessions)

        mediaSessionManager.addOnActiveSessionsChangedListener({ updatedSessions ->
            Log.d("MediaSessionListener", "Active sessions changed: ${updatedSessions?.size}")
            MediaControllerManager.getInstance(applicationContext).updateSessions(updatedSessions)
        }, componentName)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d("MediaSessionListener", "Listener disconnected")
    }
}
