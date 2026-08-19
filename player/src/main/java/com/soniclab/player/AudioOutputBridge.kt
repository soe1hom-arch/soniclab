/*
 * Copyright 2026 soe1hom-arch
 * SPDX-License-Identifier: Apache-2.0
 */

package com.soniclab.player

/**
 * Build-time output configuration read by [PlaybackService] whenever the
 * player is (re)built. Only affects Direct mode (no DSP), where media3's own
 * float path is used. With the DSP chain active, [DspAudioSink] always runs
 * the chain in 32-bit float and outputs float PCM regardless of this flag.
 *
 * In DSP mode this flag still selects AudioTrack playback params for
 * speed/pitch handling.
 */
object AudioOutputBridge {
    @Volatile
    var hiResEnabled: Boolean = false
}
