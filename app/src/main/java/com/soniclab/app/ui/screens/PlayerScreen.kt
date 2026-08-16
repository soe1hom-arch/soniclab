package com.soniclab.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.soniclab.app.di.AppContainer
import com.soniclab.app.ui.common.AlbumArt
import com.soniclab.app.ui.common.appViewModel
import com.soniclab.core.util.TimeFormat
import com.soniclab.player.SpatialAudioProcessor
import com.soniclab.visualizer.SpectrumVisualizer
import kotlin.math.max

@Composable
fun PlayerScreen(container: AppContainer, onOpenEqualizer: () -> Unit, onOpenStudio: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val vm: PlayerViewModel = appViewModel { PlayerViewModel(it) }
    val state by vm.uiState.collectAsStateWithLifecycle()
    val spectrum by vm.spectrum.collectAsStateWithLifecycle()
    val spatialMode = vm.spatialMode

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))

        AlbumArt(
            albumId = state.currentTrack?.albumId ?: -1L,
            modifier = Modifier.size(240.dp).clip(RoundedCornerShape(28.dp))
        )

        Spacer(Modifier.height(24.dp))
        Text(
            state.currentTrack?.title ?: "Tidak Ada yang Diputar",
            style = MaterialTheme.typography.headlineMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            state.currentTrack?.artist ?: "—",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            state.currentTrack?.album ?: "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )

        Spacer(Modifier.height(24.dp))
        SpectrumVisualizer(buckets = spectrum)

        Slider(
            value = state.positionMs.toFloat(),
            onValueChange = { vm.seekTo(it.toLong()) },
            valueRange = 0f..max(state.durationMs.toFloat(), 1f)
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(TimeFormat.formatDuration(state.positionMs), style = MaterialTheme.typography.labelMedium)
            Text(TimeFormat.formatDuration(state.durationMs), style = MaterialTheme.typography.labelMedium)
        }

        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = { vm.setShuffle(!state.shuffleEnabled) }) {
                Icon(
                    Icons.Rounded.Shuffle,
                    contentDescription = "Acak",
                    tint = if (state.shuffleEnabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { vm.previous() }) {
                Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(40.dp))
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
                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(44.dp)
                )
            }
            IconButton(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                vm.next()
            }) {
                Icon(Icons.Rounded.SkipNext, contentDescription = "Berikutnya", modifier = Modifier.size(40.dp))
            }
            IconButton(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                vm.cycleRepeat()
            }) {
                Icon(
                    Icons.Rounded.Repeat,
                    contentDescription = "Ulangi",
                    tint = if (state.repeatMode != 0) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
                OutlinedButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        vm.setSpeed(speed)
                    },
                    modifier = Modifier.height(36.dp)
                ) {
                    Text(
                        if (speed == 1f) "1x" else "${speed}x",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (state.playbackSpeed == speed) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onOpenEqualizer) {
            Icon(Icons.Rounded.Equalizer, contentDescription = null)
            Text(" Equalizer & Prasetel")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onOpenStudio) {
            Icon(Icons.Rounded.GraphicEq, contentDescription = null)
            Text(" Studio: Analisis & DSP")
        }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                SpatialAudioProcessor.MODE_OFF to "Mati",
                SpatialAudioProcessor.MODE_3D to "3D",
                SpatialAudioProcessor.MODE_8D to "8D",
                SpatialAudioProcessor.MODE_3D_8D to "8D+Tengah"
            ).forEach { (mode, label) ->
                FilterChip(
                    selected = spatialMode == mode,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        vm.selectSpatialMode(mode)
                    },
                    label = { Text(label) }
                )
            }
        }

        val currentTrack = state.currentTrack
        val currentIndex = state.queue.indexOfFirst { it.id == currentTrack?.id }
        val upcoming = if (currentTrack != null && currentIndex >= 0) state.queue.drop(currentIndex + 1) else emptyList()
        if (upcoming.isNotEmpty()) {
            Text(
                "Antrean Berikutnya (${upcoming.size})",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(Modifier.height(4.dp))
            upcoming.forEachIndexed { listIndex, track ->
                val absoluteIndex = currentIndex + 1 + listIndex
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { vm.playQueueAt(absoluteIndex) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text(
                            track.title,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            track.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    IconButton(onClick = { vm.moveQueueItem(absoluteIndex, absoluteIndex - 1) }, enabled = absoluteIndex > currentIndex + 1) {
                        Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Naik")
                    }
                    IconButton(
                        onClick = { vm.moveQueueItem(absoluteIndex, absoluteIndex + 1) },
                        enabled = absoluteIndex < state.queue.lastIndex
                    ) {
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Turun")
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
