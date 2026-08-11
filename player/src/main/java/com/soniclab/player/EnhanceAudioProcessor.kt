package com.soniclab.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.soniclab.ai.AiEnhancer
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Real-time Media3 [AudioProcessor] that runs the on-device AI Enhancer on
 * the playback path. PCM16 in/out, same format through the chain. When
 * disabled (or no enhancer is attached) [isActive] returns false and the
 * audio sink bypasses the processor entirely.
 *
 * Audio is accumulated in per-channel frames of [frameChunk] samples; each
 * channel is enhanced independently (the bundled TFLite model is mono) and
 * re-interleaved. Runs on the audio thread, so the enhancer must be cheap.
 */
class EnhanceAudioProcessor : AudioProcessor {

    @Volatile
    var enhancer: AiEnhancer? = null

    @Volatile
    var enabled: Boolean = false

    private var channelCount = 0
    private val pending = ArrayList<Float>()
    private var outputBuffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var ended = false

    override fun configure(inputFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputFormat)
        }
        channelCount = inputFormat.channelCount
        pending.clear()
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        ended = false
        return inputFormat
    }

    override fun isActive(): Boolean = enabled && enhancer != null

    override fun queueInput(inputBuffer: ByteBuffer) {
        while (inputBuffer.hasRemaining()) {
            pending.add(inputBuffer.short.toFloat() / 32768f)
            if (pending.size >= frameChunk * channelCount) {
                processFrames(frameChunk)
            }
        }
    }

    override fun queueEndOfStream() {
        val remainingFrames = pending.size / channelCount
        if (remainingFrames > 0) {
            processFrames(remainingFrames)
        }
        ended = true
    }

    override fun getOutput(): ByteBuffer {
        val out = outputBuffer
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        return out
    }

    override fun isEnded(): Boolean = ended

    override fun flush() {
        pending.clear()
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        ended = false
    }

    override fun reset() {
        flush()
        channelCount = 0
    }

    override fun getDurationAfterProcessorApplied(durationUs: Long): Long = durationUs

    private fun processFrames(frameCount: Int) {
        val e = enhancer ?: return
        val chunkSize = frameCount * channelCount
        val chunk = FloatArray(chunkSize)
        for (i in 0 until chunkSize) chunk[i] = pending[i]
        pending.subList(0, chunkSize).clear()

        val perChannel = Array(channelCount) { c ->
            FloatArray(frameCount) { chunk[it * channelCount + c] }
        }
        val enhanced = Array(channelCount) { c -> e.enhance(perChannel[c]) }

        val outBytes = ByteBuffer.allocate(chunkSize * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until frameCount) {
            for (c in 0 until channelCount) {
                outBytes.putShort((enhanced[c][i].coerceIn(-1f, 1f) * 32767f).toInt().toShort())
            }
        }
        outBytes.flip()
        appendOutput(outBytes)
    }

    private fun appendOutput(newData: ByteBuffer) {
        val existing = if (outputBuffer === AudioProcessor.EMPTY_BUFFER) 0 else outputBuffer.remaining()
        val merged = ByteBuffer.allocate(existing + newData.remaining()).order(ByteOrder.LITTLE_ENDIAN)
        if (existing > 0) merged.put(outputBuffer)
        merged.put(newData)
        merged.flip()
        outputBuffer = merged
    }

    private companion object {
        const val frameChunk = 512
    }
}
