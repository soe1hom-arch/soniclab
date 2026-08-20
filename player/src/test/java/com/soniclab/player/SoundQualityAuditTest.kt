/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Quantitative sound-quality audit for the real-time playback chain and the
 * offline DSP toolkit. No listening test: measures objective metrics.
 */
package com.soniclab.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.soniclab.ai.ClassicEnhancer
import com.soniclab.analyzer.R128Meter
import com.soniclab.toolkit.PcmData
import com.soniclab.toolkit.PcmProcessor
import com.soniclab.toolkit.StretchQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

class SoundQualityAuditTest {

    private val sr = 44100
    private val floatFormat = AudioProcessor.AudioFormat(sr, 2, C.ENCODING_PCM_FLOAT)
    private val pcm16Format = AudioProcessor.AudioFormat(sr, 2, C.ENCODING_PCM_16BIT)

    private fun sineFrames(frames: Int, freq: Float, amp: Float): FloatArray {
        val out = FloatArray(frames * 2)
        for (i in 0 until frames) {
            val v = (amp * sin(2.0 * PI * freq * i / sr)).toFloat()
            out[i * 2] = v
            out[i * 2 + 1] = v
        }
        return out
    }

    private fun floatBuffer(samples: FloatArray): ByteBuffer {
        val b = ByteBuffer.allocate(samples.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        samples.forEach { b.putFloat(it) }
        b.flip()
        return b
    }

    private fun runPipeline(processors: List<AudioProcessor>, samples: FloatArray): FloatArray {
        val pipeline = DspFramePipeline(processors)
        pipeline.configure(floatFormat)
        val out = pipeline.process(floatBuffer(samples), C.ENCODING_PCM_FLOAT)
        val tail = pipeline.endOfStream()
        val total = ByteBuffer.allocate(out.remaining() + tail.remaining()).order(ByteOrder.LITTLE_ENDIAN)
        total.put(out).put(tail).flip()
        val result = FloatArray(total.remaining() / 4)
        for (i in result.indices) result[i] = total.float
        return result
    }

    private fun rmsDb(samples: FloatArray): Double = 20.0 * log10(
        sqrt(samples.sumOf { (it.toDouble() * it) / samples.size }).coerceAtLeast(1e-9)
    )

    private fun peak(samples: FloatArray): Float = samples.maxOfOrNull { abs(it) } ?: 0f

    private fun defaultChain(): List<AudioProcessor> = listOf(
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

    @Test
    fun printAuditMetrics() {
        val freqs = floatArrayOf(31.25f, 62.5f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
        val eq = EqualizerAudioProcessor()
        val sb = StringBuilder()
        for (b in freqs.indices) {
            eq.bandGainsDb = FloatArray(10)
            eq.bandGainsDb[b] = 2f // +2 dB single band
            val input = sineFrames(16384, freqs[b], 0.2f)
            val out = runPipeline(listOf(eq), input)
            val probeStart = 12000 * 2
            val probe = out.copyOfRange(probeStart, minOf(out.size, 16000 * 2))
            val measDb = rmsDb(probe) - rmsDb(input.copyOfRange(probeStart, minOf(input.size, 16000 * 2)))
            sb.append("EQ band ${freqs[b]}Hz: set=+2.0 dB measured=${"%.2f".format(measDb)} dB\n")
        }

        // Tempo / pitch duration ratios: timeStretch factor is a stretch ratio
        // (output/input); the Studio "tempo" control is a speed multiplier 1/factor.
        val data = PcmData(sineFrames(22050, 440f, 0.4f), sr, 2)
        val t2 = PcmProcessor.timeStretch(data, 2f, StretchQuality.HIGH)
        val t05 = PcmProcessor.timeStretch(data, 0.5f, StretchQuality.HIGH)
        sb.append("Stretch 2.00x (speed 0.5x) duration ratio: ${"%.3f".format(t2.samples.size / 2.0 / 22050)} (ideal 2.0)\n")
        sb.append("Stretch 0.50x (speed 2.0x) duration ratio: ${"%.3f".format(t05.samples.size / 2.0 / 22050)} (ideal 0.5)\n")
        val p12 = PcmProcessor.pitchShift(data, 12f, StretchQuality.HIGH)
        sb.append("Pitch +12st duration ratio: ${"%.3f".format(p12.samples.size / 2.0 / 22050)} (ideal 1.0)\n")

        // Limiter on/off.
        val hot = sineFrames(16384, 62.5f, 0.9f)
        AudioEqualizerBridge.setBandGain(1, 200)
        val withLim = runPipeline(defaultChain(), hot)
        AudioLimiterBridge.processor.enabled = false
        val noLim = runPipeline(defaultChain(), hot)
        AudioLimiterBridge.processor.enabled = true
        AudioEqualizerBridge.resetAll()
        sb.append("Limiter ON peak=${"%.4f".format(peak(withLim))} (raw boost would reach ~1.13)\n")
        sb.append("Limiter OFF peak=${"%.4f".format(peak(noLim))} (hard-clipped at 1.0)\n")

        System.out.println("AUDIT\n" + sb + "END_AUDIT")
        assertTrue(true)
    }

    @Test
    fun defaultChain_addsNoColoration() {
        val input = sineFrames(8192, 997f, 0.5f)
        val out = runPipeline(defaultChain(), input)
        val n = input.size / 2
        assertTrue("output has enough frames: ${out.size / 2} vs $n", out.size / 2 >= n)
        var maxDiff = 0f
        for (i in 0 until n * 2) maxDiff = maxOf(maxDiff, abs(out[i] - input[i]))
        assertTrue("max sample diff $maxDiff (expect ~0)", maxDiff < 1e-4f)
        assertEquals("RMS preserved", rmsDb(input), rmsDb(out.copyOfRange(0, n * 2)), 0.1)
    }

    @Test
    fun flatEqualizer_isBitExact() {
        val eq = EqualizerAudioProcessor()
        eq.bandGainsDb = FloatArray(EqualizerAudioProcessor.BAND_COUNT)
        val input = sineFrames(4096, 1000f, 0.5f)
        val out = runPipeline(listOf(eq), input)
        assertTrue("exact byte-for-byte passthrough at zero gains", out.contentEquals(input))
    }

    @Test
    fun equalizer_singleBandHitsSetGain() {
        val freqs = floatArrayOf(31.25f, 62.5f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
        val eq = EqualizerAudioProcessor()
        for (b in freqs.indices) {
            eq.bandGainsDb = FloatArray(10)
            eq.bandGainsDb[b] = 2f
            val input = sineFrames(16384, freqs[b], 0.2f)
            val out = runPipeline(listOf(eq), input)
            val probeStart = 12000 * 2
            val probe = out.copyOfRange(probeStart, minOf(out.size, 16000 * 2))
            val measDb = rmsDb(probe) - rmsDb(input.copyOfRange(probeStart, minOf(input.size, 16000 * 2)))
            assertTrue(
                "band ${freqs[b]}Hz measured ${"%.2f".format(measDb)} dB vs set +2.0 dB",
                abs(measDb - 2.0) < 0.3
            )
            assertTrue("finite output", probe.all { it.isFinite() })
        }
    }

    @Test
    fun limiter_preventsClippingAndClippingWithout() {
        val hot = sineFrames(16384, 62.5f, 0.9f)
        AudioEqualizerBridge.setBandGain(1, 200)
        val withLim = runPipeline(defaultChain(), hot)
        AudioLimiterBridge.processor.enabled = false
        val noLim = runPipeline(defaultChain(), hot)
        AudioLimiterBridge.processor.enabled = true
        AudioEqualizerBridge.resetAll()
        val p1 = peak(withLim)
        val p2 = peak(noLim)
        assertTrue("limiter ON: peak $p1 <= 1.0", p1 <= 1.0001f)
        assertTrue("limiter OFF: hard clip at 1.0 (peak $p2, raw would be ~1.13)", p2 == 1.0f)
        assertTrue("limiter reduces peak below hard clip", p1 < p2)
    }

    @Test
    fun enhancer_hitsLoudnessTargetWithoutClipping() {
        // The enhancer adapts per ~10 ms chunk (attack 25%/chunk, slow release),
        // so a single call must not be expected to reach the target instantly.
        // Feed the real buffer shape and assert the settled steady state.
        val e = ClassicEnhancer()
        val chunk = 441
        var settledDb = 0.0
        for (c in 0 until 60) {
            val input = FloatArray(chunk) { (0.2 * sin(2.0 * PI * 220.0 * it / sr)).toFloat() }
            val out = e.enhance(input)
            assertTrue(out.all { abs(it) <= 1.0001f })
            assertTrue(out.all { it.isFinite() })
            settledDb = rmsDb(out)
        }
        assertTrue("RMS target ~-20 dBFS after settling, got $settledDb", settledDb in -21.5..-18.5)
    }

    @Test
    fun wsolaStretchHitsTargetLength() {
        val data = PcmData(sineFrames(22050, 440f, 0.4f), sr, 2)
        val stretched = PcmProcessor.timeStretch(data, 2f, StretchQuality.HIGH)
        val ratio = stretched.samples.size / 2.0 / 22050
        assertTrue("stretch 2.0 -> duration $ratio (ideal 2.0)", ratio in 1.85..2.15)

        val shortened = PcmProcessor.timeStretch(data, 0.5f, StretchQuality.HIGH)
        val ratio2 = shortened.samples.size / 2.0 / 22050
        assertTrue("stretch 0.5 -> duration $ratio2 (ideal 0.5)", ratio2 in 0.4..0.6)
    }

    @Test
    fun pitchShiftPreservesDuration() {
        val frames = 22050
        val data = PcmData(sineFrames(frames, 440f, 0.4f), sr, 2)
        for (st in floatArrayOf(-12f, 12f)) {
            val out = PcmProcessor.pitchShift(data, st, StretchQuality.HIGH)
            assertTrue(out.samples.all { it.isFinite() })
            val ratio = out.samples.size / 2.0 / frames
            assertTrue("pitch ${st}st duration ratio $ratio (ideal 1.0)", ratio in 0.9..1.1)
        }
    }

    @Test
    fun wsolaStaysFiniteAndStable() {
        val data = PcmData(sineFrames(22050, 440f, 0.4f), sr, 2)
        for (factor in floatArrayOf(0.5f, 1f, 2f)) {
            val out = PcmProcessor.timeStretch(data, factor, StretchQuality.HIGH)
            assertTrue("tempo $factor finite", out.samples.all { it.isFinite() })
            assertTrue("tempo $factor in range", abs(out.samples.maxOf { abs(it) }) <= 1f)
        }
        val p = PcmProcessor.pitchShift(data, 12f, StretchQuality.HIGH)
        assertTrue("pitch finite", p.samples.all { it.isFinite() })
        assertTrue("pitch peak ok", abs(p.samples.maxOf { abs(it) }) <= 1f)
    }

    @Test
    fun r128Meter_measuresSineLoudness() {
        val frames = 44100 * 2
        val input = FloatArray(frames * 2) { i ->
            (0.1 * sin(2.0 * PI * 1000.0 * (i / 2) / sr)).toFloat()
        }
        val meter = R128Meter(sr, 2)
        meter.push(input)
        val lufs = meter.integratedLufs()
        val expected = 20.0 * log10(0.1) - 3.01 - 0.691
        assertTrue("measured $lufs vs expected $expected", lufs in (expected - 1.5)..(expected + 1.5))
    }

    @Test
    fun normalize_hitsMinus14Lufs() {
        val frames = 22050
        val input = FloatArray(frames * 2) { i ->
            (0.1 * sin(2.0 * PI * 1000.0 * (i / 2) / sr)).toFloat()
        }
        val data = PcmData(input, sr, 2)
        val norm = PcmProcessor.normalize(data, -14f)
        assertTrue(norm.samples.all { abs(it) <= 1f } && norm.samples.all { it.isFinite() })
        val meter = R128Meter(sr, 2)
        meter.push(norm.samples)
        assertTrue("normalized to ${meter.integratedLufs()} LUFS", meter.integratedLufs() in -15.0..-13.0)
    }
}
