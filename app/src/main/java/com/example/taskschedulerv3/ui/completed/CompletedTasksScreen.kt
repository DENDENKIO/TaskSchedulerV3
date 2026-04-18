package com.example.taskschedulerv3.ui.completed

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompletedTasksScreen(
    navController: NavController,
    vm: CompletedTasksViewModel = viewModel()
) {
    val tasks by vm.completedTasks.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("完了した予定") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        if (tasks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("完了した予定はありません", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(tasks, key = { it.id }) { task ->
                    ListItem(
                        headlineContent = { Text(task.title, fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text(task.startDate) },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { vm.restore(task) }) {
                                    Icon(Icons.Default.Restore, "元に戻す", tint = MaterialTheme.colorScheme.primary)
                                }
                                IconButton(onClick = { vm.deletePermanently(task) }) {
                                    Icon(Icons.Default.Delete, "完全に削除", tint = Color.Red.copy(alpha = 0.7f))
                                }
                            }
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        }
    }
}
