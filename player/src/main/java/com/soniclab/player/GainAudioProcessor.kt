package com.soniclab.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.pow

/**
 * Per-track gain in dB (used by auto-normalization). PCM16 in/out.
 * 0 dB = passthrough. Runs on the audio thread.
 */
class GainAudioProcessor : AudioProcessor {

    @Volatile
    var gainDb: Float = 0f

    private var outputBuffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var ended = false

    override fun configure(inputFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputFormat)
        }
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        ended = false
        return inputFormat
    }

    override fun isActive(): Boolean = gainDb != 0f

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (gainDb == 0f) {
            outputBuffer = inputBuffer
            return
        }
        val gain = 10f.pow(gainDb / 20f)
        val out = ByteBuffer.allocate(inputBuffer.remaining()).order(ByteOrder.LITTLE_ENDIAN)
        while (inputBuffer.hasRemaining()) {
            val v = inputBuffer.short.toFloat() / 32768f * gain
            out.putShort((v * 32767f).coerceIn(-32768f, 32767f).toInt().toShort())
        }
        out.flip()
        outputBuffer = out
    }

    override fun queueEndOfStream() {
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
    }

    override fun reset() {
        flush()
    }

    override fun getDurationAfterProcessorApplied(durationUs: Long): Long = durationUs
}
