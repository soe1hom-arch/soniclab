package com.soniclab.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soniclab.app.di.AppContainer
import kotlinx.coroutines.launch

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    val amoled = container.settingsRepository.amoledTheme
    val crossfadeSeconds = container.settingsRepository.crossfadeSeconds
    val sleepTimerMinutes = container.settingsRepository.sleepTimerMinutes
    val aiEnhanceEnabled = container.settingsRepository.aiEnhanceEnabled
    val autoNormalizeEnabled = container.settingsRepository.autoNormalizeEnabled
    val playbackSpeed = container.settingsRepository.playbackSpeed
    val directOutputEnabled = container.settingsRepository.directOutputEnabled
    val hiResOutputEnabled = container.settingsRepository.hiResOutputEnabled
    val ditherEnabled = container.settingsRepository.ditherEnabled
    val headroomDb = container.settingsRepository.headroomDb

    fun setAmoled(enabled: Boolean) {
        viewModelScope.launch { container.settingsRepository.setAmoledTheme(enabled) }
    }

    fun setCrossfade(seconds: Int) {
        viewModelScope.launch { container.settingsRepository.setCrossfadeSeconds(seconds) }
    }

    fun setSleepTimer(minutes: Int) {
        viewModelScope.launch { container.settingsRepository.setSleepTimerMinutes(minutes) }
        // 0 membatalkan timer yang sedang berjalan, >0 memulai/mengganti timer.
        container.playerController.startSleepTimer(minutes)
    }

    fun setAiEnhance(enabled: Boolean) {
        viewModelScope.launch { container.settingsRepository.setAiEnhanceEnabled(enabled) }
    }

    fun setAutoNormalize(enabled: Boolean) {
        viewModelScope.launch { container.settingsRepository.setAutoNormalizeEnabled(enabled) }
    }

    fun setDirectOutput(enabled: Boolean) {
        viewModelScope.launch { container.settingsRepository.setDirectOutputEnabled(enabled) }
    }

    fun setHiResOutput(enabled: Boolean) {
        viewModelScope.launch { container.settingsRepository.setHiResOutputEnabled(enabled) }
    }

    fun setDither(enabled: Boolean) {
        viewModelScope.launch { container.settingsRepository.setDitherEnabled(enabled) }
    }

    fun setHeadroom(db: Float) {
        viewModelScope.launch { container.settingsRepository.setHeadroomDb(db) }
    }
}
