package com.soniclab.player

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Real-time bass/treble tone control using RBJ low/high-shelf biquads.
 *
 * Works on every device (no AudioEffect session required) and on PCM16/float
 * tracks, and applies immediately to the currently playing track. At 0 dB
 * for both bands every coefficient is an exact identity, so there is no
 * coloration unless the user changes something.
 *
 * State per channel: 2 racks (bass => treble cascade) x (x1,x2,y1,y2).
 */
class ToneAudioProcessor : PcmAudioProcessor() {

    @Volatile
    var bassDb: Float = 0f

    @Volatile
    var trebleDb: Float = 0f

    private var cachedBassDb = 0f
    private var cachedTrebleDb = 0f
    private var bassA = FloatArray(3)
    private var bassB = FloatArray(2)
    private var trebleA = FloatArray(3)
    private var trebleB = FloatArray(2)
    private var state = FloatArray(0)

    override fun isEffectActive(): Boolean = bassDb != 0f || trebleDb != 0f

    override fun onFormatChanged() {
        resetState()
    }

    override fun onFlush() {
        resetState()
    }

    private fun resetState() {
        cachedBassDb = Float.NaN
        cachedTrebleDb = Float.NaN
        state = FloatArray(inputChannels * 8)
    }

    override fun processSamples(input: FloatArray, frames: Int): FloatArray {
        updateCoeffs()
        for (i in input.indices) {
            val c = i % inputChannels
            val base = c * 8
            val x0 = input[i]

            // Bass shelf.
            val x1 = state[base]
            val x2 = state[base + 1]
            val y1 = state[base + 2]
            val y2 = state[base + 3]
            val bass = bassA[0] * x0 + bassA[1] * x1 + bassA[2] * x2 - bassB[0] * y1 - bassB[1] * y2
            state[base] = x0
            state[base + 1] = x1
            state[base + 2] = bass
            state[base + 3] = y1

            // Treble shelf (cascaded after bass).
            val tx1 = state[base + 4]
            val tx2 = state[base + 5]
            val ty1 = state[base + 6]
            val ty2 = state[base + 7]
            val treble = trebleA[0] * bass + trebleA[1] * tx1 + trebleA[2] * tx2 - trebleB[0] * ty1 - trebleB[1] * ty2
            state[base + 4] = bass
            state[base + 5] = tx1
            state[base + 6] = treble
            state[base + 7] = ty1

            input[i] = treble
        }
        return input
    }

    private fun updateCoeffs() {
        val bass = bassDb.coerceIn(-12f, 12f)
        val treble = trebleDb.coerceIn(-12f, 12f)
        if (bass == cachedBassDb && treble == cachedTrebleDb) return
        cachedBassDb = bass
        cachedTrebleDb = treble
        val (ba, bb) = shelfCoeffs(sampleRateHz, BASS_FREQ_HZ, bass, SHELF_Q)
        bassA = ba
        bassB = bb
        val (ta, tb) = shelfCoeffs(sampleRateHz, TREBLE_FREQ_HZ, treble, SHELF_Q)
        trebleA = ta
        trebleB = tb
    }

    private fun shelfCoeffs(fs: Int, fc: Float, gainDb: Float, q: Float): Pair<FloatArray, FloatArray> {
        // RBJ audio-eq-cookbook low/high shelf. Low=boost/cut below the
        // crossover, high=above it; at 0 dB every coefficient collapses to
        // an identity filter.
        val a = 10f.pow(gainDb / 40f)
        val w0 = 2.0f * PI.toFloat() * fc / fs
        val c = cos(w0)
        val alpha = sin(w0) / (2f * q)
        val isLow = fc < 1000f
        val sqrtA = sqrt(a)
        val signC = if (isLow) -1f else 1f
        val signA1 = if (isLow) 1f else -1f

        val ap1 = a + 1f
        val am1 = a - 1f
        val b0 = a * (ap1 + am1 * c * signC + 2f * sqrtA * alpha)
        val b1 = -2f * a * ((a + 1f) * c + am1 * signC)
        val b2 = a * (ap1 + am1 * c * signC - 2f * sqrtA * alpha)
        val a0 = ap1 - am1 * c * signC + 2f * sqrtA * alpha
        val a1 = -2f * (am1 * signA1 + (a + 1f) * c)
        val a2 = ap1 - am1 * c * signC - 2f * sqrtA * alpha

        val norm = 1f / a0
        return FloatArray(3) { index ->
            when (index) {
                0 -> b0 * norm
                1 -> b1 * norm
                else -> b2 * norm
            }
        } to FloatArray(2) { index -> if (index == 0) a1 * norm else a2 * norm }
    }

    companion object {
        private const val BASS_FREQ_HZ = 220f
        private const val TREBLE_FREQ_HZ = 3200f
        private const val SHELF_Q = 0.71f
    }
}
