/*
 * Copyright 2026 soe1hom-arch
 * SPDX-License-Identifier: Apache-2.0
 */

package com.soniclab.player

/**
 * Single-process bridge for the 3D/8D spatial effect. The service reads
 * [processor]; the UI sets the presets ([mode]) or mixes 3D/8D/Surround
 * manually via the individual switches.
 */
object AudioSpatialBridge {
    val processor = SpatialAudioProcessor()

    var mode: Int
        get() = processor.mode
        set(value) {
            processor.mode = value
        }

    var spatial3d: Boolean
        get() = processor.spatial3d
        set(value) {
            processor.spatial3d = value
            processor.mode = SpatialAudioProcessor.MODE_CUSTOM
        }

    var spatial8d: Boolean
        get() = processor.spatial8d
        set(value) {
            processor.spatial8d = value
            processor.mode = SpatialAudioProcessor.MODE_CUSTOM
        }

    var surround: Boolean
        get() = processor.surround
        set(value) {
            processor.surround = value
            processor.mode = SpatialAudioProcessor.MODE_CUSTOM
        }

    var widthStrength: Float
        get() = processor.widthStrength
        set(value) {
            processor.widthStrength = value.coerceIn(0f, 1f)
        }

    var rotationSeconds: Float
        get() = processor.rotationSeconds
        set(value) {
            processor.rotationSeconds = value.coerceIn(4f, 60f)
        }

    var panDepth: Float
        get() = processor.panDepth
        set(value) {
            processor.panDepth = value.coerceIn(0f, 1f)
        }
}
