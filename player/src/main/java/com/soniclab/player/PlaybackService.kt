/*
 * Copyright 2026 soe1hom-arch
 * SPDX-License-Identifier: Apache-2.0
 */

package com.soniclab.player

import android.content.Context
import android.content.Intent
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Foreground playback service. Owns the ExoPlayer + MediaSession and injects
 * the real-time DSP chain (balance → gain → EQ → tone → reverb → AI enhance
 * → spatial → limiter) into the audio sink.
 *
 * When [DirectOutputBridge.enabled] is on the player is rebuilt without any
 * processors — a true bypass for users who want the cleanest possible path
 * (auto-normalization also stops contributing gain).
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        buildSession()
    }

    private fun buildSession() {
        val direct = DirectOutputBridge.enabled
        val hiRes = AudioOutputBridge.hiResEnabled
        val factory = if (direct) {
            DefaultRenderersFactory(this).apply {
                // Keep the hi-res/FLAC software-decoder fallback in direct mode too.
                setEnableDecoderFallback(true)
                // Direct mode is a true bypass, so media3's float path is safe here.
                setEnableAudioFloatOutput(hiRes)
                setEnableAudioTrackPlaybackParams(hiRes)
            }
        } else {
            EnhanceRenderersFactory(
                this,
                arrayOf(
                    AudioBalanceBridge.processor,
                    AudioHeadroomBridge.processor,
                    AudioGainBridge.processor,
                    AudioEqualizerBridge.processor,
                    AudioToneBridge.processor,
                    AudioReverbBridge.processor,
                    AudioEnhanceBridge.processor,
                    AudioSpatialBridge.processor,
                    AudioLimiterBridge.processor
                )
            ).apply {
                // The DSP chain runs inside DspAudioSink in 32-bit float, so
                // media3's own float path is never used (it would drop every
                // custom processor). Hi-res float output is therefore always
                // on in DSP mode.
                setEnableAudioTrackPlaybackParams(hiRes)
            }
        }
        val player = ExoPlayer.Builder(this, factory).build()
        AudioSessionBridge.sessionId = player.audioSessionId
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_SET_DIRECT_OUTPUT) {
            // Always rebuild: the caller pre-sets the bridge before starting
            // the service, so an equality check here could never trigger and
            // the live session stayed stuck in the previous mode until some
            // other toggle (e.g. hi-res output) forced a rebuild.
            DirectOutputBridge.enabled = intent.getBooleanExtra(EXTRA_DIRECT_OUTPUT, false)
            rebuildPlayer()
        } else if (intent?.action == ACTION_RECONFIGURE_OUTPUT) {
            // Output flags (e.g. hi-res float) changed: rebuild preserving the queue.
            rebuildPlayer()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    /**
     * Recreates the player with/without the DSP chain, preserving the queue,
     * position, speed/pitch and repeat/shuffle state.
     */
    private fun rebuildPlayer() {
        val old = mediaSession
        val oldPlayer = old?.player
        if (oldPlayer == null) {
            buildSession()
            return
        }
        val items = ArrayList<MediaItem>(oldPlayer.mediaItemCount)
        for (i in 0 until oldPlayer.mediaItemCount) items.add(oldPlayer.getMediaItemAt(i))
        val index = oldPlayer.currentMediaItemIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
        val position = oldPlayer.currentPosition
        val playWhenReady = oldPlayer.playWhenReady
        val params = oldPlayer.playbackParameters
        val repeat = oldPlayer.repeatMode
        val shuffle = oldPlayer.shuffleModeEnabled

        oldPlayer.release()
        old.release()

        buildSession()
        val player = mediaSession?.player ?: return
        if (items.isNotEmpty()) {
            player.setMediaItems(items, index, position)
            player.prepare()
        }
        player.playbackParameters = params ?: PlaybackParameters(1f)
        player.repeatMode = repeat
        player.shuffleModeEnabled = shuffle
        if (playWhenReady) player.play()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
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

    companion object {
        const val ACTION_SET_DIRECT_OUTPUT = "com.soniclab.player.action.SET_DIRECT_OUTPUT"
        const val EXTRA_DIRECT_OUTPUT = "direct_output"
        const val ACTION_RECONFIGURE_OUTPUT = "com.soniclab.player.action.RECONFIGURE_OUTPUT"
    }
}

/** Injects the balance/gain/EQ/enhance chain into the audio sink. */
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
        // The delegate is a plain float-capable writer/position tracker with
        // an empty processor list; DspAudioSink runs the DSP chain itself.
        val delegate = DefaultAudioSink.Builder(context)
            .setAudioProcessors(arrayOf())
            .setEnableFloatOutput(true)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .build()
        return DspAudioSink(delegate, processors.toList())
    }
}
