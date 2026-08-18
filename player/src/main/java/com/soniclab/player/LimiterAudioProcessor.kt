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

    override fun isEffectActive(): Boolean = enabled

    override fun processSamples(input: FloatArray, frames: Int): FloatArray {
        var peak = 0f
        for (i in input.indices) peak = max(peak, abs(input[i]))
        val target = if (peak > threshold && peak > 0f) threshold / peak else 1f
        currentGain = if (target < currentGain) {
            target // instant attack — same frame is attenuated, so no click
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
    }

    override fun onFlush() {
        currentGain = 1f
    }

    companion object {
        const val DEFAULT_THRESHOLD = 0.98f
        private const val RELEASE_MS = 120f
    }
}
