package com.soniclab.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.soniclab.app.di.AppContainer
import com.soniclab.app.ui.common.appViewModel
import com.soniclab.player.SpatialAudioProcessor
import java.util.Locale

@Composable
fun EqualizerScreen(container: AppContainer) {
    val haptic = LocalHapticFeedback.current
    val vm: EqualizerViewModel = appViewModel { EqualizerViewModel(it) }
    val gains by vm.bandGains.collectAsStateWithLifecycle()
    val preset by vm.activePreset.collectAsStateWithLifecycle()
    val bass by vm.bassStrength.collectAsStateWithLifecycle()
    val virtualizer by vm.virtualizerStrength.collectAsStateWithLifecycle()
    val playerState by container.playerController.uiState.collectAsStateWithLifecycle()
    val balance = vm.balance
    val spatialMode = vm.spatialMode
    val spatialWidth = vm.spatialWidth
    val rotationSeconds = vm.rotationSeconds

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Equalizer",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                vm.reset()
            }) { Text("Atur Ulang") }
        }

        Text(
            "Semua efek & penyetelan di layar ini langsung diterapkan ke lagu yang sedang diputar (real-time).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (gains.isEmpty()) {
            Text(
                "Putar lagu dulu agar equalizer sistem aktif (terhubung ke audio session player). Treble, Reverb, Room & Pitch di bawah tetap berfungsi tanpa itu.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                items(vm.presets) { p ->
                    FilterChip(
                        selected = preset?.id == p.id,
                        onClick = { vm.applyPreset(p) },
                        label = { Text(p.name) }
                    )
                }
            }
        }

        Text("Mode 3D/8D", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
            FilterChip(
                selected = spatialMode == SpatialAudioProcessor.MODE_OFF,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    vm.selectSpatialMode(SpatialAudioProcessor.MODE_OFF)
                },
                label = { Text("Mati") }
            )
            FilterChip(
                selected = spatialMode == SpatialAudioProcessor.MODE_3D,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    vm.selectSpatialMode(SpatialAudioProcessor.MODE_3D)
                },
                label = { Text("3D") }
            )
            FilterChip(
                selected = spatialMode == SpatialAudioProcessor.MODE_8D,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    vm.selectSpatialMode(SpatialAudioProcessor.MODE_8D)
                },
                label = { Text("8D") }
            )
        }
        when (spatialMode) {
            SpatialAudioProcessor.MODE_3D -> ToneSlider(
                label = "Kekuatan 3D",
                value = spatialWidth,
                valueRange = 0f..1f,
                valueText = "${(spatialWidth * 100).toInt()}%",
                onValueChange = vm::updateSpatialWidth
            )
            SpatialAudioProcessor.MODE_8D -> ToneSlider(
                label = "Kecepatan Putaran",
                value = rotationSeconds,
                valueRange = 4f..60f,
                valueText = "${rotationSeconds.toInt()} dtk/putaran",
                onValueChange = vm::updateRotationSeconds
            )
        }

        Text(
            "Tone & Efek (Real-time)",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp)
        )
        ToneSlider(
            label = "Treble",
            value = vm.trebleDb,
            valueRange = -12f..12f,
            valueText = String.format(Locale.US, "%+d dB", vm.trebleDb.toInt()),
            onValueChange = vm::updateTreble
        )
        ToneSlider(
            label = "Reverb (Room)",
            value = vm.reverbWet,
            valueRange = 0f..1f,
            valueText = "${(vm.reverbWet * 100).toInt()}%",
            onValueChange = vm::updateReverbWet
        )
        ToneSlider(
            label = "Ukuran Ruangan",
            value = vm.reverbRoom,
            valueRange = 0f..1f,
            valueText = "${(vm.reverbRoom * 100).toInt()}%",
            onValueChange = vm::updateReverbRoom
        )
        ToneSlider(
            label = "Pitch (langsung)",
            value = playerState.playbackPitch,
            valueRange = -6f..6f,
            valueText = String.format(Locale.US, "%+.0f st", playerState.playbackPitch),
            onValueChange = vm::updatePitch
        )

        if (gains.isNotEmpty()) {
            Text(
                "Efek Sistem Audio",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
            ToneSlider(
                label = "Bass",
                value = bass.toFloat(),
                valueRange = 0f..1000f,
                valueText = "${bass / 10}%",
                onValueChange = { vm.setBass(it.toInt()) }
            )
            ToneSlider(
                label = "Virtualizer",
                value = virtualizer.toFloat(),
                valueRange = 0f..1000f,
                valueText = "${virtualizer / 10}%",
                onValueChange = { vm.setVirtualizer(it.toInt()) }
            )
            val balanceText = when {
                balance <= -0.05f -> "L ${(balance * -100).toInt()}%"
                balance >= 0.05f -> "R ${(balance * 100).toInt()}%"
                else -> "C"
            }
            ToneSlider(
                label = "Balance",
                value = balance,
                valueRange = -1f..1f,
                valueText = balanceText,
                onValueChange = vm::updateBalance
            )
            Text(
                "Grafik Equalizer",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
            gains.forEachIndexed { band, gainMb ->
                BandSlider(
                    label = formatFreq(container.audioEffects.centerFreqHz(band)),
                    valueMb = gainMb,
                    onValueChange = { vm.setBandGain(band, it.toInt()) }
                )
            }
        }
    }
}

@Composable
private fun BandSlider(label: String, valueMb: Int, onValueChange: (Float) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(end = 8.dp)
        )
        Slider(
            value = valueMb.toFloat(),
            onValueChange = onValueChange,
            valueRange = -1500f..1500f,
            modifier = Modifier.weight(1f)
        )
        Text(
            "%+d dB".format(Locale.US, valueMb / 100),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
private fun ToneSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueText: String,
    onValueChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(end = 8.dp)
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.weight(1f)
        )
        Text(
            valueText,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

private fun formatFreq(hz: Int): String = when {
    hz <= 0 -> "—"
    hz >= 1000 -> String.format(Locale.US, "%.1f kHz", hz / 1000f)
    else -> "$hz Hz"
}
