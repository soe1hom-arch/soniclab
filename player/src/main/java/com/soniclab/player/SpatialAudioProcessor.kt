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
 * - 8D+Tengah: plain audio (no widening) rotated by 8D while a center anchor
 *   keeps the middle of the mix audible on headphones — sounds full, not split.
 * - Surround: 3D widening with a gentle room echo, no rotation.
 *
 * The "pan depth" control limits how far the 8D pan travels, so one channel
 * never goes fully silent on headphones.
 *
 * Works on PCM16 and PCM float, stereo and mono (mono is up-mixed to stereo
 * so spatial modes no longer silently disable on mono tracks / podcasts).
 * Runs on the audio thread, so all buffering lives here and only UI-facing
 * values are volatile.
 */
class SpatialAudioProcessor : PcmAudioProcessor() {

    /** Preset selector for the UI chips; becomes [MODE_CUSTOM] when the user mixes manually. */
    var mode: Int = MODE_OFF
        set(value) {
            field = value
            // Preset chips configure the switches; MODE_CUSTOM keeps whatever
            // combination the user picked manually.
            when (value) {
                MODE_OFF -> {
                    spatial3d = false
                    spatial8d = false
                    surround = false
                }
                MODE_3D -> {
                    spatial3d = true
                    spatial8d = false
                    surround = false
                }
                MODE_8D -> {
                    spatial3d = false
                    spatial8d = true
                    surround = false
                }
                MODE_3D_8D -> {
                    spatial3d = true
                    spatial8d = true
                    surround = false
                }
                MODE_SURROUND -> {
                    spatial3d = true
                    spatial8d = false
                    surround = true
                }
            }
        }

    /** 3D widening on/off (user can mix this freely with 8D/Surround). */
    @Volatile
    var spatial3d: Boolean = false

    /** 8D rotation on/off. */
    @Volatile
    var spatial8d: Boolean = false

    /** Surround room-echo on/off (usually combined with 3D). */
    @Volatile
    var surround: Boolean = false

    /** 3D widening strength, 0..1. */
    @Volatile
    var widthStrength: Float = DEFAULT_WIDTH_STRENGTH

    /** 8D seconds per full rotation, 4..60. */
    @Volatile
    var rotationSeconds: Float = DEFAULT_ROTATION_SECONDS

    /** 8D pan travel, 0..1 — lower keeps the center audible on headphones. */
    @Volatile
    var panDepth: Float = DEFAULT_PAN_DEPTH

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

    override fun isEffectActive(): Boolean = spatial3d || spatial8d || surround || inputChannels == 1

    override fun processSamples(input: FloatArray, frames: Int): FloatArray {
        val out = if (inputChannels == 1 && outputChannels == 2) FloatArray(frames * 2) else input
        when {
            spatial3d && spatial8d -> process3D8D(input, out, frames)
            spatial8d -> process8D(input, out, frames)
            spatial3d && surround -> processSurround(input, out, frames, widen = true)
            surround -> processSurround(input, out, frames, widen = false)
            spatial3d -> process3D(input, out, frames)
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
        val width = 1f + widthStrength.coerceIn(0f, 1f) * WIDTH_MAX
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
            // Presence guard: mid (vokal/center) ikut dinaikkan sebagian saat
            // side dilebarkan, jadi suara tengah tidak tenggelam/berlubang.
            val midGain = 1f + (width - 1f) * PRESENCE_GUARD
            out[o++] = mid * midGain + side * width
            out[o++] = mid * midGain - side * width
        }
    }

    private fun process8D(input: FloatArray, out: FloatArray, frames: Int) {
        val phaseStep = phaseStep()
        if (inputChannels == 1) {
            var i = 0
            var o = 0
            repeat(frames) {
                val v = input[i++]
                val (gainL, gainR) = panGains()
                phase += phaseStep
                val echoL = delayBufferL[(delayWritePos - delayReadOffset + delayBufferL.size) % delayBufferL.size]
                val echoR = delayBufferR[(delayWritePos - delayReadOffset + delayBufferR.size) % delayBufferR.size]
                val outL = v * gainL + echoL * ECHO_FEEDBACK
                val outR = v * gainR + echoR * ECHO_FEEDBACK
                delayBufferL[delayWritePos] = outL
                delayBufferR[delayWritePos] = outR
                delayWritePos = (delayWritePos + 1) % delayBufferL.size
                out[o++] = outL
                out[o++] = outR
            }
            return
        }
        var i = 0
        var o = 0
        repeat(frames) {
            val l = input[i++]
            val r = input[i++]
            val (gainL, gainR) = panGains()
            phase += phaseStep
            val readPos = (delayWritePos - delayReadOffset + delayBufferL.size) % delayBufferL.size
            val echoL = delayBufferL[readPos]
            val echoR = delayBufferR[readPos]
            val outL = l * gainL + echoL * ECHO_FEEDBACK
            val outR = r * gainR + echoR * ECHO_FEEDBACK
            delayBufferL[delayWritePos] = outL
            delayBufferR[delayWritePos] = outR
            delayWritePos = (delayWritePos + 1) % delayBufferL.size
            out[o++] = outL
            out[o++] = outR
        }
    }

