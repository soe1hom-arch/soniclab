/*
 * Copyright 2026 soe1hom-arch
 * SPDX-License-Identifier: Apache-2.0
 */

package com.soniclab.ai

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Transparent DSP-based "AI Enhance" fallback — no EQ coloring, no hard
 * clipping. Adaptive gain toward a moderate loudness target (fast attack,
 * slow release, so quiet/loud passages stay even without pumping) plus a
 * soft-knee peak limiter that smooths overshoot instead of truncating it.
 * Constant input passes through unchanged in level, so the tone is not
 * altered.
 */
class ClassicEnhancer : AiEnhancer {

    override val isAiModelLoaded = false
    override val displayName = "DSP Enhance (transparan)"

    private var currentGainDb = 0f

    override fun enhance(samples: FloatArray): FloatArray {
        if (samples.isEmpty()) return samples
        var sumSq = 0.0
        var peak = 0f
        for (s in samples) {
            sumSq += s.toDouble() * s
            if (abs(s) > peak) peak = abs(s)
        }
        val rmsDb = 20.0 * log10(sqrt(sumSq / samples.size).coerceAtLeast(1e-9))

        // Approach the target gain slowly on release, quickly on attack.
        val desiredDb = (TARGET_RMS_DB - rmsDb).toFloat()
        val delta = desiredDb - currentGainDb
        currentGainDb += delta * if (delta > 0f) ATTACK else RELEASE
        currentGainDb = currentGainDb.coerceIn(-MAX_GAIN_DB, MAX_GAIN_DB)
        val gain = 10f.pow(currentGainDb / 20f)

        for (i in samples.indices) {
            samples[i] = softLimit(samples[i] * gain)
        }
        return samples
    }

    /** Smooth overshoot limiter — continuous, never a hard clip. Linear below
     *  1.0, then bends toward a hard ceiling of 1 + 1/[LIMIT_SHARPNESS], so a
     *  hot passage can never be truncated into a flat top. */
    private fun softLimit(x: Float): Float {
        val a = abs(x)
        if (a <= 1f) return x
        val over = a - 1f
        return sign(x) * (1f + over / (1f + LIMIT_SHARPNESS * over))
    }

    private fun sign(x: Float): Float = if (x < 0f) -1f else 1f

    companion object {
        /** RMS target in dBFS — moderate, matched to a hot-mastered library. */
        private const val TARGET_RMS_DB = -20f
        private const val ATTACK = 0.25f
        private const val RELEASE = 0.02f
        /** Ceiling for the adaptive boost — gentle, so quiet passages gain
         *  presence without the limiter "breathing". */
        private const val MAX_GAIN_DB = 4.5f
        /** Higher = tighter ceiling (1 + 1/8 = 1.125 max overshoot). */
        private const val LIMIT_SHARPNESS = 8f
    }
}
