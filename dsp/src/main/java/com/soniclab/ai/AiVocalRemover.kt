package com.soniclab.ai

/**
 * Karaoke / vocal reduction by phase inversion on stereo pairs.
 * True AI vocal separation plugs in here later (Demucs-style TFLite model).
 */
class AiVocalRemover {

    data class Result(val vocals: FloatArray, val instrumental: FloatArray)

    /**
     * [interleavedStereo] holds L,R,L,R,... floats in -1..1.
     */
    fun separate(interleavedStereo: FloatArray): Result {
        val frames = interleavedStereo.size / 2
        val instrumental = FloatArray(frames)
        val vocals = FloatArray(frames)
        for (i in 0 until frames) {
            val l = interleavedStereo[i * 2]
            val r = interleavedStereo[i * 2 + 1]
            // Center channel ≈ vocals (L+R); side channel ≈ instrumental (L-R).
            instrumental[i] = (l - r) * 0.5f
            vocals[i] = (l + r) * 0.5f
        }
        return Result(vocals, instrumental)
    }
}
