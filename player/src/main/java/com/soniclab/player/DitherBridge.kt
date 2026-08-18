/*
 * Copyright 2026 soe1hom-arch
 * SPDX-License-Identifier: Apache-2.0
 */

package com.soniclab.player

/**
 * User toggle for TPDF dither + noise shaping in the PCM16 encode path of
 * every active processor. Live value read on the audio thread; off = plain
 * rounding (clean, no added noise floor).
 */
object DitherBridge {
    @Volatile
    var enabled: Boolean = true
}
