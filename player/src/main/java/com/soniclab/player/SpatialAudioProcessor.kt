package com.soniclab.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Real-time 3D / 8D spatial audio on the playback path (PCM16 stereo).
 *
 * - 3D: mid/side stereo widening — pushes the image "out of the head".
 * - 8D: slow LFO-rotated pan with a feedback echo, the classic 8D effect.
 *
 * [mode]: 0 = off, 1 = 3D, 2 = 8D. Mono is always passthrough. Runs on the
 * audio thread, so all buffering lives here and only UI-facing values are
 * volatile.
 */
class SpatialAudioProcessor : AudioProcessor {

    /** 0 = off, 1 = 3D, 2 = 8D. Written from the UI thread. */
    @Volatile
    var mode: Int = 0

    /** 3D widening strength, 0..1. */
    @Volatile
    var widthStrength: Float = DEFAULT_WIDTH_STRENGTH

    /** 8D seconds per full rotation, 4..60. */
    @Volatile
    var rotationSeconds: Float = DEFAULT_ROTATION_SECONDS

    private var channelCount = 0
    private var sampleRateHz = 44100
    private var outputBuffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var ended = false

    // 8D state, touched only on the audio thread.
    private var phase = 0.0
    private var delayBufferL = FloatArray(0)
    private var delayBufferR = FloatArray(0)
    private var delayWritePos = 0
    private var delayReadOffset = 0

    override fun configure(inputFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputFormat)
        }
        channelCount = inputFormat.channelCount
        sampleRateHz = inputFormat.sampleRate
        resetDsp()
        return inputFormat
    }

    override fun isActive(): Boolean = mode != 0 && channelCount == 2

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!isActive()) {
            outputBuffer = inputBuffer
            return
        }
        val frames = inputBuffer.remaining() / 2 / channelCount
        val out = ByteBuffer.allocate(frames * 2 * channelCount).order(ByteOrder.LITTLE_ENDIAN)
        if (mode == MODE_3D) {
            process3D(inputBuffer, out, frames)
        } else {
            process8D(inputBuffer, out, frames)
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
        resetDsp()
    }

    override fun reset() {
        flush()
        channelCount = 0
    }

    override fun getDurationAfterProcessorApplied(durationUs: Long): Long = durationUs

    private fun process3D(input: ByteBuffer, out: ByteBuffer, frames: Int) {
        val width = 1f + widthStrength.coerceIn(0f, 1f) * 1.8f
        repeat(frames) {
            val l = input.short.toFloat() / 32768f
            val r = input.short.toFloat() / 32768f
            val mid = (l + r) * 0.5f
            val side = (l - r) * 0.5f
            out.putShort(clip16(mid + side * width))
            out.putShort(clip16(mid - side * width))
        }
    }

    private fun process8D(input: ByteBuffer, out: ByteBuffer, frames: Int) {
        val rate = rotationSeconds.coerceIn(4f, 60f)
        val phaseStep = (2.0 * PI) / (rate * sampleRateHz)
        repeat(frames) {
            val l = input.short.toFloat() / 32768f
            val r = input.short.toFloat() / 32768f

            // Equal-power L/R rotation.
            val pan = sin(phase).toFloat()
            phase += phaseStep
            val theta = (pan + 1f) * (PI * 0.25f).toFloat()
            val gainL = cos(theta)
            val gainR = sin(theta)

            // Feedback echo (room feel) per channel.
            val readPos = (delayWritePos - delayReadOffset + delayBufferL.size) % delayBufferL.size
            val echoL = delayBufferL[readPos]
            val echoR = delayBufferR[readPos]
            val dryL = l * gainL
            val dryR = r * gainR
            val outL = dryL + echoL * ECHO_FEEDBACK
            val outR = dryR + echoR * ECHO_FEEDBACK
            delayBufferL[delayWritePos] = dryL + echoL * ECHO_FEEDBACK
            delayBufferR[delayWritePos] = dryR + echoR * ECHO_FEEDBACK
            delayWritePos = (delayWritePos + 1) % delayBufferL.size

            out.putShort(clip16(outL))
            out.putShort(clip16(outR))
        }
    }

    private fun resetDsp() {
        phase = 0.0
        delayWritePos = 0
        val delaySamples = (sampleRateHz * ECHO_DELAY_SECONDS).toInt().coerceAtLeast(1)
        if (delayBufferL.size != delaySamples) {
            delayBufferL = FloatArray(delaySamples)
            delayBufferR = FloatArray(delaySamples)
        } else {
            delayBufferL.fill(0f)
            delayBufferR.fill(0f)
        }
        delayReadOffset = delaySamples
    }

    private fun clip16(value: Float): Short =
        (value.coerceIn(-1f, 1f) * 32767f).toInt().toShort()

    companion object {
        const val MODE_OFF = 0
        const val MODE_3D = 1
        const val MODE_8D = 2
        const val DEFAULT_WIDTH_STRENGTH = 0.6f
        const val DEFAULT_ROTATION_SECONDS = 8f
        private const val ECHO_DELAY_SECONDS = 0.38f
        private const val ECHO_FEEDBACK = 0.3f
    }
}
