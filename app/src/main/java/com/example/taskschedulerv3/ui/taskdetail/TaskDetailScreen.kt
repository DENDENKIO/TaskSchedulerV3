package com.example.taskschedulerv3.ui.taskdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.taskschedulerv3.data.model.ScheduleType
import com.example.taskschedulerv3.data.model.Task
import com.example.taskschedulerv3.navigation.Screen
import com.example.taskschedulerv3.util.PhotoFileManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(
    navController: NavController,
    taskId: Int,
    vm: TaskDetailViewModel = viewModel()
) {
    val task by vm.task.collectAsState()
    val relatedTasks by vm.relatedTasks.collectAsState()
    val photos by vm.photos.collectAsState()

    LaunchedEffect(taskId) { vm.loadTask(taskId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("詳細") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, null) }
                },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.EditTask.createRoute(taskId)) }) {
                        Icon(Icons.Default.Edit, null)
                    }
                }
            )
        }
    ) { padding ->
        task?.let { t ->
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Title
                item {
                    Text(t.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }

                // Schedule type badge
                item {
                    val (badgeColor, badgeText) = when (t.scheduleType) {
                        ScheduleType.PERIOD -> Color(0xFF1E88E5) to "期間"
                        ScheduleType.RECURRING -> Color(0xFF43A047) to "繰り返し"
                        else -> Color(0xFF757575) to "通常"
                    }
                    Surface(color = badgeColor, shape = MaterialTheme.shapes.small) {
                        Text(badgeText, color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelMedium)
                    }
                }

                // Date/time info
                item {
                    InfoRow(label = "日付", value = "${t.startDate}${t.endDate?.let { " 〜 $it" } ?: ""}")
                }
                if (t.startTime != null) {
                    item {
                        InfoRow(label = "時刻", value = "${t.startTime}${t.endTime?.let { " 〜 $it" } ?: ""}")
                    }
                }

                // Recurrence info
                if (t.scheduleType == ScheduleType.RECURRING && t.recurrencePattern != null) {
                    item {
                        val patternStr = when (t.recurrencePattern) {
                            com.example.taskschedulerv3.data.model.RecurrencePattern.DAILY -> "毎日"
                            com.example.taskschedulerv3.data.model.RecurrencePattern.WEEKLY -> "毎週"
                            com.example.taskschedulerv3.data.model.RecurrencePattern.BIWEEKLY -> "隔週"
                            com.example.taskschedulerv3.data.model.RecurrencePattern.MONTHLY_DATE -> "毎月（日付）"
                            com.example.taskschedulerv3.data.model.RecurrencePattern.MONTHLY_WEEK -> "毎月（曜日）"
                            com.example.taskschedulerv3.data.model.RecurrencePattern.YEARLY -> "毎年"
                        }
                        InfoRow(label = "繰り返し", value = patternStr + (t.recurrenceEndDate?.let { " (〜$it)" } ?: ""))
                    }
                }

                // Description
                if (t.description != null) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("メモ", style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(4.dp))
                                Text(t.description, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                // Priority
                item {
                    val (priorityColor, priorityLabel) = when (t.priority) {
                        0 -> Color(0xFFE53935) to "高"
                        2 -> Color(0xFF43A047) to "低"
                        else -> Color(0xFFFB8C00) to "中"
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("優先度:", style = MaterialTheme.typography.labelLarge)
                        Surface(color = priorityColor, shape = MaterialTheme.shapes.small) {
                            Text(priorityLabel, color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                // Notification
                item {
                    InfoRow(
                        label = "通知",
                        value = if (t.notifyEnabled) "${t.notifyMinutesBefore}分前" else "OFF"
                    )
                }

                // Related tasks section
                // Photos section
                if (photos.isNotEmpty()) {
                    item {
                        HorizontalDivider()
                        Spacer(Modifier.height(4.dp))
                        Text("カメラメモ", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(photos) { photo ->
                                AsyncImage(
                                    model = PhotoFileManager.pathToUri(photo.imagePath),
                                    contentDescription = photo.title ?: photo.date,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(120.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            navController.navigate(Screen.PhotoDetail.createRoute(photo.id))
                                        }
                                )
                            }
                        }
                    }
                }

                if (relatedTasks.isNotEmpty()) {
                    item {
                        HorizontalDivider()
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("関連予定", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            TextButton(onClick = {
                                navController.navigate(Screen.RelatedTasks.createRoute(taskId))
                            }) {
                                Text("すべて見る")
                                Icon(Icons.Default.ArrowForward, null, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                    // Show up to 3 related tasks inline
                    items(relatedTasks.take(3)) { related ->
                        RelatedTaskRow(
                            task = related,
                            onClick = { navController.navigate(Screen.TaskDetail.createRoute(related.id)) }
                        )
                    }
                }
            }
        } ?: Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "$label:",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.widthIn(min = 56.dp)
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun RelatedTaskRow(task: Task, onClick: () -> Unit) {
    val priorityColor = when (task.priority) {
        0 -> Color(0xFFE53935); 2 -> Color(0xFF43A047); else -> Color(0xFFFB8C00)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(36.dp)
                .background(priorityColor, MaterialTheme.shapes.extraSmall)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(task.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            Text(
                "${task.startDate}${task.startTime?.let { " $it" } ?: ""}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        Icon(Icons.Default.ArrowForward, null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
    }
    HorizontalDivider()
}
