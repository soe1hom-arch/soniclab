/*
 * Copyright 2026 soe1hom-arch
 * SPDX-License-Identifier: Apache-2.0
 */

package com.soniclab.player

/**
 * Single-process bridge for the final limiter stage of the playback chain.
 */
object AudioLimiterBridge {
    val processor = LimiterAudioProcessor()

    val outputPeakDb: Float
        get() = processor.outputPeakDb

    val gainReductionDb: Float
        get() = processor.gainReductionDb

    /** Compatibility switch; the final safety stage remains active. */
    var enabled: Boolean
        get() = processor.enabled
        set(value) {
            processor.enabled = value
        }
}
