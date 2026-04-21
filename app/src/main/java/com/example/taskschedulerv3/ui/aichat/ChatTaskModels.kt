package com.example.taskschedulerv3.ui.aichat

import com.example.taskschedulerv3.data.model.ScheduleType

// =============================================
// ウィザードステップ（簡略化版）
// =============================================
enum class WizardStep {
    IDLE,
    SELECT_TYPE,        // Step 1: 予定の種類
    INPUT_TITLE,        // Step 2: タスク名
    INPUT_MEMO,         // Step 3: メモ（AI整形）
    SELECT_DATE,        // Step 4: 日付
    SELECT_END_DATE,    // Step 4b: 終了日（期間のみ）
    SELECT_TIME,        // Step 5: 時刻
    SELECT_RECURRENCE,  // 繰り返しパターン
    SELECT_TAGS,        // Step 6: タグ
    SELECT_RELATIONS,   // Step 7: 関連予定（AI候補）
    SELECT_PHOTOS,      // Step 8: 写真追加
    CONFIRM,            // Step 9: 確認
    COMPLETED
}

// =============================================
// 構築中のタスクデータ
// =============================================
data class DraftTaskData(
    val scheduleType: ScheduleType = ScheduleType.NORMAL,
    val title: String = "",
    val memo: String = "",
    val startDate: String = "",      // yyyy-MM-dd
    val endDate: String = "",        // yyyy-MM-dd（期間タスク用）
    val startTime: String = "",      // HH:mm
    val endTime: String = "",        // HH:mm
    val isIndefinite: Boolean = false,
    val tagIds: List<Int> = emptyList(),
    val relatedTaskIds: List<Int> = emptyList(),
    val photoPath: String? = null,
    val recurrencePattern: String = "NONE",
    val recurrenceDays: String = "",
    val recurrenceEndDate: String = ""
)

data class DraftRoadmapStep(
    val title: String,
    val date: String = "",
    val sortOrder: Int = 0
)

// =============================================
// チャット内容の種別
// =============================================
sealed class ChatContent {
    data class Text(val body: String) : ChatContent()

    data class ChoiceButtons(
        val prompt: String,
        val choices: List<Choice>,
        val allowSkip: Boolean = false
    ) : ChatContent()

    /** テキスト入力（AI整形なし） */
    data class TextInput(
        val prompt: String,
        val hint: String = "",
        val allowSkip: Boolean = false
    ) : ChatContent()

    /** テキスト入力（AI整形あり） */
    data class TextInputWithAi(
        val prompt: String,
        val hint: String = "",
        val allowSkip: Boolean = true,
        val aiDescription: String = ""
    ) : ChatContent()

    /** 日付選択（DatePicker ＋ テキスト入力対応） */
    data class DatePickerRequest(
        val prompt: String,
        val allowSkip: Boolean = false,
        val allowTextInput: Boolean = true
    ) : ChatContent()

    /** 時刻選択（TimePicker ＋ テキスト入力対応） */
    data class TimePickerRequest(
        val prompt: String,
        val allowSkip: Boolean = true,
        val allowTextInput: Boolean = true
    ) : ChatContent()

    /** タグ選択 */
    data class TagPickerRequest(
        val prompt: String,
        val allowSkip: Boolean = true
    ) : ChatContent()

    /** 関連予定選択（AI候補付き） */
    data class RelationPickerRequest(
        val prompt: String,
        val suggestedTaskIds: List<Int> = emptyList(),
        val aiReason: String = "",
        val allowSkip: Boolean = true
    ) : ChatContent()

    /** 写真追加 */
    data class PhotoPickerRequest(
        val prompt: String,
        val allowSkip: Boolean = true
    ) : ChatContent()

    /** 繰り返しパターン選択 */
    data class RecurrencePickerRequest(
        val prompt: String
    ) : ChatContent()

    /** 確認カード */
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

data class Choice(
    val label: String,
    val value: String
)

// =============================================
// チャットメッセージ
// =============================================
data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val content: ChatContent,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
