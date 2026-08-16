package com.soniclab.ai

import android.content.Context
import android.util.Log
import com.soniclab.analyzer.Fft
import org.tensorflow.lite.Interpreter
import java.io.File
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.sin

/**
 * On-device vocal separator powered by a tiny TFLite model that predicts a
 * per-bin vocal mask from stereo spectral features
 * ([log1p(|L|), log1p(|R|), cos(phaseL - phaseR)]). The mask drives the
 * same center-channel synthesis as [SpectralVocalRemover]. When no model is
 * bundled it falls back to the classic spectral remover, so the toolkit
 * always works offline.
 */
class TfliteVocalSeparator(modelFile: File? = null) : VocalSeparator {

    private val fallback = SpectralVocalRemover()
    private var interpreter: Interpreter? = null

    override val isModelLoaded: Boolean get() = interpreter != null
    override val displayName: String
        get() = if (interpreter != null) "TFLite Vocal Separator" else "Spectral Vocal Remover (fallback)"

    init {
        try {
            if (modelFile != null && modelFile.exists()) {
                interpreter = Interpreter(loadModelFile(modelFile))
                Log.i(TAG, "Loaded vocal separator model ${modelFile.name}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vocal separator model load failed; using spectral fallback", e)
            interpreter = null
        }
    }

    override fun separate(interleavedStereo: FloatArray): SpectralVocalRemover.Result {
        val tf = interpreter ?: return fallback.separate(interleavedStereo)
        val fftSize = 2048
        val hop = fftSize / 2
        val fft = Fft(fftSize)
        val window = FloatArray(fftSize) { i -> hannWindow(i, fftSize) }
        val totalFrames = interleavedStereo.size / 2
        val vocals = FloatArray(totalFrames * 2)
        val instrumental = FloatArray(totalFrames * 2)
        val weightSum = FloatArray(totalFrames)

        val lr = FloatArray(fftSize)
        val li = FloatArray(fftSize)
        val rr = FloatArray(fftSize)
        val ri = FloatArray(fftSize)
        val features = Array(1) { FloatArray(3) }
        val output = Array(1) { FloatArray(1) }

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

            val vocRe = FloatArray(fftSize)
            val vocIm = FloatArray(fftSize)
            val inLRe = FloatArray(fftSize)
            val inLIm = FloatArray(fftSize)
            val inRRe = FloatArray(fftSize)
            val inRIm = FloatArray(fftSize)

            for (k in 0 until fftSize) {
                val magL = hypot(lr[k].toDouble(), li[k].toDouble()).toFloat()
                val magR = hypot(rr[k].toDouble(), ri[k].toDouble()).toFloat()
                val phaseL = atan2(li[k].toDouble(), lr[k].toDouble())
                val phaseR = atan2(ri[k].toDouble(), rr[k].toDouble())
                features[0][0] = ln(magL.toDouble() + 1.0).toFloat()
                features[0][1] = ln(magR.toDouble() + 1.0).toFloat()
                features[0][2] = cos(wrapAngle(phaseR - phaseL)).toFloat()
                tf.run(features, output)
                val mask = output[0][0].coerceIn(0f, 1f)

                val magC = min(magL, magR) * mask
                val phaseC = phaseL + 0.5 * wrapAngle(phaseR - phaseL)
                vocRe[k] = magC * cos(phaseC).toFloat()
                vocIm[k] = magC * sin(phaseC).toFloat()
                inLRe[k] = lr[k] - vocRe[k]
                inLIm[k] = li[k] - vocIm[k]
                inRRe[k] = rr[k] - vocRe[k]
                inRIm[k] = ri[k] - vocIm[k]
            }

            fft.inverse(vocRe, vocIm)
            fft.inverse(inLRe, inLIm)
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

        for (i in 0 until totalFrames) {
            val w = weightSum[i].coerceAtLeast(1e-6f)
            vocals[i * 2] /= w
            vocals[i * 2 + 1] /= w
            instrumental[i * 2] /= w
            instrumental[i * 2 + 1] /= w
        }
        return SpectralVocalRemover.Result(vocals, instrumental)
    }

    private fun loadModelFile(file: File): MappedByteBuffer {
        file.inputStream().use { input ->
            val channel = input.channel
            return channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size())
        }
    }

    private fun hannWindow(index: Int, size: Int): Float =
        (0.5 - 0.5 * cos(2.0 * PI * index / (size - 1))).toFloat()

    private fun wrapAngle(angle: Double): Double {
        var a = angle
        while (a > PI) a -= 2.0 * PI
        while (a < -PI) a += 2.0 * PI
        return a
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }

    companion object {
        private const val TAG = "TfliteVocalSeparator"

        /** Creates the separator wired to the bundled `separator_v1.tflite`. */
        fun load(context: Context): TfliteVocalSeparator =
            TfliteVocalSeparator(AiModelManager(context).bundledModelFile("separator_v1.tflite"))
    }
}
