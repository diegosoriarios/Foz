package com.example.foz

import android.content.Context
import android.media.MediaMetadata as LegacyMetadata
import android.media.session.MediaController as PlatformMediaController
import android.media.session.PlaybackState as LegacyPlaybackState
import android.support.v4.media.session.MediaControllerCompat as LegacyControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.foz.ui.MediaState
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import java.util.concurrent.ExecutionException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MediaControllerManager private constructor(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var legacyController: LegacyControllerCompat? = null

    private val legacyCallback = object : LegacyControllerCompat.Callback() {
        override fun onMetadataChanged(metadata: android.support.v4.media.MediaMetadataCompat?) {
            legacyController?.let { updateFromLegacyController(it) }
        }

        override fun onPlaybackStateChanged(state: android.support.v4.media.session.PlaybackStateCompat?) {
            legacyController?.let { updateFromLegacyController(it) }
        }
    }

    private val _mediaState = MutableStateFlow<MediaState?>(null)
    val mediaState: StateFlow<MediaState?> = _mediaState.asStateFlow()

    fun updateSessions(sessions: List<PlatformMediaController>?) {
        val activeSession = sessions?.firstOrNull { 
            it.playbackState?.state == LegacyPlaybackState.STATE_PLAYING
        } ?: sessions?.firstOrNull()

        if (activeSession == null) {
            releaseController()
            _mediaState.value = null
            return
        }

        try {
            val compat = LegacyControllerCompat(context, MediaSessionCompat.Token.fromToken(activeSession.sessionToken))
            if (legacyController?.packageName != compat.packageName) {
                legacyController?.unregisterCallback(legacyCallback)
                legacyController = compat
                legacyController?.registerCallback(legacyCallback)
            }
            
            // Immediately update with legacy metadata
            updateFromLegacyController(compat)
            
            connectToSession(activeSession.sessionToken)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateFromLegacyController(compat: LegacyControllerCompat) {
        val metadata = compat.metadata
        val playbackState = compat.playbackState
        
        val isPlaying = playbackState?.state == android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING
        
        _mediaState.value = MediaState(
            title = metadata?.getString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TITLE)
                ?: metadata?.getString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE),
            artist = metadata?.getString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ARTIST)
                ?: metadata?.getString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE),
            isPlaying = isPlaying,
            packageName = compat.packageName,
            artwork = metadata?.getBitmap(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ALBUM_ART)
                ?: metadata?.getBitmap(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ART)
        )
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun connectToSession(legacyToken: android.media.session.MediaSession.Token) {
        val legacyTokenCompat = MediaSessionCompat.Token.fromToken(legacyToken)
        val tokenFuture = SessionToken.createSessionToken(context, legacyTokenCompat)
        
        tokenFuture.addListener({
            try {
                val token = tokenFuture.get()
                if (controller?.connectedToken == token) return@addListener

                releaseMedia3Controller()

                val future = MediaController.Builder(context, token).buildAsync()
                controllerFuture = future
                future.addListener({
                    try {
                        val newController = future.get()
                        if (newController != null) {
                            setupController(newController)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }, MoreExecutors.directExecutor())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, MoreExecutors.directExecutor())
    }

    private fun setupController(newController: MediaController) {
        controller = newController
        newController.addListener(object : Player.Listener {
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                updateMediaState(newController)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateMediaState(newController)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                updateMediaState(newController)
            }
        })
        updateMediaState(newController)
    }

    private fun updateMediaState(player: Player) {
        val metadata = player.mediaMetadata
        val current = _mediaState.value
        _mediaState.value = MediaState(
            title = metadata.title?.toString() ?: metadata.displayTitle?.toString(),
            artist = metadata.artist?.toString() ?: metadata.subtitle?.toString(),
            isPlaying = player.isPlaying,
            packageName = controller?.connectedToken?.packageName ?: current?.packageName,
            artwork = current?.artwork
        )
    }

    fun play() {
        android.util.Log.d("MediaManager", "Play requested. Controller: ${controller?.connectedToken}, Legacy: ${legacyController?.packageName}")
        if (controller?.connectedToken != null) {
            controller?.play()
        } else if (legacyController != null) {
            legacyController?.transportControls?.play()
        }
    }

    fun pause() {
        android.util.Log.d("MediaManager", "Pause requested. Controller: ${controller?.connectedToken}, Legacy: ${legacyController?.packageName}")
        if (controller?.connectedToken != null) {
            controller?.pause()
        } else if (legacyController != null) {
            legacyController?.transportControls?.pause()
        }
    }

    fun next() {
        if (controller?.connectedToken != null) {
            controller?.seekToNext()
        } else {
            legacyController?.transportControls?.skipToNext()
        }
    }

    fun previous() {
        if (controller?.connectedToken != null) {
            controller?.seekToPrevious()
        } else {
            legacyController?.transportControls?.skipToPrevious()
        }
    }

    private fun releaseController() {
        releaseMedia3Controller()
        legacyController?.unregisterCallback(legacyCallback)
        legacyController = null
    }

    private fun releaseMedia3Controller() {
        controller?.release()
        controller = null
        controllerFuture?.cancel(false)
        controllerFuture = null
    }

    companion object {
        @Volatile
        private var INSTANCE: MediaControllerManager? = null

        fun getInstance(context: Context): MediaControllerManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MediaControllerManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
