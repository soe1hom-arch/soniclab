/*
 * Copyright 2026 soe1hom-arch
 * SPDX-License-Identifier: Apache-2.0
 */

package com.soniclab.ai

/**
 * One-tap audio enhancement pipeline. Consumes PCM and returns enhanced PCM.
 * Implementations are strictly on-device.
 */
interface AiEnhancer {
    val isAiModelLoaded: Boolean
    val displayName: String

    /** Processes one buffer of float PCM (-1..1). Must not block the UI thread. */
    fun enhance(samples: FloatArray): FloatArray
}
