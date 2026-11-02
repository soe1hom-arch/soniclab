/*
 * Copyright 2026 soe1hom-arch
 * SPDX-License-Identifier: Apache-2.0
 */

package com.soniclab.player

/**
 * Fixed user headroom (-3..0 dB) applied before the effect chain so boosts
 * (EQ, enhancer, preset loudness) have less chance of clipping into the
 * final limiter. 0 dB = passthrough.
 */
object AudioHeadroomBridge {
    val processor = GainAudioProcessor().also { it.gainDb = DEFAULT_HEADROOM_DB }

    var headroomDb: Float
        get() = processor.gainDb
        set(value) {
            val db = value.coerceIn(-3f, 0f)
            processor.gainDb = db
            AudioGainBridge.effectCompensationDb = if (AudioGainBridge.loudnessMatchEnabled) -db else 0f
        }

    private const val DEFAULT_HEADROOM_DB = -1.5f
}
