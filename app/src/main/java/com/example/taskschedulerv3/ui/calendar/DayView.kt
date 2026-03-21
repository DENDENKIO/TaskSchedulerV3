package com.example.taskschedulerv3.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.taskschedulerv3.data.model.Task
import com.example.taskschedulerv3.util.DateUtils

@Composable
fun DayView(
    selectedDate: String,
    tasks: List<Task>,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit
) {
    val today = DateUtils.today()
    val date = DateUtils.parse(selectedDate)
    val scrollState = rememberScrollState()
    val hourHeight = 64.dp

    val allDayTasks = tasks.filter { it.startDate == selectedDate && it.startTime == null }
    val timedTasks = tasks.filter { it.startDate == selectedDate && it.startTime != null }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectHorizontalDragGestures { _, dragAmount ->
                    if (dragAmount < -50) onNextDay()
                    else if (dragAmount > 50) onPreviousDay()
                }
            }
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPreviousDay) { Icon(Icons.Default.ArrowBack, null) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${date.year}年${date.monthValue}月${date.dayOfMonth}日",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (selectedDate == today) {
                    Text("今日", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            IconButton(onClick = onNextDay) { Icon(Icons.Default.ArrowForward, null) }
        }

        // All-day section
        if (allDayTasks.isNotEmpty()) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                Text("終日", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                allDayTasks.forEach { task ->
                    val color = when (task.priority) { 0 -> Color(0xFFE53935); 2 -> Color(0xFF43A047); else -> Color(0xFFFB8C00) }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                            .background(color.copy(alpha = 0.15f), MaterialTheme.shapes.small)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Box(modifier = Modifier.width(4.dp).height(20.dp).background(color, MaterialTheme.shapes.extraSmall))
                        Spacer(Modifier.width(8.dp))
                        Text(task.title, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            HorizontalDivider()
        }

        // Timed timeline
        Box(modifier = Modifier.fillMaxSize().verticalScroll(scrollState)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                // Hour labels
                Column(modifier = Modifier.width(48.dp)) {
                    (0..23).forEach { hour ->
                        Box(
                            modifier = Modifier.height(hourHeight).fillMaxWidth(),
                            contentAlignment = Alignment.TopEnd
                        ) {
                            Text(
                                text = "%02d:00".format(hour),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier.padding(end = 4.dp, top = 2.dp)
                            )
                        }
                    }
                }
                // Task area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(hourHeight * 24)
                ) {
                    // Hour lines
                    Column {
                        (0..23).forEach { _ ->
                            Box(modifier = Modifier.height(hourHeight).fillMaxWidth()) {
                                HorizontalDivider(
                                    modifier = Modifier.align(Alignment.TopStart),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                                // Half-hour line
                                HorizontalDivider(
                                    modifier = Modifier.align(Alignment.CenterStart),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                                    thickness = 0.5.dp
                                )
                            }
                        }
                    }
                    // Current time indicator
                    if (selectedDate == today) {
                        val now = java.time.LocalTime.now()
                        val totalMin = now.hour * 60 + now.minute
                        val topFraction = totalMin / (24f * 60)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .offset(y = hourHeight * 24 * topFraction)
                                .background(MaterialTheme.colorScheme.error)
                        )
                    }
                    // Task blocks
                    timedTasks.forEach { task ->
                        val startParts = task.startTime!!.split(":").map { it.toIntOrNull() ?: 0 }
                        val startMin = startParts[0] * 60 + startParts[1]
                        val endMin = task.endTime?.split(":")?.map { it.toIntOrNull() ?: 0 }
                            ?.let { it[0] * 60 + it[1] } ?: (startMin + 60)
                        val topFraction = startMin / (24f * 60)
                        val heightFraction = (endMin - startMin).coerceAtLeast(30) / (24f * 60)
                        val color = when (task.priority) { 0 -> Color(0xFFE53935); 2 -> Color(0xFF43A047); else -> Color(0xFFFB8C00) }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 4.dp)
                                .height(hourHeight * 24 * heightFraction)
                                .offset(y = hourHeight * 24 * topFraction)
                                .background(color.copy(alpha = 0.85f), MaterialTheme.shapes.small)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Column {
                                Text(task.title, color = Color.White, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                task.startTime?.let {
                                    Text(
                                        "${it}${task.endTime?.let { e -> "〜$e" } ?: ""}",
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
