package com.example.taskschedulerv3.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.taskschedulerv3.navigation.Screen
import com.example.taskschedulerv3.util.AiModelManager
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

    // AI関連
    val aiEnabled by vm.aiEnabled.collectAsState()
    val aiModelState by vm.aiModelState.collectAsState()
    var showDeleteModelDialog by remember { mutableStateOf(false) }
    var showAiOnConfirmDialog by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { vm.exportToUri(it) } }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { vm.importFromUri(it, overwrite = false) } }

    LaunchedEffect(exportImportState) {
        when (val s = exportImportState) {
            is ExportImportState.Success -> { showSnackbar = s.message; vm.clearState() }
            is ExportImportState.Error -> { showSnackbar = s.message; vm.clearState() }
            else -> {}
        }
    }

    // テーマ選択ダイアログ
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("テーマ") },
            text = {
                Column {
                    listOf(
                        ThemeMode.SYSTEM to "システム連動",
                        ThemeMode.LIGHT to "ライト",
                        ThemeMode.DARK to "ダーク"
                    ).forEach { (mode, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    vm.setThemeMode(mode)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            RadioButton(
                                selected = themeMode == mode,
                                onClick = {
                                    vm.setThemeMode(mode)
                                    showThemeDialog = false
                                }
                            )
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

    // AI ON確認ダイアログ（モデル未ダウンロード時）
    if (showAiOnConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showAiOnConfirmDialog = false },
            title = { Text("AIモデルのダウンロード") },
            text = {
                Text("AI機能を使用するには、約1GBのモデルデータをダウンロードする必要があります。\n\nWi-Fi環境でのダウンロードを推奨します。")
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.setAiEnabled(true)
                    showAiOnConfirmDialog = false
                }) { Text("ダウンロードして有効化") }
            },
            dismissButton = {
                TextButton(onClick = { showAiOnConfirmDialog = false }) { Text("キャンセル") }
            }
        )
    }

    // モデル削除確認ダイアログ
    if (showDeleteModelDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteModelDialog = false },
            title = { Text("AIモデルを削除") },
            text = {
                Text("AIモデルを削除してストレージを約${vm.getModelSizeMB()}MB解放します。\nAI機能はOFFになります。再度使用するにはダウンロードが必要です。")
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteAiModel()
                    showDeleteModelDialog = false
                }) { Text("削除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteModelDialog = false }) { Text("キャンセル") }
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
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {

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
                title = "完了した予定",
                subtitle = "完了した予定の確認・復元",
                onClick = { navController.navigate(Screen.CompletedTasks.route) }
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
                title = "仮登録管理",
                subtitle = "写真から仮登録したタスクの管理",
                onClick = { navController.navigate(Screen.QuickDraftList.route) }
            )
            HorizontalDivider()

            SettingsItem(
                title = "タグ管理",
                subtitle = "3階層タグの作成・編集・削除",
                onClick = { navController.navigate(Screen.TagManage.route) }
            )
            HorizontalDivider()

            SettingsItem(
                title = "ゴミ箱",
                subtitle = "削除したタスクの確認・復元",
                onClick = { navController.navigate(Screen.Trash.route) }
            )
            HorizontalDivider()

            SettingsItem(
                title = "データエクスポート",
                subtitle = "スケジュールをJSONで保存",
                onClick = {
                    val filename = "taskscheduler_export_${
                        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.JAPAN).format(Date())
                    }.json"
                    exportLauncher.launch(filename)
                }
            )
            HorizontalDivider()

            SettingsItem(
                title = "データインポート",
                subtitle = "JSONからスケジュールを復元",
                onClick = {
                    importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                }
            )
            HorizontalDivider()

            if (exportImportState is ExportImportState.Loading) {
                ListItem(
                    headlineContent = { Text("処理中...") },
                    trailingContent = {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    }
                )
                HorizontalDivider()
            }

            // ==================== AI設定セクション ==================== //

            Spacer(modifier = Modifier.height(8.dp))

            ListItem(
                headlineContent = {
                    Text(
                        "AI機能",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                supportingContent = {
                    Text(
                        "写真から予定の日付・タイトル・内容を自動認識します",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            )

            ListItem(
                headlineContent = { Text("AI機能を使用する") },
                supportingContent = {
                    when (aiModelState) {
                        is AiModelManager.ModelState.NotDownloaded ->
                            Text(
                                "ONにするとモデル（約1GB）をダウンロードします",
                                style = MaterialTheme.typography.bodySmall
                            )
                        is AiModelManager.ModelState.Downloading ->
                            Text(
                                "ダウンロード中... ${(aiModelState as AiModelManager.ModelState.Downloading).progress}%",
                                style = MaterialTheme.typography.bodySmall
                            )
                        is AiModelManager.ModelState.Ready ->
                            Text(
                                "モデルダウンロード済み（${vm.getModelSizeMB()}MB）",
                                style = MaterialTheme.typography.bodySmall
                            )
                        is AiModelManager.ModelState.Error ->
                            Text(
                                (aiModelState as AiModelManager.ModelState.Error).message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                    }
                },
                trailingContent = {
                    Switch(
                        checked = aiEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled && aiModelState is AiModelManager.ModelState.NotDownloaded) {
                                showAiOnConfirmDialog = true
                            } else {
                                vm.setAiEnabled(enabled)
                            }
                        },
                        enabled = aiModelState !is AiModelManager.ModelState.Downloading
                    )
                }
            )

            if (aiModelState is AiModelManager.ModelState.Downloading) {
                LinearProgressIndicator(
                    progress = {
                        (aiModelState as AiModelManager.ModelState.Downloading).progress / 100f
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            if (aiModelState is AiModelManager.ModelState.Ready) {
                ListItem(
                    headlineContent = {
                        Text("AIモデルを削除", color = MaterialTheme.colorScheme.error)
                    },
                    supportingContent = {
                        Text(
                            "ストレージを約${vm.getModelSizeMB()}MB解放します",
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    modifier = Modifier.clickable { showDeleteModelDialog = true }
                )
            }

            HorizontalDivider()

            // ==================== AI設定セクションここまで ==================== //

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
