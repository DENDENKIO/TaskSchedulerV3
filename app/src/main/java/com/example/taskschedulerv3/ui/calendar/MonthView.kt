package com.example.taskschedulerv3.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.taskschedulerv3.util.DateUtils
import java.time.LocalDate

@Composable
fun MonthView(
    year: Int,
    month: Int,
    selectedDate: String,
    tasksWithDates: Set<String>,
    onDateSelected: (String) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
    val today = DateUtils.today()
    val daysInMonth = DateUtils.daysInMonth(year, month)
    val firstDayOfWeek = DateUtils.firstDayOfWeek(year, month)
    val dayLabels = listOf("日", "月", "火", "水", "木", "金", "土")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectHorizontalDragGestures { _, dragAmount ->
                    if (dragAmount < -50) onNextMonth()
                    else if (dragAmount > 50) onPreviousMonth()
                }
            }
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPreviousMonth) { Icon(Icons.Default.ArrowBack, null) }
            Text("${year}年${month}月", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            IconButton(onClick = onNextMonth) { Icon(Icons.Default.ArrowForward, null) }
        }
        // Day of week labels
        Row(modifier = Modifier.fillMaxWidth()) {
            dayLabels.forEach { label ->
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = when (label) {
                        "日" -> Color(0xFFE53935)
                        "土" -> Color(0xFF1E88E5)
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }
        // Calendar grid
        val cells = firstDayOfWeek + daysInMonth
        val rows = (cells + 6) / 7
        for (row in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val cellIndex = row * 7 + col
                    val day = cellIndex - firstDayOfWeek + 1
                    if (day < 1 || day > daysInMonth) {
                        Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        val dateStr = "%04d-%02d-%02d".format(year, month, day)
                        val isSelected = dateStr == selectedDate
                        val isToday = dateStr == today
                        val hasTasks = tasksWithDates.contains(dateStr)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isSelected -> MaterialTheme.colorScheme.primary
                                        isToday -> MaterialTheme.colorScheme.primaryContainer
                                        else -> Color.Transparent
                                    }
                                )
                                .clickable { onDateSelected(dateStr) },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = day.toString(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = when {
                                        isSelected -> MaterialTheme.colorScheme.onPrimary
                                        col == 0 -> Color(0xFFE53935)
                                        col == 6 -> Color(0xFF1E88E5)
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }
                                )
                                if (hasTasks) {
                                    Box(
                                        modifier = Modifier.size(4.dp).clip(CircleShape)
                                            .background(if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary)
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
