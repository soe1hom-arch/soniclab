/*
 * Copyright 2026 soe1hom-arch
 * SPDX-License-Identifier: Apache-2.0
 */

package com.soniclab.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.soniclab.app.di.AppContainer
import com.soniclab.app.ui.common.AlbumArt
import com.soniclab.app.ui.common.SonicCard
import com.soniclab.app.ui.common.appViewModel
import com.soniclab.core.util.TimeFormat
import com.soniclab.player.SpatialAudioProcessor
import com.soniclab.visualizer.SpectrumVisualizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

@Composable
fun PlayerScreen(container: AppContainer, onOpenEqualizer: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val vm: PlayerViewModel = appViewModel { PlayerViewModel(it) }
    val state by vm.uiState.collectAsStateWithLifecycle()
    val spectrum by vm.spectrum.collectAsStateWithLifecycle()
    val spatialMode = vm.spatialMode
    var queueExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "SEDANG DIPUTAR",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = MaterialTheme.typography.labelSmall.letterSpacing * 1.4f
            )
            IconButton(onClick = onOpenEqualizer) {
                Icon(Icons.Rounded.Equalizer, contentDescription = "Equalizer", tint = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.height(8.dp))
        Surface(
            modifier = Modifier.size(264.dp),
            shape = RoundedCornerShape(30.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
            tonalElevation = 2.dp
        ) {
            AlbumArt(
                albumId = state.currentTrack?.albumId ?: -1L,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(30.dp))
            )
        }
        Spacer(Modifier.height(20.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                state.currentTrack?.title ?: "Tidak Ada yang Diputar",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().height(64.dp)
            )
            Text(
                state.currentTrack?.artist ?: "Pilih lagu dari pustaka Anda",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().height(24.dp)
            )
            Text(
                state.currentTrack?.album.orEmpty().ifBlank { " " },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().height(20.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier.fillMaxWidth().height(40.dp),
            contentAlignment = Alignment.Center
        ) {
            state.audioInfo?.let { info ->
                SonicCard {
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
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        SpectrumVisualizer(buckets = spectrum, modifier = Modifier.height(64.dp))
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
                Icon(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (state.isPlaying) "Jeda" else "Putar", modifier = Modifier.size(44.dp))
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

        if (state.userQueue.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { queueExpanded = !queueExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.QueueMusic, contentDescription = null)
                Text(
                    "Antrean berikutnya · ${state.userQueue.size}",
                    modifier = Modifier.weight(1f).padding(start = 10.dp),
                    textAlign = TextAlign.Start
                )
                Icon(
                    if (queueExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (queueExpanded) "Ciutkan antrean" else "Tampilkan antrean"
                )
            }
        }

        val currentTrack = state.currentTrack
        val currentIndex = state.queue.indexOfFirst { it.id == currentTrack?.id }
        val upcoming = state.userQueue
        if (queueExpanded && upcoming.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            upcoming.forEach { track ->
                val absoluteIndex = if (currentIndex >= 0) {
                    (currentIndex + 1 until state.queue.size).firstOrNull { state.queue[it].id == track.id } ?: -1
                } else -1
                if (absoluteIndex < 0) return@forEach
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { vm.playQueueAt(absoluteIndex) }.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.MusicNote, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text(track.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, color = MaterialTheme.colorScheme.onSurface)
                        Text(track.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    IconButton(onClick = { vm.moveQueueItem(absoluteIndex, absoluteIndex - 1) }, enabled = absoluteIndex > currentIndex + 1) {
                        Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Naik")
                    }
                    IconButton(onClick = { vm.moveQueueItem(absoluteIndex, absoluteIndex + 1) }, enabled = absoluteIndex < state.queue.lastIndex) {
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Turun")
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        SonicCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                        Text("Kecepatan pemutaran", style = MaterialTheme.typography.titleSmall)
                        Text("Atur tempo musik secara langsung", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(String.format(Locale.US, "%.2f×", state.playbackSpeed), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = state.playbackSpeed.coerceIn(0.5f, 2f),
                    onValueChange = { vm.previewSpeed(it) },
                    onValueChangeFinished = { vm.persistSpeed(state.playbackSpeed) },
                    valueRange = 0.5f..2f
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
                        FilterChip(
                            selected = abs(state.playbackSpeed - speed) < 0.01f,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                vm.setSpeed(speed)
                            },
                            label = { Text(String.format(Locale.US, "%.2gx", speed)) }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
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
                    selected = spatialMode == mode,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        vm.selectSpatialMode(mode)
                    },
                    label = { Text(label) }
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}
