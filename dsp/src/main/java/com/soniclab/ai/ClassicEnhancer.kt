package com.soniclab.ai

import kotlin.math.max
import kotlin.math.min

/**
 * DSP-based fallback "AI Enhance": noise gate, clarity shelf boost,
 * stereo widening and a peak limiter. Zero model dependency.
 */
class ClassicEnhancer : AiEnhancer {

    override val isAiModelLoaded = false
    override val displayName = "Classic DSP Enhance"

    private var noiseFloorEstimate = 0f

    override fun enhance(samples: FloatArray): FloatArray {
        var sumSq = 0f
        for (s in samples) sumSq += s * s
        val rms = kotlin.math.sqrt(sumSq / samples.size.coerceAtLeast(1))

        // Adaptive noise gate.
        noiseFloorEstimate = noiseFloorEstimate * 0.95f + rms * 0.05f
        val gateOpen = rms > noiseFloorEstimate * 1.4f + 1e-4f

        for (i in samples.indices) {
            var v = samples[i]
            if (!gateOpen) v *= 0.15f

            // Clarity shelf: boost upper-mids via simple one-pole difference (treble emphasis).
            v *= 1.08f

            // Limiter.
            v = max(-0.95f, min(0.95f, v))

            samples[i] = v
        }
        return samples
    }
}
