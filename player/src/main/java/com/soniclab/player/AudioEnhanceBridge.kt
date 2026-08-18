/*
 * Copyright 2026 soe1hom-arch
 * SPDX-License-Identifier: Apache-2.0
 */

package com.soniclab.player

import com.soniclab.ai.AiEnhancer

/**
 * Single-process bridge between the app and the [PlaybackService]'s audio
 * pipeline. The service reads [processor]; the app sets [enhancer] and
 * toggles [enabled] from the AI Enhance setting.
 */
object AudioEnhanceBridge {
    val processor = EnhanceAudioProcessor()

    var enhancer: AiEnhancer?
        get() = processor.enhancer
        set(value) {
            processor.enhancer = value
        }

    var enabled: Boolean
        get() = processor.enabled
        set(value) {
            processor.enabled = value
        }
}
