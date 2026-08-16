package com.soniclab.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "soniclab_settings")

/**
 * App preferences backed by DataStore. All values are local and offline.
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val ACTIVE_PRESET_ID = stringPreferencesKey("active_preset_id")
        val SHUFFLE_ENABLED = booleanPreferencesKey("shuffle_enabled")
        val REPEAT_MODE = intPreferencesKey("repeat_mode")
        val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        val CROSSFADE_SECONDS = intPreferencesKey("crossfade_seconds")
        val SLEEP_TIMER_MINUTES = intPreferencesKey("sleep_timer_minutes")
        val AMOLED_THEME = booleanPreferencesKey("amoled_theme")
        val AI_ENHANCE_ENABLED = booleanPreferencesKey("ai_enhance_enabled")
        val AUTO_NORMALIZE_ENABLED = booleanPreferencesKey("auto_normalize_enabled")
    }

    val activePresetId: Flow<String> = context.dataStore.data.map { it[Keys.ACTIVE_PRESET_ID] ?: "none" }
    val shuffleEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.SHUFFLE_ENABLED] ?: false }
    val repeatMode: Flow<Int> = context.dataStore.data.map { it[Keys.REPEAT_MODE] ?: 0 }
    val playbackSpeed: Flow<Float> = context.dataStore.data.map { it[Keys.PLAYBACK_SPEED] ?: 1f }
    val crossfadeSeconds: Flow<Int> = context.dataStore.data.map { it[Keys.CROSSFADE_SECONDS] ?: 0 }
    val sleepTimerMinutes: Flow<Int> = context.dataStore.data.map { it[Keys.SLEEP_TIMER_MINUTES] ?: 0 }
    val amoledTheme: Flow<Boolean> = context.dataStore.data.map { it[Keys.AMOLED_THEME] ?: false }
    val aiEnhanceEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.AI_ENHANCE_ENABLED] ?: false }
    val autoNormalizeEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.AUTO_NORMALIZE_ENABLED] ?: false }

    suspend fun setActivePresetId(id: String) {
        context.dataStore.edit { it[Keys.ACTIVE_PRESET_ID] = id }
    }

    suspend fun setShuffleEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SHUFFLE_ENABLED] = enabled }
    }

    suspend fun setRepeatMode(mode: Int) {
        context.dataStore.edit { it[Keys.REPEAT_MODE] = mode }
    }

    suspend fun setPlaybackSpeed(speed: Float) {
        context.dataStore.edit { it[Keys.PLAYBACK_SPEED] = speed }
    }

    suspend fun setCrossfadeSeconds(seconds: Int) {
        context.dataStore.edit { it[Keys.CROSSFADE_SECONDS] = seconds }
    }

    suspend fun setSleepTimerMinutes(minutes: Int) {
        context.dataStore.edit { it[Keys.SLEEP_TIMER_MINUTES] = minutes }
    }

    suspend fun setAmoledTheme(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AMOLED_THEME] = enabled }
    }

    suspend fun setAiEnhanceEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AI_ENHANCE_ENABLED] = enabled }
    }

    suspend fun setAutoNormalizeEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_NORMALIZE_ENABLED] = enabled }
    }
}
