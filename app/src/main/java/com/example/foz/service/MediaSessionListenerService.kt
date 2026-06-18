package com.example.foz.service

import android.content.ComponentName
import android.content.Intent
import android.media.session.MediaSessionManager
import android.service.notification.NotificationListenerService
import com.example.foz.MediaControllerManager

class MediaSessionListenerService : NotificationListenerService() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        val mediaSessionManager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
        val componentName = ComponentName(this, MediaSessionListenerService::class.java)
        
        MediaControllerManager.getInstance(applicationContext).updateSessions(
            mediaSessionManager.getActiveSessions(componentName)
        )

        mediaSessionManager.addOnActiveSessionsChangedListener({ sessions ->
            MediaControllerManager.getInstance(applicationContext).updateSessions(sessions)
        }, componentName)
    }
}
