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
import androidx.media3.common.util.Clock
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.audio.AudioSink
import com.soniclab.ai.AiEnhancer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

class DspAudioSinkTest {

    private fun rawFormat(
        encoding: Int,
        channels: Int,
        rate: Int,
        delay: Int = 0,
        padding: Int = 0
    ): Format = Format.Builder()
        .setSampleMimeType(MimeTypes.AUDIO_RAW)
        .setPcmEncoding(encoding)
        .setSampleRate(rate)
        .setChannelCount(channels)
        .setEncoderDelay(delay)
        .setEncoderPadding(padding)
        .build()

    private fun pcm16(vararg values: Short): ByteBuffer {
        val b = ByteBuffer.allocate(values.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        values.forEach { b.putShort(it) }
        b.flip()
        return b
    }

    private fun pcm24(vararg values: Int): ByteBuffer {
        val b = ByteBuffer.allocate(values.size * 3).order(ByteOrder.LITTLE_ENDIAN)
        values.forEach { v ->
            b.put((v and 0xFF).toByte())
            b.put(((v shr 8) and 0xFF).toByte())
            b.put(((v shr 16) and 0xFF).toByte())
        }
        b.flip()
        return b
    }

    private class FakeAudioSink : AudioSink {
        val fed = mutableListOf<FloatArray>()
        val pts = mutableListOf<Long>()
        var configuredFormat: Format? = null
        var ended = false
        var flushed = false

        override fun setListener(listener: AudioSink.Listener) {}
        override fun setPlayerId(playerId: PlayerId?) {}
        override fun setClock(clock: Clock) {}
        override fun supportsFormat(format: Format): Boolean = true
        override fun getFormatSupport(format: Format): Int = AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY
        override fun getCurrentPositionUs(sourceEnded: Boolean): Long = 0L
        override fun configure(inputFormat: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {
            configuredFormat = inputFormat
        }
        override fun play() {}
        override fun handleDiscontinuity() {}
        override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
            buffer.order(ByteOrder.LITTLE_ENDIAN)
            val floats = FloatArray(buffer.remaining() / 4)
            var i = 0
            while (buffer.hasRemaining()) floats[i++] = buffer.float
            fed.add(floats)
            pts.add(presentationTimeUs)
            return true
        }
        override fun playToEndOfStream() {
            ended = true
        }
        override fun isEnded(): Boolean = ended
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
        override fun flush() {
            flushed = true
        }
        override fun reset() {}
        override fun release() {}
    }

    @Test
    fun passthroughFeedsFloatToDelegate() {
        val fake = FakeAudioSink()
        val sink = DspAudioSink(fake, listOf(GainAudioProcessor())) // 0 dB -> inactive
        sink.configure(rawFormat(C.ENCODING_PCM_16BIT, 2, 44100), 0, null)
        assertEquals(C.ENCODING_PCM_FLOAT, fake.configuredFormat!!.pcmEncoding)
        assertEquals(2, fake.configuredFormat!!.channelCount)

        val handled = sink.handleBuffer(pcm16(0, 1000, -1000, 32767, -32768, -12345), 1_000_000L, 1)
        assertTrue("input must be fully handled", handled)
        assertEquals(1, fake.fed.size)
        val expected = floatArrayOf(
            0f, 1000f / 32768f, -1000f / 32768f, 32767f / 32768f, -32768f / 32768f, -12345f / 32768f
        )
        assertArrayEquals(expected, fake.fed[0], 1e-7f)
    }

    @Test
    fun decodes24BitThroughSink() {
        val fake = FakeAudioSink()
        val sink = DspAudioSink(fake, listOf(GainAudioProcessor()))
        sink.configure(rawFormat(C.ENCODING_PCM_24BIT, 2, 44100), 0, null)
        val values = intArrayOf(0, 0x400000, -0x400000, 0x7FFFFF, -0x800000, 0x123456)
        assertTrue(sink.handleBuffer(pcm24(*values), 0L, 1))
        val floats = fake.fed[0]
        for (i in values.indices) {
            assertEquals(values[i] / 8388608f, floats[i], 1e-6f)
        }
    }

    @Test
    fun encoderDelayIsDropped() {
        val fake = FakeAudioSink()
        val sink = DspAudioSink(fake, listOf(GainAudioProcessor()))
        sink.configure(rawFormat(C.ENCODING_PCM_16BIT, 2, 44100, delay = 2), 0, null)
        assertTrue(sink.handleBuffer(pcm16(1, 2, 3, 4, 5, 6, 7, 8), 0L, 1))
        val floats = fake.fed[0]
        // First 2 stereo frames (4 samples) are the encoder delay and must be dropped.
        assertEquals(4, floats.size)
        assertArrayEquals(floatArrayOf(5f / 32768f, 6f / 32768f, 7f / 32768f, 8f / 32768f), floats, 1e-7f)
    }

    @Test
    fun encoderPaddingIsRetainedUntilEndOfStream() {
        val fake = FakeAudioSink()
        val sink = DspAudioSink(fake, listOf(GainAudioProcessor()))
        sink.configure(rawFormat(C.ENCODING_PCM_16BIT, 2, 44100, padding = 2), 0, null)
        assertTrue(sink.handleBuffer(pcm16(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12), 0L, 1))
        // 6 frames fed, last 2 retained as padding -> 4 frames = 8 floats.
        assertEquals(8, fake.fed[0].size)
        sink.playToEndOfStream()
        assertTrue("delegate must be ended", fake.ended)
        assertEquals(8, fake.fed[0].size)
    }

    @Test
    fun chunkedEnhanceFlushesAtEndOfStream() {
        val fake = FakeAudioSink()
        val enh = EnhanceAudioProcessor().apply {
            enabled = true
            enhancer = object : AiEnhancer {
                override val isAiModelLoaded = false
                override val displayName = "test-identity"
                override fun enhance(samples: FloatArray): FloatArray = samples
            }
        }
        val sink = DspAudioSink(fake, listOf(enh))
        sink.configure(rawFormat(C.ENCODING_PCM_16BIT, 1, 44100), 0, null)

        val framesA = 100 // below the 512-frame chunk
        val shortsA = ShortArray(framesA) { (0.1f * sin(2.0 * PI * 440.0 * it / 44100.0).toFloat() * 32767f).roundToInt().toShort() }
        assertTrue(sink.handleBuffer(pcm16(*shortsA), 0L, 1))
        assertTrue("chunk not full -> nothing fed yet", fake.fed.isEmpty())

        val framesB = 500 // 600 total -> one 512-frame chunk emitted
        val shortsB = ShortArray(framesB) { (0.1f * sin(2.0 * PI * 440.0 * (it + framesA) / 44100.0).toFloat() * 32767f).roundToInt().toShort() }
        assertTrue(sink.handleBuffer(pcm16(*shortsB), 10_000L, 1))
        assertEquals(512, fake.fed[0].size)

        sink.playToEndOfStream()
        assertEquals("tail chunk must flush at EOS", 88, fake.fed[1].size)
        assertTrue(fake.ended)
    }
}
