package com.example.taskschedulerv3.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 写真撮影後の仮登録用エンティティ。
 * status: DRAFT / CONVERTED / DISCARDED
 */
@Entity(tableName = "quick_draft_tasks")
data class QuickDraftTask(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String? = null,
    val photoPath: String? = null,
    val ocrText: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val status: String = "DRAFT",  // DRAFT / CONVERTED / DISCARDED
    /** カンマ区切りのタグID文字列。例: "1,3,7" */
    val tagIds: String? = null,
    
    // AI解析結果を保持するフィールド
    val startDate: String? = null,
    val startTime: String? = null,
    val endTime: String? = null
)
