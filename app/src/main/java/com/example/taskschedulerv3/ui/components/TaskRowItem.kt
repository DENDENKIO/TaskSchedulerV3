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
    if (isCompleted) return DaysBadgeInfo("完了", Color(0xFF78909C), Color(0xFFECEFF1))
    val today = LocalDate.now()
    val date = try {
        LocalDate.parse(startDate, DateTimeFormatter.ISO_LOCAL_DATE)
    } catch (_: Exception) {
        return DaysBadgeInfo("-", Color(0xFF9E9E9E), Color(0xFFF0F0F0))
    }
    val days = ChronoUnit.DAYS.between(today, date)
    return when {
         days < 0   -> DaysBadgeInfo("期限切れ", Color(0xFF9E9E9E), Color(0xFFECEFF1))
         days == 0L -> DaysBadgeInfo("今日",      Color(0xFFB35A5A), Color(0xFFF2EAEA))
         days == 1L -> DaysBadgeInfo("明日",      Color(0xFFB35A5A), Color(0xFFF2EAEA))
         days <= 3  -> DaysBadgeInfo("残${days}日", Color(0xFFB38B4D), Color(0xFFF2EFEA))
         days <= 7  -> DaysBadgeInfo("残${days}日", Color(0xFF5D707E), Color(0xFFEAEEF2))
         else       -> DaysBadgeInfo("残${days}日", Color(0xFF78909C), Color(0xFFECEFF1))
    }
}

// ────────────────────────────────────────────────
// 進捗バー色
// ────────────────────────────────────────────────
private fun progressColor(progress: Int, isCompleted: Boolean): Color = when {
    isCompleted || progress >= 100 -> Color(0xFF27AE60)
    progress < 30                   -> Color(0xFFC0392B)
    else                            -> Color(0xFF01696F)
}

// ────────────────────────────────────────────────
// タグチップ行
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

@Composable
fun TaskRowItem(
    task: Task,
    tags: List<Tag> = emptyList(),
    onComplete: () -> Unit,
    onClick: () -> Unit
) {
    val badge    = calcDaysBadge(task.startDate, task.isCompleted)
    val priColor = priorityLineColor(task.priority)
    val progColor = progressColor(task.progress, task.isCompleted)
    val mutedColor = Color(0xFF7A7A8C)

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ── 優先度ライン (左端 3dp) ──
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .background(priColor)
                )

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // ── チェックボックス ──
                    val checkBg by animateColorAsState(
                        if (task.isCompleted) Color(0xFF01696F) else Color.Transparent,
                        label = "chk_bg"
                    )
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (task.isCompleted) checkBg
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (task.isCompleted) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "完了",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Surface(
                            onClick = onComplete,
                            color = Color.Transparent,
                            modifier = Modifier.fillMaxSize()
                        ) {}
                    }
                    Spacer(Modifier.width(12.dp))

                    // ── メイン情報 ──
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = task.title,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                                color = if (task.isCompleted) mutedColor else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${task.progress}%",
                                fontSize = 10.sp,
                                color = if (task.isCompleted) mutedColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                        
                        Spacer(Modifier.height(4.dp))
                        
                        LinearProgressIndicator(
                            progress = { (task.progress / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(50)),
                            color = progColor,
                            trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )
                        
                        if (tags.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            CompactTagRow(tags = tags)
                        }
                    }
                    Spacer(Modifier.width(12.dp))

                    // ── 右端バッジ ──
                    Text(
                        text = badge.label,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (task.isCompleted) mutedColor else badge.color,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (task.isCompleted) MaterialTheme.colorScheme.surfaceVariant else badge.bgColor)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        }
    }
}
