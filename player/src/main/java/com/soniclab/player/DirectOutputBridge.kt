/*
 * Copyright 2026 soe1hom-arch
 * SPDX-License-Identifier: Apache-2.0
 */

package com.soniclab.player

/**
 * Static bridge telling [PlaybackService] whether to build the audio sink
 * WITHOUT any DSP processors. "Direct" mode is a true bypass: the app toggles
 * it from Settings, the service rebuilds the player (queue/position kept),
 * and the app-side MediaController rebinds automatically.
 */
object DirectOutputBridge {
    @Volatile
    var enabled: Boolean = false
}
