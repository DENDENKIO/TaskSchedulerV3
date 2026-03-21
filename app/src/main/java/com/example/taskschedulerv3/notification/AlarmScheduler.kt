package com.example.taskschedulerv3.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.taskschedulerv3.data.model.ScheduleType
import com.example.taskschedulerv3.data.model.Task
import com.example.taskschedulerv3.util.DateUtils
import com.example.taskschedulerv3.util.RecurrenceCalculator
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object AlarmScheduler {

    /**
     * Schedule the alarm for a task. Handles NORMAL, PERIOD, and RECURRING tasks.
     * For RECURRING, schedules the next occurrence after now.
     */
    fun scheduleForTask(context: Context, task: Task) {
        if (!task.notifyEnabled) {
            cancel(context, task.id)
            return
        }

        val triggerMillis = calculateNextTriggerMillis(task) ?: run {
            cancel(context, task.id)
            return
        }

        schedule(context, task, triggerMillis)
    }

    /**
     * Calculate the next alarm trigger time in millis.
     * Returns null if no future alarm is applicable.
     */
    fun calculateNextTriggerMillis(task: Task): Long? {
        val now = LocalDateTime.now()

        return when (task.scheduleType) {
            ScheduleType.NORMAL, ScheduleType.PERIOD -> {
                val date = DateUtils.parse(task.startDate)
                val time = task.startTime?.let { parseTime(it) } ?: LocalTime.of(9, 0)
                val triggerTime = LocalDateTime.of(date, time)
                    .minusMinutes(task.notifyMinutesBefore.toLong())
                if (triggerTime.isAfter(now)) toMillis(triggerTime) else null
            }
            ScheduleType.RECURRING -> {
                return calculateNextTriggerMillisAfter(task, LocalDate.now().minusDays(1))
            }
        }
    }

    /**
     * Find the next trigger millis for a RECURRING task after [searchFrom] date.
     * Searches for the next occurrence date strictly after searchFrom,
     * then picks the one whose trigger time (startTime - notifyBefore) is in the future.
     */
    fun calculateNextTriggerMillisAfter(task: Task, searchFrom: LocalDate): Long? {
        val now = LocalDateTime.now()
        val time = task.startTime?.let { parseTime(it) } ?: LocalTime.of(9, 0)
        var cursor = searchFrom
        // Search up to 400 days ahead
        val limit = searchFrom.plusDays(400)
        while (!cursor.isAfter(limit)) {
            val candidate = RecurrenceCalculator.nextOccurrenceAfter(task, cursor) ?: return null
            val triggerTime = LocalDateTime.of(candidate, time)
                .minusMinutes(task.notifyMinutesBefore.toLong())
            if (triggerTime.isAfter(now)) return toMillis(triggerTime)
            // Trigger time already passed for this occurrence — try the next one
            cursor = candidate
        }
        return null
    }

    /** Schedule a raw alarm at the given millis (used by AlarmReceiver re-schedule). */
    fun scheduleRaw(context: Context, task: Task, triggerAtMillis: Long) {
        schedule(context, task, triggerAtMillis)
    }

    private fun schedule(context: Context, task: Task, triggerAtMillis: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = buildPendingIntent(context, task)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    fun cancel(context: Context, taskId: Int) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, taskId, intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pendingIntent?.let { alarmManager.cancel(it) }
    }

    private fun buildPendingIntent(context: Context, task: Task): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("taskId", task.id)
            putExtra("title", task.title)
            putExtra("scheduleType", task.scheduleType.name)
            val timeLabel = task.startTime ?: ""
            val minutesBefore = task.notifyMinutesBefore
            val msg = if (minutesBefore > 0) "${task.title}の${minutesBefore}分前です"
                      else "${task.title}の時間です"
            putExtra("message", msg)
        }
        return PendingIntent.getBroadcast(
            context, task.id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun parseTime(timeStr: String): LocalTime {
        return try {
            val parts = timeStr.split(":")
            LocalTime.of(parts[0].toInt(), parts[1].toInt())
        } catch (e: Exception) {
            LocalTime.of(9, 0)
        }
    }

    private fun toMillis(dt: LocalDateTime): Long =
        dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
