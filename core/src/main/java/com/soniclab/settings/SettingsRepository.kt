package com.soniclab.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "soniclab_settings")

/** Snapshot of every effect/playback setting, restored once at app start. */
data class EffectSettings(
    val spatialMode: Int = 0,
    val spatial3d: Boolean = false,
    val spatial8d: Boolean = false,
    val surround: Boolean = false,
    val spatialWidth: Float = 0.6f,
    val rotationSeconds: Float = 8f,
    val panDepth: Float = 0.6f,
    val balance: Float = 0f,
    val trebleDb: Float = 0f,
    val reverbWet: Float = 0f,
    val reverbRoom: Float = 0.5f,
    val pitchSemitones: Float = 0f,
    val bassStrength: Int = 0,
    val virtualizerStrength: Int = 0,
    val bandGains: Map<Int, Int> = emptyMap(),
    val activePresetId: String = "none",
    val playbackSpeed: Float = 1f,
    val limiterEnabled: Boolean = true,
    val directOutputEnabled: Boolean = false,
    val hiResOutput: Boolean = false,
    val ditherEnabled: Boolean = true,
    val headroomDb: Float = 0f
)

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

        // Effect chain values — persisted so nothing resets when the app is closed.
        val SPATIAL_MODE = intPreferencesKey("spatial_mode")
        val SPATIAL_3D = booleanPreferencesKey("spatial_3d")
        val SPATIAL_8D = booleanPreferencesKey("spatial_8d")
        val SPATIAL_SURROUND = booleanPreferencesKey("spatial_surround")
        val SPATIAL_WIDTH = floatPreferencesKey("spatial_width")
        val SPATIAL_ROTATION = floatPreferencesKey("spatial_rotation")
        val SPATIAL_PAN_DEPTH = floatPreferencesKey("spatial_pan_depth")
        val BALANCE = floatPreferencesKey("balance")
        val TREBLE_DB = floatPreferencesKey("treble_db")
        val REVERB_WET = floatPreferencesKey("reverb_wet")
        val REVERB_ROOM = floatPreferencesKey("reverb_room")
        val PITCH_SEMITONES = floatPreferencesKey("pitch_semitones")
        val BASS_STRENGTH = intPreferencesKey("bass_strength")
        val VIRTUALIZER_STRENGTH = intPreferencesKey("virtualizer_strength")
        val BAND_GAINS = stringPreferencesKey("band_gains")
        val LIMITER_ENABLED = booleanPreferencesKey("limiter_enabled")
        val DIRECT_OUTPUT_ENABLED = booleanPreferencesKey("direct_output_enabled")
        val HI_RES_OUTPUT = booleanPreferencesKey("hi_res_output")
        val DITHER_ENABLED = booleanPreferencesKey("dither_enabled")
        val HEADROOM_DB = floatPreferencesKey("headroom_db")
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
    val limiterEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.LIMITER_ENABLED] ?: true }
    val directOutputEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.DIRECT_OUTPUT_ENABLED] ?: false }
    val hiResOutputEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.HI_RES_OUTPUT] ?: false }
    val ditherEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.DITHER_ENABLED] ?: true }
    val headroomDb: Flow<Float> = context.dataStore.data.map { it[Keys.HEADROOM_DB] ?: 0f }

    /** Reads every effect/replay setting once (used at app startup to restore state). */
    suspend fun loadEffectSettings(): EffectSettings {
        val p = context.dataStore.data.first()
        return EffectSettings(
            spatialMode = p[Keys.SPATIAL_MODE] ?: 0,
            spatial3d = p[Keys.SPATIAL_3D] ?: false,
            spatial8d = p[Keys.SPATIAL_8D] ?: false,
            surround = p[Keys.SPATIAL_SURROUND] ?: false,
            spatialWidth = p[Keys.SPATIAL_WIDTH] ?: 0.6f,
            rotationSeconds = p[Keys.SPATIAL_ROTATION] ?: 8f,
            panDepth = p[Keys.SPATIAL_PAN_DEPTH] ?: 0.6f,
            balance = p[Keys.BALANCE] ?: 0f,
            trebleDb = p[Keys.TREBLE_DB] ?: 0f,
            reverbWet = p[Keys.REVERB_WET] ?: 0f,
            reverbRoom = p[Keys.REVERB_ROOM] ?: 0.5f,
            pitchSemitones = p[Keys.PITCH_SEMITONES] ?: 0f,
            bassStrength = p[Keys.BASS_STRENGTH] ?: 0,
            virtualizerStrength = p[Keys.VIRTUALIZER_STRENGTH] ?: 0,
            bandGains = parseBandGains(p[Keys.BAND_GAINS]),
            activePresetId = p[Keys.ACTIVE_PRESET_ID] ?: "none",
            playbackSpeed = p[Keys.PLAYBACK_SPEED] ?: 1f,
            limiterEnabled = p[Keys.LIMITER_ENABLED] ?: true,
            directOutputEnabled = p[Keys.DIRECT_OUTPUT_ENABLED] ?: false,
            hiResOutput = p[Keys.HI_RES_OUTPUT] ?: false,
            ditherEnabled = p[Keys.DITHER_ENABLED] ?: true,
            headroomDb = p[Keys.HEADROOM_DB] ?: 0f
        )
    }

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

    suspend fun setLimiterEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.LIMITER_ENABLED] = enabled }
    }

    suspend fun setDirectOutputEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.DIRECT_OUTPUT_ENABLED] = value }
    }

    suspend fun setHiResOutputEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.HI_RES_OUTPUT] = enabled }
    }

    suspend fun setDitherEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DITHER_ENABLED] = enabled }
    }

    suspend fun setHeadroomDb(db: Float) {
        context.dataStore.edit { it[Keys.HEADROOM_DB] = db.coerceIn(-3f, 0f) }
    }

    // --- Effect chain persistence (kept across app restarts) ---

    suspend fun setSpatial(mode: Int, spatial3d: Boolean, spatial8d: Boolean, surround: Boolean) {
        context.dataStore.edit {
            it[Keys.SPATIAL_MODE] = mode
            it[Keys.SPATIAL_3D] = spatial3d
            it[Keys.SPATIAL_8D] = spatial8d
            it[Keys.SPATIAL_SURROUND] = surround
        }
    }

    suspend fun setSpatialWidth(value: Float) {
        context.dataStore.edit { it[Keys.SPATIAL_WIDTH] = value }
    }

    suspend fun setRotationSeconds(value: Float) {
        context.dataStore.edit { it[Keys.SPATIAL_ROTATION] = value }
    }

    suspend fun setPanDepth(value: Float) {
        context.dataStore.edit { it[Keys.SPATIAL_PAN_DEPTH] = value }
    }

    suspend fun setBalance(value: Float) {
        context.dataStore.edit { it[Keys.BALANCE] = value }
    }

    suspend fun setTrebleDb(value: Float) {
        context.dataStore.edit { it[Keys.TREBLE_DB] = value }
    }

    suspend fun setReverb(wet: Float, room: Float) {
        context.dataStore.edit {
            it[Keys.REVERB_WET] = wet
            it[Keys.REVERB_ROOM] = room
        }
    }

    suspend fun setPitchSemitones(value: Float) {
        context.dataStore.edit { it[Keys.PITCH_SEMITONES] = value }
    }

    suspend fun setBassStrength(value: Int) {
        context.dataStore.edit { it[Keys.BASS_STRENGTH] = value }
    }

    suspend fun setVirtualizerStrength(value: Int) {
        context.dataStore.edit { it[Keys.VIRTUALIZER_STRENGTH] = value }
    }

    suspend fun setBandGains(gains: Map<Int, Int>) {
        context.dataStore.edit { it[Keys.BAND_GAINS] = encodeBandGains(gains) }
    }

    private fun encodeBandGains(gains: Map<Int, Int>): String =
        gains.entries.sortedBy { it.key }.joinToString(";") { "${it.key}:${it.value}" }

    private fun parseBandGains(raw: String?): Map<Int, Int> {
        if (raw.isNullOrBlank()) return emptyMap()
        return raw.split(";").mapNotNull { part ->
            val (band, gain) = part.split(":").map { it.toIntOrNull() ?: return@mapNotNull null }
            band to gain
        }.toMap()
    }
}
