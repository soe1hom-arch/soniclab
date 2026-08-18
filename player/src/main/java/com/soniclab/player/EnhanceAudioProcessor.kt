/*
 * Copyright 2026 soe1hom-arch
 * SPDX-License-Identifier: Apache-2.0
 */

package com.soniclab.player

import com.soniclab.ai.AiEnhancer

/**
 * Real-time Media3 [AudioProcessor] that runs the on-device AI Enhancer on
 * the playback path. Accepts PCM16 and PCM float (so hi-res/FLAC tracks keep
 * the enhancement too). When disabled (or no enhancer is attached) audio is
 * passed through untouched.
 *
 * Audio is accumulated in per-channel frames of [frameChunk] samples; each
 * channel is enhanced independently (the bundled TFLite model is mono) and
 * re-interleaved. Runs on the audio thread, so the enhancer must be cheap.
 */
class EnhanceAudioProcessor : PcmAudioProcessor() {

    @Volatile
    var enhancer: AiEnhancer? = null

    @Volatile
    var enabled: Boolean = false

    private val pending = ArrayList<Float>()

    override fun isEffectActive(): Boolean = enabled && enhancer != null

    override fun processSamples(input: FloatArray, frames: Int): FloatArray {
        for (v in input) pending.add(v)
        val e = enhancer ?: return FloatArray(0)
        val chunkSamples = frameChunk * inputChannels
        val chunks = pending.size / chunkSamples
        if (chunks == 0) return FloatArray(0)

        val result = FloatArray(chunks * chunkSamples)
        var write = 0
        repeat(chunks) {
            val slice = FloatArray(chunkSamples) { pending[it] }
            pending.subList(0, chunkSamples).clear()
            val perChannel = Array(inputChannels) { c ->
                FloatArray(frameChunk) { slice[it * inputChannels + c] }
            }
            val enhanced = Array(inputChannels) { c -> e.enhance(perChannel[c]) }
            for (frame in 0 until frameChunk) {
                for (c in 0 until inputChannels) {
                    result[write++] = enhanced[c][frame].coerceIn(-1f, 1f)
                }
            }
        }
        return result
    }

    override fun onEndOfStream() {
        val e = enhancer ?: return
        val remainingFrames = pending.size / inputChannels
        if (remainingFrames <= 0) return
        val slice = FloatArray(pending.size) { pending[it] }
        val perChannel = Array(inputChannels) { c ->
            FloatArray(remainingFrames) { slice[it * inputChannels + c] }
        }
        val enhanced = Array(inputChannels) { c -> e.enhance(perChannel[c]) }
        val result = FloatArray(remainingFrames * inputChannels)
        var write = 0
        for (frame in 0 until remainingFrames) {
            for (c in 0 until inputChannels) {
                result[write++] = enhanced[c][frame].coerceIn(-1f, 1f)
            }
        }
        appendOutput(result)
        pending.clear()
    }

    override fun onFlush() {
        pending.clear()
    }

    companion object {
        private const val frameChunk = 512
    }
}
