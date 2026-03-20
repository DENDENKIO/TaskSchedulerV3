package com.example.taskschedulerv3.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "photo_memos",
    foreignKeys = [
        ForeignKey(entity = Task::class, parentColumns = ["id"], childColumns = ["taskId"], onDelete = ForeignKey.SET_NULL)
    ],
    indices = [Index("taskId")]
)
data class PhotoMemo(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val taskId: Int? = null,
    val date: String,           // yyyy-MM-dd
    val title: String? = null,
    val memo: String? = null,
    val imagePath: String,
    val createdAt: Long = System.currentTimeMillis()
)
