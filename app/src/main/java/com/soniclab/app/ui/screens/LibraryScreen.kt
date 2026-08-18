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
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
fun LibraryScreen(
    container: AppContainer,
    onOpenPlayer: () -> Unit,
    onOpenStudio: (Track) -> Unit
) {
    val vm: LibraryViewModel = appViewModel { LibraryViewModel(it) }
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val isLoading by vm.isLoading.collectAsStateWithLifecycle()
    val libraryError by vm.libraryError.collectAsStateWithLifecycle()
    val playerState by container.playerController.uiState.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(LibraryTab.TRACKS) }
    val selection = vm.selectionLabel()

    Column(modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(
                "Perpustakaan",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(top = 20.dp, bottom = 12.dp)
            )
            if (tab == LibraryTab.TRACKS) {
                OutlinedTextField(
                    value = vm.query,
                    onValueChange = vm::onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Cari lagu, artis, atau album") },
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
                    LibraryTab.TRACKS -> TracksTab(
                        vm,
                        favorites,
                        selection,
                        currentTrackId = playerState.currentTrack?.id,
                        onOpenPlayer = onOpenPlayer,
                        onOpenStudio = onOpenStudio,
                        onPlayNext = { container.playerController.addToQueueNext(it) },
                        onAddToQueue = { container.playerController.addToQueueEnd(it) }
                    )
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
    currentTrackId: Long?,
    onOpenPlayer: () -> Unit,
    onOpenStudio: (Track) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit
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
        if (list.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    icon = Icons.Rounded.MusicNote,
                    title = if (selection != null) "Tidak ada lagu" else "Tidak ada hasil",
                    subtitle = if (selection != null) {
                        "Belum ada lagu untuk pilihan ini."
                    } else if (vm.query.isNotBlank()) {
                        "Tidak ditemukan untuk \"${vm.query}\"."
                    } else {
                        "Perpustakaan audio kosong. Izinkan akses audio untuk memindai lagu."
                    }
                )
            }
        } else {
            items(list, key = { it.id }) { track ->
                TrackRow(
                    track = track,
                    isFavorite = track.id in favorites,
                    isCurrent = track.id == currentTrackId,
                    onClick = {
                        vm.play(list, list.indexOf(track))
                        onOpenPlayer()
                    },
                    onFavorite = { vm.toggleFavorite(track.id) },
                    onOpenStudio = { onOpenStudio(track) },
                    onPlayNext = { onPlayNext(track) },
                    onAddToQueue = { onAddToQueue(track) }
                )
            }
        }
    }
}

@Composable
private fun EmptyState(icon: ImageVector, title: String, subtitle: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun AlbumsEmpty() {
    Column(
        modifier = Modifier.fillMaxSize().padding(vertical = 48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Rounded.LibraryMusic,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
        Spacer(Modifier.height(12.dp))
        Text("Tidak ada album", style = MaterialTheme.typography.titleMedium)
        Text(
            "Perpustakaan kosong atau belum ada izin audio.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun AlbumsTab(vm: LibraryViewModel) {
    val albums = vm.albums
    if (albums.isEmpty()) {
        AlbumsEmpty()
        return
    }
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
    if (artists.isEmpty()) {
        EmptyState(Icons.Rounded.Person, "Tidak ada artis", "Perpustakaan kosong atau belum ada izin audio.")
        return
    }
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
    isCurrent: Boolean,
    onClick: () -> Unit,
    onFavorite: () -> Unit,
    onOpenStudio: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AlbumArt(
            albumId = track.albumId,
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))
        )
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isCurrent) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = "Sedang Diputar",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
                Text(
                    track.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            }
            Text(
                if (isCurrent) "${track.artist} • Sedang diputar"
                else "${track.artist} • ${TimeFormat.formatDuration(track.durationMs)}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        IconButton(onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        }) {
            Icon(
                imageVector = Icons.Rounded.PlayCircle,
                contentDescription = "Putar",
                tint = MaterialTheme.colorScheme.primary
            )
        }
        IconButton(onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onFavorite()
        }) {
            Icon(
                imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = "Favorit",
                tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "Lainnya",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Putar Berikutnya") },
                    onClick = {
                        menuOpen = false
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onPlayNext()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Tambahkan ke Antrean") },
                    onClick = {
                        menuOpen = false
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onAddToQueue()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Buka di Studio (Offline)") },
                    onClick = {
                        menuOpen = false
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onOpenStudio()
                    }
                )
            }
        }
    }
}
