package com.example.taskschedulerv3.util

import com.example.taskschedulerv3.data.model.RecurrencePattern
import com.example.taskschedulerv3.data.model.Task
import java.time.LocalDate
import java.time.temporal.ChronoUnit

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
                val days = task.recurrenceDays?.split(",")?.map { it.trim().toInt() }
                    ?: listOf(start.dayOfWeek.value)
                days.contains(date.dayOfWeek.value)
            }
            RecurrencePattern.BIWEEKLY -> {
                val days = task.recurrenceDays?.split(",")?.map { it.trim().toInt() }
                    ?: listOf(start.dayOfWeek.value)
                if (!days.contains(date.dayOfWeek.value)) return false
                // Same day-of-week as start: check if it's an even number of weeks away
                val startWeekMonday = start.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                val dateWeekMonday = date.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                ChronoUnit.WEEKS.between(startWeekMonday, dateWeekMonday) % 2 == 0L
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

    /**
     * Returns all dates in [rangeStart, rangeEnd] on which a RECURRING task occurs.
     */
    fun getOccurrencesInRange(task: Task, rangeStart: LocalDate, rangeEnd: LocalDate): List<LocalDate> {
        if (task.recurrencePattern == null) return emptyList()
        val start = LocalDate.parse(task.startDate, DateUtils.formatter)
        val effectiveStart = if (start.isBefore(rangeStart)) rangeStart else start
        val endLimit = task.recurrenceEndDate?.let {
            val d = LocalDate.parse(it, DateUtils.formatter)
            if (d.isBefore(rangeEnd)) d else rangeEnd
        } ?: rangeEnd

        val result = mutableListOf<LocalDate>()
        var cursor = effectiveStart
        while (!cursor.isAfter(endLimit)) {
            if (occursOn(task, cursor)) result.add(cursor)
            cursor = cursor.plusDays(1)
        }
        return result
    }

    /**
     * Returns all dates in [rangeStart, rangeEnd] that a PERIOD task spans.
     */
    fun getPeriodDatesInRange(task: Task, rangeStart: LocalDate, rangeEnd: LocalDate): List<LocalDate> {
        val start = LocalDate.parse(task.startDate, DateUtils.formatter)
        val end = task.endDate?.let { LocalDate.parse(it, DateUtils.formatter) } ?: start
        val effectiveStart = if (start.isBefore(rangeStart)) rangeStart else start
        val effectiveEnd = if (end.isAfter(rangeEnd)) rangeEnd else end
        if (effectiveStart.isAfter(effectiveEnd)) return emptyList()
        val result = mutableListOf<LocalDate>()
        var cursor = effectiveStart
        while (!cursor.isAfter(effectiveEnd)) {
            result.add(cursor)
            cursor = cursor.plusDays(1)
        }
        return result
    }

    /**
     * Returns the next occurrence of a recurring task strictly after [after].
     * Returns null if no future occurrence exists.
     */
    fun nextOccurrenceAfter(task: Task, after: LocalDate): LocalDate? {
        val endLimit = task.recurrenceEndDate?.let { LocalDate.parse(it, DateUtils.formatter) }
        var cursor = after.plusDays(1)
        // Limit search to 400 days to avoid infinite loop
        val maxSearch = after.plusDays(400)
        while (!cursor.isAfter(maxSearch)) {
            if (endLimit != null && cursor.isAfter(endLimit)) return null
            if (occursOn(task, cursor)) return cursor
            cursor = cursor.plusDays(1)
        }
        return null
    }
}
