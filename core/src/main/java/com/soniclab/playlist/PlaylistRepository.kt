/*
 * Copyright 2026 soe1hom-arch
 * SPDX-License-Identifier: Apache-2.0
 */

package com.soniclab.playlist

import android.content.Context
import com.soniclab.core.model.Playlist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * JSON-file backed playlist repository. Fully offline; writes are serialized
 * through a Mutex and flushed to disk after every mutation.
 */
class PlaylistRepository(context: Context) {

    private val file = File(context.filesDir, "playlists.json")
    private val mutex = Mutex()

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    init {
        load()
    }

    suspend fun create(name: String, trackIds: List<Long> = emptyList()): Playlist = withContext(Dispatchers.IO) {
        mutex.withLock {
            val now = System.currentTimeMillis()
            val playlist = Playlist(
                id = UUID.randomUUID().toString(),
                name = name,
                trackIds = trackIds,
                createdAtMs = now,
                modifiedAtMs = now
            )
            _playlists.value = _playlists.value + playlist
            save()
            playlist
        }
    }

    suspend fun delete(id: String) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                _playlists.value = _playlists.value.filterNot { it.id == id }
                save()
            }
        }
    }

    suspend fun rename(id: String, newName: String) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                _playlists.value = _playlists.value.map {
                    if (it.id == id) it.copy(name = newName, modifiedAtMs = System.currentTimeMillis()) else it
                }
                save()
            }
        }
    }

    suspend fun addTracks(id: String, trackIds: List<Long>) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                _playlists.value = _playlists.value.map { p ->
                    if (p.id == id) {
                        p.copy(
                            trackIds = (p.trackIds + trackIds).distinct(),
                            modifiedAtMs = System.currentTimeMillis()
                        )
                    } else p
                }
                save()
            }
        }
    }

    suspend fun removeTrack(id: String, trackId: Long) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                _playlists.value = _playlists.value.map { p ->
                    if (p.id == id) {
                        p.copy(
                            trackIds = p.trackIds.filterNot { it == trackId },
                            modifiedAtMs = System.currentTimeMillis()
                        )
                    } else p
                }
                save()
            }
        }
    }

    fun playlistById(id: String): Playlist? = _playlists.value.firstOrNull { it.id == id }

    private fun load() {
        if (!file.exists()) return
        try {
            val root = JSONObject(file.readText())
            val array = root.getJSONArray("playlists")
            val list = buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val ids = JSONArray(obj.getString("trackIdsJson"))
                    add(
                        Playlist(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            trackIds = buildList { for (j in 0 until ids.length()) add(ids.getLong(j)) },
                            createdAtMs = obj.getLong("createdAtMs"),
                            modifiedAtMs = obj.getLong("modifiedAtMs")
                        )
                    )
                }
            }
            _playlists.value = list
        } catch (e: Exception) {
            // Corrupt store: start fresh rather than crash.
            _playlists.value = emptyList()
        }
    }

    private fun save() {
        val root = JSONObject()
        val array = JSONArray()
        _playlists.value.forEach { p ->
            val ids = JSONArray()
            p.trackIds.forEach { ids.put(it) }
            array.put(
                JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("trackIdsJson", ids.toString())
                    .put("createdAtMs", p.createdAtMs)
                    .put("modifiedAtMs", p.modifiedAtMs)
            )
        }
        root.put("playlists", array)
        file.writeText(root.toString())
    }
}
