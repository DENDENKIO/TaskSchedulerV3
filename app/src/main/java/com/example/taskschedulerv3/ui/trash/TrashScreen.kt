package com.example.taskschedulerv3.ui.trash

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(navController: NavController, vm: TrashViewModel = viewModel()) {
    val tasks by vm.deletedTasks.collectAsState()
    var showConfirmClear by remember { mutableStateOf(false) }

    if (showConfirmClear) {
        AlertDialog(
            onDismissRequest = { showConfirmClear = false },
            title = { Text("ゴミ箱を空にする") },
            text = { Text("全件完全削除します。この操作は取り消せません。") },
            confirmButton = {
                TextButton(onClick = { vm.purgeAll(); showConfirmClear = false }) {
                    Text("削除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmClear = false }) { Text("キャンセル") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ゴミ箱 (${tasks.size}件)") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                },
                actions = {
                    if (tasks.isNotEmpty()) {
                        IconButton(onClick = { showConfirmClear = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "ゴミ箱を空に")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (tasks.isEmpty()) {
            Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("ゴミ箱は空です", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                items(tasks, key = { it.id }) { task ->
                    val deletedAt = task.deletedAt ?: 0L
                    val daysLeft = 30 - ((System.currentTimeMillis() - deletedAt) / (1000 * 60 * 60 * 24)).toInt()
                    val remainColor = when {
                        daysLeft <= 3 -> MaterialTheme.colorScheme.error
                        daysLeft <= 7 -> Color(0xFFFB8C00)
                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    }
                    ListItem(
                        headlineContent = { Text(task.title) },
                        supportingContent = {
                            Column {
                                Text("削除日: ${java.text.SimpleDateFormat("yyyy/MM/dd HH:mm", java.util.Locale.JAPAN).format(java.util.Date(deletedAt))}")
                                Text("自動削除まで: ${daysLeft}日", color = remainColor, style = MaterialTheme.typography.labelSmall)
                            }
                        },
                        trailingContent = {
                            Row {
                                TextButton(onClick = { vm.restore(task) }) { Text("復元") }
                                TextButton(onClick = { vm.permanentDelete(task) }) {
                                    Text("削除", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
