package com.example.taskschedulerv3.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import com.example.taskschedulerv3.data.model.Task

object DateUtils {
    val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun today(): String = LocalDate.now().format(formatter)

    fun parse(date: String): LocalDate = LocalDate.parse(date, formatter)

    fun format(date: LocalDate): String = date.format(formatter)

    fun daysInMonth(year: Int, month: Int): Int = LocalDate.of(year, month, 1).lengthOfMonth()

    fun firstDayOfWeek(year: Int, month: Int): Int =
        LocalDate.of(year, month, 1).dayOfWeek.value % 7  // 0=Sun

    fun calculateRemainingDays(task: Task): Int {
        if (task.isIndefinite) return Int.MAX_VALUE
        val targetDateStr: String = task.endDate ?: task.startDate
        if (targetDateStr.isEmpty()) return Int.MAX_VALUE
        
        return try {
            val targetDate = LocalDate.parse(targetDateStr, formatter)
            val today = LocalDate.now()
            ChronoUnit.DAYS.between(today, targetDate).toInt()
        } catch (_: Exception) {
            Int.MAX_VALUE
        }
    }
}
