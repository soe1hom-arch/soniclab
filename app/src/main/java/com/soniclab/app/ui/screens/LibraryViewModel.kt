package com.soniclab.app.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.soniclab.app.di.AppContainer
import com.soniclab.core.model.Track

data class AlbumGroup(
    val albumId: Long,
    val name: String,
    val artist: String,
    val tracks: List<Track>
)

data class ArtistGroup(
    val name: String,
    val tracks: List<Track>
)

class LibraryViewModel(private val container: AppContainer) : ViewModel() {

    val tracks = container.libraryRepository.tracks
    val isLoading = container.libraryRepository.isLoading
    val libraryError = container.libraryRepository.error
    val favorites = container.favoritesRepository.favorites

    var query by mutableStateOf("")
        private set
    var selectedAlbumId by mutableStateOf<Long?>(null)
        private set
    var selectedArtist by mutableStateOf<String?>(null)
        private set

    init {
        container.libraryRepository.refresh()
    }

    fun onQueryChange(value: String) {
        query = value
    }

    fun refresh() = container.libraryRepository.refresh()

    fun searchResults(): List<Track> = container.libraryRepository.search(query)

    val albums: List<AlbumGroup>
        get() = tracks.value
            .groupBy { it.albumId }
            .map { (id, list) ->
                AlbumGroup(
                    albumId = id,
                    name = list.first().album,
                    artist = list.first().artist,
                    tracks = list.sortedBy { it.title.lowercase() }
                )
            }
            .sortedBy { it.name.lowercase() }

    val artists: List<ArtistGroup>
        get() = tracks.value
            .groupBy { it.artist.lowercase() }
            .map { (_, list) ->
                ArtistGroup(
                    name = list.first().artist,
                    tracks = list.sortedBy { it.title.lowercase() }
                )
            }
            .sortedBy { it.name.lowercase() }

    fun selectAlbum(albumId: Long) {
        selectedAlbumId = albumId
        selectedArtist = null
    }

    fun selectArtist(name: String) {
        selectedArtist = name
        selectedAlbumId = null
    }

    fun clearSelection() {
        selectedAlbumId = null
        selectedArtist = null
    }

    fun selectionLabel(): String? {
        val albumId = selectedAlbumId
        if (albumId != null) {
            return albums.firstOrNull { it.albumId == albumId }?.name
        }
        return selectedArtist
    }

    fun visibleTracks(): List<Track> {
        val albumId = selectedAlbumId
        if (albumId != null) {
            return tracks.value.filter { it.albumId == albumId }
        }
        val artist = selectedArtist
        if (artist != null) {
            return tracks.value.filter { it.artist == artist }
        }
        return searchResults()
    }

    fun toggleFavorite(id: Long) = container.favoritesRepository.toggle(id)

    fun play(tracks: List<Track>, index: Int) {
        container.playerController.playQueue(tracks, index)
    }
}
