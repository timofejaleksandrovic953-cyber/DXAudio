package com.dxaudio

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.CrossfadeAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.flow.MutableStateFlow

object PlayerHolder {
    val audioSessionId = MutableStateFlow(0)
}

class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var exoPlayer: ExoPlayer? = null

    @OptIn(UnstableApi::class) // Для CrossfadeAudioProcessor и buildAudioSink
    override fun onCreate() {
        super.onCreate()
        
        val prefs = getSharedPreferences("dx_prefs", MODE_PRIVATE)
        val crossfadeEnabled = prefs.getBoolean("crossfade_enabled", false)
        val crossfadeDurationMs = prefs.getLong("crossfade_duration", 5000L)

        // Кастомная RenderersFactory для внедрения Crossfade
        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: android.content.Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink? {
                if (!crossfadeEnabled) {
                    return super.buildAudioSink(context, enableFloatOutput, enableAudioTrackPlaybackParams)
                }
                val audioProcessors = arrayOf<AudioProcessor>(
                    CrossfadeAudioProcessor(crossfadeDurationMs)
                )
                return DefaultAudioSink.Builder(context)
                    .setAudioProcessorChain(DefaultAudioSink.DefaultAudioProcessorChain(audioProcessors))
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .build()
            }
        }

        exoPlayer = ExoPlayer.Builder(this, renderersFactory)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        PlayerHolder.audioSessionId.value = exoPlayer!!.audioSessionId

        // Слушатель для обновления виджета
        exoPlayer!!.addListener(object : Player.Listener {
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                super.onMediaMetadataChanged(mediaMetadata)
                val title = mediaMetadata.title?.toString() ?: "Неизвестно"
                val artist = mediaMetadata.artist?.toString() ?: "Неизвестный исполнитель"
                PlayerWidgetProvider.updateAllWidgets(this@PlaybackService, title, artist)
            }
        })

        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, exoPlayer!!)
            .setSessionActivity(sessionActivityPendingIntent)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        exoPlayer = null
        super.onDestroy()
    }
}
