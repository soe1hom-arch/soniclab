/*
 * Copyright 2026 soe1hom-arch
 * SPDX-License-Identifier: Apache-2.0
 */

package com.soniclab.ai

import android.content.Context

/**
 * The current release has no model that meets the quality bar for music, so
 * this implementation uses [ClassicEnhancer]. A neural runtime will only be
 * added together with a vetted model and its complete redistribution notices.
 */
class NeuralEnhancer internal constructor() : AiEnhancer {

    private val fallback = ClassicEnhancer()

    @Suppress("UNUSED_PARAMETER")
    constructor(context: Context) : this()

    override val isAiModelLoaded: Boolean
        get() = false

    override val displayName: String
        get() = fallback.displayName

    override fun enhance(samples: FloatArray): FloatArray = fallback.enhance(samples)
}
