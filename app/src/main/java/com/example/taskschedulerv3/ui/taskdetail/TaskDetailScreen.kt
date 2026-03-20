package com.example.taskschedulerv3.ui.taskdetail

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.taskschedulerv3.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(
    navController: NavController,
    taskId: Int,
    vm: TaskDetailViewModel = viewModel()
) {
    val task by vm.task.collectAsState()

    LaunchedEffect(taskId) { vm.loadTask(taskId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("詳細") },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, null) } },
                actions = { IconButton(onClick = { navController.navigate(Screen.EditTask.createRoute(taskId)) }) { Icon(Icons.Default.Edit, null) } }
            )
        }
    ) { padding ->
        task?.let { t ->
            Column(modifier = Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(t.title, style = MaterialTheme.typography.headlineSmall)
                Text("日付: ${t.startDate}${t.endDate?.let { " ~ $it" } ?: ""}")
                t.startTime?.let { Text("時刻: $it${t.endTime?.let { e -> " ~ $e" } ?: ""}") }
                t.description?.let { Text("メモ: $it") }
                Text("優先度: ${listOf("高","中","低")[t.priority]}")
                Text("通知: ${if (t.notifyEnabled) "${t.notifyMinutesBefore}分前" else "OFF"}")
            }
        } ?: Box(modifier = Modifier.padding(padding)) { CircularProgressIndicator() }
    }
}
