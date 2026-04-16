package com.example.taskschedulerv3.ui.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.taskschedulerv3.ui.theme.*

@Composable
fun RemainingDaysChip(days: Int, modifier: Modifier = Modifier, isParentDark: Boolean = false) {
    val isDark = isSystemInDarkTheme() || isParentDark
    val baseColor = remainingDaysColor(days, isDark)
    
    // 背景用カラーの設定 (daysに応じて調整) - Chic 渋め
    val (bgColor, textColor, label) = when {
        days < 0 -> Triple(if (isParentDark) ChicMutedRedBg else Color(0xFFF2EAEA), Color(0xFF9E9E9E), "期限切れ ${-days}d")
        days == 0 -> Triple(if (isParentDark) ChicMutedRedBg else Color(0xFFF2EAEA), Color(0xFFB35A5A), "今日")
        days <= 2 -> Triple(if (isParentDark) ChicMutedAmberBg else Color(0xFFF2EFEA), Color(0xFFB38B4D), "残り ${days}d")
        days <= 7 -> Triple(if (isParentDark) ChicMutedBlueBg else Color(0xFFEAEEF2), Color(0xFF5D707E), "残り ${days}d")
        else -> Triple(if (isParentDark) Color.White.copy(alpha = 0.05f) else Color(0xFFECEFF1), Color(0xFF78909C), "残り ${days}d")
    }

    Surface(
        shape = RoundedCornerShape(50),
        color = if (isDark) bgColor.copy(alpha = 0.25f) else bgColor,
        modifier = modifier.height(22.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isDark) baseColor else textColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}
