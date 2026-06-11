package com.dash.android.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dash.android.density.DensityPreset
import com.dash.android.ui.scale.DASH_SCALE_DEFAULT
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "dash_prefs")

class DashPreferences(private val context: Context) {

    private val densityKey = stringPreferencesKey("density_preset")
    private val scaleKey = floatPreferencesKey("dash_scale")
    private val autoRotateKey = booleanPreferencesKey("auto_rotate")
    private val lockedOrientationKey = stringPreferencesKey("locked_orientation")

    val densityPreset: Flow<DensityPreset?> = context.dataStore.data.map { prefs ->
        prefs[densityKey]?.let { name -> DensityPreset.entries.find { it.name == name } }
    }

    val dashScale: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[scaleKey] ?: DASH_SCALE_DEFAULT
    }

    val autoRotate: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[autoRotateKey] ?: true
    }

    val lockedOrientation: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[lockedOrientationKey] ?: "LANDSCAPE"
    }

    suspend fun saveDensityPreset(preset: DensityPreset) {
        context.dataStore.edit { it[densityKey] = preset.name }
    }

    suspend fun saveDashScale(scale: Float) {
        context.dataStore.edit { it[scaleKey] = scale }
    }

    suspend fun saveAutoRotate(auto: Boolean) {
        context.dataStore.edit { it[autoRotateKey] = auto }
    }

    suspend fun saveLockedOrientation(orientation: String) {
        context.dataStore.edit { it[lockedOrientationKey] = orientation }
    }
}
