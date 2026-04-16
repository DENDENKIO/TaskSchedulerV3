package com.example.taskschedulerv3.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

import com.example.taskschedulerv3.ui.theme.priorityColor

@Composable
fun PriorityBadge(priority: Int) {
    val color = priorityColor(priority, isDark = true)
    val label = when (priority) {
        0    -> "高"
        2    -> "低"
        else -> "中"
    }
    Surface(
        color = color,
        shape = MaterialTheme.shapes.extraSmall
    ) {
        Text(
            text = label,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall
        )
    }
}
