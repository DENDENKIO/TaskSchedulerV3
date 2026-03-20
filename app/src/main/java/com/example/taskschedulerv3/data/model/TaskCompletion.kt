package com.example.taskschedulerv3.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "task_completions",
    foreignKeys = [
        ForeignKey(entity = Task::class, parentColumns = ["id"], childColumns = ["taskId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("taskId"), Index(value = ["taskId", "completedDate"], unique = true)]
)
data class TaskCompletion(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val taskId: Int,
    val completedDate: String   // yyyy-MM-dd
)
