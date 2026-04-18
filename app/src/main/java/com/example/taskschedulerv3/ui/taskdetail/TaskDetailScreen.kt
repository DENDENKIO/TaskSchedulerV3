package com.example.taskschedulerv3.ui.taskdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
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
import com.example.taskschedulerv3.data.model.RoadmapStep
import com.example.taskschedulerv3.data.model.ScheduleType
import com.example.taskschedulerv3.data.model.Task
import com.example.taskschedulerv3.navigation.Screen
import com.example.taskschedulerv3.util.PhotoFileManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(
    navController: NavController,
    taskId: Int,
    onEditRequest: () -> Unit,
    vm: TaskDetailViewModel = viewModel()
) {
    val task by vm.task.collectAsState()
    val relatedTasks by vm.relatedTasks.collectAsState()
    val photos by vm.photos.collectAsState()
    val roadmapSteps by vm.roadmapSteps.collectAsState() // ステップ6

    LaunchedEffect(taskId) { vm.loadTask(taskId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("詳細") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) { 
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null) 
                    }
                },
                actions = {
                    val isDone = task?.isCompleted == true
                    IconButton(onClick = { task?.let { vm.toggleComplete(it) } }) {
                        Icon(
                            if (isDone) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                            contentDescription = if (isDone) "未完了に戻す" else "完了"
                        )
                    }
                    IconButton(onClick = onEditRequest) {
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
                    val emoji = when {
                        t.roadmapEnabled -> "🛣️"
                        t.scheduleType == ScheduleType.RECURRING -> "🔁"
                        t.isIndefinite -> "📝"
                        else -> "📅"
                    }
                    Text("$emoji ${t.title}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
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
                    val dateStr = if (t.isIndefinite) "無期限" else "${t.startDate}${t.endDate?.let { " 〜 $it" } ?: ""}"
                    InfoRow(label = "日付", value = dateStr)
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
                            com.example.taskschedulerv3.data.model.RecurrencePattern.EVERY_N_DAYS -> "${t.recurrenceDays}日ごと"
                            com.example.taskschedulerv3.data.model.RecurrencePattern.WEEKLY_MULTI -> "曜日指定 (${t.recurrenceDays})"
                            com.example.taskschedulerv3.data.model.RecurrencePattern.MONTHLY_DATES -> "毎月日付指定 (${t.recurrenceDays})"
                            com.example.taskschedulerv3.data.model.RecurrencePattern.NONE, null -> "パターン未設定"
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


                // Notification
                item {
                    InfoRow(
                        label = "通知",
                        value = if (t.notifyEnabled) "${t.notifyMinutesBefore}分前" else "OFF"
                    )
                }

                // ロードマップセクション (ステップ6)
                if (t.roadmapEnabled) {
                    item {
                        HorizontalDivider()
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("ロードマップ", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Row {
                                if (t.isCompleted || t.activeRoadmapStepId != null) {
                                    TextButton(
                                        onClick = { vm.revertRoadmapStep(t) },
                                        // 連打防止の検討：短時間の間に何度も押せないようにする等の配慮も可能
                                    ) {
                                        Text("戻す", color = MaterialTheme.colorScheme.error)
                                        Spacer(Modifier.width(4.dp))
                                        Icon(Icons.Default.Undo, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                                TextButton(onClick = {
                                    navController.navigate(Screen.RoadmapEdit.createRoute(taskId))
                                }) {
                                    Text("編集")
                                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        RoadmapTimeline(t, roadmapSteps)
                    }
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
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(14.dp))
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(task.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            Text(
                "${task.startDate}${task.startTime?.let { " $it" } ?: ""}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
    }
    HorizontalDivider()
}

@Composable
fun RoadmapTimeline(task: Task, steps: List<RoadmapStep>) {
    Column(modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp)) {
        // START (Task itself)
        // タスク本体がアクティブ（まだどのステップも進んでいない）かどうか
        val isStartActive = task.activeRoadmapStepId == null && !task.isCompleted

        TimelineNode(
            title = "START: ${task.title}",
            isCompleted = task.isCompleted,
            isActive = isStartActive,
            isStart = true,
            isEnd = steps.isEmpty()
        )
        
        // Intermediate steps
        for ((index, step) in steps.withIndex()) {
            val isActive = task.activeRoadmapStepId == step.id
            TimelineNode(
                title = step.title,
                date = step.date,
                isCompleted = step.isCompleted,
                isActive = isActive,
                isStart = false,
                isEnd = index == steps.size - 1
            )
        }
    }
}

@Composable
fun TimelineNode(
    title: String,
    date: String? = null,
    isCompleted: Boolean,
    isActive: Boolean = false,
    isStart: Boolean,
    isEnd: Boolean
) {
    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(32.dp)
        ) {
            val color = when {
                isCompleted -> Color.Gray
                isActive -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.outlineVariant
            }
            
            // Top line
            if (!isStart) {
                Box(modifier = Modifier.width(2.dp).weight(1f).background(if (isCompleted) Color.Gray else MaterialTheme.colorScheme.outlineVariant))
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            
            // Marker
            if (isActive) {
                Surface(
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    modifier = Modifier.size(20.dp),
                    border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Surface(
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(8.dp)
                        ) {}
                    }
                }
            } else {
                Surface(
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = color,
                    modifier = Modifier.size(if (isStart || isEnd) 12.dp else 8.dp)
                ) {}
            }
            
            // Bottom line
            if (!isEnd) {
                Box(modifier = Modifier.width(2.dp).weight(1f).background(if (isCompleted) Color.Gray else MaterialTheme.colorScheme.outlineVariant))
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
        
        Spacer(Modifier.width(12.dp))
        
        Column(modifier = Modifier.padding(bottom = 20.dp)) {
            Text(
                title,
                style = if (isActive || isStart) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
                fontWeight = if (isActive || isStart) FontWeight.ExtraBold else FontWeight.Normal,
                color = when {
                    isCompleted -> Color.Gray
                    isActive -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                }
            )
            date?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = if (isCompleted) Color.Gray else MaterialTheme.colorScheme.secondary)
            }
        }
    }
}
