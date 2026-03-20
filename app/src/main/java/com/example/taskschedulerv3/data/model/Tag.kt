package com.example.taskschedulerv3.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tags",
    foreignKeys = [
        ForeignKey(
            entity = Tag::class,
            parentColumns = ["id"],
            childColumns = ["parentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("parentId")]
)
data class Tag(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val color: String,          // #RRGGBB
    val level: Int,             // 1=large, 2=medium, 3=small
    val parentId: Int? = null,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
