package com.example.taskschedulerv3.ui.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskschedulerv3.util.ExportImportManager
import com.example.taskschedulerv3.util.ThemeMode
import com.example.taskschedulerv3.util.ThemePreferences
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed class ExportImportState {
    object Idle : ExportImportState()
    object Loading : ExportImportState()
    data class Success(val message: String) : ExportImportState()
    data class Error(val message: String) : ExportImportState()
}

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    val themeMode: StateFlow<ThemeMode> = ThemePreferences.getThemeMode(app)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    private val _exportImportState = MutableStateFlow<ExportImportState>(ExportImportState.Idle)
    val exportImportState: StateFlow<ExportImportState> = _exportImportState.asStateFlow()

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch {
        ThemePreferences.setThemeMode(getApplication(), mode)
    }

    fun exportToUri(uri: Uri) = viewModelScope.launch {
        _exportImportState.value = ExportImportState.Loading
        val success = ExportImportManager.exportToUri(getApplication(), uri)
        _exportImportState.value = if (success)
            ExportImportState.Success("エクスポート完了しました")
        else
            ExportImportState.Error("エクスポートに失敗しました")
    }

    fun importFromUri(uri: Uri, overwrite: Boolean = false) = viewModelScope.launch {
        _exportImportState.value = ExportImportState.Loading
        when (val result = ExportImportManager.importFromUri(getApplication(), uri, overwrite)) {
            is ExportImportManager.ImportResult.Success ->
                _exportImportState.value = ExportImportState.Success(
                    "インポート完了: タスク${result.tasksImported}件、タグ${result.tagsImported}件"
                )
            is ExportImportManager.ImportResult.Error ->
                _exportImportState.value = ExportImportState.Error(result.message)
            ExportImportManager.ImportResult.NeedsConflictResolution ->
                _exportImportState.value = ExportImportState.Error("競合が発生しました")
        }
    }

    fun clearState() { _exportImportState.value = ExportImportState.Idle }
}
