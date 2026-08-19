/*
 * Copyright 2026 soe1hom-arch
 * SPDX-License-Identifier: Apache-2.0
 */

package com.soniclab.player

/**
 * Single-process bridge for per-track gain (auto-normalization). The
 * service reads [processor]; the effects UI sets [baseGainDb] (preset
 * loudness boost) and the player controller sets [autoGainDb] per track.
 * The two are additive, so toggling auto-normalization or Direct mode
 * never wipes the user's preset gain.
 */
object AudioGainBridge {
    val processor = GainAudioProcessor()

    /** User/preset loudness boost in dB (0 = flat). */
    var baseGainDb: Float
        get() = processor.baseGainDb
        set(value) {
            processor.baseGainDb = value
        }

    /** ReplayGain auto-normalization in dB (0 = disabled). */
    var autoGainDb: Float
        get() = processor.autoGainDb
        set(value) {
            processor.autoGainDb = value
        }
}
