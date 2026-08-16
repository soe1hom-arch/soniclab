package com.soniclab.toolkit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

/** JVM unit tests for the pure-Kotlin PCM DSP (no Android dependencies). */
class PcmProcessorTest {

    private fun sineStereo(frames: Int, freq: Float, sampleRate: Int = 44100): PcmData {
        val samples = FloatArray(frames * 2)
        for (i in 0 until frames) {
            val v = sin(2.0 * PI * freq * i / sampleRate).toFloat()
            samples[i * 2] = v
            samples[i * 2 + 1] = v
        }
        return PcmData(samples, sampleRate, 2)
    }

    @Test
    fun reverse_keepsLengthAndReversesFrames() {
        val data = sineStereo(4096, 440f)
        val out = PcmProcessor.reverse(data)
        assertEquals(data.samples.size, out.samples.size)
        assertEquals(data.samples[0], out.samples[out.samples.size - 2], 1e-6f)
        assertEquals(data.samples[1], out.samples[out.samples.size - 1], 1e-6f)
        assertEquals(data.samples[data.samples.size - 2], out.samples[0], 1e-6f)
    }

    @Test
    fun normalize_raisesQuietSignalTowardTarget() {
        // Constant amplitude 0.1 -> RMS -20 dBFS; target -14 LUFS needs +6 dB.
        val data = PcmData(FloatArray(8192) { 0.1f }, 44100, 1)
        val out = PcmProcessor.normalize(data, targetLufs = -14f)
        var sumSquares = 0.0
        for (sample in out.samples) sumSquares += sample.toDouble() * sample
        val rmsDb = 20.0 * log10(sqrt(sumSquares / out.samples.size))
        assertEquals(-14f, rmsDb.toFloat(), 1.0f)
    }

    @Test
    fun timeStretch_speedsUpDurationWithoutNaN() {
        val data = sineStereo(44100 * 4, 440f)
        val out = PcmProcessor.timeStretch(data, 0.5f)
        val expected = data.samples.size / 2
        val actual = out.samples.size
        assertTrue("expected ~$expected frames, got $actual", abs(actual - expected).toDouble() / expected < 0.2)
        assertTrue(out.samples.all { it.isFinite() })
        assertTrue(out.samples.all { abs(it) <= 1.001f })
    }

    @Test
    fun timeStretch_slowsDownDuration() {
        val data = sineStereo(44100 * 2, 440f)
        val out = PcmProcessor.timeStretch(data, 2f)
        assertTrue(out.samples.size > data.samples.size)
        assertTrue(out.samples.all { it.isFinite() })
    }

    @Test
    fun resample_scalesSampleRateAndDuration() {
        val data = sineStereo(44100, 440f)
        val out = PcmProcessor.resample(data, 0.5f)
        assertEquals(22050, out.sampleRate)
        assertEquals(22050.0, out.samples.size / 2.0, 500.0)
    }

    @Test
    fun pitchShift_preservesDurationApproximately() {
        val data = sineStereo(44100 * 2, 440f)
        val out = PcmProcessor.pitchShift(data, 7f)
        val ratio = out.samples.size.toDouble() / data.samples.size
        assertTrue("duration changed by $ratio", abs(ratio - 1.0) < 0.2)
        assertTrue(out.samples.all { it.isFinite() })
    }

    @Test
    fun vocalReduction_requiresStereo() {
        val mono = PcmData(FloatArray(1024), 44100, 1)
        assertNull(PcmProcessor.vocalReduction(mono))
        assertNotNull(PcmProcessor.vocalReduction(sineStereo(1024, 440f)))
    }

    @Test
    fun conformChannels_upAndDownMix() {
        val mono = PcmData(FloatArray(1024) { it.toFloat() / 1024 }, 44100, 1)
        val stereo = PcmProcessor.conformChannels(mono, 2)
        assertEquals(2, stereo.channels)
        val back = PcmProcessor.conformChannels(stereo, 1)
        assertEquals(1, back.channels)
        assertEquals(mono.samples.size, back.samples.size)
    }

    @Test
    fun toPcmData_roundTripsPcm16() {
        val bytes = ByteArray(64) { (it - 32).toByte() }
        val data = PcmProcessor.toPcmData(bytes, 8000, 1)
        assertEquals(32, data.samples.size)
        assertTrue(data.samples.all { it >= -1.01f && it <= 1.01f })
    }
}
