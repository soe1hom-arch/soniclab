package com.soniclab.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMerge
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import android.net.Uri
import android.os.Build
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.soniclab.app.di.AppContainer
import com.soniclab.app.ui.common.appViewModel
import com.soniclab.visualizer.WaveformVisualizer
import java.util.Locale

@Composable
fun StudioScreen(container: AppContainer, uri: String? = null, title: String? = null) {
    val vm: StudioViewModel = appViewModel { StudioViewModel(it) }
    val pickedUri = vm.pickedUri
    val pickedUri2 = vm.pickedUri2
    val working = vm.working
    val progress = vm.progress
    val message = vm.message
    val analyzing = vm.analyzing
    val info = vm.info
    val waveform = vm.waveform
    val lufs = vm.lufs
    val playerState by vm.playerState.collectAsStateWithLifecycle()

    // File 1 diisi dari lagu yang dipilih di Library; fallback ke lagu yang sedang diputar.
    LaunchedEffect(uri, title) {
        if (uri != null) {
            vm.onPick(Uri.parse(uri), title)
        } else {
            vm.useCurrentTrack()
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    // Pesan sementara (mis. hasil simpan) tampil sebagai Snackbar.
    LaunchedEffect(message) {
        val current = message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(current)
        vm.clearMessageIfCurrent(current)
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            val name = it.lastPathSegment?.substringAfterLast(":") ?: it.lastPathSegment
            vm.onPick(it, name)
        }
    }
    val picker2 = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            val name = it.lastPathSegment?.substringAfterLast(":") ?: it.lastPathSegment
            vm.onPickSecond(it, name)
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
        Text("Studio", style = MaterialTheme.typography.headlineLarge)
        Text(
            "Analisis, konversi & DSP — langsung ke lagu yang sedang diputar.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("File Audio", style = MaterialTheme.typography.titleMedium)
                HorizontalDivider()
                NowPlayingRow(
                    currentTitle = playerState.currentTrack?.title,
                    onUse = vm::useCurrentTrack
                )
                FilePickRow(
                    icon = Icons.Rounded.FolderOpen,
                    label = "File 1 (utama)",
                    fileName = vm.fileName,
                    onClick = { picker.launch(arrayOf("audio/*")) }
                )
                FilePickRow(
                    icon = Icons.Rounded.LibraryMusic,
                    label = "File 2 (untuk Join)",
                    fileName = vm.fileName2,
                    onClick = { picker2.launch(arrayOf("audio/*")) }
                )
            }
        }

        if (working) {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        }
        if (analyzing) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.height(24.dp))
                Text(" Menganalisis…", style = MaterialTheme.typography.bodyLarge)
            }
        }
        vm.lastResult?.let { result ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text("Hasil Siap", style = MaterialTheme.typography.titleMedium)
                            Text(
                                result.title,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = { vm.playResult() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.PlayCircle, contentDescription = null)
                            Text(" Mainkan")
                        }
                        FilledTonalButton(
                            onClick = { vm.shareResult() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Share, contentDescription = null)
                            Text(" Bagikan")
                        }
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        OutlinedButton(
                            onClick = { vm.saveToDownloads() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.Save, contentDescription = null)
                            Text(" Simpan ke Download")
                        }
                    }
                    TextButton(
                        onClick = { vm.dismissResult() },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Tutup")
                    }
                }
            }
        }

        SectionCard("Analisis") {
            ToolActionRow(
                enabled = playerState.currentTrack != null && !analyzing,
                icon = Icons.Rounded.PlayCircle,
                title = "Analisis Lagu Saat Ini",
                subtitle = "Info, waveform & loudness (LUFS)",
                onClick = vm::analyzeCurrent
            )
            ToolActionRow(
                enabled = pickedUri != null && !analyzing,
                icon = Icons.Rounded.MusicNote,
                title = "Analisis File 1",
                subtitle = "Analisis file yang sedang dipilih",
                onClick = vm::analyzeFile
            )
        }

        info?.let {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    vm.sourceLabel?.let { label ->
                        Text(
                            label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    InfoRow("Durasi", "${it.durationMs / 1000} s")
                    InfoRow("Bitrate", if (it.bitrateKbps > 0) "${it.bitrateKbps} kbps" else "—")
                    InfoRow("Sample Rate", "${it.sampleRateHz} Hz")
                    InfoRow("Channels", it.channelCount.toString())
                    InfoRow("Codec", it.codec)
                    if (waveform.isNotEmpty()) {
                        Text("Waveform", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
                        WaveformVisualizer(waveform = waveform, modifier = Modifier.fillMaxWidth())
                        Text(
                            String.format(Locale.US, "Integrated Loudness: %.1f LUFS", lufs),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }

        SectionCard("Dekode & Konversi") {
            ToolActionRow(pickedUri != null && !working, Icons.Rounded.Info, "Info File", "Codec, bitrate, sample rate, durasi", vm::getInfo)
            ToolActionRow(pickedUri != null && !working, Icons.Rounded.Autorenew, "Konversi ke WAV", "Transcode via MediaCodec ke PCM WAV", vm::convertToWav)
            ToolActionRow(pickedUri != null && !working, Icons.Rounded.ContentCut, "Cut", "Ekspor 30 detik pertama", vm::cut)
        }

        SectionCard("DSP Engine") {
            ToolActionRow(
                pickedUri != null && pickedUri2 != null && !working,
                Icons.AutoMirrored.Rounded.CallMerge, "Join", "Gabungkan File 1 + File 2", vm::join
            )
            ToolActionRow(pickedUri != null && !working, Icons.Rounded.Replay, "Reverse", "Putar balik audio", vm::reverse)
            ToolSliderRow(
                enabled = pickedUri != null && !working,
                icon = Icons.Rounded.MusicNote,
                title = "Pitch",
                valueText = String.format(Locale.US, "%+.0f st", vm.pitchSemitones),
                subtitle = "Ubah nada (WSOLA), -12..+12 st",
                value = vm.pitchSemitones,
                valueRange = -12f..12f,
                onValueChange = vm::setPitch,
                onRun = vm::changePitch
            )
            ToolSliderRow(
                enabled = pickedUri != null && !working,
                icon = Icons.Rounded.Speed,
                title = "Tempo",
                valueText = String.format(Locale.US, "%.2f×", vm.tempoFactor),
                subtitle = "Ubah kecepatan tanpa ubah nada (WSOLA)",
                value = vm.tempoFactor,
                valueRange = 0.5f..2f,
                onValueChange = vm::setTempo,
                onRun = vm::changeTempo
            )
            ToolActionRow(pickedUri != null && !working, Icons.Rounded.Equalizer, "Normalizer (−14 LUFS)", "Ratakan loudness ke target", vm::normalize)
            ToolActionRow(pickedUri != null && !working, Icons.Rounded.MicOff, "Vocal Remover", "Hilangkan vokal (butuh file stereo)", vm::vocalReduction)
        }
    }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            HorizontalDivider()
            content()
        }
    }
}

@Composable
private fun ToolSliderRow(
    enabled: Boolean,
    icon: ImageVector,
    title: String,
    valueText: String,
    subtitle: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onRun: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    valueText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                enabled = enabled
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Button(onClick = onRun, enabled = enabled) { Text("Proses") }
    }
}

@Composable
private fun NowPlayingRow(currentTitle: String?, onUse: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text("Lagu saat ini", style = MaterialTheme.typography.bodyLarge)
            Text(
                currentTitle ?: "Tidak ada lagu diputar",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        FilledTonalButton(onClick = onUse, enabled = currentTitle != null) { Text("Pakai") }
    }
}

@Composable
private fun FilePickRow(icon: ImageVector, label: String, fileName: String?, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                fileName ?: "Belum dipilih",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        FilledTonalButton(onClick = onClick) { Text("Pilih") }
    }
}

@Composable
private fun ToolActionRow(
    enabled: Boolean,
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Button(onClick = onClick, enabled = enabled) { Text("Proses") }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
