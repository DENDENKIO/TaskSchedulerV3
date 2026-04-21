package com.example.taskschedulerv3.ui.aichat

import com.example.taskschedulerv3.data.model.ScheduleType

/**
 * AI チャット登録のウィザード状態。
 * 旧ステップ式（SELECT_TYPE, INPUT_TITLE等）は全て廃止。
 * 新方式はAI自然文解析で、ステップは7つだけ。
 */
enum class WizardStep {
    IDLE,               // 通常会話モード（ウィザード未起動）
    WAITING_INPUT,      // ユーザーの自然文入力を待機中
    WAITING_ANSWER,     // AIからの質問にユーザーが回答中
    WAITING_MODIFY,     // 確認カードの修正指示を待機中
    PHOTO_SELECT,       // 写真追加ステップ
    CONFIRM,            // 確認カード表示中
    COMPLETED           // 登録完了
}

/**
 * 構築中のタスクデータ
 */
data class DraftTaskData(
    val title: String = "",
    val memo: String = "",
    val startDate: String = "",
    val endDate: String? = null,
    val startTime: String? = null,
    val endTime: String? = null,
    val location: String? = null,
    val scheduleType: ScheduleType = ScheduleType.NORMAL,
    val isIndefinite: Boolean = false,
    val tagIds: List<Int> = emptyList(),
    val photoPath: String? = null,
    val recurrencePattern: String? = null
)

/**
 * チャット内容の種別（大幅に削減）
 */
sealed class ChatContent {
    data class Text(val body: String) : ChatContent()

    /** 写真追加（ギャラリー限定） */
    data class PhotoPickerRequest(
        val prompt: String,
        val allowSkip: Boolean = true
    ) : ChatContent()

    /** タスク確認カード */
    data class TaskConfirmation(
        val draft: DraftTaskData,
        val isActive: Boolean = true
    ) : ChatContent()

    /** 登録完了 */
    data class TaskRegistered(
        val taskTitle: String,
        val taskId: Int
    ) : ChatContent()
}

data class ChatMessage(
    val content: ChatContent,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
