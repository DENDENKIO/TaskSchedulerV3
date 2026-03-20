package com.example.taskschedulerv3.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter

object DateUtils {
    val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun today(): String = LocalDate.now().format(formatter)

    fun parse(date: String): LocalDate = LocalDate.parse(date, formatter)

    fun format(date: LocalDate): String = date.format(formatter)

    fun daysInMonth(year: Int, month: Int): Int = LocalDate.of(year, month, 1).lengthOfMonth()

    fun firstDayOfWeek(year: Int, month: Int): Int =
        LocalDate.of(year, month, 1).dayOfWeek.value % 7  // 0=Sun
}
