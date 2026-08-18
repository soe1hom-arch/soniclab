/*
 * Copyright 2026 soe1hom-arch
 * SPDX-License-Identifier: Apache-2.0
 */

package com.soniclab.player

/**
 * Single-process bridge for the real-time Room reverb. The service reads
 * [processor]; the UI sets [wetMix] (0..1) and [roomSize] (0..1).
 */
object AudioReverbBridge {
    val processor = ReverbAudioProcessor()

    var wetMix: Float
        get() = processor.wetMix
        set(value) {
            processor.wetMix = value.coerceIn(0f, 1f)
        }

    var roomSize: Float
        get() = processor.roomSize
        set(value) {
            processor.roomSize = value.coerceIn(0f, 1f)
        }
}
