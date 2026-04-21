package com.example.taskschedulerv3.ui.aichat

import com.example.taskschedulerv3.data.model.ScheduleType

// =============================================
// ウィザードの現在ステップ
// =============================================
enum class WizardStep {
    IDLE,               // ウィザード未起動
    SELECT_TYPE,        // 予定の種類選択
    SELECT_NOTIFY,      // 通知の有無
    SELECT_ROADMAP,     // ロードマップの有無
    INPUT_TITLE,        // タスク名入力
    INPUT_MEMO,         // メモ入力
    SELECT_DATE,        // 日付選択
    SELECT_END_DATE,    // 終了日選択（期間タスクのみ）
    SELECT_TIME,        // 時刻選択
    SELECT_RECURRENCE,  // 繰り返しパターン（繰り返しタスクのみ）
    SELECT_TAGS,        // タグ選択
    SELECT_RELATIONS,   // 関連予定選択
    SELECT_PHOTOS,      // 写真追加
    SET_NOTIFY_TIMING,  // 通知タイミング設定
    INPUT_ROADMAP_STEPS,// ロードマップステップ入力
    CONFIRM,            // 確認画面
    COMPLETED           // 登録完了
}

// =============================================
// ウィザードで構築中のタスクデータ
// =============================================
data class DraftTaskData(
    val scheduleType: ScheduleType = ScheduleType.NORMAL,
    val title: String = "",
    val memo: String = "",
    val startDate: String = "",      // yyyy-MM-dd
    val endDate: String = "",        // yyyy-MM-dd（期間タスク用）
    val startTime: String = "",      // HH:mm
    val endTime: String = "",        // HH:mm
    val notifyEnabled: Boolean = false,
    val notifyMinutesBefore: Int = 60,
    val roadmapEnabled: Boolean = false,
    val roadmapSteps: List<DraftRoadmapStep> = emptyList(),
    val tagIds: List<Int> = emptyList(),
    val relatedTaskIds: List<Int> = emptyList(),
    val photoPath: String? = null,
    val priority: Int = 1,  // 0=高, 1=中, 2=低
    val isIndefinite: Boolean = false,
    val recurrencePattern: String = "NONE",
    val recurrenceDays: String = "",
    val recurrenceEndDate: String = ""
)

data class DraftRoadmapStep(
    val title: String,
    val date: String = "",  // yyyy-MM-dd（任意）
    val sortOrder: Int = 0
)

// =============================================
// チャットメッセージの内容種別
// =============================================
sealed class ChatContent {
    /** 通常テキスト */
    data class Text(val body: String) : ChatContent()

    /** 選択肢ボタン群 */
    data class ChoiceButtons(
        val prompt: String,
        val choices: List<Choice>,
        val allowSkip: Boolean = false
    ) : ChatContent()

    /** テキスト入力要求 */
    data class TextInput(
        val prompt: String,
        val hint: String = "",
        val allowSkip: Boolean = false
    ) : ChatContent()

    /** 日付選択要求 */
    data class DatePickerRequest(
        val prompt: String,
        val allowSkip: Boolean = false
    ) : ChatContent()

    /** 時刻選択要求 */
    data class TimePickerRequest(
        val prompt: String,
        val allowSkip: Boolean = false
    ) : ChatContent()

    /** タグ選択要求 */
    data class TagPickerRequest(
        val prompt: String,
        val allowSkip: Boolean = true
    ) : ChatContent()

    /** 関連予定選択要求 */
    data class RelationPickerRequest(
        val prompt: String,
        val allowSkip: Boolean = true
    ) : ChatContent()

    /** 写真追加要求 */
    data class PhotoPickerRequest(
        val prompt: String,
        val allowSkip: Boolean = true
    ) : ChatContent()

    /** 繰り返しパターン選択 */
    data class RecurrencePickerRequest(
        val prompt: String
    ) : ChatContent()

    /** ロードマップステップ入力 */
    data class RoadmapStepInput(
        val prompt: String,
        val currentSteps: List<DraftRoadmapStep> = emptyList()
    ) : ChatContent()

    /** 通知タイミング選択 */
    data class NotifyTimingRequest(
        val prompt: String
    ) : ChatContent()

    /** 確認カード */
    data class TaskConfirmation(
        val draft: DraftTaskData,
        val isActive: Boolean = true
    ) : ChatContent()

    /** 登録完了カード */
    data class TaskRegistered(
        val taskTitle: String,
        val taskId: Int
    ) : ChatContent()
}

data class Choice(
    val label: String,
    val value: String
)

// =============================================
// チャットメッセージ
// =============================================
data class ChatMessage(
    val content: ChatContent,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
