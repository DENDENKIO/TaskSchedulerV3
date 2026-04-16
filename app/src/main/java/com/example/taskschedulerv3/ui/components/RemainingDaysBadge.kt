package com.example.taskschedulerv3.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable
fun RemainingDaysBadge(
    startDate: String?,
    isCompleted: Boolean,
    modifier: Modifier = Modifier
) {
    if (isCompleted) {
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = Color(0xFF2A2A2A),
            modifier = modifier
        ) {
            Text(
                text = "完了",
                fontSize = 9.sp,
                color = Color(0xFF808080),
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
            )
        }
        return
    }

    if (startDate.isNullOrBlank()) return

    val days = try {
        val d = LocalDate.parse(startDate)
        ChronoUnit.DAYS.between(LocalDate.now(), d)
    } catch (_: Exception) { return }

    val (label, bgColor, textColor) = when {
        days < 0  -> Triple("期限切れ", Color(0xFF4A1A1A), Color(0xFFF46060))
        days == 0L -> Triple("今日",    Color(0xFF4A1A1A), Color(0xFFF46060))
        days <= 3L -> Triple("${days}日後", Color(0xFF3D2800), Color(0xFFF0A030))
        days <= 7L -> Triple("${days}日後", Color(0xFF232040), Color(0xFF9B8FFF))
        else       -> Triple("${days}日後", Color(0xFF202020), Color(0xFF757575))
    }

    Surface(
        shape = RoundedCornerShape(4.dp),
        color = bgColor,
        modifier = modifier
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            color = textColor,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
        )
    }
}
