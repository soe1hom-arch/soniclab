package com.soniclab.analyzer

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Iterative radix-2 Cooley-Tukey FFT operating on real input.
 * Input length must be a power of two.
 */
class Fft(size: Int) {
    private val n = size
    private val reverse = IntArray(n)
    private val cosTable = FloatArray(n / 2)
    private val sinTable = FloatArray(n / 2)

    init {
        require(n > 1 && n and (n - 1) == 0) { "FFT size must be a power of two" }
        // Bit reversal permutation
        var bits = 0
        var k = n
        while (k > 1) { k = k shr 1; bits++ }
        for (i in 0 until n) {
            reverse[i] = Integer.reverse(i) ushr (32 - bits)
        }
        for (i in 0 until n / 2) {
            val angle = 2.0 * PI * i / n
            cosTable[i] = cos(angle).toFloat()
            sinTable[i] = sin(angle).toFloat()
        }
    }

    /**
     * In-place FFT. On return, [real]/[imag] hold the spectrum bins.
     */
    fun transform(real: FloatArray, imag: FloatArray) {
        require(real.size == n && imag.size == n)
        for (i in 0 until n) {
            val j = reverse[i]
            if (j > i) {
                val tr = real[i]; real[i] = real[j]; real[j] = tr
                val ti = imag[i]; imag[i] = imag[j]; imag[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val half = len / 2
            val step = n / len
            for (i in 0 until n step len) {
                for (j in 0 until half) {
                    val t = j * step
                    val c = cosTable[t]
                    val s = sinTable[t]
                    val k = i + j + half
                    val ur = real[k] * c - imag[k] * s
                    val ui = real[k] * s + imag[k] * c
                    real[k] = real[i + j] - ur
                    imag[k] = imag[i + j] - ui
                    real[i + j] += ur
                    imag[i + j] += ui
                }
            }
            len = len shl 1
        }
    }

    /**
     * In-place inverse FFT (unscaled is 1/n applied here). On return,
     * [real]/[imag] hold the time-domain samples.
     */
    fun inverse(real: FloatArray, imag: FloatArray) {
        require(real.size == n && imag.size == n)
        for (i in 0 until n) imag[i] = -imag[i]
        transform(real, imag)
        for (i in 0 until n) {
            imag[i] = -imag[i]
            real[i] /= n
            imag[i] /= n
        }
    }

    fun magnitudes(real: FloatArray, imag: FloatArray): FloatArray {
        transform(real, imag)
        return FloatArray(n / 2) { hypot(real[it].toDouble(), imag[it].toDouble()).toFloat() }
    }

    /** Transforms a real sample buffer into magnitude spectrum (first n/2 bins). */
    fun magnitudes(samples: FloatArray): FloatArray {
        require(samples.size == n)
        val real = samples.copyOf()
        val imag = FloatArray(n)
        return magnitudes(real, imag)
    }
}
