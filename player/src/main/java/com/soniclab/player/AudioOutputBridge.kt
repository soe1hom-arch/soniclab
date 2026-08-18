/*
 * Copyright 2026 soe1hom-arch
 * SPDX-License-Identifier: Apache-2.0
 */

package com.soniclab.player

/**
 * Build-time output configuration read by [PlaybackService] whenever the
 * player is (re)built. Hi-res keeps the chain in float from decoder to
 * AudioTrack instead of down-converting to 16-bit.
 */
object AudioOutputBridge {
    @Volatile
    var hiResEnabled: Boolean = false
}
