package com.example.taskschedulerv3.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.taskschedulerv3.data.model.Tag
import com.example.taskschedulerv3.data.model.Task
import java.time.format.DateTimeFormatter

/**
 * 第4弾: 刷新されたタスクリスト行
 * - 左端に優先度カラーライン (3dp幅)
 * - タイトル・時刻・タグチップ
 * - 右端に残日数バッジ
 * - 下部に細い進捗バー
 * - 完了時にmuted化
 */

private fun priorityColor(priority: Int): Color = when (priority) {
    0 -> Color(0xFFF46060)  // 高: 赤
    1 -> Color(0xFFF0A030)  // 中: 橙
    2 -> Color(0xFF3DD68C)  // 低: 緑
    else -> Color(0xFF555566)
}

@Composable
fun TaskListRow(
    task: Task,
    tags: List<Tag>,
    onClick: () -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val muted = task.isCompleted
    val mutedAlpha = if (muted) 0.4f else 1f
    val taskTags = tags.filter { tag -> /* tag matching done by caller */ false }  // placeholder

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // 左端: 優先度カラーライン
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(
                        if (muted) Color(0xFF3A3A3A)
                        else priorityColor(task.priority)
                    )
            )

            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium,
                            textDecoration = if (muted) TextDecoration.LineThrough else null
                        ),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = mutedAlpha),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    RemainingDaysBadge(
                        startDate = task.startDate,
                        isCompleted = task.isCompleted
                    )
                }

                Spacer(Modifier.height(3.dp))

                // 時刻 + タグ行
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (!task.startTime.isNullOrBlank()) {
                        Text(
                            text = task.startTime!!,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = mutedAlpha)
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                // 進捗バー
                val progress = task.progress / 100f
                LinearProgressIndicator(
                    progress = { if (task.isCompleted) 1f else progress },
                    modifier = Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(2.dp)),
                    color = if (muted) Color(0xFF3A3A3A) else priorityColor(task.priority).copy(alpha = 0.7f),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            // チェックボタン
            IconButton(
                onClick = onComplete,
                modifier = Modifier.align(Alignment.CenterVertically).size(36.dp)
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = if (muted) "未完了に戻す" else "完了",
                    tint = if (muted) Color(0xFF43A047) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )
    }
}
