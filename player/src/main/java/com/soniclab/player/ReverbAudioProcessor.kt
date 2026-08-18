/*
 * Copyright 2026 soe1hom-arch
 * SPDX-License-Identifier: Apache-2.0
 */

package com.soniclab.player

/**
 * Real-time "Room" reverb using a freeverb-style Schroeder network
 * (4 comb filters feeding 2 all-pass filters per channel). The right channel
 * gets slightly longer delays for a wider stereo image.
 *
 * [wetMix] 0..1 blends dry/reverb, [roomSize] 0..1 scales the comb feedback.
 * Applies immediately to the currently playing track; wetMix=0 is passthrough.
 * Runs on the audio thread; all state lives here.
 */
class ReverbAudioProcessor : PcmAudioProcessor() {

    @Volatile
    var wetMix: Float = 0f

    @Volatile
    var roomSize: Float = 0.5f

    private var chains = emptyArray<Chain>()

    override fun isEffectActive(): Boolean = wetMix > 0f

    override fun onFormatChanged() {
        buildChains()
    }

    override fun onFlush() {
        buildChains()
    }

    private fun buildChains() {
        chains = Array(inputChannels) { channel ->
            val combDelays = COMB_TUNINGS.map { delay ->
                (delay * sampleRateHz).toInt() + if (channel % 2 == 1) STEREO_OFFSET else 0
            }
            Chain(
                combs = combDelays.map { Comb(FloatArray(it)) },
                allpasses = ALLPASS_TUNINGS.map { delay ->
                    Allpass(FloatArray((delay * sampleRateHz).toInt().coerceAtLeast(1)))
                }
            )
        }
    }

    override fun processSamples(input: FloatArray, frames: Int): FloatArray {
        val dry = 1f - wetMix.coerceIn(0f, 1f)
        val feedback = FEEDBACK_MIN + roomSize.coerceIn(0f, 1f) * (FEEDBACK_MAX - FEEDBACK_MIN)
        for (i in input.indices) {
            val chain = chains[i % inputChannels]
            var wet = 0f
            for (comb in chain.combs) {
                val delayed = comb.buffer[comb.position]
                comb.damped += (delayed - comb.damped) * DAMPING
                comb.buffer[comb.position] = input[i] + comb.damped * feedback
                comb.position = (comb.position + 1) % comb.buffer.size
                wet += delayed
            }
            for (ap in chain.allpasses) {
                val buffered = ap.buffer[ap.position]
                val out = -wet + buffered
                ap.buffer[ap.position] = wet + buffered * ALLPASS_FEEDBACK
                ap.position = (ap.position + 1) % ap.buffer.size
                wet = out
            }
            input[i] = input[i] * dry + wet * wetMix * REVERB_GAIN
        }
        return input
    }

    private class Comb(val buffer: FloatArray) {
        var position = 0
        var damped = 0f
    }

    private class Allpass(val buffer: FloatArray) {
        var position = 0
    }

    private class Chain(val combs: List<Comb>, val allpasses: List<Allpass>)

    companion object {
        private val COMB_TUNINGS = floatArrayOf(0.0297f, 0.0371f, 0.0411f, 0.0437f)
        private val ALLPASS_TUNINGS = floatArrayOf(0.0051f, 0.0017f)
        private const val STEREO_OFFSET = 23
        private const val DAMPING = 0.45f
        private const val ALLPASS_FEEDBACK = 0.5f
        private const val FEEDBACK_MIN = 0.7f
        private const val FEEDBACK_MAX = 0.9f
        private const val REVERB_GAIN = 0.55f
    }
}
