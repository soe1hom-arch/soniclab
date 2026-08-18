/*
 * Copyright 2026 soe1hom-arch
 * SPDX-License-Identifier: Apache-2.0
 */

package com.soniclab.player

import kotlin.math.min

/**
 * Stereo balance: [-1..1], -1 = full left, 0 = center, +1 = full right.
 * Passthrough for mono. Runs on the audio thread.
 */
class BalanceAudioProcessor : PcmAudioProcessor() {

    @Volatile
    var balance: Float = 0f

    override fun isEffectActive(): Boolean = balance != 0f && outputChannels >= 2

    override fun processSamples(input: FloatArray, frames: Int): FloatArray {
        val gainL = min(1f, 1f - balance).coerceIn(0f, 1f)
        val gainR = min(1f, 1f + balance).coerceIn(0f, 1f)
        var channel = 0
        for (i in input.indices) {
            input[i] *= if (channel++ % 2 == 0) gainL else gainR
        }
        return input
    }
}
