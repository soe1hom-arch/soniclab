/*
 * Copyright 2026 soe1hom-arch
 * SPDX-License-Identifier: Apache-2.0
 */

package com.soniclab.analyzer

/**
 * Turns PCM samples into a small number of display-ready spectrum buckets
 * (logarithmic frequency grouping, like a typical music visualizer).
 */
class SpectrumAnalyzer(fftSize: Int = 1024, private val buckets: Int = 48) {

    private val fft = Fft(fftSize)
    private val smoothing = FloatArray(buckets)

    /**
     * [samples] must be exactly fftSize long, range -1f..1f.
     * Returns normalized bucket magnitudes in 0f..1f with light smoothing.
     */
    fun analyze(samples: FloatArray): FloatArray {
        val mags = fft.magnitudes(samples)
        val result = FloatArray(buckets)
        val maxBin = mags.size - 1
        for (b in 0 until buckets) {
            val low = ((b.toDouble() / buckets).pow2ToIndex(maxBin))
            val high = ((b + 1).toDouble() / buckets).pow2ToIndex(maxBin)
            var sum = 0f
            var count = 0
            for (i in low..high) {
                sum += mags[i]
                count++
            }
            result[b] = (sum / count.coerceAtLeast(1)) / (maxBin + 1)
        }
        // normalize
        val peak = result.maxOrNull()?.takeIf { it > 0f } ?: 1f
        for (b in 0 until buckets) {
            val target = (result[b] / peak).coerceIn(0f, 1f)
            result[b] = smoothing[b] + (target - smoothing[b]) * 0.6f
            smoothing[b] = result[b]
        }
        return result
    }

    private fun Double.pow2ToIndex(maxBin: Int): Int =
        ((Math.pow(2.0, this * 10.0) - 1.0) / 1023.0 * maxBin).toInt().coerceIn(0, maxBin)
}
