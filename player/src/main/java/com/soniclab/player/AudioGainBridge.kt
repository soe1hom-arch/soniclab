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
    val processor = GainAudioProcessor().also { it.baseGainDb = DEFAULT_BASE_GAIN_DB }

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

    /** Makeup gain used by the optional loudness-matching feature. */
    var effectCompensationDb: Float
        get() = processor.effectCompensationDb
        set(value) {
            processor.effectCompensationDb = value.coerceIn(0f, 3f)
        }

    @Volatile
    var loudnessMatchEnabled: Boolean = true

    private const val DEFAULT_BASE_GAIN_DB = 3f
}
