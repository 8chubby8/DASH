package com.dash.android.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
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

    val densityPreset: Flow<DensityPreset?> = context.dataStore.data.map { prefs ->
        prefs[densityKey]?.let { name -> DensityPreset.entries.find { it.name == name } }
    }

    val dashScale: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[scaleKey] ?: DASH_SCALE_DEFAULT
    }

    suspend fun saveDensityPreset(preset: DensityPreset) {
        context.dataStore.edit { it[densityKey] = preset.name }
    }

    suspend fun saveDashScale(scale: Float) {
        context.dataStore.edit { it[scaleKey] = scale }
    }
}
