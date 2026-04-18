package com.example.taskschedulerv3.ui.schedulelist

import androidx.compose.ui.graphics.Color
import com.example.taskschedulerv3.data.model.Task

/**
 * 一覧表示専用のデータモデル。
 * ロードマップの現在の段階や親子関係の件数など、表示に必要な情報を集約する。
 */
data class TaskListItemUiModel(
    val task: Task,
    val displayTitle: String,
    val displayDate: String,
    val progressPercent: Int,
    val emoji: String,
    val relatedCount: Int = 0,
    val isRoadmapTask: Boolean = false,
    val activeStageLabel: String? = null,
    val activeStageColor: Color = Color.Unspecified,
    val completedSteps: Int = 0,
    val totalSteps: Int = 0
)
