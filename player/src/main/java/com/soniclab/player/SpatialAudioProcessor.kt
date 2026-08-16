package com.soniclab.player

import androidx.media3.common.audio.AudioProcessor
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Real-time 3D / 8D spatial audio on the playback path.
 *
 * - 3D: mid/side stereo widening — pushes the image "out of the head".
 * - 8D: slow LFO-rotated pan with a feedback echo, the classic 8D effect.
 *
 * Works on PCM16 and PCM float, stereo and mono (mono is up-mixed to stereo
 * so 3D/8D no longer silently disables on mono tracks / podcasts). Runs on
 * the audio thread, so all buffering lives here and only UI-facing values
 * are volatile.
 */
class SpatialAudioProcessor : PcmAudioProcessor() {

    /** 0 = off, 1 = 3D, 2 = 8D. Written from the UI thread. */
    @Volatile
    var mode: Int = 0

    /** 3D widening strength, 0..1. */
    @Volatile
    var widthStrength: Float = DEFAULT_WIDTH_STRENGTH

    /** 8D seconds per full rotation, 4..60. */
    @Volatile
    var rotationSeconds: Float = DEFAULT_ROTATION_SECONDS

    // 8D state, touched only on the audio thread.
    private var phase = 0.0
    private var delayBufferL = FloatArray(0)
    private var delayBufferR = FloatArray(0)
    private var delayWritePos = 0
    private var delayReadOffset = 0

    override fun outputFormat(input: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        // Up-mix mono to stereo so spatial modes work on every track; the
        // format stays stable while the effect toggles mid-stream.
        return if (input.channelCount == 1) {
            AudioProcessor.AudioFormat(input.sampleRate, 2, input.encoding)
        } else {
            input
        }
    }

    override fun isEffectActive(): Boolean = mode != 0 || inputChannels == 1

    override fun processSamples(input: FloatArray, frames: Int): FloatArray {
        val out = if (inputChannels == 1 && outputChannels == 2) FloatArray(frames * 2) else input
        when (mode) {
            MODE_3D -> process3D(input, out, frames)
            MODE_8D -> process8D(input, out, frames)
            else -> if (out !== input) duplicateMono(input, out)
        }
        return out
    }

    override fun onFormatChanged() {
        resetDsp()
    }

    override fun onFlush() {
        resetDsp()
    }

    private fun process3D(input: FloatArray, out: FloatArray, frames: Int) {
        val width = 1f + widthStrength.coerceIn(0f, 1f) * 1.8f
        if (inputChannels == 1) {
            // No side information in mono; keep the duplicated signal intact.
            duplicateMono(input, out)
            return
        }
        var i = 0
        var o = 0
        repeat(frames) {
            val l = input[i++]
            val r = input[i++]
            val mid = (l + r) * 0.5f
            val side = (l - r) * 0.5f
            out[o++] = mid + side * width
            out[o++] = mid - side * width
        }
    }

    private fun process8D(input: FloatArray, out: FloatArray, frames: Int) {
        val rate = rotationSeconds.coerceIn(4f, 60f)
        val phaseStep = (2.0 * PI) / (rate * sampleRateHz)
        if (inputChannels == 1) {
            var i = 0
            var o = 0
            repeat(frames) {
                val v = input[i++]
                val pan = sin(phase).toFloat()
                phase += phaseStep
                val theta = (pan + 1f) * (PI * 0.25f).toFloat()
                val gainL = cos(theta)
                val gainR = sin(theta)
                out[o++] = v * gainL
                out[o++] = v * gainR
            }
            return
        }
        var i = 0
        var o = 0
        repeat(frames) {
            val l = input[i++]
            val r = input[i++]

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
            delayBufferL[delayWritePos] = outL
            delayBufferR[delayWritePos] = outR
            delayWritePos = (delayWritePos + 1) % delayBufferL.size

            out[o++] = outL
            out[o++] = outR
        }
    }

    private fun duplicateMono(input: FloatArray, out: FloatArray) {
        var i = 0
        var o = 0
        while (i < input.size) {
            val v = input[i++]
            out[o++] = v
            out[o++] = v
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
