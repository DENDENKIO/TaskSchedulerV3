package com.example.taskschedulerv3.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.taskschedulerv3.data.model.PhotoMemo
import com.example.taskschedulerv3.data.model.Tag
import com.example.taskschedulerv3.data.model.Task
import com.example.taskschedulerv3.ui.theme.OnTaskCardBg
import com.example.taskschedulerv3.ui.theme.OnTaskCardBgVariant
import com.example.taskschedulerv3.ui.theme.TaskCardBg
import com.example.taskschedulerv3.ui.theme.priorityColor

@Composable
fun TaskCard(
    task: Task,
    tags: List<Tag>,
    photos: List<PhotoMemo>,
    remainingDays: Int,
    onChecked: (Boolean) -> Unit,
    onCardClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCardClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = TaskCardBg // 指定通りの暗い青
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = task.isCompleted,
                        onCheckedChange = onChecked,
                        modifier = Modifier.size(24.dp),
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary,
                            uncheckedColor = OnTaskCardBgVariant.copy(alpha = 0.5f),
                            checkmarkColor = Color.White
                        )
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        ),
                        textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = if (task.isCompleted) OnTaskCardBg.copy(alpha = 0.4f) 
                                else OnTaskCardBg
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (task.startTime != null) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = OnTaskCardBg.copy(alpha = 0.1f)
                        ) {
                            Text(
                                text = "${task.startTime}${if (task.endTime != null) "〜${task.endTime}" else ""}",
                                style = MaterialTheme.typography.labelMedium,
                                color = OnTaskCardBgVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (remainingDays != Int.MAX_VALUE) {
                        RemainingDaysChip(days = remainingDays, isParentDark = true)
                    }
                }

                if (tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(tags) { tag ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = OnTaskCardBg.copy(alpha = 0.08f),
                                border = null
                            ) {
                                Text(
                                    tag.name, 
                                    style = MaterialTheme.typography.labelSmall,
                                    color = OnTaskCardBgVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
                if (photos.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    PhotoThumbnailRow(photos = photos, maxShow = 3)
                }
            }

            // 右端の優先度カラーストライプ
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .matchParentSize()
                    .align(Alignment.CenterEnd)
                    .background(priorityColor(task.priority, isDark = true))
            )

            // Override click on thumbnails if onPhotoClick is handled (handled by passing an action if needed)
            // But wait, TaskCard doesn't have navController natively.
            // (If used, it relies on navigation upstream. We just ensure PhotoThumbnailRow can take it).
        }
    }
}
