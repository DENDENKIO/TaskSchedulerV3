package com.example.taskschedulerv3.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.taskschedulerv3.data.model.Task
import com.example.taskschedulerv3.ui.components.ProgressRingCard
import com.example.taskschedulerv3.ui.components.TaskCard
import com.example.taskschedulerv3.util.DateUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToDetail: (Int) -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val incompleteTasks by viewModel.incompleteTasks.collectAsState()
    val completedTasks by viewModel.completedTasks.collectAsState()
    val filterType by viewModel.filterType.collectAsState()
    val totalCount by viewModel.totalCount.collectAsState()
    val completedCount by viewModel.completedCount.collectAsState()
    
    var showCompleted by remember { mutableStateOf(false) }

    Column {
        TopAppBar(
            title = { 
                Text(
                    "TaskFlow", 
                    style = MaterialTheme.typography.titleLarge, 
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                ) 
            },
            actions = {
                IconButton(onClick = { /* 通知 */ }) {
                    Icon(Icons.Outlined.Notifications, contentDescription = "通知")
                }
                IconButton(onClick = onNavigateToSettings) {
                    Icon(Icons.Outlined.Settings, contentDescription = "設定")
                }
            }
        )

        ProgressRingCard(completed = completedCount, total = totalCount)
        
        // フィルターChipRow
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("今日", "直近7日", "無期限", "全て").forEach { label ->
                FilterChip(
                    selected = filterType == label,
                    onClick = { viewModel.setFilterType(label) },
                    label = { Text(label) }
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(incompleteTasks, key = { it.id }) { task ->
                HomeTaskItem(
                    task = task,
                    viewModel = viewModel,
                    onToggleComplete = { viewModel.toggleComplete(task) },
                    onClick = { onNavigateToDetail(task.id) }
                )
            }

            if (completedTasks.isNotEmpty()) {
                item {
                    TextButton(onClick = { showCompleted = !showCompleted }) {
                        Text(
                            if (showCompleted) "完了済みを非表示" else "完了済みを表示 (${completedTasks.size}件)",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                if (showCompleted) {
                    items(completedTasks, key = { it.id }) { task ->
                        HomeTaskItem(
                            task = task,
                            viewModel = viewModel,
                            onToggleComplete = { viewModel.toggleComplete(task) },
                            onClick = { onNavigateToDetail(task.id) },
                            modifier = Modifier.alpha(0.6f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HomeTaskItem(
    task: Task,
    viewModel: HomeViewModel,
    onToggleComplete: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tagsFlow = remember(task.id) { viewModel.getTagsForTask(task.id) }
    val photosFlow = remember(task.id) { viewModel.getPhotosForTask(task.id) }
    
    val tags by tagsFlow.collectAsState(initial = emptyList())
    val photos by photosFlow.collectAsState(initial = emptyAsPhotoList())
    val remainingDays = remember(task.startDate, task.endDate, task.isIndefinite) {
        DateUtils.calculateRemainingDays(task)
    }

    TaskCard(
        task = task,
        tags = tags,
        photos = photos,
        remainingDays = remainingDays,
        onChecked = { onToggleComplete() },
        onCardClick = { onClick() },
        modifier = modifier
    )
}

// 型推論回避のためのヘルパー
@Composable
private fun emptyAsPhotoList() = remember { emptyList<com.example.taskschedulerv3.data.model.PhotoMemo>() }
