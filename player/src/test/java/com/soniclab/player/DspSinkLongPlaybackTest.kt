/*
 * Copyright 2026 soe1hom-arch
 * SPDX-License-Identifier: Apache-2.0
 */

package com.soniclab.player

import androidx.media3.common.AudioAttributes
import androidx.media3.common.AuxEffectInfo
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.Clock
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.audio.AudioSink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Long-playback guards for [DspAudioSink]: with the full DSP chain active and
 * a delegate that applies AudioTrack-style backpressure (a limited number of
 * frames consumed per call), a multi-minute stream must reach the output
 * exactly once and the internal queue must stay bounded (no OOM after
 * ~5 minutes, the symptom that used to kill the app).
 */
class DspSinkLongPlaybackTest {

    private fun fullChain(): List<AudioProcessor> = listOf(
        BalanceAudioProcessor(),
        GainAudioProcessor(),
        GainAudioProcessor(),
        EqualizerAudioProcessor(),
        ToneAudioProcessor(),
        ReverbAudioProcessor(),
        EnhanceAudioProcessor(),
        SpatialAudioProcessor(),
        LimiterAudioProcessor()
    )

    private open class ProbeSink : AudioSink {
        var totalFloats = 0L
        var buffers = 0
        override fun setListener(listener: AudioSink.Listener) {}
        override fun setPlayerId(playerId: PlayerId?) {}
        override fun setClock(clock: Clock) {}
        override fun supportsFormat(format: Format): Boolean = true
        override fun getFormatSupport(format: Format): Int = AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY
        override fun getCurrentPositionUs(sourceEnded: Boolean): Long = 0L
        override fun configure(inputFormat: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {}
        override fun play() {}
        override fun handleDiscontinuity() {}
        override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
            buffer.order(ByteOrder.LITTLE_ENDIAN)
            while (buffer.remaining() >= 4) {
                buffer.float
                totalFloats++
            }
            buffers++
            return true
        }
        override fun playToEndOfStream() {}
        override fun isEnded(): Boolean = true
        override fun hasPendingData(): Boolean = false
        override fun setPlaybackParameters(playbackParameters: PlaybackParameters) {}
        override fun getPlaybackParameters(): PlaybackParameters = PlaybackParameters(1f)
        override fun setSkipSilenceEnabled(skipSilenceEnabled: Boolean) {}
        override fun getSkipSilenceEnabled(): Boolean = false
        override fun setAudioAttributes(audioAttributes: AudioAttributes) {}
        override fun getAudioAttributes(): AudioAttributes? = null
        override fun setAudioSessionId(audioSessionId: Int) {}
        override fun setAuxEffectInfo(auxEffectInfo: AuxEffectInfo) {}
        override fun enableTunnelingV21() {}
        override fun disableTunneling() {}
        override fun setVolume(volume: Float) {}
        override fun pause() {}
        override fun flush() {}
        override fun reset() {}
        override fun release() {}
    }

    /** Consumes at most [maxFramesPerCall] frames per call, like a full AudioTrack. */
    private class BackpressureSink(private val maxFramesPerCall: Int) : ProbeSink() {
        override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
            buffer.order(ByteOrder.LITTLE_ENDIAN)
            var left = maxFramesPerCall
            while (buffer.remaining() >= 4 && left-- > 0) {
                buffer.float
                totalFloats++
            }
            buffers++
            return buffer.remaining() == 0
        }
    }

    private fun format(rate: Int, encoding: Int): Format = Format.Builder()
        .setSampleMimeType(MimeTypes.AUDIO_RAW)
        .setPcmEncoding(encoding)
        .setSampleRate(rate)
        .setChannelCount(2)
        .build()

    private fun pcm16Buffer(frames: Int, phase: Float): ByteBuffer {
        val b = ByteBuffer.allocate(frames * 2 * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (f in 0 until frames) {
            val v = (0.25f * sin(2.0 * PI * 440.0 * f / 44100.0 + phase).toFloat() * 32767f).toInt().toShort()
            b.putShort(v)
            b.putShort(v)
        }
        b.flip()
        return b
    }

    @Test
    fun fullChainWithBackpressureDeliversEveryFrameAndStaysBounded() {
        val probe = BackpressureSink(maxFramesPerCall = 2048)
        val chain = fullChain()
        val sink = DspAudioSink(probe, chain)
        sink.configure(format(44100, C.ENCODING_PCM_16BIT), 0, null)
        sink.play()

        // Representative effect state: EQ boost + bass + reverb + 3D + limiter.
        val eq = chain[3] as EqualizerAudioProcessor
        val tone = chain[4] as ToneAudioProcessor
        val reverb = chain[5] as ReverbAudioProcessor
        val spatial = chain[7] as SpatialAudioProcessor
        eq.bandGainsDb = FloatArray(10) { 0f }.also { it[0] = 6f }
        tone.bassDb = 3f
        reverb.wetMix = 0.25f
        spatial.spatial3d = true

        val totalFrames = 2 * 60 * 44100
        var fed = 0
        while (fed < totalFrames) {
            val frames = minOf(8192, totalFrames - fed)
            val b = pcm16Buffer(frames, fed.toFloat())
            var done = sink.handleBuffer(b, fed * 1_000_000L / 44100, 1)
            var guard = 0
            while (!done && guard++ < 1000) {
                done = sink.handleBuffer(b, fed * 1_000_000L / 44100, 1)
            }
            assertTrue("input must drain under backpressure at fed=$fed", done)
            fed += frames
        }
        sink.playToEndOfStream()
        var guard = 0
        while (!sink.isEnded() && guard++ < 100_000) sink.playToEndOfStream()

        assertEquals("every frame must reach the delegate exactly once", totalFrames * 2L, probe.totalFloats)
        Runtime.getRuntime().gc()
        val heap = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        assertTrue("internal queue must stay bounded (heap=$heap)", heap < 64 * 1024 * 1024)
    }
}
