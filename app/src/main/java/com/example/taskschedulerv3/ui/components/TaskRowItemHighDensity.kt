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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.taskschedulerv3.data.model.Tag
import com.example.taskschedulerv3.ui.schedulelist.TaskListItemUiModel

@Composable
fun TaskRowItemHighDensity(
    uiModel: TaskListItemUiModel,
    tags: List<Tag> = emptyList(),
    onComplete: () -> Unit,
    onClick: () -> Unit
) {
    val task = uiModel.task
    val priColor = when (task.priority) {
        0 -> Color(0xFFC0392B)
        1 -> Color(0xFFD4891A)
        2 -> Color(0xFF27AE60)
        else -> Color(0xFFBDBDBD)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 優先度ライン
                Box(modifier = Modifier.width(3.dp).fillMaxHeight().background(priColor))

                Row(
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 小型円形進捗
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onComplete() }, 
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            progress = { (uiModel.progressPercent / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxSize(),
                            strokeWidth = 2.dp,
                            color = if (task.isCompleted) Color.Gray else MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        
                        if (task.roadmapEnabled && !task.isCompleted) {
                            Text(
                                text = "${uiModel.completedSteps}/${uiModel.totalSteps}",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else if (task.isCompleted) {
                            Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }

                    Spacer(Modifier.width(10.dp))

                    // メイン情報
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = formatTitle(uiModel),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (uiModel.relatedCount > 0) {
                                Text(
                                    " (${uiModel.relatedCount})",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        
                        // 下段：時刻・タグ
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            task.startTime?.let {
                                Text(it, fontSize = 10.sp, color = Color.Gray)
                            }
                            if (tags.isNotEmpty()) {
                                CompactTagRow(tags = tags.take(2))
                            }
                        }
                    }
                }
            }
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        }
    }
}

@Composable
private fun formatTitle(uiModel: TaskListItemUiModel): AnnotatedString {
    val title = uiModel.task.title
    val label = uiModel.activeStageLabel
    
    return buildAnnotatedString {
        append(uiModel.emoji)
        append(" ")
        if (label != null) {
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold)) {
                append("【$label】")
            }
        }
        append(title)
    }
}
