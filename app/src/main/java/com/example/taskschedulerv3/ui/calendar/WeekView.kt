package com.example.taskschedulerv3.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.taskschedulerv3.data.model.Task
import com.example.taskschedulerv3.util.DateUtils
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

@Composable
fun WeekView(
    selectedDate: String,
    tasks: List<Task>,
    onDateSelected: (String) -> Unit,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit
) {
    val today = DateUtils.today()
    val sel = DateUtils.parse(selectedDate)
    val weekStart = sel.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val weekDays = (0..6).map { weekStart.plusDays(it.toLong()) }
    val scrollState = rememberScrollState()
    val hourHeight = 56.dp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectHorizontalDragGestures { _, dragAmount ->
                    if (dragAmount < -50) onNextWeek()
                    else if (dragAmount > 50) onPreviousWeek()
                }
            }
    ) {
        // Header navigation
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPreviousWeek) { Icon(Icons.Default.ArrowBack, null) }
            Text(
                "${weekStart.monthValue}月${weekStart.dayOfMonth}日〜${weekDays.last().dayOfMonth}日",
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onNextWeek) { Icon(Icons.Default.ArrowForward, null) }
        }

        // Day column headers
        Row(modifier = Modifier.fillMaxWidth()) {
            // Time gutter
            Box(modifier = Modifier.width(40.dp))
            weekDays.forEach { day ->
                val dateStr = DateUtils.format(day)
                val isToday = dateStr == today
                val isSelected = dateStr == selectedDate
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onDateSelected(dateStr) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = listOf("月", "火", "水", "木", "金", "土", "日")[day.dayOfWeek.value - 1],
                        fontSize = 10.sp,
                        color = when {
                            day.dayOfWeek.value == 7 -> Color(0xFFE53935)
                            day.dayOfWeek.value == 6 -> Color(0xFF1E88E5)
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(
                                when {
                                    isSelected -> MaterialTheme.colorScheme.primary
                                    isToday -> MaterialTheme.colorScheme.primaryContainer
                                    else -> Color.Transparent
                                },
                                shape = MaterialTheme.shapes.small
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = day.dayOfMonth.toString(),
                            fontSize = 12.sp,
                            color = when {
                                isSelected -> MaterialTheme.colorScheme.onPrimary
                                isToday -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                            fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        HorizontalDivider()

        // All-day tasks row
        val allDayTasks = tasks.filter { it.startTime == null }
        if (allDayTasks.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                Box(modifier = Modifier.width(40.dp)) {
                    Text("終日", fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.align(Alignment.Center))
                }
                weekDays.forEach { day ->
                    val dateStr = DateUtils.format(day)
                    Column(modifier = Modifier.weight(1f).padding(horizontal = 1.dp)) {
                        allDayTasks.filter { it.startDate == dateStr }.forEach { task ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 1.dp)
                                    .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.extraSmall)
                            ) {
                                Text(
                                    text = task.title,
                                    fontSize = 9.sp,
                                    maxLines = 1,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
            HorizontalDivider()
        }

        // Time grid
        Box(modifier = Modifier.fillMaxSize().verticalScroll(scrollState)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                // Hour labels
                Column(modifier = Modifier.width(40.dp)) {
                    (0..23).forEach { hour ->
                        Box(modifier = Modifier.height(hourHeight), contentAlignment = Alignment.TopCenter) {
                            Text(
                                text = "%02d:00".format(hour),
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
                // Day columns
                weekDays.forEach { day ->
                    val dateStr = DateUtils.format(day)
                    val dayTasks = tasks.filter { it.startDate == dateStr && it.startTime != null }
                    Box(modifier = Modifier.weight(1f).height(hourHeight * 24)) {
                        // Hour dividers
                        Column {
                            (0..23).forEach { _ ->
                                HorizontalDivider(modifier = Modifier.height(hourHeight), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            }
                        }
                        // Task blocks
                        dayTasks.forEach { task ->
                            val startParts = task.startTime!!.split(":").map { it.toIntOrNull() ?: 0 }
                            val startMin = startParts[0] * 60 + startParts[1]
                            val endMin = task.endTime?.split(":")?.map { it.toIntOrNull() ?: 0 }
                                ?.let { it[0] * 60 + it[1] } ?: (startMin + 60)
                            val topFraction = startMin / (24f * 60)
                            val heightFraction = (endMin - startMin).coerceAtLeast(30) / (24f * 60)
                            val priorityColor = when (task.priority) {
                                0 -> Color(0xFFE53935)
                                2 -> Color(0xFF43A047)
                                else -> Color(0xFFFB8C00)
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 1.dp)
                                    .height(hourHeight * 24 * heightFraction)
                                    .offset(y = hourHeight * 24 * topFraction)
                                    .background(priorityColor.copy(alpha = 0.8f), MaterialTheme.shapes.extraSmall)
                            ) {
                                Text(
                                    text = task.title,
                                    fontSize = 8.sp,
                                    color = Color.White,
                                    maxLines = 2,
                                    modifier = Modifier.padding(2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
