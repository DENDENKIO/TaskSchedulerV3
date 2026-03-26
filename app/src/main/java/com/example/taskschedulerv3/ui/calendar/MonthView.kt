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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val CELL_W: Dp = 56.dp
private val GANTT_ROW_H: Dp = 26.dp

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

        // ヘッダー
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

        // 横スクロールを全セクションで共有する
        val sharedScroll = rememberScrollState()

        LazyColumn(modifier = Modifier.fillMaxSize()) {

            // 日付横並びグリッド
            item {
                Row(
                    modifier = Modifier
                        .horizontalScroll(sharedScroll)
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

            // ガントチャートセクション
            if (ganttRows.isNotEmpty()) {
                item {
                    HorizontalDivider(modifier = Modifier.padding(top = 2.dp))
                    Text(
                        text = "予定一覧",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 2.dp)
                    )
                }
                items(ganttRows, key = { it.task.id }) { row ->
                    GanttChartRow(
                        ganttRow = row,
                        dayRows = dayRows,
                        sharedScroll = sharedScroll
                    )
                }
            }
        }
    }
}

// ── 日付セル（予定縦書きなし・ドットインジケーターのみ） ──
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

    val cellBg = when {
        isSelected       -> MaterialTheme.colorScheme.primaryContainer
        row.isToday      -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)
        row.hasRangeTask -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
        else             -> Color.Transparent
    }

    Column(
        modifier = Modifier
            .width(CELL_W)
            .background(cellBg)
            .border(
                width = if (isSelected) 1.5.dp else 0.dp,
                color  = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 曜日ラベル
        Text(
            text  = row.dayOfWeekLabel,
            style = MaterialTheme.typography.labelSmall,
            color = dateColor.copy(alpha = 0.75f),
            fontSize = 9.sp
        )
        // 日付番号（今日は円形バッジ）
        Box(
            modifier = Modifier
                .size(28.dp)
                .then(
                    if (row.isToday) Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiary)
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text  = row.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (row.isToday) FontWeight.Bold else FontWeight.Normal,
                color = if (row.isToday) MaterialTheme.colorScheme.onTertiary else dateColor
            )
        }
        // ガント行ありの日は下部にドット
        if (row.hasRangeTask) {
            Spacer(Modifier.height(3.dp))
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary)
            )
        }
    }
}

// ── ガントチャート1行（左端=開始日、中間=・、右端=終了日） ──
@Composable
private fun GanttChartRow(
    ganttRow: GanttRow,
    dayRows: List<MonthDayRow>,
    sharedScroll: androidx.compose.foundation.ScrollState
) {
    val ganttColor   = MaterialTheme.colorScheme.primary
    val onGanttColor = MaterialTheme.colorScheme.onPrimary

    // 開始・終了の日の番号（dd）
    val startDay = remember(ganttRow.startDateInMonth) {
        ganttRow.startDateInMonth.takeLast(2).trimStart('0').ifEmpty { "0" }
    }
    val endDay = remember(ganttRow.endDateInMonth) {
        ganttRow.endDateInMonth.takeLast(2).trimStart('0').ifEmpty { "0" }
    }
    val isSingleDay = ganttRow.startDateInMonth == ganttRow.endDateInMonth

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(GANTT_ROW_H + 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // タスク名ラベル（固定80dp）
        Text(
            text  = ganttRow.task.title,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .width(80.dp)
                .padding(start = 8.dp, end = 4.dp)
        )

        // 日付グリッドと幅を共有スクロールで同期
        Row(modifier = Modifier.horizontalScroll(sharedScroll)) {
            dayRows.forEachIndexed { idx, dayRow ->
                val isActive = dayRow.dateStr in ganttRow.activeDatesInMonth
                val isStart  = dayRow.dateStr == ganttRow.startDateInMonth
                val isEnd    = dayRow.dateStr == ganttRow.endDateInMonth

                // セル形状
                val shape = when {
                    isSingleDay          -> RoundedCornerShape(50)
                    isStart              -> RoundedCornerShape(topStart = 50f, bottomStart = 50f, topEnd = 0f, bottomEnd = 0f)
                    isEnd                -> RoundedCornerShape(topStart = 0f, bottomStart = 0f, topEnd = 50f, bottomEnd = 50f)
                    isActive             -> RoundedCornerShape(0)
                    else                 -> RoundedCornerShape(0)
                }

                Box(
                    modifier = Modifier
                        .width(CELL_W)
                        .height(GANTT_ROW_H)
                        .padding(
                            top    = 2.dp,
                            bottom = 2.dp,
                            start  = if (isStart) 4.dp else 0.dp,
                            end    = if (isEnd)   4.dp else 0.dp
                        )
                        .clip(shape)
                        .background(if (isActive) ganttColor.copy(alpha = 0.82f) else Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    if (isActive) {
                        val label = when {
                            // 単日予定: その日のみ日付表示
                            isSingleDay -> startDay
                            // 開始日セル: 左端に開始日
                            isStart     -> startDay
                            // 終了日セル: 右端に終了日
                            isEnd       -> endDay
                            // 中間セル: 「・」
                            else        -> "・"
                        }
                        Text(
                            text  = label,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            color = onGanttColor,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }

    HorizontalDivider(
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    )
}
