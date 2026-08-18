package com.soniclab.app.ui.screens

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soniclab.analyzer.AudioInfoAnalyzer
import com.soniclab.analyzer.R128Meter
import com.soniclab.app.di.AppContainer
import com.soniclab.toolkit.ToolkitResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

/**
 * Single studio entry for the playback menu: audio toolkit (convert/cut/join/
 * reverse/pitch/tempo/normalize/vocal-remover) + analyzer (info/waveform/LUFS),
 * both bound to the currently playing track.
 */
class StudioViewModel(private val container: AppContainer) : ViewModel() {

    /** Hasil tool yang baru selesai; ditampilkan sebagai kartu dengan aksi. */
    data class StudioResult(val title: String, val path: String)

    var pickedUri by mutableStateOf<Uri?>(null)
        private set
    var fileName by mutableStateOf<String?>(null)
        private set
    var pickedUri2 by mutableStateOf<Uri?>(null)
        private set
    var fileName2 by mutableStateOf<String?>(null)
        private set
    var pitchSemitones by mutableFloatStateOf(0f)
        private set
    var tempoFactor by mutableFloatStateOf(1f)
        private set
    var working by mutableStateOf(false)
        private set
    var progress by mutableStateOf(0f)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var lastResult by mutableStateOf<StudioResult?>(null)
        private set

    var analyzing by mutableStateOf(false)
        private set
    var info by mutableStateOf<AudioInfoAnalyzer.AudioInfo?>(null)
        private set
    var waveform by mutableStateOf<List<Pair<Float, Float>>>(emptyList())
        private set
    var lufs by mutableStateOf(0f)
        private set
    var sourceLabel by mutableStateOf<String?>(null)
        private set

    val playerState = container.playerController.uiState

    fun useCurrentTrack() {
        val track = playerState.value.currentTrack ?: return
        pickedUri = track.uri
        fileName = track.title
        pickedUri2 = null
        fileName2 = null
        message = null
        lastResult = null
    }

    fun onPick(uri: Uri?, displayName: String?) {
        pickedUri = uri
        fileName = displayName
        message = null
        lastResult = null
        info = null
        waveform = emptyList()
        lufs = 0f
        sourceLabel = null
    }

    fun onPickSecond(uri: Uri?, displayName: String?) {
        pickedUri2 = uri
        fileName2 = displayName
        message = null
        lastResult = null
    }

    fun dismissResult() {
        lastResult = null
    }

    fun clearMessageIfCurrent(text: String) {
        if (message == text) message = null
    }

    fun playResult() {
        val r = lastResult ?: return
        container.playerController.playSingleTrack(Uri.fromFile(File(r.path)), r.title)
    }

