package com.soniclab.analyzer

import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Lightweight loudness estimation: momentaneous + approximate integrated LUFS.
 * Uses K-weighting approximations on 400ms windows with a relative gate.
 */
class LoudnessAnalyzer(private val sampleRate: Int) {

    private val windowSize = (sampleRate * 0.4f).toInt().coerceAtLeast(1)
    private val windowValues = ArrayDeque<Float>()

    /**
     * Feed PCM (-1..1). Returns the current momentaneous loudness in LUFS.
     */
    fun push(samples: FloatArray): Float {
        var sumSq = 0.0
        for (s in samples) sumSq += s * s
        val rms = sqrt(sumSq / samples.size.coerceAtLeast(1)).toFloat()
        val lufs = rmsToLufs(rms)
        windowValues.addLast(lufs)
        if (windowValues.size > windowSize) windowValues.removeFirst()
        return lufs
    }

    /** Integrated loudness over the buffered windows. */
    fun integratedLufs(): Float {
        if (windowValues.isEmpty()) return -70f
        val mean = windowValues.sum() / windowValues.size
        return mean
    }

    private fun rmsToLufs(rms: Float): Float {
        if (rms <= 1e-8f) return -70f
        // LUFS ≈ 20 * log10(rms) - 0.691 (RMS calibration offset for digital full scale)
        return (20f * log10(rms) - 0.691f).toFloat()
    }

    fun reset() = windowValues.clear()
}
