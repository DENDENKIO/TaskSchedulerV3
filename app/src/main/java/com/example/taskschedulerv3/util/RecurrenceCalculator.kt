package com.example.taskschedulerv3.util

import com.example.taskschedulerv3.data.model.RecurrencePattern
import com.example.taskschedulerv3.data.model.Task
import java.time.LocalDate

object RecurrenceCalculator {
    /**
     * Returns true if the recurring task should occur on the given date.
     */
    fun occursOn(task: Task, date: LocalDate): Boolean {
        val start = LocalDate.parse(task.startDate, DateUtils.formatter)
        if (date.isBefore(start)) return false
        task.recurrenceEndDate?.let {
            if (date.isAfter(LocalDate.parse(it, DateUtils.formatter))) return false
        }
        return when (task.recurrencePattern) {
            RecurrencePattern.DAILY -> true
            RecurrencePattern.WEEKLY -> {
                val days = task.recurrenceDays?.split(",")?.map { it.trim().toInt() } ?: listOf(start.dayOfWeek.value)
                days.contains(date.dayOfWeek.value)
            }
            RecurrencePattern.BIWEEKLY -> {
                val daysDiff = start.until(date, java.time.temporal.ChronoUnit.DAYS)
                daysDiff % 14 == 0L
            }
            RecurrencePattern.MONTHLY_DATE -> date.dayOfMonth == start.dayOfMonth
            RecurrencePattern.MONTHLY_WEEK -> {
                val weekOfMonth = (date.dayOfMonth - 1) / 7 + 1
                val startWeek = (start.dayOfMonth - 1) / 7 + 1
                weekOfMonth == startWeek && date.dayOfWeek == start.dayOfWeek
            }
            RecurrencePattern.YEARLY -> date.monthValue == start.monthValue && date.dayOfMonth == start.dayOfMonth
            null -> false
        }
    }
}
