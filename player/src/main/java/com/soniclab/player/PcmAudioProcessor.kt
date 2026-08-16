package com.soniclab.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder

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
            for (v in values) bytes.putShort((v.coerceIn(-1f, 1f) * 32767f).toInt().toShort())
        }
        bytes.flip()
        return bytes
    }
}
