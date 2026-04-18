package com.example.taskschedulerv3.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 1つの予定（Task）に紐づく中間目標（ステップ）を表す。
 */
@Entity(tableName = "roadmap_steps")
data class RoadmapStep(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val taskId: Int,                    // 親となる Task ID
    val title: String,                  // ステップ名
    val date: String?,                   // 予定日 (yyyy-MM-dd)
    var sortOrder: Int,                 // 並び順
    val isCompleted: Boolean = false,   // 完了フラグ
    val completedAt: Long? = null,      // 完了日時
    val notificationEnabled: Boolean = true,
    val notificationRequestCode: Int? = null
)
