package com.dash.android.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dash.android.density.DensityPreset
import com.dash.android.ui.motion.TransitionId
import com.dash.android.ui.motion.TransitionSpeed
import com.dash.android.ui.scale.DASH_SCALE_DEFAULT
import com.dash.android.ui.scale.DASH_TEXT_SCALE_DEFAULT
import com.dash.android.ui.systembar.SystemBarConfig
import com.dash.android.weather.ManualLocation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "dash_prefs")

class DashPreferences(private val context: Context) {

    // Lenient on purpose: unknown keys are ignored and defaults are written, so a config saved by
    // an older or newer DASH build still decodes rather than throwing.
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val densityKey = stringPreferencesKey("density_preset")
    private val scaleKey = floatPreferencesKey("dash_scale")
    private val textScaleKey = floatPreferencesKey("dash_text_scale")
    private val autoRotateKey = booleanPreferencesKey("auto_rotate")
    private val lockedOrientationKey = stringPreferencesKey("locked_orientation")
    private val splashModeKey = stringPreferencesKey("splash_mode")
    private val splashColourKey = longPreferencesKey("splash_colour")
    private val splashImageUriKey = stringPreferencesKey("splash_image_uri")
    private val systemBarKey = stringPreferencesKey("system_bar_config")
    private val manualLocationKey = stringPreferencesKey("manual_location")

    private fun transitionKey(id: TransitionId) = stringPreferencesKey("transition_" + id.key)

    val densityPreset: Flow<DensityPreset?> = context.dataStore.data.map { prefs ->
        prefs[densityKey]?.let { name -> DensityPreset.entries.find { it.name == name } }
    }

    val dashScale: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[scaleKey] ?: DASH_SCALE_DEFAULT
    }

    val dashTextScale: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[textScaleKey] ?: DASH_TEXT_SCALE_DEFAULT
    }

    val autoRotate: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[autoRotateKey] ?: true
    }

    val lockedOrientation: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[lockedOrientationKey] ?: "LANDSCAPE"
    }

    val splashMode: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[splashModeKey] ?: "COLOUR"
    }

    val splashColour: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[splashColourKey] ?: 0xFF000000L
    }

    val splashImageUri: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[splashImageUriKey] ?: ""
    }

    val systemBarConfig: Flow<SystemBarConfig> = context.dataStore.data.map { prefs ->
        prefs[systemBarKey]
            ?.let { runCatching { json.decodeFromString<SystemBarConfig>(it) }.getOrNull() }
            ?: SystemBarConfig.default()
    }

    /**
     * The per-transition speeds (roadmap 1.5.5). Every [TransitionId] resolves to a stored speed or
     * its own default, so the map is always complete. A speed is stored by name, so relabelling the
     * millis of a preset never orphans a saved choice.
     */
    val transitions: Flow<Map<TransitionId, TransitionSpeed>> = context.dataStore.data.map { prefs ->
        TransitionId.entries.associateWith { id ->
            prefs[transitionKey(id)]
                ?.let { name -> runCatching { TransitionSpeed.valueOf(name) }.getOrNull() }
                ?: id.default
        }
    }

    /** A user-pinned place for the weather scene, or null when location is automatic (GPS/IP). */
    val manualLocation: Flow<ManualLocation?> = context.dataStore.data.map { prefs ->
        prefs[manualLocationKey]
            ?.let { runCatching { json.decodeFromString<ManualLocation>(it) }.getOrNull() }
    }

    suspend fun saveDensityPreset(preset: DensityPreset) {
        context.dataStore.edit { it[densityKey] = preset.name }
    }

    suspend fun saveDashScale(scale: Float) {
        context.dataStore.edit { it[scaleKey] = scale }
    }

    suspend fun saveDashTextScale(scale: Float) {
        context.dataStore.edit { it[textScaleKey] = scale }
    }

    suspend fun saveAutoRotate(auto: Boolean) {
        context.dataStore.edit { it[autoRotateKey] = auto }
    }

    suspend fun saveLockedOrientation(orientation: String) {
        context.dataStore.edit { it[lockedOrientationKey] = orientation }
    }

    suspend fun saveSplashMode(mode: String) {
        context.dataStore.edit { it[splashModeKey] = mode }
    }

    suspend fun saveSplashColour(colour: Long) {
        context.dataStore.edit { it[splashColourKey] = colour }
    }

    suspend fun saveSplashImageUri(uri: String) {
        context.dataStore.edit { it[splashImageUriKey] = uri }
    }

    suspend fun saveSystemBarConfig(config: SystemBarConfig) {
        context.dataStore.edit { it[systemBarKey] = json.encodeToString(config) }
    }

    /** Sets one transition's speed — the per-transition breakout. Diverging from the rest makes the
     *  master read "Custom". */
    suspend fun setTransition(id: TransitionId, speed: TransitionSpeed) {
        context.dataStore.edit { it[transitionKey(id)] = speed.name }
    }

    /** The master pace: writes every transition to one speed in a single edit. */
    suspend fun setAllTransitions(speed: TransitionSpeed) {
        context.dataStore.edit { prefs ->
            TransitionId.entries.forEach { prefs[transitionKey(it)] = speed.name }
        }
    }

    /** Clears the stored bar config so it falls back to [SystemBarConfig.default]. */
    suspend fun resetSystemBar() {
        context.dataStore.edit { it.remove(systemBarKey) }
    }

    suspend fun saveManualLocation(location: ManualLocation) {
        context.dataStore.edit { it[manualLocationKey] = json.encodeToString(location) }
    }

    /** Clears the pinned place so the weather scene returns to automatic (GPS → IP) location. */
    suspend fun clearManualLocation() {
        context.dataStore.edit { it.remove(manualLocationKey) }
    }
}
