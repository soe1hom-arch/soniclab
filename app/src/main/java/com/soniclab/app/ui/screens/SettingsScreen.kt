package com.soniclab.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.OfflineBolt
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.soniclab.app.BuildConfig
import com.soniclab.app.R
import com.soniclab.app.di.AppContainer
import com.soniclab.app.ui.common.appViewModel
import com.soniclab.app.ui.theme.CyanAccent
import com.soniclab.app.ui.theme.PurpleAccent

@Composable
fun SettingsScreen(container: AppContainer) {
    val vm: SettingsViewModel = appViewModel { SettingsViewModel(it) }
    val amoled by vm.amoled.collectAsStateWithLifecycle(initialValue = false)
    val crossfade by vm.crossfadeSeconds.collectAsStateWithLifecycle(initialValue = 0)
    val sleepTimer by vm.sleepTimerMinutes.collectAsStateWithLifecycle(initialValue = 0)
    val aiEnhance by vm.aiEnhanceEnabled.collectAsStateWithLifecycle(initialValue = false)
    val autoNormalize by vm.autoNormalizeEnabled.collectAsStateWithLifecycle(initialValue = false)
    var showAbout by remember { mutableStateOf(false) }

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
                .clickable { showAbout = true }
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

    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Tutup") }
        },
        title = { Text("Tentang Aplikasi") },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .heightIn(max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF140A2E), Color(0xFF3D1B96), PurpleAccent)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_launcher_foreground),
                            contentDescription = null,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                    Column(Modifier.padding(start = 14.dp)) {
                        Text("SonicLab", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Versi ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    "Pemutar musik & toolkit audio premium, sepenuhnya offline. " +
                        "Tanpa iklan, tanpa akun, tanpa internet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider()

                Text("Fitur Unggulan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                AboutRow(Icons.Rounded.GraphicEq, "DSP real-time", "Equalizer, 3D/8D, reverb, pitch & balance")
                AboutRow(Icons.Rounded.AutoAwesome, "AI on-device", "Enhancer & pemisah vokal tanpa internet")
                AboutRow(Icons.Rounded.OfflineBolt, "Fokus privasi", "Semua proses di perangkat, tanpa data dikirim")

                HorizontalDivider()

                Text("Informasi Aplikasi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                AboutRow(Icons.Rounded.Description, "Lisensi", "Apache-2.0 — open source")
                AboutRow(
                    Icons.Rounded.Public,
                    "Kode sumber",
                    "github.com/soe1hom-arch/soniclab",
                    onClick = { openUrl(context, GITHUB_URL) }
                )

                HorizontalDivider()

                Text("Tentang Developer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                AboutRow(Icons.Rounded.Person, "soe1hom-arch", "Developer Android — Kotlin, Compose, Media3")
                Text(
                    "Proyek pribadi yang dibuat dengan fokus pada kualitas suara, privasi, " +
                        "dan pengalaman premium. Umpan balik sangat dihargai — silakan buka " +
                        "issue di repositori GitHub.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

private const val GITHUB_URL = "https://github.com/soe1hom-arch/soniclab"

@Composable
private fun AboutRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null
) {
    val content: @Composable RowScope.() -> Unit = {
        Icon(icon, contentDescription = null, tint = PurpleAccent, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (onClick != null) {
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    if (onClick != null) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    } else {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: Exception) {
        // No browser available; ignore silently.
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
