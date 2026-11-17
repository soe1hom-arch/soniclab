/*
 * Copyright 2026 soe1hom-arch
 * SPDX-License-Identifier: Apache-2.0
 */

package com.soniclab.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SpatialState(val mode: Int, val spatial3d: Boolean, val spatial8d: Boolean, val surround: Boolean) {
    val enabled: Boolean get() = spatial3d || spatial8d || surround
}

/**
 * Single-process bridge for the 3D/8D spatial effect. The service reads
 * [processor]; the UI sets the presets ([mode]) or mixes 3D/8D/Surround
 * manually via the individual switches.
 */
object AudioSpatialBridge {
    val processor = SpatialAudioProcessor()
    private val _state = MutableStateFlow(snapshot())
    val state = _state.asStateFlow()

    private fun snapshot() = SpatialState(processor.mode, processor.spatial3d, processor.spatial8d, processor.surround)
    private fun publishState() { _state.value = snapshot() }

    var mode: Int
        get() = processor.mode
        set(value) {
            processor.mode = value
            publishState()
        }

    var spatial3d: Boolean
        get() = processor.spatial3d
        set(value) {
            processor.spatial3d = value
            processor.mode = SpatialAudioProcessor.MODE_CUSTOM
            publishState()
        }

    var spatial8d: Boolean
        get() = processor.spatial8d
        set(value) {
            processor.spatial8d = value
            processor.mode = SpatialAudioProcessor.MODE_CUSTOM
            publishState()
        }

    var surround: Boolean
        get() = processor.surround
        set(value) {
            processor.surround = value
            processor.mode = SpatialAudioProcessor.MODE_CUSTOM
            publishState()
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
