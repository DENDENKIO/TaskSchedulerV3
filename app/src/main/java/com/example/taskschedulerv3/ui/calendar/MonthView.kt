package com.example.taskschedulerv3.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 1日セルの固定幅
private val CELL_W: Dp = 56.dp
// ガントチャート1行の高さ
private val GANTT_ROW_H: Dp = 24.dp

/**
 * MonthView — 日付を横並び(列方向)、予定を縦方向(行方向)に表示。
 * 期間予定(PERIOD)はカレンダー下部にガントチャート形式で行追加表示。
 */
@Composable
fun MonthView(
    year: Int,
    month: Int,
    selectedDate: String,
    dayRows: List<MonthDayRow>,
    ganttRows: List<GanttRow>,
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

        LazyColumn(modifier = Modifier.fillMaxSize()) {

            // ── 日付横並びグリッド ──
            item {
                HorizontalDateGrid(
                    dayRows = dayRows,
                    selectedDate = selectedDate,
                    onDateSelected = onDateSelected
                )
            }

            // ── ガントチャートセクション ──
            if (ganttRows.isNotEmpty()) {
                item {
                    HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
                    Text(
                        text = "期間予定",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 2.dp)
                    )
                }
                items(ganttRows, key = { it.task.id }) { row ->
                    GanttChartRow(
                        ganttRow = row,
                        dayRows = dayRows
                    )
                }
            }
        }
    }
}

// ── 日付横並びグリッド ──────────────────────────────────────────
@Composable
private fun HorizontalDateGrid(
    dayRows: List<MonthDayRow>,
    selectedDate: String,
    onDateSelected: (String) -> Unit
) {
    val scrollState = rememberScrollState()
    // 曜日ヘッダー + 日付セルを横スクロール
    Row(
        modifier = Modifier
            .horizontalScroll(scrollState)
            .padding(vertical = 4.dp)
    ) {
        dayRows.forEach { row ->
            DayCell(
                row = row,
                isSelected = row.dateStr == selectedDate,
                onClick = { onDateSelected(row.dateStr) }
            )
        }
    }
}

// ── 1日セル（日付 + 予定縦書き） ────────────────────────────────
@Composable
private fun DayCell(
    row: MonthDayRow,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val dateColor = when {
        row.isHoliday  -> Color(0xFFE53935)
        row.isSaturday -> Color(0xFF1E88E5)
        else           -> MaterialTheme.colorScheme.onSurface
    }

    // 期間予定がある日はセル背景をハイライト
    val cellBg = when {
        isSelected       -> MaterialTheme.colorScheme.primaryContainer
        row.isToday      -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)
        row.hasRangeTask -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
        else             -> Color.Transparent
    }

    Column(
        modifier = Modifier
            .width(CELL_W)
            .background(cellBg)
            .border(
                width = if (isSelected) 1.5.dp else 0.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 曜日ラベル
        Text(
            text = row.dayOfWeekLabel,
            style = MaterialTheme.typography.labelSmall,
            color = dateColor.copy(alpha = 0.75f),
            fontSize = 9.sp
        )
        // 日付番号
        Box(
            modifier = Modifier
                .size(28.dp)
                .then(
                    if (row.isToday) Modifier.clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiary)
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = row.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (row.isToday) FontWeight.Bold else FontWeight.Normal,
                color = if (row.isToday) MaterialTheme.colorScheme.onTertiary else dateColor
            )
        }

        // ── 予定を縦方向に列挙（スポット予定のみ） ──
        if (row.taskLines.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            row.taskLines.take(3).forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 8.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(2.dp)
                        )
                        .padding(horizontal = 2.dp, vertical = 1.dp)
                )
            }
            // 3件超の場合は件数表示
            if (row.taskLines.size > 3) {
                Text(
                    text = "+${row.taskLines.size - 3}",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 8.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }

        // 期間予定インジケーター（下部ドット）
        if (row.hasRangeTask) {
            Spacer(Modifier.height(2.dp))
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary)
            )
        }
    }
}

// ── ガントチャート1行 ─────────────────────────────────────────
@Composable
private fun GanttChartRow(
    ganttRow: GanttRow,
    dayRows: List<MonthDayRow>
) {
    val scrollState = rememberScrollState()
    val ganttColor = MaterialTheme.colorScheme.primary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(GANTT_ROW_H + 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // タスク名ラベル（固定幅）
        Text(
            text = ganttRow.task.title,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .width(80.dp)
                .padding(start = 8.dp, end = 4.dp)
        )

        // 横スクロール同期（日付グリッドと幅を合わせる）
        Row(
            modifier = Modifier
                .horizontalScroll(scrollState)
        ) {
            dayRows.forEach { dayRow ->
                val isActive = dayRow.dateStr in ganttRow.activeDatesInMonth
                val isStart  = isActive && dayRows
                    .getOrNull(dayRows.indexOfFirst { it.dateStr == dayRow.dateStr } - 1)
                    ?.dateStr !in ganttRow.activeDatesInMonth
                val isEnd    = isActive && dayRows
                    .getOrNull(dayRows.indexOfFirst { it.dateStr == dayRow.dateStr } + 1)
                    ?.dateStr !in ganttRow.activeDatesInMonth

                val shape = when {
                    isStart && isEnd -> RoundedCornerShape(50)
                    isStart          -> RoundedCornerShape(topStart = 50f, bottomStart = 50f, topEnd = 0f, bottomEnd = 0f)
                    isEnd            -> RoundedCornerShape(topStart = 0f, bottomStart = 0f, topEnd = 50f, bottomEnd = 50f)
                    isActive         -> RoundedCornerShape(0)
                    else             -> RoundedCornerShape(0)
                }

                Box(
                    modifier = Modifier
                        .width(CELL_W)
                        .height(GANTT_ROW_H)
                        .padding(
                            top = 2.dp, bottom = 2.dp,
                            start = if (isStart) 4.dp else 0.dp,
                            end   = if (isEnd)   4.dp else 0.dp
                        )
                        .clip(shape)
                        .background(if (isActive) ganttColor.copy(alpha = 0.75f) else Color.Transparent)
                )
            }
        }
    }

    HorizontalDivider(
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    )
}
