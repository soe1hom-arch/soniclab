/*
 * Copyright 2026 soe1hom-arch
 * SPDX-License-Identifier: Apache-2.0
 */

package com.soniclab.player

import kotlin.math.pow

/**
 * Per-track gain in dB (used by auto-normalization). 0 dB = passthrough.
 * Runs on the audio thread.
 */
class GainAudioProcessor : PcmAudioProcessor() {

    @Volatile
    var gainDb: Float = 0f

    override fun isEffectActive(): Boolean = gainDb != 0f

    override fun processSamples(input: FloatArray, frames: Int): FloatArray {
        val gain = 10f.pow(gainDb / 20f)
        for (i in input.indices) input[i] *= gain
        return input
    }
}
