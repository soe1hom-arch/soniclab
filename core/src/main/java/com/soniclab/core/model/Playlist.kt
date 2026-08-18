/*
 * Copyright 2026 soe1hom-arch
 * SPDX-License-Identifier: Apache-2.0
 */

package com.soniclab.core.model

/**
 * A user-created playlist. Track ids reference [Track.id] from the media library.
 */
data class Playlist(
    val id: String,
    val name: String,
    val trackIds: List<Long>,
    val createdAtMs: Long,
    val modifiedAtMs: Long
)
