package com.soniclab.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min

/**
 * Stereo balance: [-1..1], -1 = full left, 0 = center, +1 = full right.
 * PCM16 in/out, passthrough for mono. Runs on the audio thread.
 */
class BalanceAudioProcessor : AudioProcessor {

    @Volatile
    var balance: Float = 0f

    private var channelCount = 0
    private var outputBuffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var ended = false

    override fun configure(inputFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputFormat)
        }
        channelCount = inputFormat.channelCount
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        ended = false
        return inputFormat
    }

    override fun isActive(): Boolean = balance != 0f && channelCount >= 2

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (channelCount < 2) {
            outputBuffer = inputBuffer
            return
        }
        val gainL = min(1f, 1f - balance).coerceIn(0f, 1f)
        val gainR = min(1f, 1f + balance).coerceIn(0f, 1f)
        val out = ByteBuffer.allocate(inputBuffer.remaining()).order(ByteOrder.LITTLE_ENDIAN)
        var channel = 0
        while (inputBuffer.hasRemaining()) {
            val v = inputBuffer.short.toFloat() / 32768f
            val g = if (channel % 2 == 0) gainL else gainR
            out.putShort((v * g * 32767f).coerceIn(-32768f, 32767f).toInt().toShort())
            channel++
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
        channelCount = 0
    }

    override fun getDurationAfterProcessorApplied(durationUs: Long): Long = durationUs
}
