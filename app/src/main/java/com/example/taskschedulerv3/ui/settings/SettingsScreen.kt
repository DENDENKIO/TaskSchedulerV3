package com.example.taskschedulerv3.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.taskschedulerv3.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    Scaffold(topBar = { TopAppBar(title = { Text("設定") }) }) { padding ->
        Column(modifier = Modifier.padding(padding)) {
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
                subtitle = "スケジュールをJSONで保存（Phase7実装予定）",
                onClick = {}
            )
            HorizontalDivider()
            SettingsItem(
                title = "データインポート",
                subtitle = "JSONからスケジュールを復元（Phase7実装予定）",
                onClick = {}
            )
            HorizontalDivider()
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
