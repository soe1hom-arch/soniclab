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
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Media3 AudioSink wrapper that keeps the DSP chain in float domain.
 *
 * Critical invariant: every byte buffer handed to the delegate must be an
 * exact multiple of the output frame size. DefaultAudioSink rejects any chunk
 * that is not aligned to the channel/sample layout, which causes the runtime
 * IllegalArgumentException seen in logs.
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

    override fun setListener(listener: AudioSink.Listener) = delegate.setListener(listener)
    override fun setPlayerId(playerId: PlayerId?) = delegate.setPlayerId(playerId)
    override fun setClock(clock: Clock) = delegate.setClock(clock)
    override fun supportsFormat(format: Format): Boolean = supportsEncoding(format) && delegate.supportsFormat(format)
    override fun getFormatSupport(format: Format): Int =
        if (supportsEncoding(format)) delegate.getFormatSupport(format) else AudioSink.SINK_FORMAT_UNSUPPORTED
    override fun getCurrentPositionUs(sourceEnded: Boolean): Long = delegate.getCurrentPositionUs(sourceEnded)

    override fun configure(inputFormat: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {
        if (MimeTypes.AUDIO_RAW != inputFormat.sampleMimeType) {
            rawMode = false
            resetPending()
            delegate.configure(inputFormat, specifiedBufferSize, outputChannels)
            return
        }
        if (!isSupportedEncoding(inputFormat.pcmEncoding)) {
            throw AudioSink.ConfigurationException("Unsupported PCM encoding ${inputFormat.pcmEncoding}", inputFormat)
        }
        rawMode = true
        inputEncoding = inputFormat.pcmEncoding
        sampleRate = inputFormat.sampleRate
        inputChannels = inputFormat.channelCount
        inputFrameSize = inputChannels * bytesPerSample(inputEncoding)
        pipeline.configure(AudioProcessor.AudioFormat(inputFormat.sampleRate, inputFormat.channelCount, C.ENCODING_PCM_FLOAT))
        outFrameSize = pipeline.outputChannels * 4
        delayFramesRemaining = inputFormat.encoderDelay.coerceAtLeast(0)
        paddingFrames = inputFormat.encoderPadding.coerceAtLeast(0)
        resetPending()
        val delegateFormat = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_RAW)
            .setPcmEncoding(C.ENCODING_PCM_FLOAT)
            .setSampleRate(pipeline.sampleRateHz)
            .setChannelCount(pipeline.outputChannels)
            .build()
        delegate.configure(delegateFormat, specifiedBufferSize, null)
    }

    override fun play() = delegate.play()
    override fun handleDiscontinuity() = delegate.handleDiscontinuity()

    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
        if (!rawMode) return delegate.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)

        // Media3's DefaultAudioSink requires the same input buffer instance to be
        // reused until the previous one is fully drained. If we accept a different
        // buffer while a previous input is still pending, the delegate will throw
        // IllegalArgumentException on the next handleBuffer() call.
        if (pendingInput != null && buffer !== pendingInput) {
            return false
        }

        if (pendingInput == null) {
            pendingInput = buffer
            if (firstPtsUs == C.TIME_UNSET) firstPtsUs = presentationTimeUs
        }

        drainToDelegate()
        while (pendingInput?.hasRemaining() == true) {
            // Only a chunk already handed to the delegate counts as backpressure.
            // Bytes held back for encoder padding or partial frames stay in
            // outputBytes legitimately and must not stall the input.
            if (pendingOut != null) return false
            if ((outputEnd - outputStart) / outFrameSize >= sampleRate) break
            processNextInputSlice()
            drainToDelegate()
        }

        if (pendingInput?.hasRemaining() == false) pendingInput = null
        return pendingInput == null && pendingOut == null
    }

    override fun playToEndOfStream() {
        if (!rawMode) {
            delegate.playToEndOfStream()
            return
        }

        drainToDelegate()
        if (pendingOut != null) return

        if (pendingInput?.hasRemaining() == true) {
            if ((outputEnd - outputStart) / outFrameSize >= sampleRate) return
            processNextInputSlice()
            drainToDelegate()
            if (pendingInput?.hasRemaining() == true || pendingOut != null) return
        } else {
            pendingInput = null
        }

        if (!eosQueued) {
            eosQueued = true
            appendOutput(pipeline.endOfStream())
        }
        drainToDelegate()
        if (pendingOut != null) return

        outputBytes = ByteArray(0)
        outputStart = 0
        outputEnd = 0
        delegate.playToEndOfStream()
    }

    override fun isEnded(): Boolean =
        eosQueued && pendingInput == null && pendingOut == null && outputStart >= outputEnd && delegate.isEnded()

    override fun hasPendingData(): Boolean =
        pendingOut != null || outputStart < outputEnd || pendingInput != null || delegate.hasPendingData()

    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) = delegate.setPlaybackParameters(playbackParameters)
    override fun getPlaybackParameters(): PlaybackParameters = delegate.playbackParameters
    override fun setSkipSilenceEnabled(skipSilenceEnabled: Boolean) = delegate.setSkipSilenceEnabled(skipSilenceEnabled)
    override fun getSkipSilenceEnabled(): Boolean = delegate.skipSilenceEnabled
    override fun setAudioAttributes(audioAttributes: AudioAttributes) = delegate.setAudioAttributes(audioAttributes)
    override fun getAudioAttributes(): AudioAttributes? = delegate.audioAttributes
    override fun setAudioSessionId(audioSessionId: Int) = delegate.setAudioSessionId(audioSessionId)
    override fun setAuxEffectInfo(auxEffectInfo: AuxEffectInfo) = delegate.setAuxEffectInfo(auxEffectInfo)

    @RequiresApi(23)
    override fun setPreferredDevice(audioDeviceInfo: AudioDeviceInfo?) = delegate.setPreferredDevice(audioDeviceInfo)

    override fun setOutputStreamOffsetUs(outputStreamOffsetUs: Long) = delegate.setOutputStreamOffsetUs(outputStreamOffsetUs)
    override fun enableTunnelingV21() = delegate.enableTunnelingV21()
    override fun disableTunneling() = delegate.disableTunneling()
    override fun setOffloadMode(offloadMode: Int) = delegate.setOffloadMode(offloadMode)
    override fun setOffloadDelayPadding(delayInFrames: Int, paddingInFrames: Int) = delegate.setOffloadDelayPadding(delayInFrames, paddingInFrames)
    override fun setVolume(volume: Float) = delegate.setVolume(volume)
    override fun pause() = delegate.pause()

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

    override fun release() = delegate.release()

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

    private fun processNextInputSlice() {
        val input = pendingInput ?: return
        var sliceBytes = minOf(INPUT_SLICE_FRAMES * inputFrameSize, input.remaining())
        sliceBytes -= sliceBytes % inputFrameSize
        if (sliceBytes <= 0) sliceBytes = input.remaining()
        val slice = input.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        slice.limit(slice.position() + sliceBytes)
        val floatOut = pipeline.process(slice, inputEncoding)
        input.position(input.position() + sliceBytes)
        appendOutput(floatOut)
    }

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

    private fun drainToDelegate() {
        while (true) {
            if (pendingOut == null) {
                val totalBufferedBytes = outputEnd - outputStart
                if (totalBufferedBytes <= 0 || firstPtsUs == C.TIME_UNSET) return

                val retainBytes = paddingFrames * outFrameSize
                val safeBytes = (totalBufferedBytes - retainBytes).coerceAtLeast(0)
                val feedableFrames = safeBytes / outFrameSize
                val feedableBytes = feedableFrames * outFrameSize
                if (feedableBytes <= 0) return

                pendingOutPtsUs = firstPtsUs + submittedFrames * 1_000_000L / sampleRate
                submittedFrames += feedableFrames.toLong()
                pendingOut = ByteBuffer.wrap(outputBytes, outputStart, feedableBytes).order(ByteOrder.LITTLE_ENDIAN)
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

    private fun supportsEncoding(format: Format): Boolean =
        MimeTypes.AUDIO_RAW != format.sampleMimeType || isSupportedEncoding(format.pcmEncoding)

    private fun isSupportedEncoding(encoding: Int): Boolean =
        encoding == C.ENCODING_PCM_16BIT ||
            encoding == C.ENCODING_PCM_24BIT ||
            encoding == C.ENCODING_PCM_32BIT ||
            encoding == C.ENCODING_PCM_FLOAT

    private companion object {
        const val INPUT_SLICE_FRAMES = 4096
    }
}

