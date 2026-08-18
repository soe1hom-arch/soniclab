/*
 * Copyright 2026 soe1hom-arch
 * SPDX-License-Identifier: Apache-2.0
 */

package com.soniclab.core.model

import android.net.Uri

/**
 * A single audio track from the local media library.
 */
data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val uri: Uri,
    val dataPath: String,
    val sizeBytes: Long,
    val mimeType: String?,
    val dateAddedMs: Long,
    val favorite: Boolean = false
) {
    val isPlayable: Boolean get() = sizeBytes > 0 || durationMs > 0
}
