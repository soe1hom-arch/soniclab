/*
 * Copyright 2026 soe1hom-arch
 * SPDX-License-Identifier: Apache-2.0
 */

package com.soniclab.player

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Real-time 10-band software equalizer in the playback chain (RBJ peaking
 * biquads). Fixed ISO-ish center frequencies make the EQ behave identically
 * on every device — no dependence on the deprecated Android AudioEffect
 * session API. At all-zero gains every coefficient collapses to identity,
 * so there is no coloration unless the user changes something.
 *
 * State per channel: 10 bands x (x1, x2, y1, y2). Runs on the audio thread.
 */
class EqualizerAudioProcessor : PcmAudioProcessor() {

    @Volatile
    var bandGainsDb: FloatArray = FloatArray(BAND_COUNT)

    private var cachedGains = FloatArray(BAND_COUNT) { Float.NaN }
    private var coeffs = Array(BAND_COUNT) { FloatArray(5) }
    private var state = FloatArray(0)

    override fun isEffectActive(): Boolean {
        val gains = bandGainsDb
        for (g in gains) if (g != 0f) return true
        return false
    }

    override fun onFormatChanged() {
        resetState()
    }

    override fun onFlush() {
        resetState()
    }

    private fun resetState() {
        cachedGains = FloatArray(BAND_COUNT) { Float.NaN }
        state = FloatArray(inputChannels * BAND_COUNT * 4)
        updateCoeffs()
    }

    override fun processSamples(input: FloatArray, frames: Int): FloatArray {
        updateCoeffs()
        val ch = inputChannels.coerceAtLeast(1)
        for (i in input.indices) {
            val base = (i % ch) * (BAND_COUNT * 4)
            var x = input[i]
            for (b in 0 until BAND_COUNT) {
                val o = base + b * 4
                val x1 = state[o]
                val x2 = state[o + 1]
                val y1 = state[o + 2]
                val y2 = state[o + 3]
                val c = coeffs[b]
                val y = c[0] * x + c[1] * x1 + c[2] * x2 - c[3] * y1 - c[4] * y2
                state[o] = x
                state[o + 1] = x1
                state[o + 2] = y
                state[o + 3] = y1
                x = y
            }
            input[i] = x
        }
        return input
    }

    private fun updateCoeffs() {
        val gains = bandGainsDb
        var changed = false
        for (b in 0 until BAND_COUNT) {
            if (gains[b] != cachedGains[b]) {
                changed = true
                break
            }
        }
        if (!changed) return
        for (b in 0 until BAND_COUNT) {
            cachedGains[b] = gains[b]
            coeffs[b] = peakingCoeffs(sampleRateHz, CENTER_FREQS[b], gains[b].coerceIn(-15f, 15f))
        }
    }

    private fun peakingCoeffs(fs: Int, fc: Float, gainDb: Float): FloatArray {
        val a = 10f.pow(gainDb / 40f)
        val w0 = 2f * PI.toFloat() * fc / fs
        val c = cos(w0)
        val alpha = sin(w0) / (2f * BAND_Q)
        val b0 = 1f + alpha * a
        val b1 = -2f * c
        val b2 = 1f - alpha * a
        val a0 = 1f + alpha / a
        val a1 = -2f * c
        val a2 = 1f - alpha / a
        val norm = 1f / a0
        return floatArrayOf(b0 * norm, b1 * norm, b2 * norm, a1 * norm, a2 * norm)
    }

    companion object {
        const val BAND_COUNT = 10
        const val MIN_GAIN_MB = -1500
        const val MAX_GAIN_MB = 1500
        val CENTER_FREQS = floatArrayOf(
            31.25f, 62.5f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f
        )
        private const val BAND_Q = 1.41f
    }
}
