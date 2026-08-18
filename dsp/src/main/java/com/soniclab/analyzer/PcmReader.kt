/*
 * Copyright 2026 soe1hom-arch
 * SPDX-License-Identifier: Apache-2.0
 */

package com.soniclab.analyzer

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Decodes a whole audio file to mono float PCM using MediaCodec.
 * Used by waveform, loudness and spectrum analysis.
 */
class PcmReader(private val context: Context) {

    data class Decoded(val samples: FloatArray, val sampleRate: Int, val channels: Int)

    /**
     * Decodes [uri] fully. [maxSeconds] bounds the read for very long files.
     */
    fun decode(uri: Uri, maxSeconds: Int = 600, onProgress: (Float) -> Unit = {}): Decoded? {
        var extractor: MediaExtractor? = null
        var codec: MediaCodec? = null
        try {
            extractor = MediaExtractor()
            extractor.setDataSource(context, uri, null)

            var audioIndex = -1
            for (i in 0 until extractor.trackCount) {
                if (extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    audioIndex = i
                    break
                }
            }
            if (audioIndex < 0) return null
            val format = extractor.getTrackFormat(audioIndex)
            extractor.selectTrack(audioIndex)

            val sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100
            val channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2
            val maxSamples = sampleRate * maxSeconds
            val maxBytes = maxSamples * channels * 2

            codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME) ?: return null)
            codec.configure(format, null, null, 0)
            codec.start()

            val out = java.io.ByteArrayOutputStream()
            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            val timeoutUs = 10_000L

            while (!outputDone && out.size() < maxBytes) {
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(timeoutUs)
                    if (inIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inIndex) ?: continue
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)
                when {
                    outIndex >= 0 -> {
                        val outBuffer = codec.getOutputBuffer(outIndex)
                        if (outBuffer != null && bufferInfo.size > 0) {
                            outBuffer.position(bufferInfo.offset)
                            outBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            val bytes = ByteArray(bufferInfo.size)
                            outBuffer.get(bytes)
                            out.write(bytes)
                        }
                        val end = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                        codec.releaseOutputBuffer(outIndex, false)
                        if (end) outputDone = true
                    }
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {}
                    outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> if (inputDone) Thread.sleep(5)
                }
            }

            val pcm = out.toByteArray()
            val byteBuffer = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN)
            val totalFrames = pcm.size / (channels * 2)
            val mono = FloatArray(totalFrames)
            var idx = 0
            for (frame in 0 until totalFrames) {
                var sum = 0
                for (ch in 0 until channels) {
                    sum += byteBuffer.short.toInt()
                }
                mono[idx++] = sum / channels.toFloat() / 32768f
            }
            return Decoded(mono, sampleRate, channels)
        } catch (e: Exception) {
            return null
        } finally {
            runCatching { extractor?.release() }
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
        }
    }
}
