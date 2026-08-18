package com.soniclab.analyzer

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin

/**
 * EBU R128 / ITU-R BS.1770-4 loudness meter (pure Kotlin, JVM-testable).
 *
 * - K-weighting: RBJ high-pass (38.135 Hz, Q = 0.500327) followed by a +4 dB
 *   high-shelf (1681.974 Hz, Q = 0.707946) per channel — the same filters
 *   libebur128 uses (constants match at 48 kHz).
 * - Blocking: 400 ms blocks; block loudness L = -0.691 + 10*log10(mean
 *   channel energy) — the BS.1770 "M" response, not plain RMS.
 * - Integrated loudness: absolute gate (-70 LUFS), then relative gate
 *   (-10 LU below the absolute-gated mean).
 *
 * Feed interleaved PCM floats in -1..1. Partial trailing blocks are ignored
 * for the integrated measurement, per the standard.
 */
class R128Meter(private val sampleRate: Int, private val channels: Int = 2) {

    /** 400 ms in frames (BS.1770 block length), per channel. */
    private val blockFrames = (sampleRate * BLOCK_SECONDS).toInt().coerceAtLeast(1)

    /** Interleaved samples per 400 ms block. */
    private val samplesPerBlock = blockFrames * channels

    private val filters = Array(channels) { ChannelFilters(sampleRate) }
    private val blockEnergy = DoubleArray(channels)
    private var samplesInBlock = 0

    /** Linear-domain energies and LUFS of the absolute-gated blocks. */
    private val gatedEnergies = ArrayList<Double>()
    private val gatedLufs = ArrayList<Float>()

    private var lastMomentary = QUIET_LUFS
    private var peak = 0f

    /** Feeds interleaved PCM samples (L,R,L,R,... or just samples when mono). */
    fun push(samples: FloatArray) {
        for (i in samples.indices) {
            val c = i % channels
            val k = filters[c].process(samples[i])
            blockEnergy[c] += k.toDouble() * k
            if (k > peak) peak = k
            samplesInBlock++
            if (samplesInBlock >= samplesPerBlock) finishBlock()
        }
    }

    /** Loudness of the most recently completed 400 ms block (LUFS). */
    fun momentaryLufs(): Float = lastMomentary

    /** Integrated loudness with the -70 LUFS absolute and -10 LU relative gates. */
    fun integratedLufs(): Float {
        if (gatedLufs.isEmpty()) return QUIET_LUFS
        val absMean = gatedLufs.sum() / gatedLufs.size
        if (absMean <= QUIET_LUFS) return QUIET_LUFS
        val threshold = absMean - RELATIVE_GATE_LU
        var kept = 0.0
        var count = 0
        for (i in gatedLufs.indices) {
            if (gatedLufs[i] >= threshold) {
                kept += gatedEnergies[i]
                count++
            }
        }
        if (count == 0) return QUIET_LUFS
        return (-0.691 + 10.0 * log10(kept / count)).toFloat()
    }

    val truePeak: Float get() = peak

    fun reset() {
        blockEnergy.fill(0.0)
        samplesInBlock = 0
        gatedEnergies.clear()
        gatedLufs.clear()
        lastMomentary = QUIET_LUFS
        peak = 0f
        filters.forEach { it.reset() }
    }

    private fun finishBlock() {
        var energy = 0.0
        for (c in 0 until channels) energy += blockEnergy[c]
        // Mean square over the whole 400 ms block (time x channels), per BS.1770.
        energy /= (channels.toDouble() * blockFrames)
        blockEnergy.fill(0.0)
        samplesInBlock = 0
        val lufs = if (energy <= 1e-12) {
            QUIET_LUFS
        } else {
            (-0.691 + 10.0 * log10(energy)).toFloat()
        }
        lastMomentary = lufs
        if (lufs > ABSOLUTE_GATE_LUFS) {
            gatedEnergies.add(energy)
            gatedLufs.add(lufs)
        }
    }

    private class ChannelFilters(private val sampleRate: Int) {
        private val hpf = Biquad(hpfCoeffs(sampleRate))
        private val shelf = Biquad(shelfCoeffs(sampleRate))



        fun process(x: Float): Float = shelf.process(hpf.process(x))

        fun reset() {
            hpf.reset()
            shelf.reset()
        }

        private fun hpfCoeffs(fs: Int): FloatArray {
            // RBJ high-pass, libebur128's HPF stage.
            val w0 = 2.0 * PI * HPF_FREQ / fs
            val c = cos(w0)
            val alpha = sin(w0) / (2.0 * HPF_Q)
            val a0 = 1.0 + alpha
            return floatArrayOf(
                ((1.0 + c) / 2.0 / a0).toFloat(),
                (-(1.0 + c) / a0).toFloat(),
                ((1.0 + c) / 2.0 / a0).toFloat(),
                (-2.0 * c / a0).toFloat(),
                ((1.0 - alpha) / a0).toFloat()
            )
        }

        private fun shelfCoeffs(fs: Int): FloatArray {
            // RBJ high-shelf, libebur128's +4 dB stage.
            val a = 10.0.pow(SHELF_DB / 40.0)
            val w0 = 2.0 * PI * SHELF_FREQ / fs
            val c = cos(w0)
            val alpha = sin(w0) / (2.0 * SHELF_Q)
            val sqrtA = kotlin.math.sqrt(a)
            val ap1 = a + 1.0
            val am1 = a - 1.0
            val b0 = a * (ap1 + am1 * c + 2.0 * sqrtA * alpha)
            val b1 = -2.0 * a * (am1 + ap1 * c)
            val b2 = a * (ap1 + am1 * c - 2.0 * sqrtA * alpha)
            val a0r = ap1 - am1 * c + 2.0 * sqrtA * alpha
            val a1r = 2.0 * (am1 - ap1 * c)
            val a2r = ap1 - am1 * c - 2.0 * sqrtA * alpha
            return floatArrayOf(
                (b0 / a0r).toFloat(), (b1 / a0r).toFloat(), (b2 / a0r).toFloat(),
                (a1r / a0r).toFloat(), (a2r / a0r).toFloat()
            )
        }
    }

    /** Direct-form-I biquad; float state is ample for these gentle filters. */
    private class Biquad(private val coeffs: FloatArray) {
        private var x1 = 0f
        private var x2 = 0f
        private var y1 = 0f
        private var y2 = 0f

        fun process(x: Float): Float {
            val y = coeffs[0] * x + coeffs[1] * x1 + coeffs[2] * x2 - coeffs[3] * y1 - coeffs[4] * y2
            x2 = x1
            x1 = x
            y2 = y1
            y1 = y
            return y
        }

        fun reset() {
            x1 = 0f; x2 = 0f; y1 = 0f; y2 = 0f
        }
    }

    companion object {
        private const val BLOCK_SECONDS = 0.4
        const val QUIET_LUFS = -70f
        private const val ABSOLUTE_GATE_LUFS = -70f
        private const val RELATIVE_GATE_LU = 10f
        private const val HPF_FREQ = 38.1354709820245
        private const val HPF_Q = 0.500327037323877
        private const val SHELF_FREQ = 1681.974450955533
        private const val SHELF_Q = 0.707945784384138
        private const val SHELF_DB = 4.0
    }
}
