package com.example.taskschedulerv3.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.sp

/**
 * MonthView — 月初〜月末を行リスト形式で表示。
 * 各行: 日付（曜日）＋ タグ別件数チップ（横スクロール対応）
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 日付 + 曜日（固定幅）
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

        Spacer(Modifier.width(8.dp))

        // タグチップ群（横スクロール）
        if (row.tagChips.isEmpty()) {
            // 予定なし
            Text(
                text = "—",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
        } else {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                row.tagChips.forEach { chip ->
                    TagCountChip(chip)
                }
            }
        }
    }
}

@Composable
private fun TagCountChip(chip: TagChip) {
    val bgColor = try {
        Color(android.graphics.Color.parseColor(chip.color))
    } catch (e: Exception) {
        Color(0xFF9E9E9E)
    }
    // Determine text color based on luminance
    val luminance = (0.299f * bgColor.red + 0.587f * bgColor.green + 0.114f * bgColor.blue)
    val textColor = if (luminance > 0.5f) Color.Black else Color.White

    Surface(
        shape = RoundedCornerShape(4.dp),
        color = bgColor,
        modifier = Modifier.height(22.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = chip.label,
                color = textColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            Text(
                text = chip.count.toString(),
                color = textColor.copy(alpha = 0.9f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}
