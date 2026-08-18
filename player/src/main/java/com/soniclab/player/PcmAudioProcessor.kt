package com.soniclab.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * Shared base for the real-time playback chain processors.
 *
 * - Accepts PCM16 and PCM float, so every effect keeps working on hi-res /
 *   FLAC tracks (decoders emit float when float output is enabled).
 * - Always reports [isActive] true: Media3 only re-evaluates that flag on a
 *   flush (seek / track change / stop), which is why effect toggles used to
 *   "sometimes not work" mid-track. With an always-active processor the
 *   subclass just passes audio through while its effect is off, and toggling
 *   takes effect on the very next buffer.
 *
 * Subclasses process interleaved float frames and return an array of
 * `frames * outputChannels` values (upmixing is supported by overriding
 * [outputFormat]). Runs on the audio thread; all state lives here and only
 * UI-facing settings are @Volatile fields.
 */
abstract class PcmAudioProcessor : AudioProcessor {

    protected var sampleRateHz = 44100
    protected var inputChannels = 0
    protected var outputChannels = 0
    protected var encoding = C.ENCODING_PCM_16BIT

    private var outputBuffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var ended = false

    /** Per-channel [e1, e2] error history of the 2nd-order noise shaper. */
    private var noiseShapeState = FloatArray(0)
    private var ditherSeed = 0x9E3779B9

    /** Format this processor emits for [input]; override to change channels. */
    protected open fun outputFormat(input: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat = input

    /** True when this buffer actually needs processing; false = exact passthrough. */
    protected abstract fun isEffectActive(): Boolean

    /** Process interleaved float samples; must return `frames * outputChannels` values. */
    protected abstract fun processSamples(input: FloatArray, frames: Int): FloatArray

    protected open fun onFormatChanged() {}
    protected open fun onFlush() {}
    protected open fun onEndOfStream() {}

    override fun configure(inputFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputFormat.encoding != C.ENCODING_PCM_16BIT &&
            inputFormat.encoding != C.ENCODING_PCM_FLOAT
        ) {
            throw AudioProcessor.UnhandledAudioFormatException(inputFormat)
        }
        encoding = inputFormat.encoding
        inputChannels = inputFormat.channelCount
        sampleRateHz = inputFormat.sampleRate
        val output = outputFormat(inputFormat)
        outputChannels = output.channelCount
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        ended = false
        noiseShapeState = FloatArray(outputChannels.coerceAtLeast(1) * 2)
        onFormatChanged()
        return output
    }

    override fun isActive(): Boolean = true

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!isEffectActive()) {
            outputBuffer = inputBuffer
            return
        }
        val frames = inputBuffer.remaining() / bytesPerSample / inputChannels
        val input = decode(inputBuffer, frames * inputChannels)
        outputBuffer = encode(processSamples(input, frames))
    }

    override fun queueEndOfStream() {
        onEndOfStream()
        ended = true
    }

    override fun getOutput(): ByteBuffer {
        val out = outputBuffer
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        return out
    }

    override fun isEnded(): Boolean = ended

    override fun flush() {
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        ended = false
        noiseShapeState.fill(0f)
        onFlush()
    }

    override fun reset() {
        flush()
        inputChannels = 0
        outputChannels = 0
        encoding = C.ENCODING_PCM_16BIT
    }

    override fun getDurationAfterProcessorApplied(durationUs: Long): Long = durationUs

    private val bytesPerSample: Int
        get() = if (encoding == C.ENCODING_PCM_FLOAT) 4 else 2

    private fun decode(buffer: ByteBuffer, count: Int): FloatArray {
        val out = FloatArray(count)
        if (encoding == C.ENCODING_PCM_FLOAT) {
            for (i in 0 until count) out[i] = buffer.float
        } else {
            for (i in 0 until count) out[i] = buffer.short.toFloat() / 32768f
        }
        return out
    }

    /** Enqueues processor output (e.g. leftover frames drained at EOS). */
    protected fun appendOutput(values: FloatArray) {
        val newData = encode(values)
        val existing = if (outputBuffer === AudioProcessor.EMPTY_BUFFER) 0 else outputBuffer.remaining()
        if (existing == 0) {
            outputBuffer = newData
            return
        }
        val merged = ByteBuffer.allocate(existing + newData.remaining())
            .order(ByteOrder.nativeOrder())
        merged.put(outputBuffer)
        merged.put(newData)
        merged.flip()
        outputBuffer = merged
    }

    private fun encode(values: FloatArray): ByteBuffer {
        val bytes = ByteBuffer.allocate(values.size * bytesPerSample)
            .order(ByteOrder.nativeOrder())
        if (encoding == C.ENCODING_PCM_FLOAT) {
            for (v in values) bytes.putFloat(v.coerceIn(-1f, 1f))
        } else {
            encode16(values, bytes)
        }
        bytes.flip()
        return bytes
    }

    /**
     * PCM16 conversion. When [DitherBridge.enabled] (default) it applies TPDF
     * dither + 2nd-order (1 - z^-1)^2 noise shaping to remove the quantization
     * distortion a naive truncation would add on top of the DSP; when disabled
     * it does plain rounding. This path only runs when an effect is actually
     * active (the passthrough path is bit-exact).
     */
    private fun encode16(values: FloatArray, bytes: ByteBuffer) {
        if (values.isEmpty()) return
        val ch = outputChannels.coerceAtLeast(1)
        if (noiseShapeState.size < ch * 2) noiseShapeState = FloatArray(ch * 2)
        val lsb = 1f / 32768f
        val dithering = DitherBridge.enabled
        var i = 0
        while (i < values.size) {
            val v = values[i].coerceIn(-1f, 1f)
            if (!dithering) {
                bytes.putShort((v * 32768f).roundToInt().coerceIn(-32768, 32767).toShort())
                i++
                continue
            }
            val base = (i % ch) * 2
            val e1 = noiseShapeState[base]
            val e2 = noiseShapeState[base + 1]
            val shaped = v + (nextRandom() - nextRandom()) * lsb
            val y = ((shaped - 2f * e1 + e2) * 32768f).roundToInt().coerceIn(-32768, 32767)
            val err = y / 32768f - shaped
            noiseShapeState[base] = err
            noiseShapeState[base + 1] = e1
            bytes.putShort(y.toShort())
            i++
        }
    }

    /** Cheap xorshift32 PRNG for the TPDF dither (no allocation, audio-thread safe). */
    private fun nextRandom(): Float {
        var x = ditherSeed
        x = x xor (x shl 13)
        x = x xor (x ushr 17)
        x = x xor (x shl 5)
        ditherSeed = x
        return (x ushr 8 and 0xFFFF).toFloat() / 65536f
    }
}
