package com.example.taskschedulerv3.ui.recurring

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.taskschedulerv3.data.model.RecurrencePattern
import com.example.taskschedulerv3.data.model.Task
import com.example.taskschedulerv3.navigation.Screen
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringScreen(
    navController: NavController,
    onAddTask: () -> Unit,
    onEditRequest: (Int) -> Unit,
    vm: RecurringViewModel = viewModel()
) {
    val allTasks by vm.recurringTasks.collectAsState()
    val todayTasks by vm.todayRecurringTasks.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tasks = if (selectedTab == 0) allTasks else todayTasks
    var deleteTarget by remember { mutableStateOf<Task?>(null) }

    deleteTarget?.let { task ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("繰り返し予定を削除") },
            text = { Text("「${task.title}」を削除しますか？") },
            confirmButton = {
                TextButton(onClick = { vm.delete(task); deleteTarget = null }) {
                    Text("削除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("キャンセル") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("繰り返し予定") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddTask) {
                Icon(Icons.Default.Add, contentDescription = "追加")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // 今日 / すべて タブ
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("すべて (${allTasks.size})") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("今日 (${todayTasks.size})") }
                )
            }

            if (tasks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (selectedTab == 1) "今日の繰り返し予定はありません" else "繰り返し予定はありません",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(tasks, key = { it.id }) { task ->
                        RecurringTaskItem(
                            task = task,
                            onEdit = { onEditRequest(task.id) },
                            onDelete = { deleteTarget = task }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun RecurringTaskItem(
    task: Task,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val patternStr = when (task.recurrencePattern) {
        RecurrencePattern.DAILY        -> "毎日"
        RecurrencePattern.WEEKLY       -> "毎週"
        RecurrencePattern.BIWEEKLY     -> "隔週"
        RecurrencePattern.MONTHLY_DATE -> "毎月（日付）"
        RecurrencePattern.MONTHLY_WEEK -> "毎月（曜日）"
        RecurrencePattern.YEARLY       -> "毎年"
        RecurrencePattern.EVERY_N_DAYS -> "${task.recurrenceDays}日ごと"
        RecurrencePattern.WEEKLY_MULTI -> "曜日指定 (${task.recurrenceDays})"
        RecurrencePattern.MONTHLY_DATES -> "毎月日付指定 (${task.recurrenceDays})"
        RecurrencePattern.NONE, null   -> "パターン未設定"
    }

    ListItem(
        headlineContent = {
            Text(task.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        },
        supportingContent = {
            Column {
                Text(
                    text = "繰り返し: $patternStr${task.recurrenceEndDate?.let { " (〜$it)" } ?: " (無期限)"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF43A047)
                )
                Text(
                    text = "開始: ${task.startDate}" +
                        (task.startTime?.let { "  時刻: $it" } ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        },
        trailingContent = {
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "編集", modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete, contentDescription = "削除",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    )
}
