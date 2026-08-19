/*
 * Copyright 2026 soe1hom-arch
 * SPDX-License-Identifier: Apache-2.0
 */

package com.soniclab.player

import android.media.AudioDeviceInfo
import androidx.annotation.RequiresApi
import androidx.media3.common.AudioAttributes
import androidx.media3.common.AuxEffectInfo
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.Clock
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Custom [AudioSink] that keeps the whole DSP chain in 32-bit float and still
 * reaches hi-res AudioTrack output.
 *
 * media3's [DefaultAudioSink] only inserts custom [AudioProcessor]s on its
 * 16-bit path; with float output enabled it drops them entirely (so effects
 * silently stopped on hi-res PCM). This sink wraps a float-capable
 * [DefaultAudioSink] used as the AudioTrack writer / position tracker, and
 * runs the chain itself: every renderer buffer is normalized to float,
 * processed by [DspFramePipeline], and re-fed to the delegate as float PCM.
 *
 * Encoder delay/padding (gapless trimming) is handled here since media3 only
 * trims on its 16-bit path.
 *
 * Timing contract (mirrors [DefaultAudioSink]): the delegate's internal
 * position timeline advances per *first-seen* buffer (its submitted-frame
 * count), not per consumed byte, and it forces a re-sync (with an
 * `onPositionDiscontinuity`) when the PTS we pass disagrees by more than
 * 200ms. We therefore stamp every chunk handed to the delegate with
 * `firstPtsUs + submittedFrames * frameDuration`, computed up front when the
 * chunk is created, and never re-stamp a partially written chunk. The renderer
 * input is consumed in bounded slices so that when the AudioTrack applies
 * backpressure the sink stops feeding (returns `false`, retaining the buffer)
 * instead of building a multi-second internal queue that would desync the
 * position.
 */
