package com.soniclab.player

import android.content.Context
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * MediaSessionService hosting the shared ExoPlayer instance.
 * Provides lock-screen controls, notification controls, background playback
 * and Android Auto integration through Media3. The audio pipeline includes
 * the [EnhanceAudioProcessor] so the AI Enhance setting applies in real
 * time to the playing audio (gapless transitions stay native to ExoPlayer).
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val factory = EnhanceRenderersFactory(
            this,
            arrayOf(
                AudioBalanceBridge.processor,
                AudioGainBridge.processor,
                AudioToneBridge.processor,
                AudioReverbBridge.processor,
                AudioEnhanceBridge.processor,
                AudioSpatialBridge.processor
            )
        )
        val player = ExoPlayer.Builder(this, factory).build()
        AudioSessionBridge.sessionId = player.audioSessionId
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}

/** Injects the balance/gain/enhance chain into the audio sink. */
private class EnhanceRenderersFactory(context: Context, private val processors: Array<AudioProcessor>) :
    DefaultRenderersFactory(context) {

    init {
        // Fall back to software decoders (e.g. FLAC hi-res) when the
        // hardware audio path can't handle a format on a given device.
        setEnableDecoderFallback(true)
    }

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean
    ): AudioSink {
        return DefaultAudioSink.Builder(context)
            .setAudioProcessors(processors)
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .build()
    }
}
