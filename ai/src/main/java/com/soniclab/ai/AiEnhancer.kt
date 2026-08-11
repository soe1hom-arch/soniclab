package com.soniclab.ai

/**
 * One-tap AI Enhance pipeline. Consumes PCM and returns enhanced PCM.
 * Implementations are strictly on-device (TensorFlow Lite or classic DSP).
 */
interface AiEnhancer {
    val isAiModelLoaded: Boolean
    val displayName: String

    /** Processes one buffer of float PCM (-1..1). Must not block the UI thread. */
    fun enhance(samples: FloatArray): FloatArray
}
