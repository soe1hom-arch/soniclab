/*
 * Copyright 2026 soe1hom-arch
 * SPDX-License-Identifier: Apache-2.0
 */

package com.soniclab.player

/**
 * Single-process bridge for the real-time bass/treble tone control. The
 * service reads [processor]; the UI sets [bassDb] / [trebleDb] (-12..+12).
 */
object AudioToneBridge {
    val processor = ToneAudioProcessor()

    var bassDb: Float
        get() = processor.bassDb
        set(value) {
            processor.bassDb = value.coerceIn(-12f, 12f)
        }

    var trebleDb: Float
        get() = processor.trebleDb
        set(value) {
            processor.trebleDb = value.coerceIn(-12f, 12f)
        }
}
