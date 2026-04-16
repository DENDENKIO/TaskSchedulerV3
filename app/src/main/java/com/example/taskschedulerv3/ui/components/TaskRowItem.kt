package com.example.taskschedulerv3.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.taskschedulerv3.data.model.Tag
import com.example.taskschedulerv3.data.model.Task
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

// ────────────────────────────────────────────────
// 優先度カラー (priority: 0=高, 1=中, 2=低)
// ────────────────────────────────────────────────
private fun priorityLineColor(priority: Int): Color = when (priority) {
    0 -> Color(0xFFC0392B)
    1 -> Color(0xFFD4891A)
    2 -> Color(0xFF27AE60)
    else -> Color(0xFFBDBDBD)
}

// ────────────────────────────────────────────────
// 残日数バッジ
// ────────────────────────────────────────────────
data class DaysBadgeInfo(val label: String, val color: Color, val bgColor: Color)

fun calcDaysBadge(startDate: String, isCompleted: Boolean): DaysBadgeInfo {
    if (isCompleted) return DaysBadgeInfo("完了", Color(0xFF9E9E9E), Color(0xFFF0F0F0))
    val today = LocalDate.now()
    val date = try {
        LocalDate.parse(startDate, DateTimeFormatter.ISO_LOCAL_DATE)
    } catch (_: Exception) {
        return DaysBadgeInfo("-", Color(0xFF9E9E9E), Color(0xFFF0F0F0))
    }
    val days = ChronoUnit.DAYS.between(today, date)
    return when {
        days < 0   -> DaysBadgeInfo("期限切れ", Color(0xFF9E9E9E), Color(0xFFEEEEEE))
        days == 0L -> DaysBadgeInfo("今日",      Color(0xFFC0392B), Color(0xFFFDE8E8))
        days == 1L -> DaysBadgeInfo("明日",      Color(0xFFC0392B), Color(0xFFFDE8E8))
        days <= 3  -> DaysBadgeInfo("残${days}日", Color(0xFFC0392B), Color(0xFFFDE8E8))
        days <= 7  -> DaysBadgeInfo("残${days}日", Color(0xFFB7680E), Color(0xFFFEF3E2))
        else       -> DaysBadgeInfo("残${days}日", Color(0xFF1E7E34), Color(0xFFE4F5E9))
    }
}

// ────────────────────────────────────────────────
// 進捗バー色 (進捗は0固定・実装時にTaskにフィールド追加可)
// ────────────────────────────────────────────────
private fun progressColor(progress: Int, isCompleted: Boolean): Color = when {
    isCompleted || progress >= 100 -> Color(0xFF27AE60)
    progress < 30                   -> Color(0xFFC0392B)
    else                            -> Color(0xFF01696F)
}

// ────────────────────────────────────────────────
// タグチップ行 (最大2件 + +n 省略)
// ────────────────────────────────────────────────
@Composable
fun CompactTagRow(tags: List<Tag>, maxVisible: Int = 2) {
    if (tags.isEmpty()) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tags.take(maxVisible).forEach { tag ->
            val bg = try {
                Color(android.graphics.Color.parseColor(tag.color))
            } catch (_: Exception) { Color(0xFFE0E0E0) }
            val fg = if (bg.luminance() > 0.5f) Color(0xFF1A1A1A) else Color(0xFFF5F5F5)
            Text(
                text = tag.name,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                color = fg,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(bg.copy(alpha = 0.22f))
                    .padding(horizontal = 6.dp, vertical = 1.dp),
                maxLines = 1
            )
        }
        if (tags.size > maxVisible) {
            Text(
                text = "+${tags.size - maxVisible}",
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            )
        }
    }
}

// ────────────────────────────────────────────────
// メインのタスク行アイテム
//
// @param progress  進捗率 0-100。Task entity に progress がない間は
//                  呼び出し元から常に 0 を渡してOK。
// @param tags      ViewModelから取得したタグリスト。不明時は emptyList()。
// ────────────────────────────────────────────────
@Composable
fun TaskRowItem(
    task: Task,
    tags: List<Tag> = emptyList(),
    progress: Int = 0,
    onComplete: () -> Unit,
    onClick: () -> Unit
) {
    val badge    = calcDaysBadge(task.startDate, task.isCompleted)
    val priColor = priorityLineColor(task.priority)
    val progColor = progressColor(progress, task.isCompleted)

    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 0.dp, end = 12.dp, top = 7.dp, bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ── 優先度ライン 3dp ──
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(38.dp)
                        .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                        .background(priColor)
                )
                Spacer(Modifier.width(8.dp))

                // ── チェックボックス (18dp) ──
                val checkBg by animateColorAsState(
                    if (task.isCompleted) Color(0xFF01696F) else Color.Transparent,
                    label = "chk_bg"
                )
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(RoundedCornerShape(5.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                if (task.isCompleted) checkBg
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            )
                    )
                    if (task.isCompleted) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "完了",
                            tint = Color.White,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                    // タップ領域を幅広げる　
                    Surface(
                        onClick = onComplete,
                        color = Color.Transparent,
                        modifier = Modifier.matchParentSize()
                    ) {}
                }
                Spacer(Modifier.width(8.dp))

                // ── メイン情報列 ──
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    // 件名
                    Text(
                        text = task.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                        color = if (task.isCompleted)
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                        else
                            MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    // 進捗バー + %
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = { (progress / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .weight(1f)
                                .height(3.dp)
                                .clip(RoundedCornerShape(50)),
                            color = progColor,
                            trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        )
                        Text(
                            text = "${progress}%",
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            modifier = Modifier.width(26.dp),
                            fontWeight = FontWeight.Medium
                        )
                    }
                    // タグ行
                    CompactTagRow(tags = tags, maxVisible = 2)
                }
                Spacer(Modifier.width(8.dp))

                // ── 残日数バッジ ──
                Text(
                    text = badge.label,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = badge.color,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(badge.bgColor)
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                )
            }
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )
        }
    }
}
