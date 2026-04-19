package com.example.taskschedulerv3.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * アプリ全体で共有する単一の DataStore インスタンス。
 * ThemePreferences / AiPreferences など、すべてここから参照する。
 */
val Context.settingsDataStore: DataStore<Preferences>
        by preferencesDataStore(name = "settings")
