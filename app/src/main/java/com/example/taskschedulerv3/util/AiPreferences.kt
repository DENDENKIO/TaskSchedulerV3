package com.example.taskschedulerv3.util

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

object AiPreferences {
    private val AI_ENABLED_KEY = booleanPreferencesKey("ai_enabled")

    fun getAiEnabled(context: Context): Flow<Boolean> =
        context.settingsDataStore.data.map { prefs ->
            prefs[AI_ENABLED_KEY] ?: false
        }

    suspend fun setAiEnabled(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[AI_ENABLED_KEY] = enabled
        }
    }
}
