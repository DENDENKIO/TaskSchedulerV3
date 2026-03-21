package com.example.taskschedulerv3.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.taskschedulerv3.util.DateUtils
import java.time.LocalDate

@Composable
fun YearView(
    year: Int,
    taskDates: Set<String>,
    onMonthSelected: (Int) -> Unit,
    onPreviousYear: () -> Unit,
    onNextYear: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectHorizontalDragGestures { _, dragAmount ->
                    if (dragAmount < -50) onNextYear()
                    else if (dragAmount > 50) onPreviousYear()
                }
            }
    ) {
        // Year header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPreviousYear) { Icon(Icons.Default.ArrowBack, null) }
            Text("${year}年", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            IconButton(onClick = onNextYear) { Icon(Icons.Default.ArrowForward, null) }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(4.dp)
        ) {
            items((1..12).toList()) { month ->
                MiniMonthCalendar(
                    year = year,
                    month = month,
                    taskDates = taskDates,
                    onClick = { onMonthSelected(month) }
                )
            }
        }
    }
}

@Composable
fun MiniMonthCalendar(
    year: Int,
    month: Int,
    taskDates: Set<String>,
    onClick: () -> Unit
) {
    val today = DateUtils.today()
    val daysInMonth = DateUtils.daysInMonth(year, month)
    val firstDayOfWeek = DateUtils.firstDayOfWeek(year, month)

    Card(
        modifier = Modifier.padding(4.dp).clickable { onClick() },
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(4.dp)) {
            Text(
                text = "${month}月",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            // Day headers (S M T W T F S)
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("日", "月", "火", "水", "木", "金", "土").forEach { d ->
                    Text(
                        text = d,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        fontSize = 7.sp,
                        color = when (d) {
                            "日" -> Color(0xFFE53935)
                            "土" -> Color(0xFF1E88E5)
                            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        }
                    )
                }
            }
            // Days grid
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
                            val isToday = dateStr == today
                            val hasTasks = taskDates.contains(dateStr)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(CircleShape)
                                    .background(if (isToday) MaterialTheme.colorScheme.primaryContainer else Color.Transparent),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = day.toString(),
                                        fontSize = 7.sp,
                                        color = when {
                                            isToday -> MaterialTheme.colorScheme.primary
                                            col == 0 -> Color(0xFFE53935)
                                            col == 6 -> Color(0xFF1E88E5)
                                            else -> MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                    if (hasTasks) {
                                        Box(
                                            modifier = Modifier
                                                .size(5.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFFE53935)) // 目立つ赤
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
}
