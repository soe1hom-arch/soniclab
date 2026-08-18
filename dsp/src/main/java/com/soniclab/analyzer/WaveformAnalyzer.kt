/*
 * Copyright 2026 soe1hom-arch
 * SPDX-License-Identifier: Apache-2.0
 */

package com.soniclab.analyzer

import android.content.Context
import android.net.Uri

/**
 * Produces a compact min/max waveform from a decoded file.
 */
class WaveformAnalyzer(private val context: Context) {

    private val pcmReader = PcmReader(context)

    /**
     * Returns [bucketCount] pairs of (min, max) values in -1f..1f.
     */
    fun analyze(uri: Uri, bucketCount: Int = 400): List<Pair<Float, Float>>? {
        val decoded = pcmReader.decode(uri) ?: return null
        val samples = decoded.samples
        if (samples.isEmpty()) return emptyList()
        val buckets = MutableList(bucketCount) { 0f to 0f }
        val perBucket = samples.size / bucketCount
        for (b in 0 until bucketCount) {
            val start = b * perBucket
            val end = if (b == bucketCount - 1) samples.size else start + perBucket
            var min = Float.MAX_VALUE
            var max = Float.MIN_VALUE
            for (i in start until end) {
                val v = samples[i]
                if (v < min) min = v
                if (v > max) max = v
            }
            if (min == Float.MAX_VALUE) min = 0f
            if (max == Float.MIN_VALUE) max = 0f
            buckets[b] = min to max
        }
        return buckets
    }
}
