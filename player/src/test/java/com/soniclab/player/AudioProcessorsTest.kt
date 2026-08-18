/*
 * Copyright 2026 soe1hom-arch
 * SPDX-License-Identifier: Apache-2.0
 */

package com.soniclab.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.soniclab.ai.AiEnhancer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

class AudioProcessorsTest {

    private val stereoPcm16 = AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_16BIT)
    private val monoPcm16 = AudioProcessor.AudioFormat(44100, 1, C.ENCODING_PCM_16BIT)
    private val stereoFloat = AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_FLOAT)

    private fun pcm16(vararg values: Short): ByteBuffer {
        val b = ByteBuffer.allocate(values.size * 2).order(ByteOrder.nativeOrder())
        values.forEach { b.putShort(it) }
        b.flip()
        return b
    }

    private fun floats(vararg values: Float): ByteBuffer {
        val b = ByteBuffer.allocate(values.size * 4).order(ByteOrder.nativeOrder())
        values.forEach { b.putFloat(it) }
        b.flip()
        return b
    }

    private fun readShorts(buffer: ByteBuffer): ShortArray {
        val out = ShortArray(buffer.remaining() / 2)
        var i = 0
        while (buffer.hasRemaining()) out[i++] = buffer.short
        return out
    }

    private fun readFloats(buffer: ByteBuffer): FloatArray {
        val out = FloatArray(buffer.remaining() / 4)
        var i = 0
        while (buffer.hasRemaining()) out[i++] = buffer.float
        return out
    }

    // --- SpatialAudioProcessor ---

    @Test
    fun spatial_3D_upmixesMonoToStereo() {
        val p = SpatialAudioProcessor()
        p.mode = SpatialAudioProcessor.MODE_3D
        val configured = p.configure(monoPcm16)
        assertEquals(2, configured.channelCount)
        assertTrue(p.isActive)

        p.queueInput(pcm16(1000, -1000, 2000, -2000))
        val out = readShorts(p.getOutput())
        assertEquals(8, out.size) // 4 frames x 2 channels
    }

    @Test
    fun spatial_8D_worksOnFloatStereo() {
        val p = SpatialAudioProcessor()
        p.mode = SpatialAudioProcessor.MODE_8D
        val configured = p.configure(stereoFloat)
        assertEquals(C.ENCODING_PCM_FLOAT, configured.encoding)

        p.queueInput(floats(0.5f, -0.5f, 0.25f, -0.25f))
        val out = p.getOutput()
        assertEquals(4 * 4, out.remaining()) // 2 frames x 2ch x 4 bytes
    }

    @Test
    fun spatial_3D8D_keepsCenterAudible() {
        val p = SpatialAudioProcessor()
        p.mode = SpatialAudioProcessor.MODE_3D_8D
        p.panDepth = 0.6f
        p.configure(stereoFloat)
        val center = FloatArray(64) { 0.5f }
        p.queueInput(floats(*center))
        val out = readFloats(p.getOutput())
        assertEquals(64, out.size)
        var minAbs = 1f
        for (v in out) minAbs = minOf(minAbs, abs(v))
        assertTrue("3D+8D must keep the center audible on headphones (min=$minAbs)", minAbs > 0.2f)
    }

    @Test
    fun spatial_8D_panDepth_keepsFarChannelAudible() {
        val p = SpatialAudioProcessor()
        p.mode = SpatialAudioProcessor.MODE_8D
        p.panDepth = 0.5f
        p.configure(stereoFloat)
        p.queueInput(floats(*FloatArray(64) { 0.5f }))
        val out = readFloats(p.getOutput())
        var minAbs = 1f
        for (v in out) minAbs = minOf(minAbs, abs(v))
        assertTrue("8D pan must not silence a channel (min=$minAbs)", minAbs > 0.1f)
    }

    @Test
    fun spatial_off_passesThroughStereo() {

        val p = SpatialAudioProcessor()
        p.mode = SpatialAudioProcessor.MODE_OFF
        p.configure(stereoPcm16)
        val input = pcm16(5000, -5000, 3000, -3000)
        p.queueInput(input)
        val out = p.getOutput()
        assertEquals(8, out.remaining())
        assertArrayEquals(shortArrayOf(5000, -5000, 3000, -3000), readShorts(out))
    }

    @Test
    fun spatial_modeToggle_appliesWithoutReconfigure() {
        val p = SpatialAudioProcessor()
        p.mode = SpatialAudioProcessor.MODE_OFF
        p.configure(stereoPcm16)
        p.queueInput(pcm16(5000, -5000))
        assertArrayEquals(shortArrayOf(5000, -5000), readShorts(p.getOutput()))

        // Toggling mid-stream takes effect on the next buffer without a flush.
        p.mode = SpatialAudioProcessor.MODE_3D
        p.queueInput(pcm16(5000, -5000))
        val out = readShorts(p.getOutput())
        assertNotEquals(shortArrayOf(5000, -5000).toList(), out.toList())
    }

    // --- LimiterAudioProcessor ---

    @Test
    fun limiter_reducesPeakToThreshold() {
        val p = LimiterAudioProcessor()
        p.threshold = LimiterAudioProcessor.DEFAULT_THRESHOLD
        p.configure(stereoFloat)
        p.queueInput(floats(2f, -2f, 0.5f, 0.5f))
        val out = readFloats(p.getOutput())
        assertEquals(4, out.size)
        var peak = 0f
        for (v in out) peak = max(peak, abs(v))
        assertTrue("peak must be limited to threshold, got $peak", peak <= 0.981f)
    }

    @Test
    fun limiter_disabled_passesThrough() {
        val p = LimiterAudioProcessor()
        p.enabled = false
        p.configure(stereoFloat)
        p.queueInput(floats(2f, -2f))
        assertArrayEquals(floatArrayOf(2f, -2f), readFloats(p.getOutput()), 0f)
    }

    @Test
    fun spatial_3D_presenceGuardBoostsCenter() {
        val p = SpatialAudioProcessor()
        p.mode = SpatialAudioProcessor.MODE_3D
        p.widthStrength = 1f
        p.configure(stereoFloat)
        p.queueInput(floats(*FloatArray(8) { 0.5f }))
        val out = readFloats(p.getOutput())
        for (v in out) {
            assertTrue("center must stay boosted above 0.5, got $v", abs(v) > 0.5f)
        }
    }

    // --- BalanceAudioProcessor ---

    @Test
    fun balance_pansLeft() {
        val p = BalanceAudioProcessor()
        p.balance = -1f
        p.configure(stereoPcm16)
        p.queueInput(pcm16(16000, -8000))
        val out = readShorts(p.getOutput())
        assertTrue(abs(out[0] - 16000) <= 1) // left stays full (16-bit rounding)
        assertEquals(0, out[1].toInt()) // right muted
    }

    @Test
    fun balance_center_passesThrough() {
        val p = BalanceAudioProcessor()
        p.balance = 0f
        p.configure(stereoPcm16)
        p.queueInput(pcm16(16000, -8000))
        assertArrayEquals(shortArrayOf(16000, -8000), readShorts(p.getOutput()))
    }

    // --- GainAudioProcessor ---

    @Test
    fun gain_6dB_doublesAmplitude() {
        val p = GainAudioProcessor()
        p.gainDb = 6.02f
        p.configure(stereoPcm16)
        p.queueInput(pcm16(8000, -4000))
        val out = readShorts(p.getOutput())
        assertTrue(abs(out[0] - 16000) <= 2)
        assertTrue(abs(out[1] + 8000) <= 2)
    }

    // --- ToneAudioProcessor ---

    @Test
    fun tone_zeroDb_isExactPassthrough() {
        val p = ToneAudioProcessor()
        p.bassDb = 0f
        p.trebleDb = 0f
        p.configure(monoPcm16)
        val input = pcm16(*ShortArray(64) { (sin(it * 0.1) * 10000).toInt().toShort() })
        val expected = readShorts(ByteBuffer.wrap(input.array()).order(ByteOrder.nativeOrder()))
        p.queueInput(input)
        assertArrayEquals(expected, readShorts(p.getOutput()))
    }

    @Test
    fun tone_bassBoost_raisesLowFrequency() {
        val p = ToneAudioProcessor()
        p.bassDb = 12f
        p.trebleDb = 0f
        p.configure(monoPcm16)
        p.queueInput(sineBuffer(220f, 4096, 44100))
        val boosted = readShorts(p.getOutput())
        assertTrue(rms(boosted) > 0.3f)
    }

    @Test
    fun tone_trebleBoost_raisesHighFrequency() {
        val p = ToneAudioProcessor()
        p.bassDb = 0f
        p.trebleDb = 12f
        p.configure(monoPcm16)
        p.queueInput(sineBuffer(8000f, 4096, 44100))
        val boosted = readShorts(p.getOutput())
        assertTrue(rms(boosted) > 0.3f)
    }

    // --- ReverbAudioProcessor ---

    @Test
    fun reverb_zeroWet_isPassthrough() {
        val p = ReverbAudioProcessor()
        p.wetMix = 0f
        p.configure(stereoPcm16)
        p.queueInput(pcm16(1000, -1000))
        assertArrayEquals(shortArrayOf(1000, -1000), readShorts(p.getOutput()))
    }

    @Test
    fun reverb_wet_changesOutputAndKeepsLength() {
        val p = ReverbAudioProcessor()
        p.wetMix = 0.6f
        p.roomSize = 0.7f
        p.configure(stereoPcm16)
        val src = ShortArray(2048) { (sin(2.0 * Math.PI * 440 / 44100 * it) * 12000).toInt().toShort() }
        val stereo = ShortArray(src.size * 2) { src[it / 2] }
        p.queueInput(pcm16(*stereo))
        val out = readShorts(p.getOutput())
        assertEquals(stereo.size, out.size)
        var different = 0
        for (i in out.indices) if (abs(out[i] - stereo[i]) > 40) different++
        assertTrue(different > 0)
    }

    // --- EqualizerAudioProcessor ---

    @Test
    fun equalizer_flat_isExactPassthrough() {
        val p = EqualizerAudioProcessor()
        p.bandGainsDb = FloatArray(EqualizerAudioProcessor.BAND_COUNT)
        p.configure(monoPcm16)
        val input = pcm16(*ShortArray(64) { (sin(it * 0.1) * 10000).toInt().toShort() })
        val expected = readShorts(ByteBuffer.wrap(input.array()).order(ByteOrder.nativeOrder()))
        p.queueInput(input)
        assertArrayEquals(expected, readShorts(p.getOutput()))
    }

    @Test
    fun equalizer_1kHzBoost_raisesThatBand() {
        val p = EqualizerAudioProcessor()
        // Boost the 1 kHz band; a 1 kHz sine must get louder.
        val gains = FloatArray(EqualizerAudioProcessor.BAND_COUNT)
        gains[5] = 12f
        p.bandGainsDb = gains
        p.configure(monoPcm16)
        p.queueInput(sineBuffer(1000f, 4096, 44100))
        val out = readShorts(p.getOutput())
        val flat = readShorts(ByteBuffer.wrap(
            pcm16(*ShortArray(4096) { (sin(2.0 * Math.PI * 1000 / 44100 * it) * 12000).toInt().toShort() }).array()
        ).order(ByteOrder.nativeOrder()))
        assertTrue("boosted rms ${rms(out)} vs flat ${rms(flat)}", rms(out) > rms(flat) * 1.5f)
    }

    // --- PCM16 dither / noise shaping ---

    @Test
    fun dither_16bit_encodesWithinTolerance() {
        val p = GainAudioProcessor()
        p.gainDb = 0.1f // tiny non-identity gain forces the float -> 16-bit path
        p.configure(stereoPcm16)
        p.queueInput(pcm16(8000, -8000, 16000, -16000))
        val out = readShorts(p.getOutput())
        assertEquals(4, out.size)
        // Gain of +0.1 dB ~ +1.16%; rounding + dither stays within a couple of LSB.
        assertTrue(abs(out[0] - 8093) <= 2)
        assertTrue(abs(out[1] + 8093) <= 2)
        assertTrue(abs(out[2] - 16186) <= 2)
        assertTrue(abs(out[3] + 16186) <= 2)
    }

    @Test
    fun dither_longBufferStaysClean() {
        // Regression: the noise shaper must feed back only the raw quantizer
        // error. The old loop accumulated the error (unstable pole) and an
        // entire buffer decayed into full-scale alternating noise (SNR ~ -12 dB).
        val n = 16384
        val input = ShortArray(n) { (sin(2.0 * PI * 440.0 / 44100.0 * it) * 12000).toInt().toShort() }
        DitherBridge.enabled = true
        try {
            val p = GainAudioProcessor().apply { gainDb = 0.5f }
            p.configure(monoPcm16)
            p.queueInput(pcm16(*input))
            val out = readShorts(p.getOutput())
            assertEquals(n, out.size)
            val gain = 10f.pow(0.5f / 20f)
            var maxErr = 0
            for (k in out.indices) {
                val ideal = (input[k] * gain).toInt().coerceIn(-32768, 32767)
                maxErr = max(maxErr, abs(out[k] - ideal))
            }
            assertTrue("dither must not blow up the signal (maxErr=$maxErr LSB)", maxErr <= 8)
        } finally {
            DitherBridge.enabled = true
        }
    }

    @Test
    fun dither_passthrough_staysBitExact() {
        val p = GainAudioProcessor()
        p.gainDb = 0f
        p.configure(stereoPcm16)
        val input = pcm16(12345, -23456, 1000, -1000)
        p.queueInput(input)
        assertArrayEquals(shortArrayOf(12345, -23456, 1000, -1000), readShorts(p.getOutput()))
    }

    // --- EnhanceAudioProcessor ---

    @Test
    fun enhance_disabled_passesThrough() {
        val p = EnhanceAudioProcessor()
        p.enabled = false
        p.configure(stereoPcm16)
        p.queueInput(pcm16(5000, -5000))
        assertArrayEquals(shortArrayOf(5000, -5000), readShorts(p.getOutput()))
    }

    @Test
    fun enhance_enabled_identityEnhancerMatchesInput() {
        val p = EnhanceAudioProcessor()
        p.enabled = true
        p.enhancer = IdentityEnhancer
        p.configure(monoPcm16)
        p.queueInput(sineBuffer(440f, 1024, 44100))
        val out = readShorts(p.getOutput())
        assertEquals(1024, out.size)
        p.queueEndOfStream()
        assertEquals(0, p.getOutput().remaining())
        assertTrue(p.isEnded)
    }

    private fun sineBuffer(freq: Float, frames: Int, sampleRate: Int): ByteBuffer {
        val values = ShortArray(frames) {
            (sin(2.0 * Math.PI * freq / sampleRate * it) * 12000).toInt().toShort()
        }
        return pcm16(*values)
    }

    private fun rms(values: ShortArray): Float {
        if (values.isEmpty()) return 0f
        var sum = 0.0
        for (v in values) sum += (v.toDouble() / 32768.0) * (v.toDouble() / 32768.0)
        return kotlin.math.sqrt((sum / values.size).toFloat())
    }

    private object IdentityEnhancer : AiEnhancer {
        override val isAiModelLoaded: Boolean get() = true
        override val displayName: String get() = "Identity (test)"
        override fun enhance(samples: FloatArray): FloatArray = samples
    }
}
