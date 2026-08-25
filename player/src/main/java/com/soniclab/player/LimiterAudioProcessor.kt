/*
 * Copyright 2026 soe1hom-arch
 * SPDX-License-Identifier: Apache-2.0
 */

package com.soniclab.player

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max

/**
 * Final safety stage of the playback chain: a transparent peak limiter that
 * scales whole frames down instead of letting hard clipping truncate samples.
 * Boosting presets (bass + treble) can push peaks past full scale; the limiter
 * catches that at the end, so loud tracks stay clean instead of "pecah".
 *
 * Attack is instantaneous (the transient frame itself is attenuated, so there
 * is no click); release is slow to avoid pumping.
 */
class LimiterAudioProcessor : PcmAudioProcessor() {

    @Volatile
    var enabled: Boolean = true

    /** Peak level above which gain reduction kicks in (0..1). */
    @Volatile
    var threshold: Float = DEFAULT_THRESHOLD

    private var currentGain = 1f
    private val lookaheadBuffer = FloatArray(LOOKAHEAD_FRAMES)
    private var lookaheadPos = 0
    private var lookaheadFilled = false

    override fun isEffectActive(): Boolean = enabled

    override fun processSamples(input: FloatArray, frames: Int): FloatArray {
        // Scan ahead to find the peak in the lookahead window so transients
        // are attenuated *before* they hit the output — no more hard-clip
        // clicks on bass attacks.
        var futurePeak = 0f
        for (i in input.indices) futurePeak = max(futurePeak, abs(input[i]))
        lookaheadBuffer[lookaheadPos] = futurePeak
        lookaheadPos = (lookaheadPos + 1) % LOOKAHEAD_FRAMES
        if (lookaheadPos == 0) lookaheadFilled = true

        // Determine the worst peak the buffer has seen recently.
        var bufferPeak = 0f
        val count = if (lookaheadFilled) LOOKAHEAD_FRAMES else lookaheadPos
        for (i in 0 until count) bufferPeak = max(bufferPeak, lookaheadBuffer[i])

        val target = if (bufferPeak > threshold && bufferPeak > 0f) threshold / bufferPeak else 1f
        currentGain = if (target < currentGain) {
            target
        } else {
            val releaseCoef = 1f - exp(-1f / (RELEASE_MS / 1000f * sampleRateHz / frames.toFloat()))
            currentGain + (target - currentGain) * releaseCoef
        }
        if (currentGain != 1f) {
            for (i in input.indices) input[i] *= currentGain
        }
        return input
    }

    override fun onFormatChanged() {
        currentGain = 1f
        lookaheadPos = 0
        lookaheadFilled = false
    }

    override fun onFlush() {
        currentGain = 1f
        lookaheadPos = 0
        lookaheadFilled = false
    }

    companion object {
        const val DEFAULT_THRESHOLD = 0.90f
        private const val RELEASE_MS = 120f
        /** Lookahead in frames (~5 ms at 44.1 kHz). */
        private const val LOOKAHEAD_FRAMES = 220
    }
}
