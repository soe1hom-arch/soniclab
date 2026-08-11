package com.soniclab.app.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.soniclab.app.di.AppContainer
import com.soniclab.app.ui.common.AlbumArt
import com.soniclab.app.ui.common.appViewModel
import com.soniclab.core.model.Track
import com.soniclab.core.permission.AudioPermissions
import com.soniclab.core.util.TimeFormat

private enum class LibraryTab(val label: String) {
    TRACKS("Lagu"),
    ALBUMS("Album"),
    ARTISTS("Artis")
}

@Composable
fun LibraryScreen(container: AppContainer, onOpenPlayer: () -> Unit) {
    val vm: LibraryViewModel = appViewModel { LibraryViewModel(it) }
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val isLoading by vm.isLoading.collectAsStateWithLifecycle()
    val libraryError by vm.libraryError.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(LibraryTab.TRACKS) }
    val selection = vm.selectionLabel()

    Column(modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(
                "Library",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(top = 20.dp, bottom = 12.dp)
            )
            if (tab == LibraryTab.TRACKS) {
                OutlinedTextField(
                    value = vm.query,
                    onValueChange = vm::onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search tracks, artists, albums") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (vm.query.isNotEmpty()) {
                            IconButton(onClick = { vm.onQueryChange("") }) {
                                Icon(Icons.Rounded.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
                )
            }
        }

        TabRow(selectedTabIndex = tab.ordinal) {
            LibraryTab.entries.forEach { item ->
                Tab(
                    selected = tab == item,
                    onClick = { tab = item },
                    text = { Text(item.label) }
                )
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                isLoading && tracks.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                libraryError != null && tracks.isEmpty() -> Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(libraryError.orEmpty(), color = MaterialTheme.colorScheme.error)
                    if (!container.libraryRepository.hasAccess()) {
                        Spacer(Modifier.height(12.dp))
                        val context = LocalContext.current
                        Button(onClick = {
                            (context as? Activity)?.let { AudioPermissions.openAppSettings(it) }
                        }) {
                            Text("Buka Pengaturan & Izinkan Audio")
                        }
                    }
                }
                else -> when (tab) {
                    LibraryTab.TRACKS -> TracksTab(vm, favorites, selection, onOpenPlayer)
                    LibraryTab.ALBUMS -> AlbumsTab(vm)
                    LibraryTab.ARTISTS -> ArtistsTab(vm)
                }
            }
        }
    }
}

@Composable
private fun TracksTab(
    vm: LibraryViewModel,
    favorites: Set<Long>,
    selection: String?,
    onOpenPlayer: () -> Unit
) {
    val list = vm.visibleTracks()
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(top = 8.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (selection != null) {
            item(key = "selection") {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { vm.clearSelection() }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.primary)
                    Text(
                        "  $selection",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )
                }
            }
        }
        items(list, key = { it.id }) { track ->
            TrackRow(
                track = track,
                isFavorite = track.id in favorites,
                onClick = {
                    vm.play(list, list.indexOf(track))
                    onOpenPlayer()
                },
                onFavorite = { vm.toggleFavorite(track.id) }
            )
        }
    }
}

@Composable
private fun AlbumsTab(vm: LibraryViewModel) {
    val albums = vm.albums
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(albums, key = { it.albumId }) { album ->
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { vm.selectAlbum(album.albumId) }
            ) {
                AlbumArt(
                    albumId = album.albumId,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp))
                )
                Spacer(Modifier.height(6.dp))
                Text(album.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text(
                    "${album.artist} • ${album.tracks.size} lagu",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun ArtistsTab(vm: LibraryViewModel) {
    val artists = vm.artists
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        items(artists, key = { it.name }) { artist ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable { vm.selectArtist(artist.name) }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(artist.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    Text(
                        "${artist.tracks.size} lagu",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TrackRow(
    track: Track,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onFavorite: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AlbumArt(
            albumId = track.albumId,
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))
        )
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(track.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Text(
                "${track.artist} • ${TimeFormat.formatDuration(track.durationMs)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        IconButton(onClick = onClick) {
            Icon(
                imageVector = Icons.Rounded.PlayCircle,
                contentDescription = "Play",
                tint = MaterialTheme.colorScheme.primary
            )
        }
        IconButton(onClick = onFavorite) {
            Icon(
                imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = "Favorite",
                tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
