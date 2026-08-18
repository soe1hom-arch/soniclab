/*
 * Copyright 2026 soe1hom-arch
 * SPDX-License-Identifier: Apache-2.0
 */

package com.soniclab.player

/**
 * Build-time output configuration read by [PlaybackService] whenever the
 * player is (re)built. Hi-res float output only applies in Direct mode
 * (no DSP): media3's float-output pipeline excludes custom processors, so
 * enabling it while the DSP chain is active would silently bypass every
 * effect on hi-res PCM.
 */
object AudioOutputBridge {
    @Volatile
    var hiResEnabled: Boolean = false
}