    /**
     * 8D rotation over the plain signal with a center anchor — the rotation
     * pans left/right but the middle of the mix stays audible and the sound
     * stays full (no mid/side widening that can sound split on headphones).
     */
    private fun process3D8D(input: FloatArray, out: FloatArray, frames: Int) {
        val phaseStep = phaseStep()
        if (inputChannels == 1) {
            var i = 0
            var o = 0
            repeat(frames) {
                val v = input[i++]
                val (gainL, gainR) = panGains()
                phase += phaseStep
                val anchor = v * CENTER_ANCHOR
                val readPos = (delayWritePos - delayReadOffset + delayBufferL.size) % delayBufferL.size
                val echoL = delayBufferL[readPos]
                val echoR = delayBufferR[readPos]
                val outL = v * gainL + anchor + echoL * ECHO_FEEDBACK
                val outR = v * gainR + anchor + echoR * ECHO_FEEDBACK
                delayBufferL[delayWritePos] = outL
                delayBufferR[delayWritePos] = outR
                delayWritePos = (delayWritePos + 1) % delayBufferL.size
                out[o++] = outL
                out[o++] = outR
            }
            return
        }
        var i = 0
        var o = 0
        repeat(frames) {
            val l = input[i++]
            val r = input[i++]
            val mid = (l + r) * 0.5f
            val (gainL, gainR) = panGains()
            phase += phaseStep
            val anchor = mid * CENTER_ANCHOR
            val readPos = (delayWritePos - delayReadOffset + delayBufferL.size) % delayBufferL.size
            val echoL = delayBufferL[readPos]
            val echoR = delayBufferR[readPos]
            val outL = l * gainL + anchor + echoL * ECHO_FEEDBACK
            val outR = r * gainR + anchor + echoR * ECHO_FEEDBACK
            delayBufferL[delayWritePos] = outL
            delayBufferR[delayWritePos] = outR
            delayWritePos = (delayWritePos + 1) % delayBufferL.size
            out[o++] = outL
            out[o++] = outR
        }
    }

    /** 3D widening plus a gentle room echo, no rotation. */
    private fun processSurround(input: FloatArray, out: FloatArray, frames: Int, widen: Boolean) {
        val width = 1f + widthStrength.coerceIn(0f, 1f) * WIDTH_MAX
        val midGain = 1f + (width - 1f) * PRESENCE_GUARD
        if (inputChannels == 1) {
            var i = 0
            var o = 0
            repeat(frames) {
                val v = input[i++]
                val readPos = (delayWritePos - delayReadOffset + delayBufferL.size) % delayBufferL.size
                val echoL = delayBufferL[readPos]
                val echoR = delayBufferR[readPos]
                val outL = v + echoL * ECHO_FEEDBACK
                val outR = v + echoR * ECHO_FEEDBACK
                delayBufferL[delayWritePos] = outL
                delayBufferR[delayWritePos] = outR
                delayWritePos = (delayWritePos + 1) % delayBufferL.size
                out[o++] = outL
                out[o++] = outR
            }
            return
        }
        var i = 0
        var o = 0
        repeat(frames) {
            val l = input[i++]
            val r = input[i++]
            val mid = (l + r) * 0.5f
            val side = (l - r) * 0.5f
            val widL = if (widen) mid * midGain + side * width else l
            val widR = if (widen) mid * midGain - side * width else r
            val readPos = (delayWritePos - delayReadOffset + delayBufferL.size) % delayBufferL.size
            val echoL = delayBufferL[readPos]
            val echoR = delayBufferR[readPos]
            val outL = widL + echoL * ECHO_FEEDBACK
            val outR = widR + echoR * ECHO_FEEDBACK
            delayBufferL[delayWritePos] = outL
            delayBufferR[delayWritePos] = outR
            delayWritePos = (delayWritePos + 1) % delayBufferL.size
            out[o++] = outL
            out[o++] = outR
        }
    }

    private fun panGains(): Pair<Float, Float> {
        // Equal-power pan limited by panDepth so the far channel keeps its
        // center anchor instead of going fully silent (headset-friendly).
        val depth = panDepth.coerceIn(0.1f, 1f)
        val pan = sin(phase).toFloat()
        val theta = (pan * depth + 1f) * (PI * 0.25f).toFloat()
        return cos(theta) to sin(theta)
    }

    private fun phaseStep(): Double = (2.0 * PI) / (rotationSeconds.coerceIn(4f, 60f) * sampleRateHz)

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
        const val MODE_3D_8D = 3
        const val MODE_SURROUND = 4
        const val MODE_CUSTOM = 5
        const val DEFAULT_WIDTH_STRENGTH = 0.6f
        const val DEFAULT_ROTATION_SECONDS = 8f
        const val DEFAULT_PAN_DEPTH = 0.6f
        private const val CENTER_ANCHOR = 0.45f
        private const val WIDTH_MAX = 1.8f
        private const val PRESENCE_GUARD = 0.35f
        private const val ECHO_DELAY_SECONDS = 0.38f
        private const val ECHO_FEEDBACK = 0.3f
    }
}
