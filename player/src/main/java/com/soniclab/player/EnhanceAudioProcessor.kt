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
 * Audio is accumulated in per-channel frames of ~10 ms ([frameChunk] follows
 * the sample rate: 441 @ 44.1 kHz, 960 @ 96 kHz); each channel is enhanced
 * independently (the bundled model is mono) and re-interleaved. A fixed
 * 512-frame chunk would shrink to ~5 ms at 96 kHz and make the enhancer's
 * gain adaptation audibly grainy on hi-res tracks. Runs on the audio thread,
 * so the enhancer must be cheap.
 *
 * Enhancer output is never hard-clamped here: a per-sample clamp at ±1 would
 * flat-top peaks into harsh "pecah" distortion before the final limiter ever
 * sees them. Overshoot is smoothed by the enhancer's own soft limiter and
 * caught by [LimiterAudioProcessor] at the end of the chain instead.
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
                    result[write++] = guardSample(enhanced[c][frame])
                }
            }
        }
        return result
    }

    override fun onEndOfStream() {
        val e = enhancer ?: return
        if (!enabled) {
            // Stale frames buffered while the effect was active must not be
            // released at EOS after the user switched it off mid-track.
            pending.clear()
            return
        }
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
                result[write++] = guardSample(enhanced[c][frame])
            }
        }
        appendOutput(result)
        pending.clear()
    }

    override fun onFlush() {
        pending.clear()
    }

    /** Passes real samples through; guards NaN/Inf from a broken model. */
    private fun guardSample(v: Float): Float = if (v.isFinite()) v else 0f

    /** ~10 ms of frames at the current sample rate (256..4096). */
    private val frameChunk: Int
        get() = (sampleRateHz / 100).coerceIn(256, 4096)
}
