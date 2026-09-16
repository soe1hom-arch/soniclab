/*
 * Copyright 2026 soe1hom-arch
 * SPDX-License-Identifier: Apache-2.0
 */

package com.soniclab.player

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Stereo balance: [-1..1], -1 = full left, 0 = center, +1 = full right.
 * Uses equal-power pan law (cos/sin) so the center stays at unity
 * instead of the +3 dB bump that linear pan produces.
 * Passthrough for mono. Runs on the audio thread.
 */
class BalanceAudioProcessor : PcmAudioProcessor() {

    @Volatile
    var balance: Float = 0f

    override fun isEffectActive(): Boolean = balance != 0f && outputChannels >= 2

    override fun processSamples(input: FloatArray, frames: Int): FloatArray {
        if (balance == 0f) return input
        // Equal-power pan: -1 = full left, 0 = center, +1 = full right.
        // At center (balance=0), both gains = -3 dB (1/sqrt(2)), so the
        // perceived loudness stays constant as the image moves left/right.
        val theta = ((balance + 1f) * 0.25f * PI).toFloat()
        val gainL = cos(theta)
        val gainR = sin(theta)
        var channel = 0
        for (i in input.indices) {
            input[i] *= if (channel++ % 2 == 0) gainL else gainR
        }
        return input
    }
}
