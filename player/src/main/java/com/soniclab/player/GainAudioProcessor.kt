/*
 * Copyright 2026 soe1hom-arch
 * SPDX-License-Identifier: Apache-2.0
 */

package com.soniclab.player

import kotlin.math.pow

/**
 * Per-track gain in dB. Three independent stages sum together so system
 * toggles never disturb the user's sound:
 *
 * - [gainDb]: fixed gain (e.g. pre-effect headroom),
 * - [baseGainDb]: user/preset loudness boost (never touched by toggles),
 * - [autoGainDb]: ReplayGain auto-normalization (0 when disabled).
 *
 * 0 dB total = passthrough. Runs on the audio thread.
 */
class GainAudioProcessor : PcmAudioProcessor() {

    @Volatile
    var gainDb: Float = 0f

    /** User/preset loudness boost; only the effects UI writes this. */
    @Volatile
    var baseGainDb: Float = 0f

    /** Auto-normalization gain; the player writes this per track. */
    @Volatile
    var autoGainDb: Float = 0f

    override fun isEffectActive(): Boolean = gainDb != 0f || baseGainDb != 0f || autoGainDb != 0f

    override fun processSamples(input: FloatArray, frames: Int): FloatArray {
        val gain = 10f.pow((gainDb + baseGainDb + autoGainDb) / 20f)
        for (i in input.indices) input[i] *= gain
        return input
    }
}
