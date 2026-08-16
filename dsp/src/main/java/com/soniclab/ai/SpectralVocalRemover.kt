package com.soniclab.ai

import com.soniclab.analyzer.Fft
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * Spectral (karaoke) vocal remover built on STFT center-channel extraction,
 * far better than phase inversion: per frequency bin the coherent "center"
 * (usually vocals) is estimated from the stereo pair and removed, while
 * out-of-phase content (instruments) is preserved.
 *
 * Output arrays are interleaved stereo (L,R,L,R,...). Heavy but fully
 * offline; run on a background thread.
 */
class SpectralVocalRemover(private val fftSize: Int = 2048) {

    data class Result(val vocals: FloatArray, val instrumental: FloatArray)

    private val hop = fftSize / 2
    private val fft = Fft(fftSize)
    private val window = FloatArray(fftSize) { i -> hann(i, fftSize) }

    fun separate(interleavedStereo: FloatArray): Result {
        val totalFrames = interleavedStereo.size / 2
        val vocals = FloatArray(totalFrames * 2)
        val instrumental = FloatArray(totalFrames * 2)
        val weightSum = FloatArray(totalFrames)

        val lr = FloatArray(fftSize)
        val li = FloatArray(fftSize)
        val rr = FloatArray(fftSize)
        val ri = FloatArray(fftSize)
        val centerSpec = FloatArray(fftSize * 2) // interleaved center spectrum (re, im)
        val dL = FloatArray(fftSize * 2)
        val dR = FloatArray(fftSize * 2)

        var pos = 0
        while (pos < totalFrames) {
            lr.fill(0f); li.fill(0f); rr.fill(0f); ri.fill(0f)
            val available = minOf(fftSize, totalFrames - pos)
            for (i in 0 until available) {
                val w = window[i]
                lr[i] = interleavedStereo[(pos + i) * 2] * w
                rr[i] = interleavedStereo[(pos + i) * 2 + 1] * w
            }

            fft.transform(lr, li)
            fft.transform(rr, ri)

            // Estimate the coherent center component per bin (vocals).
            for (k in 0 until fftSize) {
                val magL = hypot(lr[k].toDouble(), li[k].toDouble()).toFloat()
                val magR = hypot(rr[k].toDouble(), ri[k].toDouble()).toFloat()
                val magC = min(magL, magR)
                val phaseL = atan2(li[k].toDouble(), lr[k].toDouble())
                val phaseR = atan2(ri[k].toDouble(), rr[k].toDouble())
                val phaseC = phaseL + 0.5 * wrapAngle(phaseR - phaseL)
                centerSpec[k * 2] = magC * cos(phaseC).toFloat()
                centerSpec[k * 2 + 1] = magC * sin(phaseC).toFloat()
                dL[k * 2] = lr[k] - centerSpec[k * 2]
                dL[k * 2 + 1] = li[k] - centerSpec[k * 2 + 1]
                dR[k * 2] = rr[k] - centerSpec[k * 2]
                dR[k * 2 + 1] = ri[k] - centerSpec[k * 2 + 1]
            }

            // Vocals frame = IFFT(center); instrumental = IFFT(L-C), IFFT(R-C).
            val vocRe = FloatArray(fftSize) { centerSpec[it * 2] }
            val vocIm = FloatArray(fftSize) { centerSpec[it * 2 + 1] }
            fft.inverse(vocRe, vocIm)
            val inLRe = FloatArray(fftSize) { dL[it * 2] }
            val inLIm = FloatArray(fftSize) { dL[it * 2 + 1] }
            fft.inverse(inLRe, inLIm)
            val inRRe = FloatArray(fftSize) { dR[it * 2] }
            val inRIm = FloatArray(fftSize) { dR[it * 2 + 1] }
            fft.inverse(inRRe, inRIm)

            val n = minOf(fftSize, totalFrames - pos)
            for (i in 0 until n) {
                val w = window[i]
                val base = (pos + i) * 2
                vocals[base] += vocRe[i] * w
                vocals[base + 1] += vocRe[i] * w
                instrumental[base] += inLRe[i] * w
                instrumental[base + 1] += inRRe[i] * w
                weightSum[pos + i] += w
            }

            pos += hop
        }

        // Weighted overlap-add normalization.
        for (i in 0 until totalFrames) {
            val w = weightSum[i].coerceAtLeast(1e-6f)
            vocals[i * 2] /= w
            vocals[i * 2 + 1] /= w
            instrumental[i * 2] /= w
            instrumental[i * 2 + 1] /= w
        }
        return Result(vocals, instrumental)
    }

    private fun hann(index: Int, size: Int): Float =
        (0.5 - 0.5 * cos(2.0 * PI * index / (size - 1))).toFloat()

    private fun wrapAngle(angle: Double): Double {
        var a = angle
        while (a > PI) a -= 2.0 * PI
        while (a < -PI) a += 2.0 * PI
        return a
    }
}
