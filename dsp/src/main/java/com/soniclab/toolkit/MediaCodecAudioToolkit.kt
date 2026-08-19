/*
 * Copyright 2026 soe1hom-arch
 * SPDX-License-Identifier: Apache-2.0
 */

package com.soniclab.toolkit

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.soniclab.ai.SpectralVocalRemover
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val MAX_FULL_DECODE_US = 10L * 60L * 1000_000L

/**
 * Fully offline toolkit implementation built on MediaExtractor + MediaCodec
 * (decode) and pure-Kotlin PCM processing ([PcmProcessor]). Every operation
 * runs on-device with no FFmpeg binary and no network access.
 */
class MediaCodecAudioToolkit(private val context: Context) : AudioToolkit {

    override suspend fun getInfo(uri: Uri): ToolkitResult = withContext(Dispatchers.IO) {
        try {
            val retriever = MediaMetadataRetriever()
            runCatching { retriever.setDataSource(context, uri) }
                .getOrElse { return@withContext ToolkitResult.Failure("Unsupported file") }
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0

            val extractor = MediaExtractor()
            extractor.setDataSource(context, uri, null)
            var sampleRate = 0
            var channels = 0
            var mime: String? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                if (format.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else -1
                    channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else -1
                    mime = format.getString(MediaFormat.KEY_MIME)
                    break
                }
            }
            extractor.release()
            retriever.release()

            ToolkitResult.Info(
                AudioFileInfo(
                    durationMs = durationMs,
                    bitrateKbps = bitrate / 1000,
                    sampleRateHz = sampleRate,
                    channelCount = channels,
                    codec = mime?.substringAfterLast("audio/") ?: "unknown",
                    mimeType = mime,
                    title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
                    artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                    album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                )
            )
        } catch (e: Exception) {
            ToolkitResult.Failure("Failed to read audio info: ${e.message}", e)
        }
    }

    override suspend fun convertToWav(uri: Uri, outputPath: String, onProgress: (Float) -> Unit): ToolkitResult =
        withContext(Dispatchers.IO) {
            try {
                val decoded = decodePcmBytes(uri, startMs = null, endMs = null, onProgress)
                writeWavFile(outputPath, PcmProcessor.toPcmData(decoded.pcmBytes, decoded.sampleRate, decoded.channels))
                onProgress(1f)
                ToolkitResult.Success(outputPath)
            } catch (e: Exception) {
                ToolkitResult.Failure("Convert failed: ${e.message}", e)
            }
        }

    override suspend fun cut(uri: Uri, startMs: Long, endMs: Long, outputPath: String, onProgress: (Float) -> Unit): ToolkitResult =
        withContext(Dispatchers.IO) {
            try {
                val decoded = decodePcmBytes(uri, startMs = startMs, endMs = endMs, onProgress)
                writeWavFile(outputPath, PcmProcessor.toPcmData(decoded.pcmBytes, decoded.sampleRate, decoded.channels))
                onProgress(1f)
                ToolkitResult.Success(outputPath)
            } catch (e: Exception) {
                ToolkitResult.Failure("Cut failed: ${e.message}", e)
            }
        }

    override suspend fun join(tracks: List<Uri>, outputPath: String, onProgress: (Float) -> Unit): ToolkitResult =
        withContext(Dispatchers.IO) {
            try {
                if (tracks.isEmpty()) return@withContext ToolkitResult.Failure("No tracks to join")
                val parts = mutableListOf<PcmData>()
                var reference: PcmData? = null
                tracks.forEachIndexed { index, uri ->
                    val decoded = decodePcmBytes(uri, startMs = null, endMs = null) {
                        onProgress((index + it) / tracks.size)
                    }
                    var data = PcmProcessor.toPcmData(decoded.pcmBytes, decoded.sampleRate, decoded.channels)
                    val base = reference ?: data.also { reference = it }
                    data = PcmProcessor.conformSampleRate(PcmProcessor.conformChannels(data, base.channels), base.sampleRate)
                    parts += data
                }
                val base = reference ?: return@withContext ToolkitResult.Failure("No decodable tracks")
                val total = parts.sumOf { it.samples.size }
                val out = FloatArray(total)
                var offset = 0
                for (part in parts) {
                    part.samples.copyInto(out, offset)
                    offset += part.samples.size
                }
                writeWavFile(outputPath, PcmData(out, base.sampleRate, base.channels))
                onProgress(1f)
                ToolkitResult.Success(outputPath)
            } catch (e: Exception) {
                ToolkitResult.Failure("Join failed: ${e.message}", e)
            }
        }

    override suspend fun normalize(uri: Uri, outputPath: String, targetLufs: Float, onProgress: (Float) -> Unit): ToolkitResult =
        withContext(Dispatchers.IO) {
            try {
                val decoded = decodePcmBytes(uri, startMs = null, endMs = null, onProgress)
                val data = PcmProcessor.toPcmData(decoded.pcmBytes, decoded.sampleRate, decoded.channels)
                val normalized = PcmProcessor.normalize(data, targetLufs)
                writeWavFile(outputPath, normalized)
                onProgress(1f)
                ToolkitResult.Success(outputPath)
            } catch (e: Exception) {
                ToolkitResult.Failure("Normalize failed: ${e.message}", e)
            }
        }

    override suspend fun reverse(uri: Uri, outputPath: String, onProgress: (Float) -> Unit): ToolkitResult =
        withContext(Dispatchers.IO) {
            try {
                val decoded = decodePcmBytes(uri, startMs = null, endMs = null, onProgress)
                val data = PcmProcessor.toPcmData(decoded.pcmBytes, decoded.sampleRate, decoded.channels)
                writeWavFile(outputPath, PcmProcessor.reverse(data))
                onProgress(1f)
                ToolkitResult.Success(outputPath)
            } catch (e: Exception) {
                ToolkitResult.Failure("Reverse failed: ${e.message}", e)
            }
        }

    override suspend fun changePitch(
        uri: Uri,
        semitones: Float,
        outputPath: String,
        quality: StretchQuality,
        onProgress: (Float) -> Unit
    ): ToolkitResult = withContext(Dispatchers.IO) {
            try {
                val decoded = decodePcmBytes(uri, startMs = null, endMs = null, onProgress)
                val data = PcmProcessor.toPcmData(decoded.pcmBytes, decoded.sampleRate, decoded.channels)
                writeWavFile(outputPath, PcmProcessor.pitchShift(data, semitones, quality))
                onProgress(1f)
                ToolkitResult.Success(outputPath)
            } catch (e: Exception) {
                ToolkitResult.Failure("Pitch change failed: ${e.message}", e)
            }
        }

    /**
     * Tempo change without pitch shift: [factor] is the speed multiplier
     * (2.0 = twice as fast, output half as long; 0.5 = half speed, twice as
     * long), converted to the internal stretch ratio 1/factor.
     */
    override suspend fun changeTempo(
        uri: Uri,
        factor: Float,
        outputPath: String,
        quality: StretchQuality,
        onProgress: (Float) -> Unit
    ): ToolkitResult = withContext(Dispatchers.IO) {
            try {
                val decoded = decodePcmBytes(uri, startMs = null, endMs = null, onProgress)
                val data = PcmProcessor.toPcmData(decoded.pcmBytes, decoded.sampleRate, decoded.channels)
                writeWavFile(outputPath, PcmProcessor.timeStretch(data, 1f / factor, quality))
                onProgress(1f)
                ToolkitResult.Success(outputPath)
            } catch (e: Exception) {
                ToolkitResult.Failure("Tempo change failed: ${e.message}", e)
            }
        }

    override suspend fun vocalReduction(uri: Uri, outputPath: String, onProgress: (Float) -> Unit): ToolkitResult =
        withContext(Dispatchers.IO) {
            try {
                val decoded = decodePcmBytes(uri, startMs = null, endMs = null, onProgress)
                val data = PcmProcessor.toPcmData(decoded.pcmBytes, decoded.sampleRate, decoded.channels)
                if (data.channels < 2) return@withContext ToolkitResult.Failure("Vocal reduction requires a stereo file")
                // On-device separator: STFT center-channel extraction with a
                // soft ratio mask (karaoke), fully offline — no network, no
                // bundled model needed.
                val result = SpectralVocalRemover().separate(data.samples)
                writeWavFile(outputPath, PcmData(result.instrumental, data.sampleRate, 2))
                onProgress(1f)
                ToolkitResult.Success(outputPath)
            } catch (e: Exception) {
                ToolkitResult.Failure("Vocal reduction failed: ${e.message}", e)
            }
        }

    // ---- internals ----

    private class DecodedPcm(val pcmBytes: ByteArray, val sampleRate: Int, val channels: Int)

    private fun decodePcmBytes(
        uri: Uri,
        startMs: Long?,
        endMs: Long?,
        onProgress: (Float) -> Unit
    ): DecodedPcm {
        var extractor: MediaExtractor? = null
        var codec: MediaCodec? = null
        var result: DecodedPcm? = null
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
            if (audioIndex < 0) throw IllegalArgumentException("No audio track found")

            val format = extractor.getTrackFormat(audioIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: throw IllegalArgumentException("Unknown codec")
            extractor.selectTrack(audioIndex)

            // Full-file decode holds PCM in memory; refuse very long files so
            // DSP ops fail with a clear message instead of an OOM crash.
            if (endMs == null && format.containsKey(MediaFormat.KEY_DURATION)) {
                val durationUs = format.getLong(MediaFormat.KEY_DURATION)
                if (durationUs > MAX_FULL_DECODE_US) {
                    throw IllegalArgumentException("Audio terlalu panjang untuk diproses on-device (maks 10 menit)")
                }
            }

            startMs?.let { extractor.seekTo(it * 1000L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC) }

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100
            val channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2
            val pcmOut = ByteArrayOutputStream()
            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            val totalUs = (endMs ?: Long.MAX_VALUE) * 1000L

            while (!outputDone) {
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inIndex) ?: continue
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            val pts = extractor.sampleTime
                            if (endMs != null && pts > totalUs) {
                                codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(inIndex, 0, sampleSize, pts, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                when {
                    outIndex >= 0 -> {
                        val outBuffer = codec.getOutputBuffer(outIndex)
                        if (outBuffer != null && bufferInfo.size > 0) {
                            outBuffer.position(bufferInfo.offset)
                            outBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            val pcm = ByteArray(bufferInfo.size)
                            outBuffer.get(pcm)
                            pcmOut.write(pcm)
                            onProgress(if (totalUs > 0) (bufferInfo.presentationTimeUs.toFloat() / totalUs).coerceIn(0f, 1f) else 0f)
                        }
                        val isEnd = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                        codec.releaseOutputBuffer(outIndex, false)
                        if (isEnd) outputDone = true
                    }
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
                    outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> if (inputDone) Thread.sleep(5)
                }
            }

            result = DecodedPcm(pcmOut.toByteArray(), sampleRate, channels)
        } finally {
            runCatching { extractor?.release() }
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
        }
        return requireNotNull(result)
    }

    private fun writeWavFile(path: String, data: PcmData) {
        val bytesPerSample = 2
        val blockAlign = data.channels * bytesPerSample
        val byteRate = data.sampleRate * blockAlign
        val dataSize = data.samples.size * bytesPerSample

        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(36 + dataSize)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16)
        header.putShort(1)
        header.putShort(data.channels.toShort())
        header.putInt(data.sampleRate)
        header.putInt(byteRate)
        header.putShort(blockAlign.toShort())
        header.putShort(16)
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(dataSize)

        FileOutputStream(path).use { out ->
            out.write(header.array())
            val pcm = ByteBuffer.allocate(dataSize).order(ByteOrder.LITTLE_ENDIAN)
            for (sample in data.samples) {
                pcm.putShort((sample.coerceIn(-1f, 1f) * 32767f).toInt().toShort())
            }
            out.write(pcm.array())
        }
    }
}

