package com.example.taskschedulerv3.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * MonthView — 月初〜月末を行リスト形式で表示。
 * 各行: 日付（曜日）＋ 予定（タグ+タイトル）
 */
@Composable
fun MonthView(
    year: Int,
    month: Int,
    selectedDate: String,
    dayRows: List<MonthDayRow>,
    onDateSelected: (String) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {

        // ── ヘッダー（前月 / 年月 / 次月） ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPreviousMonth) {
                Icon(Icons.Default.ArrowBack, contentDescription = "前月")
            }
            Text(
                text = "${year}年${month}月",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onNextMonth) {
                Icon(Icons.Default.ArrowForward, contentDescription = "次月")
            }
        }

        HorizontalDivider()

        // ── 日付行リスト ──
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(dayRows, key = { it.dateStr }) { row ->
                MonthDayRowItem(
                    row = row,
                    isSelected = row.dateStr == selectedDate,
                    onClick = { onDateSelected(row.dateStr) }
                )
                HorizontalDivider(thickness = 0.5.dp)
            }
        }
    }
}

@Composable
private fun MonthDayRowItem(
    row: MonthDayRow,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val dateColor = when {
        row.isHoliday  -> Color(0xFFE53935)
        row.isSaturday -> Color(0xFF1E88E5)
        else           -> MaterialTheme.colorScheme.onSurface
    }
    val bgColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        row.isToday -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
        else        -> Color.Transparent
    }
    val lineCount = if (row.taskLines.isEmpty()) 1 else row.taskLines.size

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .clickable(onClick = onClick)
    ) {
        repeat(lineCount) { index ->
            val lineText = row.taskLines.getOrNull(index)
            val zebraColor = if (index % 2 == 0)
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            else
                Color.Transparent
            val textColor = if (lineText == null) {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.onSurface
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(zebraColor)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (index == 0) {
                    Column(
                        modifier = Modifier.width(44.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = row.dayOfMonth.toString(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (row.isToday) FontWeight.Bold else FontWeight.Normal,
                            color = dateColor
                        )
                        Text(
                            text = row.dayOfWeekLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = dateColor.copy(alpha = 0.8f)
                        )
                    }
                } else {
                    Spacer(Modifier.width(44.dp))
                }

                Spacer(Modifier.width(8.dp))

                Text(
                    text = lineText ?: "—",
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor,
                    maxLines = 1
                )
            }
        }
    }
}
