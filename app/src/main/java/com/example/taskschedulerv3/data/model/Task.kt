package com.example.taskschedulerv3.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String? = null,
    val startDate: String,                  // yyyy-MM-dd
    val endDate: String? = null,            // period task only
    val startTime: String? = null,          // HH:mm, null if all-day
    val endTime: String? = null,
    val scheduleType: ScheduleType = ScheduleType.NORMAL,
    val recurrencePattern: RecurrencePattern? = null,
    val recurrenceDays: String? = null,     // comma-separated 1-7
    val recurrenceEndDate: String? = null,
    val priority: Int = 1,                  // 0=high,1=medium,2=low
    val isCompleted: Boolean = false,
    val notifyEnabled: Boolean = true,
    val notifyMinutesBefore: Int = 10,
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
