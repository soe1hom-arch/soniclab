package com.soniclab.visualizer

import android.media.audiofx.Visualizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.hypot
import kotlin.math.log10

/**
 * Captures FFT data from the player's audio session and exposes normalized
 * spectrum buckets to the UI. Falls back silently when the Visualizer API is
 * unavailable (e.g. permission denied on some devices).
 */
class VisualizerEngine(private val buckets: Int = 48) {

    private var visualizer: Visualizer? = null
    private val smoothing = FloatArray(buckets)

    private val _spectrum = MutableStateFlow(FloatArray(buckets))
    val spectrum: StateFlow<FloatArray> = _spectrum.asStateFlow()

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    fun attachTo(sessionId: Int) {
        release()
        if (sessionId == 0) return
        try {
            val vis = Visualizer(sessionId)
            val captureSize = 1024
            vis.captureSize = captureSize
            vis.setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(visualizer: Visualizer?, waveform: ByteArray?, samplingRate: Int) {
                        // Not used by the spectrum screen.
                    }

                    override fun onFftDataCapture(visualizer: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                        if (fft == null) return
                        _spectrum.value = fftToBuckets(fft, captureSize)
                    }
                },
                Visualizer.getMaxCaptureRate() / 2,
                false,
                true
            )
            vis.enabled = true
            _isActive.value = true
        } catch (e: Exception) {
            release()
        }
    }

    fun release() {
        runCatching { visualizer?.enabled = false }
        runCatching { visualizer?.release() }
        visualizer = null
        _isActive.value = false
    }

    private fun fftToBuckets(fftBytes: ByteArray, captureSize: Int): FloatArray {
        // Visualizer packs interleaved 8-bit real/imag; DC is at index 0 real.
        val bins = captureSize / 2
        val result = FloatArray(buckets)
        var peak = 0f
        val magnitudes = FloatArray(bins)
        for (i in 0 until bins) {
            val re = (fftBytes[i * 2].toInt() and 0xFF) - 128
            val im = (fftBytes[i * 2 + 1].toInt() and 0xFF) - 128
            magnitudes[i] = hypot(re.toDouble(), im.toDouble()).toFloat()
            if (magnitudes[i] > peak) peak = magnitudes[i]
        }
        if (peak <= 0f) peak = 1f

        // Logarithmic grouping into display buckets.
        val logBins = (log10(bins.toDouble()) / log10(2.0)).toInt()
        for (b in 0 until buckets) {
            val expStart = b.toDouble() / buckets * logBins
            val expEnd = (b + 1).toDouble() / buckets * logBins
            val low = (Math.pow(2.0, expStart).toInt()).coerceIn(0, bins - 1)
            val high = (Math.pow(2.0, expEnd).toInt()).coerceIn(low + 1, bins)
            var sum = 0f
            for (i in low until high) sum += magnitudes[i]
            val target = ((sum / (high - low)) / peak).coerceIn(0f, 1f)
            result[b] = smoothing[b] + (target - smoothing[b]) * 0.55f
            smoothing[b] = result[b]
        }
        return result
    }
}
