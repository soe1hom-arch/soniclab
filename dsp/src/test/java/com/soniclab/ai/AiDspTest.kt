/*
 * Copyright 2026 soe1hom-arch
 * SPDX-License-Identifier: Apache-2.0
 */

package com.soniclab.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

/** JVM tests for the on-device DSP fallback and the spectral vocal remover. */
class AiDspTest {

    @Test
    fun classicEnhancer_keepsLevelAndNeverClips() {
        val e = ClassicEnhancer()
        val samples = FloatArray(4096) { 0.2f }
        val out = e.enhance(samples)
        assertTrue(out.all { it.isFinite() })
        assertTrue(out.all { abs(it) <= 1f })
        // 0.2 is already near the -18 dBFS target; level must stay ~unchanged.
        val rms = sqrt(out.sumOf { (it * it).toDouble() } / out.size)
        assertEquals(0.2, rms, 0.05)
    }

    @Test
    fun classicEnhancer_tamesHotSignalWithoutHardClipping() {
        val e = ClassicEnhancer()
        val samples = FloatArray(4096) { 0.98f }
        val out = e.enhance(samples)
        assertTrue(out.all { it.isFinite() })
        assertTrue("no hard clip above 1.02: ${out.maxOf { abs(it) }}", out.all { abs(it) <= 1.02f })
    }

    @Test
    fun spectralVocalRemover_splitsCenterFromPannedSide() {
        val frames = 44100 * 2
        val input = FloatArray(frames * 2)
        for (i in 0 until frames) {
            val center = 0.3 * sin(2.0 * PI * 440 * i / 44100)
            val sideL = 0.3 * sin(2.0 * PI * 880 * i / 44100) // hard-panned left
            input[i * 2] = (center + sideL).toFloat()
            input[i * 2 + 1] = center.toFloat()
        }
        val result = SpectralVocalRemover().separate(input)
        assertEquals(input.size, result.vocals.size)
        assertEquals(input.size, result.instrumental.size)

        // Correlate with the two source tones over a steady middle segment.
        val start = frames / 4
        val end = start + frames / 2
        val vocCenter = correlate(result.vocals, start, end) { i -> sin(2.0 * PI * 440 * i / 44100) }
        val instCenter = correlate(result.instrumental, start, end) { i -> sin(2.0 * PI * 440 * i / 44100) }
        val vocSide = correlate(result.vocals, start, end) { i -> sin(2.0 * PI * 880 * i / 44100) }
        val instSide = correlate(result.instrumental, start, end) { i -> sin(2.0 * PI * 880 * i / 44100) }

        assertTrue("center (vocals) should stay in vocals: voc=$vocCenter inst=$instCenter", vocCenter > instCenter)
        assertTrue("side (panned) should stay in instrumental: voc=$vocSide inst=$instSide", instSide > vocSide)
    }

    private fun correlate(samples: FloatArray, start: Int, end: Int, tone: (Int) -> Double): Double {
        var sum = 0.0
        var count = 0
        for (i in start until end) {
            // Average the stereo pair into a mono estimate.
            val v = (samples[i * 2] + samples[i * 2 + 1]) / 2f
            sum += v * tone(i)
            count++
        }
        return sum / count
    }
}
