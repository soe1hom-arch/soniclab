package com.soniclab.analyzer

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri

/**
 * Codec/bitrate/sample-rate details for the Analyzer screen.
 */
class AudioInfoAnalyzer(private val context: Context) {

    data class AudioInfo(
        val durationMs: Long,
        val bitrateKbps: Int,
        val sampleRateHz: Int,
        val channelCount: Int,
        val codec: String,
        val mimeType: String?
    )

    fun analyze(uri: Uri): AudioInfo? {
        val retriever = MediaMetadataRetriever()
        var extractor: MediaExtractor? = null
        return try {
            runCatching { retriever.setDataSource(context, uri) }.getOrNull() ?: return null
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0

            extractor = MediaExtractor()
            extractor.setDataSource(context, uri, null)
            var sampleRate = 0
            var channels = 0
            var mime: String? = null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                if (fmt.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    sampleRate = if (fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 0
                    channels = if (fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 0
                    mime = fmt.getString(MediaFormat.KEY_MIME)
                    break
                }
            }
            AudioInfo(
                durationMs = durationMs,
                bitrateKbps = bitrate / 1000,
                sampleRateHz = sampleRate,
                channelCount = channels,
                codec = mime?.substringAfterLast("audio/") ?: "unknown",
                mimeType = mime
            )
        } catch (e: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
            runCatching { extractor?.release() }
        }
    }
}
