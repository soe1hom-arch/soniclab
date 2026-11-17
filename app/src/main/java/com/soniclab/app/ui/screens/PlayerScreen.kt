/*
 * Copyright 2026 soe1hom-arch
 * SPDX-License-Identifier: Apache-2.0
 */

package com.soniclab.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SurroundSound
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.soniclab.app.di.AppContainer
import com.soniclab.app.ui.common.AlbumArt
import com.soniclab.app.ui.common.appViewModel
import com.soniclab.core.model.Track
import com.soniclab.core.util.TimeFormat
import com.soniclab.player.SpatialAudioProcessor
import com.soniclab.visualizer.SpectrumVisualizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private enum class PlayerSheet { Speed, Spatial, Timer, Queue }

private const val SWIPE_TRACK_THRESHOLD_PX = 120f
private const val SWIPE_CLOSE_THRESHOLD_PX = 240f

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PlayerScreen(container: AppContainer, onOpenEqualizer: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val vm: PlayerViewModel = appViewModel { PlayerViewModel(it) }
    val state by vm.uiState.collectAsStateWithLifecycle()
    val spectrum by vm.spectrum.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val sleepTimer by vm.sleepTimerMinutes.collectAsStateWithLifecycle(initialValue = 0)
    var sheet by remember { mutableStateOf<PlayerSheet?>(null) }

    val track = state.currentTrack
    val isFavorite = track != null && track.id in favorites

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .pointerInput(Unit) {
                var dragY = 0f
                detectVerticalDragGestures(
                    onDragStart = { dragY = 0f },
                    onDragEnd = { if (dragY > SWIPE_CLOSE_THRESHOLD_PX) onClose() },
                    onVerticalDrag = { _, delta -> dragY += delta }
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Tutup pemutar")
            }
            Text(
                "SEDANG DIPUTAR",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            IconButton(
                enabled = track != null,
                onClick = { track?.let { shareTrack(context, it) } }
            ) {
                Icon(Icons.Rounded.Share, contentDescription = "Bagikan")
            }
        }

        // The cover takes whatever height is left, so the controls never move
        // or scroll off-screen regardless of device size.
        BoxWithConstraints(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            val coverSize = min(maxWidth.value, maxHeight.value).coerceAtLeast(120f).dp
            Surface(
                modifier = Modifier
                    .size(coverSize)
                    .pointerInput(Unit) {
                        var dragX = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { dragX = 0f },
                            onDragEnd = {
                                when {
                                    dragX < -SWIPE_TRACK_THRESHOLD_PX -> vm.next()
                                    dragX > SWIPE_TRACK_THRESHOLD_PX -> vm.previous()
                                }
                            },
                            onHorizontalDrag = { _, delta -> dragX += delta }
                        )
                    },
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
                tonalElevation = 2.dp
            ) {
                AlbumArt(
                    albumId = track?.albumId ?: -1L,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp))
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    track?.title ?: "Tidak Ada yang Diputar",
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE)
                )
                Text(
                    track?.artist ?: "Pilih lagu dari pustaka Anda",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    track?.album.orEmpty().ifBlank { " " },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                enabled = track != null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    track?.let { vm.toggleFavorite(it.id) }
                }
            ) {
                Icon(
                    if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = "Favorit",
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Fixed-height slot so the audio info chip appearing/disappearing never shifts the layout.
        Row(
            modifier = Modifier.fillMaxWidth().height(28.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            state.audioInfo?.let { info ->
                Text(
                    buildString {
                        append(info.codec)
                        if (info.sampleRateHz > 0) append(" • ${info.sampleRateHz / 1000f} kHz")
                        append(" • ${info.bitDepth}")
                        if (info.channels > 0) append(" • ${if (info.channels == 1) "Mono" else "Stereo"}")
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        SpectrumVisualizer(buckets = spectrum, modifier = Modifier.height(40.dp))
        Slider(
            value = state.positionMs.toFloat(),
            onValueChange = { vm.seekTo(it.toLong()) },
            valueRange = 0f..max(state.durationMs.toFloat(), 1f)
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(TimeFormat.formatDuration(state.positionMs), style = MaterialTheme.typography.labelMedium)
            Text(TimeFormat.formatDuration(state.durationMs), style = MaterialTheme.typography.labelMedium)
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = { vm.setShuffle(!state.shuffleEnabled) }) {
                Icon(Icons.Rounded.Shuffle, contentDescription = "Acak", tint = if (state.shuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { vm.previous() }) {
                Icon(Icons.Rounded.SkipPrevious, contentDescription = "Sebelumnya", modifier = Modifier.size(40.dp))
            }
            FilledIconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    vm.togglePlayPause()
                },
                modifier = Modifier.size(72.dp)
            ) {
                Icon(
                    if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (state.isPlaying) "Jeda" else "Putar",
                    modifier = Modifier.size(44.dp)
                )
            }
            IconButton(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                vm.next()
            }) {
                Icon(Icons.Rounded.SkipNext, contentDescription = "Berikutnya", modifier = Modifier.size(40.dp))
            }
            IconButton(onClick = { vm.cycleRepeat() }) {
                Icon(Icons.Rounded.Repeat, contentDescription = "Ulangi", tint = if (state.repeatMode != 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ShortcutButton(Icons.Rounded.Equalizer, "Equalizer", false, onOpenEqualizer)
            ShortcutButton(Icons.Rounded.Speed, "Kecepatan", abs(state.playbackSpeed - 1f) > 0.01f) { sheet = PlayerSheet.Speed }
            ShortcutButton(Icons.Rounded.SurroundSound, "Spasial", vm.spatialMode != SpatialAudioProcessor.MODE_OFF) { sheet = PlayerSheet.Spatial }
            ShortcutButton(Icons.Rounded.Timer, "Timer", sleepTimer > 0) { sheet = PlayerSheet.Timer }
            ShortcutButton(Icons.Rounded.QueueMusic, "Antrean", state.userQueue.isNotEmpty()) { sheet = PlayerSheet.Queue }
        }
    }

    sheet?.let { current ->
        ModalBottomSheet(onDismissRequest = { sheet = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp)
            ) {
                when (current) {
                    PlayerSheet.Speed -> SpeedSheet(state.playbackSpeed, vm, haptic::performHapticFeedback)
                    PlayerSheet.Spatial -> SpatialSheet(vm, haptic::performHapticFeedback)
                    PlayerSheet.Timer -> TimerSheet(sleepTimer) { vm.setSleepTimer(it) }
                    PlayerSheet.Queue -> QueueSheet(state.queue, state.userQueue, track, vm)
                }
            }
        }
    }
}

@Composable
private fun ShortcutButton(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    val tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = tint)
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1)
    }
}

@Composable
private fun SpeedSheet(speed: Float, vm: PlayerViewModel, haptic: (HapticFeedbackType) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Kecepatan pemutaran", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text(String.format(Locale.US, "%.2f×", speed), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    }
    Slider(
        value = speed.coerceIn(0.5f, 2f),
        onValueChange = { vm.previewSpeed(it) },
        onValueChangeFinished = { vm.persistSpeed(speed) },
        valueRange = 0.5f..2f
    )
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { preset ->
            FilterChip(
                selected = abs(speed - preset) < 0.01f,
                onClick = {
                    haptic(HapticFeedbackType.TextHandleMove)
                    vm.setSpeed(preset)
                },
                label = { Text(String.format(Locale.US, "%.2gx", preset)) }
            )
        }
    }
}

@Composable
private fun SpatialSheet(vm: PlayerViewModel, haptic: (HapticFeedbackType) -> Unit) {
    Text("Audio spasial", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(
            SpatialAudioProcessor.MODE_OFF to "Mati",
            SpatialAudioProcessor.MODE_3D to "3D",
            SpatialAudioProcessor.MODE_8D to "8D",
            SpatialAudioProcessor.MODE_3D_8D to "8D+Tengah"
        ).forEach { (mode, label) ->
            FilterChip(
                selected = vm.spatialMode == mode,
                onClick = {
                    haptic(HapticFeedbackType.LongPress)
                    vm.selectSpatialMode(mode)
                },
                label = { Text(label) }
            )
        }
    }
}

@Composable
private fun TimerSheet(current: Int, onSelect: (Int) -> Unit) {
    Text("Sleep timer", style = MaterialTheme.typography.titleMedium)
    Text(
        if (current > 0) "Pemutaran berhenti otomatis dalam $current menit" else "Pemutaran tidak dibatasi waktu",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(0 to "Mati", 15 to "15m", 30 to "30m", 60 to "60m").forEach { (minutes, label) ->
            FilterChip(selected = current == minutes, onClick = { onSelect(minutes) }, label = { Text(label) })
        }
    }
}

@Composable
private fun QueueSheet(queue: List<Track>, upcoming: List<Track>, current: Track?, vm: PlayerViewModel) {
    Text("Antrean berikutnya", style = MaterialTheme.typography.titleMedium)
    if (upcoming.isEmpty()) {
        Text(
            "Belum ada lagu yang ditambahkan ke antrean.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 12.dp)
        )
        return
    }
    val currentIndex = queue.indexOfFirst { it.id == current?.id }
    Spacer(Modifier.height(4.dp))
    upcoming.forEach { item ->
        val absoluteIndex = if (currentIndex >= 0) {
            (currentIndex + 1 until queue.size).firstOrNull { queue[it].id == item.id } ?: -1
        } else -1
        if (absoluteIndex < 0) return@forEach
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { vm.playQueueAt(absoluteIndex) }.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.MusicNote, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(item.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { vm.moveQueueItem(absoluteIndex, absoluteIndex - 1) }, enabled = absoluteIndex > currentIndex + 1) {
                Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Naik")
            }
            IconButton(onClick = { vm.moveQueueItem(absoluteIndex, absoluteIndex + 1) }, enabled = absoluteIndex < queue.lastIndex) {
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Turun")
            }
        }
    }
}

// Shares track details as text; the audio file itself is not attached.
private fun shareTrack(context: android.content.Context, track: Track) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "Sedang mendengarkan ${track.title} — ${track.artist} di SonicLab")
    }
    context.startActivity(Intent.createChooser(send, "Bagikan lagu").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
