/*
 * Copyright 2026 soe1hom-arch
 * SPDX-License-Identifier: Apache-2.0
 */

package com.soniclab.player

/**
 * Single-process bridge for per-track gain (auto-normalization). The
 * service reads [processor]; the controller sets [gainDb].
 */
object AudioGainBridge {
    val processor = GainAudioProcessor()

    var gainDb: Float
        get() = processor.gainDb
        set(value) {
            processor.gainDb = value
        }
}
