/*
 * Copyright 2026 soe1hom-arch
 * SPDX-License-Identifier: Apache-2.0
 */

package com.soniclab.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.soniclab.app.BuildConfig
import com.soniclab.app.di.AppContainer
import com.soniclab.app.ui.common.appViewModel
import com.soniclab.app.ui.theme.CyanAccent
import com.soniclab.app.ui.theme.PurpleAccent

@Composable
fun SettingsScreen(container: AppContainer, onOpenAbout: () -> Unit, onOpenEqualizer: () -> Unit) {
    val vm: SettingsViewModel = appViewModel { SettingsViewModel(it) }
    val amoled by vm.amoled.collectAsStateWithLifecycle(initialValue = false)
    val crossfade by vm.crossfadeSeconds.collectAsStateWithLifecycle(initialValue = 0)
    val sleepTimer by vm.sleepTimerMinutes.collectAsStateWithLifecycle(initialValue = 0)
    val aiEnhance by vm.aiEnhanceEnabled.collectAsStateWithLifecycle(initialValue = false)
    val autoNormalize by vm.autoNormalizeEnabled.collectAsStateWithLifecycle(initialValue = false)
    val directOutput by vm.directOutputEnabled.collectAsStateWithLifecycle(initialValue = false)
    val hiRes by vm.hiResOutputEnabled.collectAsStateWithLifecycle(initialValue = false)
    val dither by vm.ditherEnabled.collectAsStateWithLifecycle(initialValue = true)
    val headroom by vm.headroomDb.collectAsStateWithLifecycle(initialValue = 0f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Pengaturan", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(bottom = 16.dp))

        ToggleRow(
            icon = { Icon(Icons.Rounded.DarkMode, contentDescription = null) },
            title = "Tema AMOLED",
            subtitle = "Hitam pekat untuk layar OLED",
            checked = amoled,
            onCheckedChange = vm::setAmoled
        )

        ToggleRow(
            icon = { Icon(Icons.Rounded.AutoAwesome, contentDescription = null) },
            title = "AI Enhance",
            subtitle = "Penyempurnaan suara real-time saat playback (on-device)",
            checked = aiEnhance,
            onCheckedChange = vm::setAiEnhance
        )

        ToggleRow(
            icon = { Icon(Icons.Rounded.AutoAwesome, contentDescription = null) },
            title = "Auto Normalisasi (ReplayGain)",
            subtitle = "Samakan volume antar-lagu ke -14 LUFS (pengukuran EBU R128)",
            checked = autoNormalize,
            onCheckedChange = vm::setAutoNormalize
        )

        ToggleRow(
            icon = { Icon(Icons.Rounded.GraphicEq, contentDescription = null) },
            title = "Mode Langsung (Direct)",
            subtitle = "Lewati semua efek DSP untuk jalur output paling bersih (semua efek dimatikan)",
            checked = directOutput,
            onCheckedChange = vm::setDirectOutput
        )

        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onOpenEqualizer)
                .padding(vertical = 12.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = CyanAccent)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text("Equalizer & Efek Suara", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Bass, 10-band EQ, 3D/8D, reverb, tone & balance",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Output Audio", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Kualitas jalur keluaran sebelum dan setelah DSP",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                // Hi-res float output only works in Direct mode: media3's
                // float-output pipeline drops the custom DSP chain, so enabling
                // it while effects are active would silently disable every toggle.
                if (directOutput) {
                    ToggleRow(
                        icon = { Icon(Icons.Rounded.HighQuality, contentDescription = null) },
                        title = "Output Hi-Res 24-bit",
                        subtitle = "Jalur float 24-bit untuk Mode Langsung (tanpa DSP)",
                        checked = hiRes,
                        onCheckedChange = vm::setHiResOutput
                    )
                }
                ToggleRow(
                    icon = { Icon(Icons.Rounded.GraphicEq, contentDescription = null) },
                    title = "Dither TPDF + Noise Shaping",
                    subtitle = "Hilangkan artifact kuantisasi 16-bit saat efek aktif",
                    checked = dither,
                    onCheckedChange = vm::setDither
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Speed, contentDescription = null)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text("Headroom", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Ruang aman sebelum efek; kurangi level agar tidak pecah",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        if (headroom <= -0.05f) String.format("%.0f dB", headroom) else "0 dB",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = headroom,
                    onValueChange = vm::setHeadroom,
                    valueRange = -3f..0f
                )
            }
        }

        Text(
            text = "Enhance berjalan on-device: ${container.enhancer.displayName}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        Spacer(Modifier.height(16.dp))

        Text("Crossfade", style = MaterialTheme.typography.titleMedium)
        Slider(
            value = crossfade.toFloat(),
            onValueChange = { vm.setCrossfade(it.toInt()) },
            valueRange = 0f..12f
        )
        Text(
            if (crossfade == 0) "Off" else "$crossfade detik",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(16.dp))

        Text("Sleep Timer", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0 to "Mati", 15 to "15m", 30 to "30m", 60 to "60m").forEach { (minutes, label) ->
                FilterChip(
                    selected = sleepTimer == minutes,
                    onClick = { vm.setSleepTimer(minutes) },
                    label = { Text(label) }
                )
            }
        }
        if (sleepTimer > 0) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Icon(Icons.Rounded.Timer, contentDescription = null)
                Text(" Playback berhenti otomatis dalam $sleepTimer menit")
            }
        }

        Spacer(Modifier.height(24.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onOpenAbout)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Info, contentDescription = null, tint = PurpleAccent)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text("Tentang Aplikasi & Developer", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Versi, fitur, dan informasi pengembang",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "SonicLab v${BuildConfig.VERSION_NAME} — fully offline",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

}

@Composable
private fun ToggleRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
