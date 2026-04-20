package com.example.taskschedulerv3.ui.aichat

import com.example.taskschedulerv3.data.model.RecurrencePattern
import com.example.taskschedulerv3.data.model.ScheduleType

/**
 * AIチャットでのタスク登録用データモデル。
 * ユーザーとの会話で段階的に組み立てていく。
 */
data class DraftTaskData(
    val title: String = "",
    val description: String? = null,
    val scheduleType: ScheduleType = ScheduleType.NORMAL,
    val startDate: String = "",           // yyyy-MM-dd
    val endDate: String? = null,          // PERIOD用
    val startTime: String? = null,        // HH:mm
    val endTime: String? = null,
    val isIndefinite: Boolean = false,
    val notifyEnabled: Boolean = true,
    val notifyMinutesBefore: Int = 60,
    val recurrencePattern: RecurrencePattern? = null,
    val recurrenceDays: String? = null,
    val recurrenceEndDate: String? = null,
    val roadmapEnabled: Boolean = false,
    val roadmapSteps: List<DraftRoadmapStep> = emptyList(),
    val tagIds: List<Int> = emptyList(),
    val relatedTaskIds: List<Int> = emptyList(),
    val photoPaths: List<String> = emptyList(),
    val priority: Int = 1                 // 0=high, 1=medium, 2=low
) {
    /** 必須項目が揃っているか */
    fun isReadyToRegister(): Boolean {
        if (title.isBlank()) return false
        if (!isIndefinite && startDate.isBlank()) return false
        return true
    }

    /** 次に聞くべき不足項目 */
    fun nextMissingField(): String? {
        if (title.isBlank()) return "title"
        if (!isIndefinite && startDate.isBlank()) return "date"
        return null
    }
}

data class DraftRoadmapStep(
    val title: String,
    val date: String?,       // yyyy-MM-dd
    val sortOrder: Int
)

/**
 * チャットメッセージの種類を拡張。
 * 通常テキストに加えて、確認カードやアクションボタンを表示するための型。
 */
sealed class ChatContent {
    data class Text(val text: String) : ChatContent()
    data class TaskConfirmation(val draft: DraftTaskData) : ChatContent()
    data class TaskRegistered(val taskId: Int, val title: String) : ChatContent()
}
