package com.example.taskschedulerv3.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "task_relations",
    foreignKeys = [
        ForeignKey(entity = Task::class, parentColumns = ["id"], childColumns = ["taskId1"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Task::class, parentColumns = ["id"], childColumns = ["taskId2"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("taskId1"), Index("taskId2"), Index(value = ["taskId1", "taskId2"], unique = true)]
)
data class TaskRelation(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val taskId1: Int,   // always taskId1 < taskId2
    val taskId2: Int
)