class DspAudioSink(
    private val delegate: AudioSink,
    private val processors: List<AudioProcessor>
) : AudioSink {

    private val pipeline = DspFramePipeline(processors)

    private var rawMode = false
    private var inputEncoding = C.ENCODING_PCM_16BIT
    private var sampleRate = 0
    private var outFrameSize = 0

    private var inputChannels = 0
    private var inputFrameSize = 0

    private var pendingInput: ByteBuffer? = null
    private var pendingOut: ByteBuffer? = null
    private var outputBytes = ByteArray(0)
    private var outputStart = 0
    private var outputEnd = 0
    private var pendingOutPtsUs = C.TIME_UNSET
    private var firstPtsUs = C.TIME_UNSET
    private var submittedFrames = 0L
    private var eosQueued = false

    private var delayFramesRemaining = 0
    private var paddingFrames = 0

    override fun setListener(listener: AudioSink.Listener) {
        delegate.setListener(listener)
    }

    override fun setPlayerId(playerId: PlayerId?) {
        delegate.setPlayerId(playerId)
    }

    override fun setClock(clock: Clock) {
        delegate.setClock(clock)
    }

    override fun supportsFormat(format: Format): Boolean =
        supportsEncoding(format) && delegate.supportsFormat(format)

    override fun getFormatSupport(format: Format): Int =
        if (supportsEncoding(format)) delegate.getFormatSupport(format) else AudioSink.SINK_FORMAT_UNSUPPORTED

    override fun getCurrentPositionUs(sourceEnded: Boolean): Long = delegate.getCurrentPositionUs(sourceEnded)

    override fun configure(inputFormat: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {
        if (MimeTypes.AUDIO_RAW != inputFormat.sampleMimeType) {
            // Passthrough/offload (not used by SonicLab today): hand over untouched.
            rawMode = false
            resetPending()
            delegate.configure(inputFormat, specifiedBufferSize, outputChannels)
            return
        }
        if (!isSupportedEncoding(inputFormat.pcmEncoding)) {
            throw AudioSink.ConfigurationException(
                "Unsupported PCM encoding ${inputFormat.pcmEncoding}",
                inputFormat
            )
        }
        rawMode = true
        inputEncoding = inputFormat.pcmEncoding
        sampleRate = inputFormat.sampleRate
        inputChannels = inputFormat.channelCount
        inputFrameSize = inputChannels * bytesPerSample(inputEncoding)

        // Configure the chain in the float domain.
        pipeline.configure(
            AudioProcessor.AudioFormat(inputFormat.sampleRate, inputFormat.channelCount, C.ENCODING_PCM_FLOAT)
        )
        outFrameSize = pipeline.outputChannels * 4

        delayFramesRemaining = inputFormat.encoderDelay.coerceAtLeast(0)
        paddingFrames = inputFormat.encoderPadding.coerceAtLeast(0)
        resetPending()

        // The delegate is configured for the chain's output format (float).
        val delegateFormat = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_RAW)
            .setPcmEncoding(C.ENCODING_PCM_FLOAT)
            .setSampleRate(pipeline.sampleRateHz)
            .setChannelCount(pipeline.outputChannels)
            .build()
        delegate.configure(delegateFormat, specifiedBufferSize, null)
    }

    override fun play() {
        delegate.play()
    }

    override fun handleDiscontinuity() {
        delegate.handleDiscontinuity()
    }

    override fun handleBuffer(
        buffer: ByteBuffer,
        presentationTimeUs: Long,
        encodedAccessUnitCount: Int
    ): Boolean {
        if (!rawMode) {
            return delegate.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
        }
        check(pendingInput == null || buffer === pendingInput)

        if (pendingInput == null) {
            pendingInput = buffer
            if (firstPtsUs == C.TIME_UNSET) firstPtsUs = presentationTimeUs
        }

        // Feed the delegate anything already waiting first (backpressure).
        drainToDelegate()

        // Consume input only as fast as the delegate absorbs output, so the
        // internal queue stays bounded. Return false (retain the buffer) once
        // the queue limit is hit; the renderer retries with the same buffer.
        while (pendingInput?.hasRemaining() == true) {
            if ((outputEnd - outputStart) / outFrameSize >= sampleRate) break
            processNextInputSlice()
            drainToDelegate()
        }
        if (pendingInput?.hasRemaining() == false) pendingInput = null
        return pendingInput == null
    }

    override fun playToEndOfStream() {
        if (!rawMode) {
            delegate.playToEndOfStream()
            return
        }
        // Consume any input still retained by backpressure.
        while (pendingInput?.hasRemaining() == true) {
            processNextInputSlice()
            drainToDelegate()
        }
        pendingInput = null
        if (!eosQueued) {
            eosQueued = true
            appendOutput(pipeline.endOfStream())
        }
        drainToDelegate()
        if (pendingOut != null) {
            // Delegate is applying backpressure; retry on the next call.
            return
        }
        // Everything feedable was consumed; drop the retained encoder-padding
        // tail and let the delegate finish.
        outputBytes = ByteArray(0)
        outputStart = 0
        outputEnd = 0
        delegate.playToEndOfStream()
    }

    override fun isEnded(): Boolean =
        eosQueued && pendingInput == null && pendingOut == null && outputStart >= outputEnd && delegate.isEnded()

    override fun hasPendingData(): Boolean =
        pendingOut != null || outputStart < outputEnd || pendingInput != null || delegate.hasPendingData()

    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) {
        delegate.setPlaybackParameters(playbackParameters)
    }

    override fun getPlaybackParameters(): PlaybackParameters = delegate.playbackParameters

    override fun setSkipSilenceEnabled(skipSilenceEnabled: Boolean) {
        delegate.setSkipSilenceEnabled(skipSilenceEnabled)
    }

    override fun getSkipSilenceEnabled(): Boolean = delegate.skipSilenceEnabled

    override fun setAudioAttributes(audioAttributes: AudioAttributes) {
        delegate.setAudioAttributes(audioAttributes)
    }

    override fun getAudioAttributes(): AudioAttributes? = delegate.audioAttributes

    override fun setAudioSessionId(audioSessionId: Int) {
        delegate.setAudioSessionId(audioSessionId)
    }

    override fun setAuxEffectInfo(auxEffectInfo: AuxEffectInfo) {
        delegate.setAuxEffectInfo(auxEffectInfo)
    }

    @RequiresApi(23)
    override fun setPreferredDevice(audioDeviceInfo: AudioDeviceInfo?) {
        delegate.setPreferredDevice(audioDeviceInfo)
    }

    override fun setOutputStreamOffsetUs(outputStreamOffsetUs: Long) {
        delegate.setOutputStreamOffsetUs(outputStreamOffsetUs)
    }

    override fun enableTunnelingV21() {
        delegate.enableTunnelingV21()
    }

    override fun disableTunneling() {
        delegate.disableTunneling()
    }

    override fun setOffloadMode(offloadMode: Int) {
        delegate.setOffloadMode(offloadMode)
    }

    override fun setOffloadDelayPadding(delayInFrames: Int, paddingInFrames: Int) {
        delegate.setOffloadDelayPadding(delayInFrames, paddingInFrames)
    }

    override fun setVolume(volume: Float) {
        delegate.setVolume(volume)
    }

    override fun pause() {
        delegate.pause()
    }

    override fun flush() {
        pipeline.flush()
        resetPending()
        delegate.flush()
    }

    override fun reset() {
        pipeline.reset()
        resetPending()
        rawMode = false
        inputEncoding = C.ENCODING_PCM_16BIT
        sampleRate = 0
        inputChannels = 0
        inputFrameSize = 0
        outFrameSize = 0
        delayFramesRemaining = 0
        paddingFrames = 0
        delegate.reset()
    }

    override fun release() {
        delegate.release()
    }

    private fun resetPending() {
        pendingInput = null
        pendingOut = null
        outputBytes = ByteArray(0)
        outputStart = 0
        outputEnd = 0
        pendingOutPtsUs = C.TIME_UNSET
        firstPtsUs = C.TIME_UNSET
        submittedFrames = 0L
        eosQueued = false
    }

    /** Processes at most [INPUT_SLICE_FRAMES] frames of the retained input. */
    private fun processNextInputSlice() {
        val input = pendingInput ?: return
        var sliceBytes = minOf(INPUT_SLICE_FRAMES * inputFrameSize, input.remaining())
        sliceBytes -= sliceBytes % inputFrameSize
        if (sliceBytes <= 0) {
            // Trailing partial frame: consume it; decodeToFloat drops it.
            sliceBytes = input.remaining()
        }
        val slice = input.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        slice.limit(slice.position() + sliceBytes)
        val floatOut = pipeline.process(slice, inputEncoding)
        input.position(input.position() + sliceBytes)
        appendOutput(floatOut)
    }

    /** Appends float PCM [bytes], dropping any remaining encoder-delay frames first. */
    private fun appendOutput(bytes: ByteBuffer) {
        if (!bytes.hasRemaining()) return
        if (delayFramesRemaining > 0) {
            val dropFrames = minOf(delayFramesRemaining, bytes.remaining() / outFrameSize)
            if (dropFrames > 0) {
                bytes.position(bytes.position() + dropFrames * outFrameSize)
                delayFramesRemaining -= dropFrames
            }
            if (!bytes.hasRemaining()) return
        }
        val src = ByteArray(bytes.remaining())
        bytes.get(src)
        val need = outputEnd + src.size
        if (need > outputBytes.size) {
            val grown = ByteArray(need.coerceAtLeast(outputBytes.size * 2))
            outputBytes.copyInto(grown, 0, outputStart, outputEnd)
            src.copyInto(grown, outputEnd - outputStart)
            outputBytes = grown
            outputEnd = outputEnd - outputStart + src.size
            outputStart = 0
        } else {
            src.copyInto(outputBytes, outputEnd)
            outputEnd = need
        }
    }

    /** Feeds buffered chain output into the delegate until it applies backpressure. */
    private fun drainToDelegate() {
        while (true) {
            if (pendingOut == null) {
                val retain = paddingFrames * outFrameSize
                val feedable = outputEnd - outputStart - retain
                if (feedable <= 0 || firstPtsUs == C.TIME_UNSET) return
                // Stamp this chunk once, from the submitted-frame count, so the
                // delegate's expected timeline always matches (no re-sync).
                pendingOutPtsUs = firstPtsUs + submittedFrames * 1_000_000L / sampleRate
                submittedFrames += feedable / outFrameSize
                pendingOut = ByteBuffer
                    .wrap(outputBytes, outputStart, feedable)
                    .order(ByteOrder.LITTLE_ENDIAN)
            }
            val out = pendingOut!!
            val before = out.position()
            val ok = delegate.handleBuffer(out, pendingOutPtsUs, 1)
            val consumed = out.position() - before
            outputStart += consumed
            if (!out.hasRemaining()) pendingOut = null
            if (!ok || consumed == 0) return
        }
    }

    private fun bytesPerSample(encoding: Int): Int = when (encoding) {
        C.ENCODING_PCM_16BIT -> 2
        C.ENCODING_PCM_24BIT -> 3
        C.ENCODING_PCM_32BIT -> 4
        C.ENCODING_PCM_FLOAT -> 4
        else -> throw IllegalArgumentException("Unsupported PCM encoding $encoding")
    }

    private fun supportsEncoding(format: Format): Boolean {
        if (MimeTypes.AUDIO_RAW != format.sampleMimeType) return true
        return isSupportedEncoding(format.pcmEncoding)
    }

    private fun isSupportedEncoding(encoding: Int): Boolean =
        encoding == C.ENCODING_PCM_16BIT ||
            encoding == C.ENCODING_PCM_24BIT ||
            encoding == C.ENCODING_PCM_32BIT ||
            encoding == C.ENCODING_PCM_FLOAT

    private companion object {
        /** Frames of renderer input consumed per processing step. */
        private const val INPUT_SLICE_FRAMES = 4096
    }
}
