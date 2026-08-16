package com.soniclab.ai

/**
 * Vocal separation engine. Implementations are strictly on-device:
 * a lightweight TFLite mask model, or the classic STFT center-channel
 * extraction as fallback. Outputs interleaved stereo.
 */
interface VocalSeparator {
    val isModelLoaded: Boolean
    val displayName: String

    fun separate(interleavedStereo: FloatArray): SpectralVocalRemover.Result
}
