/*
 * Copyright 2026 soe1hom-arch
 * SPDX-License-Identifier: Apache-2.0
 */

package com.soniclab.player

/** Coordinates the persistent effects switch and temporary A/B comparison. */
object AudioBypassBridge {
    @Volatile
    var masterEffectsEnabled: Boolean = true

    @Volatile
    var abBypass: Boolean = false

    val enabled: Boolean
        get() = !masterEffectsEnabled || abBypass
}
