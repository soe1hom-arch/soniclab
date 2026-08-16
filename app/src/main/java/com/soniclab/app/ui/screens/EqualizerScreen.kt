package com.soniclab.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.SettingsInputComponent
import androidx.compose.material.icons.rounded.SurroundSound
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.soniclab.app.di.AppContainer
import com.soniclab.app.ui.common.appViewModel
import com.soniclab.player.SpatialAudioProcessor
import java.util.Locale

@Composable
fun EqualizerScreen(container: AppContainer, onOpenStudio: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val vm: EqualizerViewModel = appViewModel { EqualizerViewModel(it) }
    val gains by vm.bandGains.collectAsStateWithLifecycle()
    val preset by vm.activePreset.collectAsStateWithLifecycle()
    val bass by vm.bassStrength.collectAsStateWithLifecycle()
    val virtualizer by vm.virtualizerStrength.collectAsStateWithLifecycle()
    val playerState by container.playerController.uiState.collectAsStateWithLifecycle()
    val balance = vm.balance
    val spatialMode = vm.spatialMode
    val spatial3d = vm.spatial3d
    val spatial8d = vm.spatial8d
    val surround = vm.surround
    val spatialWidth = vm.spatialWidth
    val rotationSeconds = vm.rotationSeconds
    val panDepth = vm.panDepth

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
            "Semua efek & penyetelan langsung diterapkan ke lagu yang sedang diputar (real-time). Ketuk menu untuk membuka penyetelannya.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        SettingSection(
            title = "Prasetel Equalizer",
            subtitle = "Prasetel suara, band EQ, bass & balance",
            icon = Icons.Rounded.Tune,
            initiallyExpanded = true
        ) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                items(vm.presets) { p ->
                    FilterChip(
                        selected = preset?.id == p.id,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            vm.applyPreset(p)
                        },
                        label = { Text(p.name) }
                    )
                }
            }
            if (gains.isEmpty()) {
                Text(
                    "Putar lagu dulu agar equalizer sistem aktif (terhubung ke audio session player).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                )
            } else {
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

        SettingSection(
            title = "Mode 3D / 8D",
            subtitle = "Efek ruang: pilih prasetel atau racik sendiri 3D, 8D & Surround",
            icon = Icons.Rounded.SurroundSound,
            initiallyExpanded = true
        ) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                items(
                    listOf(
                        SpatialAudioProcessor.MODE_OFF to "Mati",
                        SpatialAudioProcessor.MODE_3D to "3D",
                        SpatialAudioProcessor.MODE_8D to "8D",
                        SpatialAudioProcessor.MODE_3D_8D to "8D + Tengah",
                        SpatialAudioProcessor.MODE_SURROUND to "Surround"
                    )
                ) { (mode, label) ->
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

            Text(
                "Racik sendiri (kombinasi bebas 3D, 8D & Surround):",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text("3D (lebar stereo)", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Switch(checked = spatial3d, onCheckedChange = vm::set3dEnabled)
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text("8D (putaran kiri-kanan)", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Switch(checked = spatial8d, onCheckedChange = vm::set8dEnabled)
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text("Surround (gema ruang)", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Switch(checked = surround, onCheckedChange = vm::setSurroundEnabled)
            }

            if (spatial3d) {
                ToneSlider(
                    label = "Kekuatan 3D",
                    value = spatialWidth,
                    valueRange = 0f..1f,
                    valueText = "${(spatialWidth * 100).toInt()}%",
                    onValueChange = vm::updateSpatialWidth
                )
            }
            if (spatial8d) {
                ToneSlider(
                    label = "Kecepatan Putaran",
                    value = rotationSeconds,
                    valueRange = 4f..60f,
                    valueText = "${rotationSeconds.toInt()} dtk/putaran",
                    onValueChange = vm::updateRotationSeconds
                )
                ToneSlider(
                    label = "Kedalaman Pan 8D",
                    value = panDepth,
                    valueRange = 0.1f..1f,
                    valueText = "${(panDepth * 100).toInt()}%",
                    onValueChange = vm::updatePanDepth
                )
            }
        }

        SettingSection(
            title = "Tone & Efek",
            subtitle = "Treble, reverb, ukuran ruangan & pitch langsung",
            icon = Icons.Rounded.GraphicEq,
            initiallyExpanded = false
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text("Limiter (anti-pecah)", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Switch(checked = vm.limiterEnabled, onCheckedChange = vm::setLimiter)
            }
            Text(
                "Mencegah distorsi saat preset boost penuh atau lagu keras.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp)
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
        }

        SettingSection(
            title = "Studio: Analisis & DSP",
            subtitle = "Analyzer, konversi WAV & alat DSP lanjutan",
            icon = Icons.Rounded.SettingsInputComponent,
            initiallyExpanded = false
        ) {
            Text(
                "Analisis lagu (waveform, info file, loudness), vocal remover, enhancer, dan alat konversi/cut tersedia di Studio.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            OutlinedButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onOpenStudio()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.GraphicEq, contentDescription = null)
                Text(" Buka Studio")
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SettingSection(
    title: String,
    subtitle: String,
    icon: ImageVector,
    initiallyExpanded: Boolean,
    content: @Composable () -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = if (expanded) "Tutup" else "Buka",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(top = 4.dp)) { content() }
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
