package com.example.taskschedulerv3.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.taskschedulerv3.navigation.Screen
import com.example.taskschedulerv3.util.ThemeMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController, vm: SettingsViewModel = viewModel()) {
    val themeMode by vm.themeMode.collectAsState()
    val exportImportState by vm.exportImportState.collectAsState()
    var showThemeDialog by remember { mutableStateOf(false) }
    var showSnackbar by remember { mutableStateOf<String?>(null) }

    // Export: CreateDocument launcher
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { vm.exportToUri(it) } }

    // Import: OpenDocument launcher
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { vm.importFromUri(it, overwrite = false) } }

    // Handle state changes
    LaunchedEffect(exportImportState) {
        when (val s = exportImportState) {
            is ExportImportState.Success -> { showSnackbar = s.message; vm.clearState() }
            is ExportImportState.Error -> { showSnackbar = s.message; vm.clearState() }
            else -> {}
        }
    }

    // Theme dialog
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("テーマ") },
            text = {
                Column {
                    listOf(ThemeMode.SYSTEM to "システム連動", ThemeMode.LIGHT to "ライト", ThemeMode.DARK to "ダーク")
                        .forEach { (mode, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { vm.setThemeMode(mode); showThemeDialog = false }
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                RadioButton(selected = themeMode == mode, onClick = { vm.setThemeMode(mode); showThemeDialog = false })
                                Text(label, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) { Text("閉じる") }
            }
        )
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(showSnackbar) {
        showSnackbar?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            showSnackbar = null
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("設定") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {

            // Theme setting
            SettingsItem(
                title = "テーマ",
                subtitle = when (themeMode) {
                    ThemeMode.LIGHT -> "ライト"
                    ThemeMode.DARK -> "ダーク"
                    ThemeMode.SYSTEM -> "システム連動"
                },
                onClick = { showThemeDialog = true }
            )
            HorizontalDivider()

            SettingsItem(
                title = "無期限予定",
                subtitle = "日付を決めない予定の一覧・編集・削除",
                onClick = { navController.navigate(Screen.IndefiniteTask.route) }
            )
            HorizontalDivider()

            SettingsItem(
                title = "繰り返し予定",
                subtitle = "繰り返し予定の一覧・編集・削除",
                onClick = { navController.navigate(Screen.Recurring.route) }
            )
            HorizontalDivider()

            SettingsItem(
                title = "タグ管理",
                subtitle = "3階層タグの作成・編集・削除",
                onClick = { navController.navigate(Screen.TagManage.route) }
            )
            HorizontalDivider()

            SettingsItem(
                title = "ゴミ筱",
                subtitle = "削除したタスクの確認・復元",
                onClick = { navController.navigate(Screen.Trash.route) }
            )
            HorizontalDivider()

            // Export
            SettingsItem(
                title = "データエクスポート",
                subtitle = "スケジュールをJSONで保存",
                onClick = {
                    val filename = "taskscheduler_export_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.JAPAN).format(Date())}.json"
                    exportLauncher.launch(filename)
                }
            )
            HorizontalDivider()

            // Import
            SettingsItem(
                title = "データインポート",
                subtitle = "JSONからスケジュールを復元",
                onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }
            )
            HorizontalDivider()

            // Loading indicator for export/import
            if (exportImportState is ExportImportState.Loading) {
                ListItem(
                    headlineContent = { Text("処理中...") },
                    trailingContent = { CircularProgressIndicator(modifier = Modifier.size(20.dp)) }
                )
                HorizontalDivider()
            }

            ListItem(
                headlineContent = { Text("バージョン情報") },
                supportingContent = { Text("TaskSchedulerV3  v1.0") }
            )
        }
    }
}

@Composable
fun SettingsItem(title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle, style = MaterialTheme.typography.bodySmall) },
        trailingContent = { Icon(Icons.Default.ChevronRight, null) },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
