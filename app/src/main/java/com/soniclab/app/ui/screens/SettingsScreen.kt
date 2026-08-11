package com.soniclab.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.soniclab.app.BuildConfig
import com.soniclab.app.di.AppContainer
import com.soniclab.app.ui.common.appViewModel

@Composable
fun SettingsScreen(container: AppContainer) {
    val vm: SettingsViewModel = appViewModel { SettingsViewModel(it) }
    val amoled by vm.amoled.collectAsStateWithLifecycle(initialValue = false)
    val crossfade by vm.crossfadeSeconds.collectAsStateWithLifecycle(initialValue = 0)
    val sleepTimer by vm.sleepTimerMinutes.collectAsStateWithLifecycle(initialValue = 0)
    val aiEnhance by vm.aiEnhanceEnabled.collectAsStateWithLifecycle(initialValue = false)
    val autoNormalize by vm.autoNormalizeEnabled.collectAsStateWithLifecycle(initialValue = false)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(bottom = 16.dp))

        ToggleRow(
            icon = { Icon(Icons.Rounded.DarkMode, contentDescription = null) },
            title = "AMOLED Theme",
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
            subtitle = "Samakan volume antar-lagu ke -14 LUFS saat playback",
            checked = autoNormalize,
            onCheckedChange = vm::setAutoNormalize
        )

        Text(
            text = if (container.enhancer.isAiModelLoaded) {
                "Model AI aktif: ${container.enhancer.displayName}"
            } else {
                "Model AI belum dibundel — pakai Classic DSP fallback (offline)"
            },
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
        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            listOf(0 to "Off", 15 to "15m", 30 to "30m", 60 to "60m").forEach { (minutes, label) ->
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