    fun shareResult() {
        val r = lastResult ?: return
        val context = container.appContext
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", File(r.path))
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "audio/wav"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(send, "Bagikan hasil SonicLab").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun saveToDownloads() {
        val r = lastResult ?: return
        viewModelScope.launch(Dispatchers.IO) {
            message = saveToDownloadsBlocking(r)
        }
    }

    private fun saveToDownloadsBlocking(r: StudioResult): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return "Simpan otomatis butuh Android 10+ — gunakan Bagikan untuk menyimpannya."
        }
        return runCatching {
            val resolver = container.appContext.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, r.title + ".wav")
                put(MediaStore.MediaColumns.MIME_TYPE, "audio/wav")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/SonicLab")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("Gagal membuat file di MediaStore")
            resolver.openOutputStream(uri)?.use { out ->
                File(r.path).inputStream().use { it.copyTo(out) }
            } ?: error("Gagal menulis file")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            "Tersimpan di Download/SonicLab/${r.title}.wav"
        }.getOrElse { "Gagal menyimpan: ${it.message}" }
    }

    fun analyzeCurrent() {
        val track = playerState.value.currentTrack ?: return
        analyze(track.uri, track.title)
    }

    fun analyzeFile() {
        val uri = pickedUri ?: return
        analyze(uri, fileName)
    }

    fun analyze(uri: Uri, label: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            analyzing = true
            sourceLabel = label
            info = container.audioInfoAnalyzer.analyze(uri)
            waveform = container.waveformAnalyzer.analyze(uri, 400) ?: emptyList()
            lufs = computeLufs(container, uri)
            analyzing = false
        }
    }

    fun getInfo() {
        val uri = pickedUri ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val result = container.toolkit.getInfo(uri)
            message = when (result) {
                is ToolkitResult.Info -> {
                    val info = result.info
                    "Codec: ${info.codec} • ${info.bitrateKbps} kbps • ${info.sampleRateHz} Hz • ${info.channelCount} ch • ${info.durationMs / 1000}s"
                }
                is ToolkitResult.Failure -> "Gagal: ${result.message}"
                is ToolkitResult.Success -> "Selesai"
            }
        }
    }

    fun convertToWav() {
        runTool("Konversi ke WAV") { uri, output, onProgress ->
            container.toolkit.convertToWav(uri, output, onProgress)
        }
    }

    fun cut() {
        runTool("Cut (30 detik pertama)") { uri, output, onProgress ->
            container.toolkit.cut(uri, 0L, 30_000L, output, onProgress)
        }
    }

    fun join() {
        val uri1 = pickedUri ?: return
        val uri2 = pickedUri2 ?: return
        launchTool("Join (File 1 + File 2)") { output, onProgress ->
            container.toolkit.join(listOf(uri1, uri2), output, onProgress)
        }
    }

    fun normalize() {
        runTool("Normalisasi (−14 LUFS)") { uri, output, onProgress ->
            container.toolkit.normalize(uri, output, targetLufs = -14f, onProgress = onProgress)
        }
    }

    fun reverse() {
        runTool("Reverse") { uri, output, onProgress ->
            container.toolkit.reverse(uri, output, onProgress)
        }
    }

    fun setPitch(value: Float) {
        pitchSemitones = value.coerceIn(-12f, 12f)
    }

    fun setTempo(value: Float) {
        tempoFactor = value.coerceIn(0.5f, 2f)
    }

    fun changePitch() {
        runTool("Pitch ${String.format(Locale.US, "%+.0f st", pitchSemitones)}") { uri, output, onProgress ->
            container.toolkit.changePitch(uri, pitchSemitones, output, onProgress = onProgress)
        }
    }

    fun changeTempo() {
        runTool("Tempo ${String.format(Locale.US, "%.2f×", tempoFactor)}") { uri, output, onProgress ->
            container.toolkit.changeTempo(uri, tempoFactor, output, onProgress = onProgress)
        }
    }

    fun vocalReduction() {
        runTool("Vocal Remover (instrumental)") { uri, output, onProgress ->
            container.toolkit.vocalReduction(uri, output, onProgress)
        }
    }

    private fun runTool(title: String, block: suspend (Uri, String, (Float) -> Unit) -> ToolkitResult) {
        val uri = pickedUri ?: return
        launchTool(title) { output, onProgress -> block(uri, output, onProgress) }
    }

    private fun launchTool(title: String, block: suspend (String, (Float) -> Unit) -> ToolkitResult) {
        viewModelScope.launch(Dispatchers.IO) {
            val output = File(container.appContext.filesDir, outputFileName(title)).absolutePath
            message = finishTool(runWithProgress { block(output, it) }, title)
        }
    }

    private fun outputFileName(title: String): String {
        val base = title.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .take(24)
        return "${base}_${System.currentTimeMillis()}.wav"
    }

    private fun finishTool(result: ToolkitResult, title: String): String? = when (result) {
        is ToolkitResult.Success -> {
            lastResult = StudioResult(title, result.outputPath)
            null
        }
        is ToolkitResult.Failure -> "Gagal: ${result.message}"
        is ToolkitResult.Info -> "OK"
    }

    private suspend fun runWithProgress(block: suspend (onProgress: (Float) -> Unit) -> ToolkitResult): ToolkitResult {
        working = true
        progress = 0f
        return try {
            block { progress = it }
        } finally {
            working = false
        }
    }

    private fun computeLufs(container: AppContainer, uri: Uri): Float {
        val decoded = container.analyzerDecoder.decode(uri, maxSeconds = 180) ?: return -70f
        // PcmReader decodes to a mono downmix; measure it as one channel.
        val meter = R128Meter(decoded.sampleRate, channels = 1)
        val step = 8192
        var i = 0
        while (i < decoded.samples.size) {
            val end = (i + step).coerceAtMost(decoded.samples.size)
            meter.push(decoded.samples.copyOfRange(i, end))
            i = end
        }
        return meter.integratedLufs()
    }
}
