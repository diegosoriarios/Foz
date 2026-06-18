package com.example.foz

import android.content.Context
import android.media.MediaMetadata as LegacyMetadata
import android.media.session.MediaController as LegacyMediaController
import android.media.session.PlaybackState as LegacyPlaybackState
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
    private var legacyController: LegacyMediaController? = null

    private val legacyCallback = object : LegacyMediaController.Callback() {
        override fun onMetadataChanged(metadata: LegacyMetadata?) {
            legacyController?.let { updateFromLegacyController(it) }
        }

        override fun onPlaybackStateChanged(state: LegacyPlaybackState?) {
            legacyController?.let { updateFromLegacyController(it) }
        }
    }

    private val _mediaState = MutableStateFlow<MediaState?>(null)
    val mediaState: StateFlow<MediaState?> = _mediaState.asStateFlow()

    fun updateSessions(sessions: List<LegacyMediaController>?) {
        val activeSession = sessions?.firstOrNull { 
            it.playbackState?.state == LegacyPlaybackState.STATE_PLAYING
        } ?: sessions?.firstOrNull()

        if (activeSession == null) {
            releaseController()
            _mediaState.value = null
            return
        }

        if (legacyController != activeSession) {
            legacyController?.unregisterCallback(legacyCallback)
            legacyController = activeSession
            legacyController?.registerCallback(legacyCallback)
        }

        // Immediately update with legacy metadata
        updateFromLegacyController(activeSession)

        connectToSession(activeSession.sessionToken)
    }

    private fun updateFromLegacyController(legacyController: LegacyMediaController) {
        val metadata = legacyController.metadata
        val playbackState = legacyController.playbackState
        
        val isPlaying = playbackState?.state == LegacyPlaybackState.STATE_PLAYING
        
        _mediaState.value = MediaState(
            title = metadata?.getString(LegacyMetadata.METADATA_KEY_TITLE)
                ?: metadata?.getString(LegacyMetadata.METADATA_KEY_DISPLAY_TITLE),
            artist = metadata?.getString(LegacyMetadata.METADATA_KEY_ARTIST)
                ?: metadata?.getString(LegacyMetadata.METADATA_KEY_DISPLAY_SUBTITLE),
            isPlaying = isPlaying,
            packageName = legacyController.packageName,
            artwork = metadata?.getBitmap(LegacyMetadata.METADATA_KEY_ALBUM_ART)
                ?: metadata?.getBitmap(LegacyMetadata.METADATA_KEY_ART)
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

                releaseController()

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
            packageName = controller?.connectedToken?.packageName,
            artwork = current?.artwork
        )
    }

    fun play() { controller?.play() }
    fun pause() { controller?.pause() }
    fun next() { controller?.seekToNext() }
    fun previous() { controller?.seekToPrevious() }

    private fun releaseController() {
        legacyController?.unregisterCallback(legacyCallback)
        legacyController = null
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
