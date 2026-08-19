/*
 * Copyright 2026 soe1hom-arch
 * SPDX-License-Identifier: Apache-2.0
 */

package com.soniclab.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.soniclab.ai.AiEnhancer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

class DspFramePipelineTest {

    private val stereoFloat = AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_FLOAT)
    private val monoFloat = AudioProcessor.AudioFormat(44100, 1, C.ENCODING_PCM_FLOAT)

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

    private fun readFloats(buffer: ByteBuffer): FloatArray {
        val out = FloatArray(buffer.remaining() / 4)
        var i = 0
        while (buffer.hasRemaining()) out[i++] = buffer.float
        return out
    }

    @Test
    fun passthrough16IsExactFloat() {
        val pipeline = DspFramePipeline(listOf(GainAudioProcessor())) // 0 dB -> inactive
        pipeline.configure(stereoFloat)
        val samples = shortArrayOf(0, 1000, -1000, 32767, -32768, 12345)
        val floats = readFloats(pipeline.process(pcm16(*samples), C.ENCODING_PCM_16BIT))
        assertEquals(samples.size, floats.size)
        for (i in samples.indices) {
            assertEquals(samples[i] / 32768f, floats[i], 0f)
        }
    }

    @Test
    fun decodes24BitCorrectly() {
        val pipeline = DspFramePipeline(listOf(GainAudioProcessor()))
        pipeline.configure(stereoFloat)
        val values = intArrayOf(0, 0x400000, -0x400000, 0x7FFFFF, -0x800000, 0x123456)
        val floats = readFloats(pipeline.process(pcm24(*values), C.ENCODING_PCM_24BIT))
        assertEquals(values.size, floats.size)
        for (i in values.indices) {
            assertEquals(values[i] / 8388608f, floats[i], 1e-6f)
        }
    }

    @Test
    fun eqBoostRaisesAmplitude() {
        val eq = EqualizerAudioProcessor()
        val pipeline = DspFramePipeline(listOf(eq))
        pipeline.configure(monoFloat)
        val n = 4096
        val sine = FloatArray(n) { 0.2f * sin(2.0 * PI * 1000.0 * it / 44100.0).toFloat() }
        val shorts = ShortArray(n) { (sine[it] * 32767f).roundToInt().toShort() }

        val flat = readFloats(pipeline.process(pcm16(*shorts), C.ENCODING_PCM_16BIT))
        val flatRms = rms(flat)

        // Boost the 1000 Hz band by +12 dB: a 1000 Hz sine must grow ~4x.
        eq.bandGainsDb = FloatArray(EqualizerAudioProcessor.BAND_COUNT) { 0f }.also { it[5] = 12f }
        val boosted = readFloats(pipeline.process(pcm16(*shorts), C.ENCODING_PCM_16BIT))
        val boostedRms = rms(boosted)

        assertTrue("boosted RMS ${boostedRms} should clearly exceed flat $flatRms", boostedRms > flatRms * 3f)
    }

    @Test
    fun spatialUpmixesMonoToStereo() {
        val sp = SpatialAudioProcessor().apply { spatial3d = true }
        val pipeline = DspFramePipeline(listOf(sp))
        pipeline.configure(monoFloat)
        assertEquals(2, pipeline.outputChannels)

        val n = 512
        val shorts = ShortArray(n) { (0.3f * sin(2.0 * PI * 440.0 * it / 44100.0).toFloat() * 32767f).roundToInt().toShort() }
        val out = readFloats(pipeline.process(pcm16(*shorts), C.ENCODING_PCM_16BIT))
        assertEquals(n * 2, out.size)
        var peak = 0f
        for (v in out) peak = maxOf(peak, abs(v))
        assertTrue("3D output should carry signal (peak=$peak)", peak > 0.1f)
    }

    @Test
    fun enhanceTailDrainsAtEndOfStream() {
        val enh = EnhanceAudioProcessor().apply {
            enabled = true
            enhancer = object : AiEnhancer {
                override val isAiModelLoaded = false
                override val displayName = "test-identity"
                override fun enhance(samples: FloatArray): FloatArray = samples
            }
        }
        val pipeline = DspFramePipeline(listOf(enh))
        pipeline.configure(monoFloat)

        val frames = 100 // below the 512-frame chunk
        val shorts = ShortArray(frames) { (0.1f * sin(2.0 * PI * 440.0 * it / 44100.0).toFloat() * 32767f).roundToInt().toShort() }
        val partial = pipeline.process(pcm16(*shorts), C.ENCODING_PCM_16BIT)
        assertTrue("chunk not full yet -> no output", !partial.hasRemaining())

        val tail = pipeline.endOfStream()
        assertTrue("EOS must flush the remaining partial chunk", tail.hasRemaining())
        assertEquals(frames, readFloats(tail).size)
    }

    private fun rms(values: FloatArray): Float {
        var sum = 0.0
        for (v in values) sum += v.toDouble() * v
        return sqrt(sum / values.size).toFloat()
    }
}
