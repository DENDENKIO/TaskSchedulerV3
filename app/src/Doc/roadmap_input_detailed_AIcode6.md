Now I have a thorough understanding of both the current code and the LiteRT-LM API. Let me design the new tap-based step-by-step task registration system.

---

## 新設計：タップ選択式＋段階的やり取りでのタスク登録

AIに自然文を解釈させる負荷を排除し、**UI ボタンのタップ → 段階的な質問 → 確認 → 登録**というフローに変更します。AIは「会話を自然にする案内役」としてのみ使い、データ入力はすべてUI操作で行います。

---

### フロー概要

```
ユーザー「予定を登録したい」等のキーワード検出
  ↓
Step 1: 予定の種類を選択（通常／期間／無期限／繰り返し）← ボタン
  ↓
Step 2: 通知の有無（あり／なし）← ボタン
  ↓
Step 3: ロードマップの有無（あり／なし）← ボタン
  ↓
Step 4: タスク名入力 ← テキスト入力（必須）
  ↓
Step 5: メモ入力 ← テキスト入力 or スキップボタン
  ↓
Step 6: 日付選択 ← DatePicker（通常・繰り返しは必須、無期限はスキップ）
  ↓
Step 7: 時刻選択 ← TimePicker or スキップボタン
  ↓
Step 8: タグ選択 ← チップ選択（複数可）or スキップボタン
  ↓
Step 9: 関連予定選択 ← リスト選択（複数可）or スキップボタン
  ↓
Step 10: 写真追加 ← カメラ／ギャラリー or スキップボタン
  ↓
Step 11: (通知ありの場合) 通知タイミング設定
  ↓
Step 12: (ロードマップありの場合) ステップ追加
  ↓
Step 13: 確認カード表示 →「登録する」or「修正する」or「キャンセル」
```

---

### 変更ファイル一覧

| # | ファイル | 変更内容 |
|---|---|---|
| 1 | `ui/aichat/ChatTaskModels.kt` | **全置換** – ウィザードステップ用の sealed class を再設計 |
| 2 | `ui/aichat/AiChatViewModel.kt` | **全置換** – ウィザードステートマシン方式に変更 |
| 3 | `ui/aichat/AiChatScreen.kt` | **全置換** – ステップ別UIカード追加 |
| 4 | 既存ファイル群 | **変更なし** |

---

### ファイル 1: `ChatTaskModels.kt`

**パス**: `app/src/main/java/com/example/taskschedulerv3/ui/aichat/ChatTaskModels.kt`

```kotlin
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
```

---

### ファイル 2: `AiChatViewModel.kt`

**パス**: `app/src/main/java/com/example/taskschedulerv3/ui/aichat/AiChatViewModel.kt`

```kotlin
package com.example.taskschedulerv3.ui.aichat

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskschedulerv3.data.db.AppDatabase
import com.example.taskschedulerv3.data.model.RecurrencePattern
import com.example.taskschedulerv3.data.model.RoadmapStep
import com.example.taskschedulerv3.data.model.ScheduleType
import com.example.taskschedulerv3.data.model.Tag
import com.example.taskschedulerv3.data.model.Task
import com.example.taskschedulerv3.data.model.TaskRelation
import com.example.taskschedulerv3.data.model.TaskTagCrossRef
import com.example.taskschedulerv3.data.repository.TaskRepository
import com.example.taskschedulerv3.notification.AlarmScheduler
import com.example.taskschedulerv3.util.AiEngineManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class AiChatViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "AiChatVM"
    }

    // --- DB ---
    private val db = AppDatabase.getDatabase(application)
    private val taskDao = db.taskDao()
    private val tagDao = db.tagDao()
    private val crossRefDao = db.taskTagCrossRefDao()
    private val relationDao = db.taskRelationDao()
    private val roadmapStepDao = db.roadmapStepDao()
    private val repository = TaskRepository(taskDao, roadmapStepDao)

    // --- Chat State ---
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()

    // --- Wizard State ---
    private val _wizardStep = MutableStateFlow(WizardStep.IDLE)
    val wizardStep: StateFlow<WizardStep> = _wizardStep.asStateFlow()

    private val _draft = MutableStateFlow(DraftTaskData())
    val draft: StateFlow<DraftTaskData> = _draft.asStateFlow()

    // --- Tags cache ---
    private val _allTags = MutableStateFlow<List<Tag>>(emptyList())
    val allTags: StateFlow<List<Tag>> = _allTags.asStateFlow()

    // --- Tasks cache (for relation picker) ---
    private val _allTasks = MutableStateFlow<List<Task>>(emptyList())
    val allTasks: StateFlow<List<Task>> = _allTasks.asStateFlow()

    init {
        // 初回あいさつ
        addAiMessage(ChatContent.Text(
            "こんにちは！予定の検索や登録のお手伝いをします。\n" +
            "「予定を登録」と入力するか、質問を自由にどうぞ。"
        ))
        // タグ・タスクを読み込み
        viewModelScope.launch {
            tagDao.getAllTags().collect { _allTags.value = it }
        }
        viewModelScope.launch {
            taskDao.getAllTasks().collect { _allTasks.value = it.filter { t -> !t.isDeleted && !t.isCompleted } }
        }
    }

    // =========================================================
    // ユーザーのテキスト入力（チャット欄からの送信）
    // =========================================================
    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        addUserMessage(trimmed)

        viewModelScope.launch {
            _isTyping.value = true
            try {
                processInput(trimmed)
            } catch (e: Exception) {
                Log.e(TAG, "processInput error", e)
                addAiMessage(ChatContent.Text("エラーが発生しました: ${e.message}"))
            } finally {
                _isTyping.value = false
            }
        }
    }

    // =========================================================
    // ウィザードの選択肢がタップされた
    // =========================================================
    fun onChoiceSelected(stepAtSelection: WizardStep, value: String) {
        viewModelScope.launch {
            _isTyping.value = true
            try {
                handleWizardChoice(stepAtSelection, value)
            } catch (e: Exception) {
                Log.e(TAG, "wizard choice error", e)
                addAiMessage(ChatContent.Text("エラーが発生しました: ${e.message}"))
            } finally {
                _isTyping.value = false
            }
        }
    }

    // =========================================================
    // 日付が選択された
    // =========================================================
    fun onDateSelected(date: String) {
        val step = _wizardStep.value
        addUserMessage(date)
        viewModelScope.launch {
            when (step) {
                WizardStep.SELECT_DATE -> {
                    _draft.value = _draft.value.copy(startDate = date)
                    if (_draft.value.scheduleType == ScheduleType.PERIOD) {
                        moveToStep(WizardStep.SELECT_END_DATE)
                    } else {
                        moveToStep(WizardStep.SELECT_TIME)
                    }
                }
                WizardStep.SELECT_END_DATE -> {
                    _draft.value = _draft.value.copy(endDate = date)
                    moveToStep(WizardStep.SELECT_TIME)
                }
                else -> {}
            }
        }
    }

    // =========================================================
    // 時刻が選択された
    // =========================================================
    fun onTimeSelected(startTime: String, endTime: String = "") {
        val display = if (endTime.isNotEmpty()) "$startTime 〜 $endTime" else startTime
        addUserMessage(display)
        viewModelScope.launch {
            _draft.value = _draft.value.copy(startTime = startTime, endTime = endTime)
            moveAfterTime()
        }
    }

    // =========================================================
    // タグが選択された
    // =========================================================
    fun onTagsSelected(tagIds: List<Int>) {
        val names = _allTags.value.filter { it.id in tagIds }.joinToString(", ") { it.name }
        addUserMessage(if (tagIds.isEmpty()) "スキップ" else "タグ: $names")
        viewModelScope.launch {
            _draft.value = _draft.value.copy(tagIds = tagIds)
            moveToStep(WizardStep.SELECT_RELATIONS)
        }
    }

    // =========================================================
    // 関連予定が選択された
    // =========================================================
    fun onRelationsSelected(taskIds: List<Int>) {
        addUserMessage(if (taskIds.isEmpty()) "スキップ" else "関連予定: ${taskIds.size}件")
        viewModelScope.launch {
            _draft.value = _draft.value.copy(relatedTaskIds = taskIds)
            moveToStep(WizardStep.SELECT_PHOTOS)
        }
    }

    // =========================================================
    // 写真が選択された（パス）
    // =========================================================
    fun onPhotoSelected(path: String?) {
        addUserMessage(if (path == null) "スキップ" else "写真を追加しました")
        viewModelScope.launch {
            _draft.value = _draft.value.copy(photoPath = path)
            moveAfterPhotos()
        }
    }

    // =========================================================
    // 通知タイミング選択
    // =========================================================
    fun onNotifyTimingSelected(minutes: Int) {
        val label = when (minutes) {
            0 -> "予定時刻"
            5 -> "5分前"
            10 -> "10分前"
            15 -> "15分前"
            30 -> "30分前"
            60 -> "1時間前"
            1440 -> "1日前"
            else -> "${minutes}分前"
        }
        addUserMessage(label)
        viewModelScope.launch {
            _draft.value = _draft.value.copy(notifyMinutesBefore = minutes)
            moveAfterNotify()
        }
    }

    // =========================================================
    // 繰り返しパターン選択
    // =========================================================
    fun onRecurrenceSelected(pattern: String, days: String = "", endDate: String = "") {
        addUserMessage("繰り返し: ${recurrenceLabel(pattern)}")
        viewModelScope.launch {
            _draft.value = _draft.value.copy(
                recurrencePattern = pattern,
                recurrenceDays = days,
                recurrenceEndDate = endDate
            )
            moveToStep(WizardStep.SELECT_TIME)
        }
    }

    // =========================================================
    // ロードマップステップ追加
    // =========================================================
    fun onRoadmapStepsSet(steps: List<DraftRoadmapStep>) {
        addUserMessage("ロードマップ: ${steps.size}ステップ")
        viewModelScope.launch {
            _draft.value = _draft.value.copy(roadmapSteps = steps)
            moveToStep(WizardStep.CONFIRM)
        }
    }

    // =========================================================
    // 確認画面のアクション
    // =========================================================
    fun confirmRegistration() {
        viewModelScope.launch {
            _isTyping.value = true
            try {
                val taskId = registerTask(_draft.value)
                _wizardStep.value = WizardStep.COMPLETED
                addAiMessage(ChatContent.TaskRegistered(
                    taskTitle = _draft.value.title,
                    taskId = taskId
                ))
                addAiMessage(ChatContent.Text(
                    "「${_draft.value.title}」を登録しました！\n他にご用件はありますか？"
                ))
                // リセット
                _draft.value = DraftTaskData()
                _wizardStep.value = WizardStep.IDLE
            } catch (e: Exception) {
                Log.e(TAG, "registration error", e)
                addAiMessage(ChatContent.Text("登録中にエラーが発生しました: ${e.message}"))
            } finally {
                _isTyping.value = false
            }
        }
    }

    fun cancelRegistration() {
        _draft.value = DraftTaskData()
        _wizardStep.value = WizardStep.IDLE
        addAiMessage(ChatContent.Text("登録をキャンセルしました。他にご用件はありますか？"))
    }

    fun goBackToModify() {
        // 確認画面から修正へ戻る → Step 1 から再開
        addAiMessage(ChatContent.Text("修正します。最初からやり直しましょう。"))
        moveToStep(WizardStep.SELECT_TYPE)
    }

    // =========================================================
    // 内部: テキスト入力の処理振り分け
    // =========================================================
    private suspend fun processInput(text: String) {
        val step = _wizardStep.value

        when (step) {
            WizardStep.IDLE -> {
                // 登録キーワード検出
                if (isRegistrationIntent(text)) {
                    startWizard()
                } else {
                    // 従来の検索 or 自由会話
                    handleNonWizardInput(text)
                }
            }
            WizardStep.INPUT_TITLE -> {
                _draft.value = _draft.value.copy(title = text)
                addAiMessage(ChatContent.Text("タスク名を「${text}」に設定しました。"))
                moveToStep(WizardStep.INPUT_MEMO)
            }
            WizardStep.INPUT_MEMO -> {
                _draft.value = _draft.value.copy(memo = text)
                addAiMessage(ChatContent.Text("メモを設定しました。"))
                moveToDateStep()
            }
            WizardStep.INPUT_ROADMAP_STEPS -> {
                // テキスト入力でステップ追加（改行区切り）
                val steps = text.split("\n").filter { it.isNotBlank() }.mapIndexed { i, line ->
                    DraftRoadmapStep(title = line.trim(), sortOrder = i)
                }
                if (steps.isEmpty()) {
                    addAiMessage(ChatContent.Text("ステップが空です。もう一度入力してください。"))
                } else {
                    onRoadmapStepsSet(steps)
                }
            }
            else -> {
                // ウィザード中にテキストが来た場合、意図を判断
                if (isRegistrationCancel(text)) {
                    cancelRegistration()
                } else {
                    addAiMessage(ChatContent.Text(
                        "現在、予定登録の途中です。上のボタンから選択してください。\n" +
                        "キャンセルする場合は「キャンセル」と入力してください。"
                    ))
                }
            }
        }
    }

    // =========================================================
    // ウィザード開始
    // =========================================================
    private fun startWizard() {
        _draft.value = DraftTaskData()
        addAiMessage(ChatContent.Text("予定を登録しましょう！まず予定の種類を選んでください。"))
        moveToStep(WizardStep.SELECT_TYPE)
    }

    // =========================================================
    // ウィザード: ボタン選択の処理
    // =========================================================
    private suspend fun handleWizardChoice(step: WizardStep, value: String) {
        // ユーザーの選択を表示
        addUserMessage(value)

        when (step) {
            WizardStep.SELECT_TYPE -> {
                val type = when (value) {
                    "通常" -> ScheduleType.NORMAL
                    "期間" -> ScheduleType.PERIOD
                    "無期限" -> {
                        _draft.value = _draft.value.copy(
                            scheduleType = ScheduleType.NORMAL,
                            isIndefinite = true
                        )
                        addAiMessage(ChatContent.Text("無期限タスクに設定しました。"))
                        moveToStep(WizardStep.SELECT_NOTIFY)
                        return
                    }
                    "繰り返し" -> ScheduleType.RECURRING
                    else -> ScheduleType.NORMAL
                }
                _draft.value = _draft.value.copy(scheduleType = type)
                addAiMessage(ChatContent.Text("「${value}」を選択しました。"))
                moveToStep(WizardStep.SELECT_NOTIFY)
            }

            WizardStep.SELECT_NOTIFY -> {
                val enabled = value == "あり"
                _draft.value = _draft.value.copy(notifyEnabled = enabled)
                addAiMessage(ChatContent.Text(
                    if (enabled) "通知ありに設定しました。タイミングは後で設定します。"
                    else "通知なしに設定しました。"
                ))
                moveToStep(WizardStep.SELECT_ROADMAP)
            }

            WizardStep.SELECT_ROADMAP -> {
                val enabled = value == "あり"
                _draft.value = _draft.value.copy(roadmapEnabled = enabled)
                addAiMessage(ChatContent.Text(
                    if (enabled) "ロードマップ機能をオンにしました。ステップは後で入力します。"
                    else "ロードマップなしに設定しました。"
                ))
                moveToStep(WizardStep.INPUT_TITLE)
            }

            WizardStep.INPUT_MEMO -> {
                // 「スキップ」ボタン
                if (value == "スキップ") {
                    addAiMessage(ChatContent.Text("メモをスキップしました。"))
                    moveToDateStep()
                }
            }

            WizardStep.SELECT_TIME -> {
                if (value == "スキップ") {
                    addAiMessage(ChatContent.Text("時刻設定をスキップしました。"))
                    moveAfterTime()
                }
            }

            WizardStep.SELECT_TAGS -> {
                if (value == "スキップ") {
                    onTagsSelected(emptyList())
                }
            }

            WizardStep.SELECT_RELATIONS -> {
                if (value == "スキップ") {
                    onRelationsSelected(emptyList())
                }
            }

            WizardStep.SELECT_PHOTOS -> {
                if (value == "スキップ") {
                    onPhotoSelected(null)
                }
            }

            WizardStep.SET_NOTIFY_TIMING -> {
                // 選択肢から分数を解析
                val minutes = when (value) {
                    "予定時刻" -> 0
                    "5分前" -> 5
                    "10分前" -> 10
                    "15分前" -> 15
                    "30分前" -> 30
                    "1時間前" -> 60
                    "1日前" -> 1440
                    else -> 60
                }
                onNotifyTimingSelected(minutes)
            }

            WizardStep.INPUT_ROADMAP_STEPS -> {
                if (value == "完了") {
                    val steps = _draft.value.roadmapSteps
                    if (steps.isEmpty()) {
                        addAiMessage(ChatContent.Text("少なくとも1つのステップを追加してください。"))
                    } else {
                        moveToStep(WizardStep.CONFIRM)
                    }
                }
            }

            WizardStep.CONFIRM -> {
                when (value) {
                    "登録する" -> confirmRegistration()
                    "修正する" -> goBackToModify()
                    "キャンセル" -> cancelRegistration()
                }
            }

            else -> {}
        }
    }

    // =========================================================
    // ステップ遷移ヘルパー
    // =========================================================
    private fun moveToStep(step: WizardStep) {
        _wizardStep.value = step
        when (step) {
            WizardStep.SELECT_TYPE -> {
                addAiMessage(ChatContent.ChoiceButtons(
                    prompt = "予定の種類を選択してください",
                    choices = listOf(
                        Choice("通常", "通常"),
                        Choice("期間", "期間"),
                        Choice("無期限", "無期限"),
                        Choice("繰り返し", "繰り返し")
                    )
                ))
            }

            WizardStep.SELECT_NOTIFY -> {
                addAiMessage(ChatContent.ChoiceButtons(
                    prompt = "通知は必要ですか？",
                    choices = listOf(
                        Choice("あり", "あり"),
                        Choice("なし", "なし")
                    )
                ))
            }

            WizardStep.SELECT_ROADMAP -> {
                addAiMessage(ChatContent.ChoiceButtons(
                    prompt = "ロードマップ機能を使いますか？\n（大きなタスクをステップに分けて管理できます）",
                    choices = listOf(
                        Choice("あり", "あり"),
                        Choice("なし", "なし")
                    )
                ))
            }

            WizardStep.INPUT_TITLE -> {
                addAiMessage(ChatContent.TextInput(
                    prompt = "タスク名を入力してください（必須）",
                    hint = "例: 歯医者、チームミーティング",
                    allowSkip = false
                ))
            }

            WizardStep.INPUT_MEMO -> {
                addAiMessage(ChatContent.TextInput(
                    prompt = "メモがあれば入力してください",
                    hint = "例: 資料を持参すること",
                    allowSkip = true
                ))
            }

            WizardStep.SELECT_DATE -> {
                val isRequired = !_draft.value.isIndefinite
                addAiMessage(ChatContent.DatePickerRequest(
                    prompt = if (_draft.value.scheduleType == ScheduleType.PERIOD)
                        "開始日を選択してください"
                    else
                        "日付を選択してください" +
                        if (!isRequired) "（スキップ可能）" else "",
                    allowSkip = !isRequired
                ))
            }

            WizardStep.SELECT_END_DATE -> {
                addAiMessage(ChatContent.DatePickerRequest(
                    prompt = "終了日を選択してください",
                    allowSkip = false
                ))
            }

            WizardStep.SELECT_TIME -> {
                addAiMessage(ChatContent.TimePickerRequest(
                    prompt = "時刻を設定してください",
                    allowSkip = true
                ))
            }

            WizardStep.SELECT_RECURRENCE -> {
                addAiMessage(ChatContent.RecurrencePickerRequest(
                    prompt = "繰り返しパターンを選択してください"
                ))
            }

            WizardStep.SELECT_TAGS -> {
                addAiMessage(ChatContent.TagPickerRequest(
                    prompt = "タグを選択してください（複数選択可）",
                    allowSkip = true
                ))
            }

            WizardStep.SELECT_RELATIONS -> {
                addAiMessage(ChatContent.RelationPickerRequest(
                    prompt = "関連する予定がありますか？（複数選択可）",
                    allowSkip = true
                ))
            }

            WizardStep.SELECT_PHOTOS -> {
                addAiMessage(ChatContent.PhotoPickerRequest(
                    prompt = "写真を追加しますか？",
                    allowSkip = true
                ))
            }

            WizardStep.SET_NOTIFY_TIMING -> {
                addAiMessage(ChatContent.NotifyTimingRequest(
                    prompt = "いつ通知しますか？"
                ))
            }

            WizardStep.INPUT_ROADMAP_STEPS -> {
                addAiMessage(ChatContent.RoadmapStepInput(
                    prompt = "ロードマップのステップを入力してください\n（1行に1ステップ、または1つずつ追加）",
                    currentSteps = _draft.value.roadmapSteps
                ))
            }

            WizardStep.CONFIRM -> {
                addAiMessage(ChatContent.Text("以下の内容で登録してよろしいですか？"))
                addAiMessage(ChatContent.TaskConfirmation(
                    draft = _draft.value,
                    isActive = true
                ))
            }

            else -> {}
        }
    }

    /** メモの後→日付ステップへ（無期限はスキップ） */
    private fun moveToDateStep() {
        val d = _draft.value
        if (d.isIndefinite) {
            // 無期限タスクは日付不要 → 時刻へ
            moveToStep(WizardStep.SELECT_TIME)
        } else if (d.scheduleType == ScheduleType.RECURRING) {
            // 繰り返し → まず繰り返しパターン選択 → 日付 → 時刻
            moveToStep(WizardStep.SELECT_RECURRENCE)
        } else {
            moveToStep(WizardStep.SELECT_DATE)
        }
    }

    /** 時刻の後→タグ or 繰り返し日付 */
    private fun moveAfterTime() {
        val d = _draft.value
        if (d.scheduleType == ScheduleType.RECURRING && d.startDate.isEmpty()) {
            // 繰り返しは日付も必要
            moveToStep(WizardStep.SELECT_DATE)
        } else {
            moveToStep(WizardStep.SELECT_TAGS)
        }
    }

    /** 写真の後→通知 or ロードマップ or 確認 */
    private fun moveAfterPhotos() {
        val d = _draft.value
        if (d.notifyEnabled) {
            moveToStep(WizardStep.SET_NOTIFY_TIMING)
        } else {
            moveAfterNotify()
        }
    }

    /** 通知の後→ロードマップ or 確認 */
    private fun moveAfterNotify() {
        val d = _draft.value
        if (d.roadmapEnabled) {
            moveToStep(WizardStep.INPUT_ROADMAP_STEPS)
        } else {
            moveToStep(WizardStep.CONFIRM)
        }
    }

    // =========================================================
    // 非ウィザード入力（検索 / 自由会話）
    // =========================================================
    private suspend fun handleNonWizardInput(text: String) {
        // 日付検索
        val dateMatch = detectDateKeyword(text)
        if (dateMatch != null) {
            val tasks = withContext(Dispatchers.IO) {
                taskDao.getTasksByDate(dateMatch).first()
            }
            if (tasks.isEmpty()) {
                addAiMessage(ChatContent.Text("${dateMatch} の予定は見つかりませんでした。"))
            } else {
                val summary = tasks.joinToString("\n") { t ->
                    val time = if (t.startTime != null) " ${t.startTime}" else ""
                    "• ${t.title}$time"
                }
                addAiMessage(ChatContent.Text("${dateMatch} の予定:\n$summary"))
            }
            return
        }

        // AI 自由応答
        if (AiEngineManager.isLoaded()) {
            val response = withContext(Dispatchers.IO) {
                AiEngineManager.generateResponse(text)
            }
            if (response != null) {
                addAiMessage(ChatContent.Text(response))
            } else {
                addAiMessage(ChatContent.Text(
                    "応答を生成できませんでした。「予定を登録」で予定登録を開始できます。"
                ))
            }
        } else {
            addAiMessage(ChatContent.Text(
                "AI エンジンが読み込まれていません。\n" +
                "設定画面からAI機能を有効にしてください。\n\n" +
                "「予定を登録」と入力すると、AI無しでも予定登録ができます。"
            ))
        }
    }

    // =========================================================
    // DB 登録
    // =========================================================
    private suspend fun registerTask(d: DraftTaskData): Int = withContext(Dispatchers.IO) {
        val now = LocalDateTime.now()
        val task = Task(
            title = d.title.ifEmpty {
                now.format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")) + " 登録"
            },
            description = d.memo.ifEmpty { null },
            startDate = d.startDate.ifEmpty {
                LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            },
            endDate = d.endDate.ifEmpty { null },
            startTime = d.startTime.ifEmpty { null },
            endTime = d.endTime.ifEmpty { null },
            scheduleType = if (d.isIndefinite) ScheduleType.NORMAL else d.scheduleType,
            recurrencePattern = if (d.scheduleType == ScheduleType.RECURRING)
                d.recurrencePattern else null,
            recurrenceDays = d.recurrenceDays.ifEmpty { null },
            recurrenceEndDate = d.recurrenceEndDate.ifEmpty { null },
            priority = d.priority,
            notifyEnabled = d.notifyEnabled,
            notifyMinutesBefore = d.notifyMinutesBefore,
            isIndefinite = d.isIndefinite,
            roadmapEnabled = d.roadmapEnabled,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        val taskId = taskDao.insertTask(task).toInt()
        Log.d(TAG, "Task registered: id=$taskId title=${task.title}")

        // タグ紐付け
        d.tagIds.forEach { tagId ->
            crossRefDao.insert(TaskTagCrossRef(taskId = taskId, tagId = tagId))
        }

        // 関連予定
        d.relatedTaskIds.forEach { relId ->
            val id1 = minOf(taskId, relId)
            val id2 = maxOf(taskId, relId)
            relationDao.insert(TaskRelation(taskId1 = id1, taskId2 = id2))
        }

        // ロードマップステップ
        if (d.roadmapEnabled && d.roadmapSteps.isNotEmpty()) {
            d.roadmapSteps.forEachIndexed { index, step ->
                roadmapStepDao.insert(
                    RoadmapStep(
                        taskId = taskId,
                        title = step.title,
                        date = step.date.ifEmpty { null },
                        sortOrder = index
                    )
                )
            }
        }

        // 通知スケジュール
        if (d.notifyEnabled) {
            val registeredTask = taskDao.getTaskById(taskId)
            if (registeredTask != null) {
                AlarmScheduler.scheduleForTask(getApplication(), registeredTask)
            }
        }

        taskId
    }

    // =========================================================
    // ユーティリティ
    // =========================================================
    private fun addAiMessage(content: ChatContent) {
        _messages.value = _messages.value + ChatMessage(content = content, isUser = false)
    }

    private fun addUserMessage(text: String) {
        _messages.value = _messages.value + ChatMessage(
            content = ChatContent.Text(text),
            isUser = true
        )
    }

    private fun isRegistrationIntent(text: String): Boolean {
        val keywords = listOf("登録", "予定を作", "タスクを作", "新しい予定", "新規登録",
            "予定を追加", "タスクを追加", "スケジュール登録", "予定作成")
        return keywords.any { text.contains(it) }
    }

    private fun isRegistrationCancel(text: String): Boolean {
        return text.contains("キャンセル") || text.contains("やめる") || text.contains("中止")
    }

    private fun detectDateKeyword(text: String): String? {
        val today = LocalDate.now()
        return when {
            text.contains("今日") -> today.format(DateTimeFormatter.ISO_LOCAL_DATE)
            text.contains("明日") -> today.plusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE)
            text.contains("明後日") -> today.plusDays(2).format(DateTimeFormatter.ISO_LOCAL_DATE)
            else -> {
                val regex = Regex("""(\d{4})[/-](\d{1,2})[/-](\d{1,2})""")
                regex.find(text)?.let {
                    try {
                        val d = LocalDate.of(
                            it.groupValues[1].toInt(),
                            it.groupValues[2].toInt(),
                            it.groupValues[3].toInt()
                        )
                        d.format(DateTimeFormatter.ISO_LOCAL_DATE)
                    } catch (_: Exception) { null }
                }
            }
        }
    }

    private fun recurrenceLabel(pattern: String): String {
        return when (pattern) {
            "DAILY" -> "毎日"
            "WEEKLY" -> "毎週"
            "BIWEEKLY" -> "隔週"
            "MONTHLY_DATE" -> "毎月（日付）"
            "MONTHLY_WEEK" -> "毎月（曜日）"
            "YEARLY" -> "毎年"
            "EVERY_N_DAYS" -> "N日ごと"
            "WEEKLY_MULTI" -> "毎週（複数曜日）"
            "MONTHLY_DATES" -> "毎月（複数日付）"
            else -> pattern
        }
    }
}
```

---

### ファイル 3: `AiChatScreen.kt`

**パス**: `app/src/main/java/com/example/taskschedulerv3/ui/aichat/AiChatScreen.kt`

```kotlin
package com.example.taskschedulerv3.ui.aichat

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.taskschedulerv3.data.model.ScheduleType
import com.example.taskschedulerv3.data.model.Tag
import com.example.taskschedulerv3.data.model.Task
import kotlinx.coroutines.launch
import java.util.Calendar

// =============================================
// メイン画面
// =============================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(
    navController: NavController,
    viewModel: AiChatViewModel = viewModel()
) {
    val messages by viewModel.messages.collectAsState()
    val isTyping by viewModel.isTyping.collectAsState()
    val wizardStep by viewModel.wizardStep.collectAsState()
    val allTags by viewModel.allTags.collectAsState()
    val allTasks by viewModel.allTasks.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var inputText by remember { mutableStateOf("") }

    // 新しいメッセージが来たらスクロール
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI アシスタント") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "戻る")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // メッセージ一覧
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(messages) { msg ->
                    ChatMessageItem(
                        message = msg,
                        wizardStep = wizardStep,
                        allTags = allTags,
                        allTasks = allTasks,
                        onChoiceSelected = { value ->
                            viewModel.onChoiceSelected(wizardStep, value)
                        },
                        onDateSelected = { viewModel.onDateSelected(it) },
                        onTimeSelected = { s, e -> viewModel.onTimeSelected(s, e) },
                        onTagsSelected = { viewModel.onTagsSelected(it) },
                        onRelationsSelected = { viewModel.onRelationsSelected(it) },
                        onPhotoSelected = { viewModel.onPhotoSelected(it) },
                        onNotifyTimingSelected = { viewModel.onNotifyTimingSelected(it) },
                        onRecurrenceSelected = { p, d, e ->
                            viewModel.onRecurrenceSelected(p, d, e)
                        },
                        onRoadmapStepsSet = { viewModel.onRoadmapStepsSet(it) },
                        onConfirm = { viewModel.confirmRegistration() },
                        onModify = { viewModel.goBackToModify() },
                        onCancel = { viewModel.cancelRegistration() },
                        isLatestAiMessage = msg == messages.lastOrNull { !it.isUser }
                    )
                }

                if (isTyping) {
                    item { ChatTypingIndicator() }
                }
            }

            // 入力欄
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            if (wizardStep == WizardStep.IDLE) "なんでも聞いてください…"
                            else "テキストを入力…"
                        )
                    },
                    maxLines = 3,
                    shape = RoundedCornerShape(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            viewModel.sendMessage(inputText)
                            inputText = ""
                        }
                    }
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "送信",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

// =============================================
// メッセージアイテムの振り分け
// =============================================
@Composable
fun ChatMessageItem(
    message: ChatMessage,
    wizardStep: WizardStep,
    allTags: List<Tag>,
    allTasks: List<Task>,
    onChoiceSelected: (String) -> Unit,
    onDateSelected: (String) -> Unit,
    onTimeSelected: (String, String) -> Unit,
    onTagsSelected: (List<Int>) -> Unit,
    onRelationsSelected: (List<Int>) -> Unit,
    onPhotoSelected: (String?) -> Unit,
    onNotifyTimingSelected: (Int) -> Unit,
    onRecurrenceSelected: (String, String, String) -> Unit,
    onRoadmapStepsSet: (List<DraftRoadmapStep>) -> Unit,
    onConfirm: () -> Unit,
    onModify: () -> Unit,
    onCancel: () -> Unit,
    isLatestAiMessage: Boolean
) {
    when (val content = message.content) {
        is ChatContent.Text -> {
            ChatBubble(text = content.body, isUser = message.isUser)
        }

        is ChatContent.ChoiceButtons -> {
            AiCardWrapper {
                Text(content.prompt, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    content.choices.forEach { choice ->
                        FilledTonalButton(
                            onClick = { onChoiceSelected(choice.value) },
                            enabled = isLatestAiMessage
                        ) {
                            Text(choice.label)
                        }
                    }
                    if (content.allowSkip) {
                        OutlinedButton(
                            onClick = { onChoiceSelected("スキップ") },
                            enabled = isLatestAiMessage
                        ) {
                            Text("スキップ")
                        }
                    }
                }
            }
        }

        is ChatContent.TextInput -> {
            AiCardWrapper {
                Text(content.prompt, fontWeight = FontWeight.Medium)
                if (content.hint.isNotEmpty()) {
                    Text(content.hint, color = Color.Gray, fontSize = 12.sp)
                }
                if (content.allowSkip && isLatestAiMessage) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = { onChoiceSelected("スキップ") }) {
                        Text("スキップ")
                    }
                }
            }
        }

        is ChatContent.DatePickerRequest -> {
            DatePickerCard(
                prompt = content.prompt,
                allowSkip = content.allowSkip,
                enabled = isLatestAiMessage,
                onDateSelected = onDateSelected,
                onSkip = { onChoiceSelected("スキップ") }
            )
        }

        is ChatContent.TimePickerRequest -> {
            TimePickerCard(
                prompt = content.prompt,
                allowSkip = content.allowSkip,
                enabled = isLatestAiMessage,
                onTimeSelected = onTimeSelected,
                onSkip = { onChoiceSelected("スキップ") }
            )
        }

        is ChatContent.TagPickerRequest -> {
            TagPickerCard(
                prompt = content.prompt,
                tags = allTags,
                enabled = isLatestAiMessage,
                onTagsSelected = onTagsSelected,
                onSkip = { onTagsSelected(emptyList()) }
            )
        }

        is ChatContent.RelationPickerRequest -> {
            RelationPickerCard(
                prompt = content.prompt,
                tasks = allTasks,
                enabled = isLatestAiMessage,
                onRelationsSelected = onRelationsSelected,
                onSkip = { onRelationsSelected(emptyList()) }
            )
        }

        is ChatContent.PhotoPickerRequest -> {
            PhotoPickerCard(
                prompt = content.prompt,
                enabled = isLatestAiMessage,
                onPhotoSelected = onPhotoSelected,
                onSkip = { onPhotoSelected(null) }
            )
        }

        is ChatContent.NotifyTimingRequest -> {
            NotifyTimingCard(
                prompt = content.prompt,
                enabled = isLatestAiMessage,
                onSelected = onNotifyTimingSelected
            )
        }

        is ChatContent.RecurrencePickerRequest -> {
            RecurrencePickerCard(
                prompt = content.prompt,
                enabled = isLatestAiMessage,
                onSelected = onRecurrenceSelected
            )
        }

        is ChatContent.RoadmapStepInput -> {
            RoadmapStepCard(
                prompt = content.prompt,
                currentSteps = content.currentSteps,
                enabled = isLatestAiMessage,
                onStepsSet = onRoadmapStepsSet
            )
        }

        is ChatContent.TaskConfirmation -> {
            TaskConfirmationCard(
                draft = content.draft,
                allTags = allTags,
                isActive = content.isActive && isLatestAiMessage,
                onConfirm = onConfirm,
                onModify = onModify,
                onCancel = onCancel
            )
        }

        is ChatContent.TaskRegistered -> {
            TaskRegisteredCard(title = content.taskTitle)
        }
    }
}

// =============================================
// チャットバブル
// =============================================
@Composable
fun ChatBubble(text: String, isUser: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            color = if (isUser)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(12.dp),
                fontSize = 14.sp
            )
        }
    }
}

// =============================================
// AIカードラッパー
// =============================================
@Composable
fun AiCardWrapper(content: @Composable ColumnScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Card(
            modifier = Modifier.widthIn(max = 320.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(
                topStart = 16.dp, topEnd = 16.dp,
                bottomStart = 4.dp, bottomEnd = 16.dp
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                content = content
            )
        }
    }
}

// =============================================
// 日付選択カード
// =============================================
@Composable
fun DatePickerCard(
    prompt: String,
    allowSkip: Boolean,
    enabled: Boolean,
    onDateSelected: (String) -> Unit,
    onSkip: () -> Unit
) {
    val context = LocalContext.current
    var selectedDate by remember { mutableStateOf("") }

    AiCardWrapper {
        Text(prompt, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
                onClick = {
                    val cal = Calendar.getInstance()
                    DatePickerDialog(context, { _, y, m, d ->
                        val date = String.format("%04d-%02d-%02d", y, m + 1, d)
                        selectedDate = date
                        onDateSelected(date)
                    }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
                        .show()
                },
                enabled = enabled
            ) {
                Text("日付を選択")
            }
            if (allowSkip) {
                OutlinedButton(onClick = onSkip, enabled = enabled) {
                    Text("スキップ")
                }
            }
        }
        if (selectedDate.isNotEmpty()) {
            Text("選択: $selectedDate", fontSize = 12.sp, color = Color.Gray)
        }
    }
}

// =============================================
// 時刻選択カード
// =============================================
@Composable
fun TimePickerCard(
    prompt: String,
    allowSkip: Boolean,
    enabled: Boolean,
    onTimeSelected: (String, String) -> Unit,
    onSkip: () -> Unit
) {
    val context = LocalContext.current
    var startTime by remember { mutableStateOf("") }
    var endTime by remember { mutableStateOf("") }

    AiCardWrapper {
        Text(prompt, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
                onClick = {
                    TimePickerDialog(context, { _, h, m ->
                        startTime = String.format("%02d:%02d", h, m)
                    }, 9, 0, true).show()
                },
                enabled = enabled
            ) {
                Text(if (startTime.isEmpty()) "開始時刻" else startTime)
            }
            FilledTonalButton(
                onClick = {
                    TimePickerDialog(context, { _, h, m ->
                        endTime = String.format("%02d:%02d", h, m)
                    }, 10, 0, true).show()
                },
                enabled = enabled
            ) {
                Text(if (endTime.isEmpty()) "終了時刻" else endTime)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
                onClick = { onTimeSelected(startTime, endTime) },
                enabled = enabled && startTime.isNotEmpty()
            ) {
                Text("決定")
            }
            if (allowSkip) {
                OutlinedButton(onClick = onSkip, enabled = enabled) {
                    Text("スキップ")
                }
            }
        }
    }
}

// =============================================
// タグ選択カード
// =============================================
@Composable
fun TagPickerCard(
    prompt: String,
    tags: List<Tag>,
    enabled: Boolean,
    onTagsSelected: (List<Int>) -> Unit,
    onSkip: () -> Unit
) {
    var selectedIds by remember { mutableStateOf(setOf<Int>()) }

    AiCardWrapper {
        Text(prompt, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(8.dp))

        if (tags.isEmpty()) {
            Text("タグがありません", fontSize = 12.sp, color = Color.Gray)
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                tags.forEach { tag ->
                    FilterChip(
                        selected = tag.id in selectedIds,
                        onClick = {
                            selectedIds = if (tag.id in selectedIds)
                                selectedIds - tag.id
                            else
                                selectedIds + tag.id
                        },
                        label = { Text(tag.name, fontSize = 12.sp) },
                        enabled = enabled
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
                onClick = { onTagsSelected(selectedIds.toList()) },
                enabled = enabled
            ) {
                Text("決定")
            }
            OutlinedButton(onClick = onSkip, enabled = enabled) {
                Text("スキップ")
            }
        }
    }
}

// =============================================
// 関連予定選択カード
// =============================================
@Composable
fun RelationPickerCard(
    prompt: String,
    tasks: List<Task>,
    enabled: Boolean,
    onRelationsSelected: (List<Int>) -> Unit,
    onSkip: () -> Unit
) {
    var selectedIds by remember { mutableStateOf(setOf<Int>()) }
    var expanded by remember { mutableStateOf(false) }

    AiCardWrapper {
        Text(prompt, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(8.dp))

        if (tasks.isEmpty()) {
            Text("関連付けできる予定がありません", fontSize = 12.sp, color = Color.Gray)
        } else {
            // 選択済み表示
            if (selectedIds.isNotEmpty()) {
                selectedIds.forEach { id ->
                    val t = tasks.find { it.id == id }
                    if (t != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(t.title, fontSize = 12.sp, maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            // タスクリスト（折りたたみ）
            OutlinedButton(
                onClick = { expanded = !expanded },
                enabled = enabled
            ) {
                Text(if (expanded) "一覧を閉じる" else "一覧から選択")
            }

            if (expanded) {
                Column(
                    modifier = Modifier
                        .heightIn(max = 200.dp)
                ) {
                    tasks.take(30).forEach { task ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = enabled) {
                                    selectedIds = if (task.id in selectedIds)
                                        selectedIds - task.id
                                    else
                                        selectedIds + task.id
                                }
                                .padding(vertical = 4.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = task.id in selectedIds,
                                onCheckedChange = null,
                                enabled = enabled
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                task.title,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
                onClick = { onRelationsSelected(selectedIds.toList()) },
                enabled = enabled
            ) {
                Text("決定")
            }
            OutlinedButton(onClick = onSkip, enabled = enabled) {
                Text("スキップ")
            }
        }
    }
}

// =============================================
// 写真追加カード
// =============================================
@Composable
fun PhotoPickerCard(
    prompt: String,
    enabled: Boolean,
    onPhotoSelected: (String?) -> Unit,
    onSkip: () -> Unit
) {
    // 簡易版: カメラ／ギャラリー起動は親Activityに委譲する想定
    // ここではスキップのみ対応。写真機能は既存のCaptureSheetを流用
    AiCardWrapper {
        Text(prompt, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "※ 写真はタスク登録後に編集画面から追加できます",
            fontSize = 11.sp,
            color = Color.Gray
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onSkip, enabled = enabled) {
                Text("スキップ")
            }
        }
    }
}

// =============================================
// 通知タイミング選択カード
// =============================================
@Composable
fun NotifyTimingCard(
    prompt: String,
    enabled: Boolean,
    onSelected: (Int) -> Unit
) {
    val options = listOf(
        "予定時刻" to 0,
        "5分前" to 5,
        "10分前" to 10,
        "15分前" to 15,
        "30分前" to 30,
        "1時間前" to 60,
        "1日前" to 1440
    )

    AiCardWrapper {
        Text(prompt, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { (label, minutes) ->
                FilledTonalButton(
                    onClick = { onSelected(minutes) },
                    enabled = enabled
                ) {
                    Text(label, fontSize = 12.sp)
                }
            }
        }
    }
}

// =============================================
// 繰り返しパターン選択カード
// =============================================
@Composable
fun RecurrencePickerCard(
    prompt: String,
    enabled: Boolean,
    onSelected: (String, String, String) -> Unit
) {
    val patterns = listOf(
        "毎日" to "DAILY",
        "毎週" to "WEEKLY",
        "隔週" to "BIWEEKLY",
        "毎月（日付）" to "MONTHLY_DATE",
        "毎月（曜日）" to "MONTHLY_WEEK",
        "毎年" to "YEARLY"
    )

    AiCardWrapper {
        Text(prompt, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            patterns.forEach { (label, value) ->
                FilledTonalButton(
                    onClick = { onSelected(value, "", "") },
                    enabled = enabled
                ) {
                    Text(label, fontSize = 12.sp)
                }
            }
        }
    }
}

// =============================================
// ロードマップステップ入力カード
// =============================================
@Composable
fun RoadmapStepCard(
    prompt: String,
    currentSteps: List<DraftRoadmapStep>,
    enabled: Boolean,
    onStepsSet: (List<DraftRoadmapStep>) -> Unit
) {
    var steps by remember { mutableStateOf(currentSteps.toMutableList()) }
    var newStepText by remember { mutableStateOf("") }

    AiCardWrapper {
        Text(prompt, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(8.dp))

        // 既存ステップ表示
        steps.forEachIndexed { index, step ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${index + 1}. ${step.title}",
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        steps = steps.toMutableList().apply { removeAt(index) }
                    },
                    modifier = Modifier.size(24.dp),
                    enabled = enabled
                ) {
                    Icon(Icons.Default.Close, contentDescription = "削除",
                        modifier = Modifier.size(16.dp))
                }
            }
        }

        // 新規追加
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 4.dp)
        ) {
            OutlinedTextField(
                value = newStepText,
                onValueChange = { newStepText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("ステップ名", fontSize = 12.sp) },
                singleLine = true,
                enabled = enabled,
                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
            )
            IconButton(
                onClick = {
                    if (newStepText.isNotBlank()) {
                        steps = steps.toMutableList().apply {
                            add(DraftRoadmapStep(
                                title = newStepText.trim(),
                                sortOrder = size
                            ))
                        }
                        newStepText = ""
                    }
                },
                enabled = enabled && newStepText.isNotBlank()
            ) {
                Icon(Icons.Default.Add, contentDescription = "追加")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        FilledTonalButton(
            onClick = { onStepsSet(steps) },
            enabled = enabled && steps.isNotEmpty()
        ) {
            Text("ステップ確定")
        }
    }
}

// =============================================
// 確認カード
// =============================================
@Composable
fun TaskConfirmationCard(
    draft: DraftTaskData,
    allTags: List<Tag>,
    isActive: Boolean,
    onConfirm: () -> Unit,
    onModify: () -> Unit,
    onCancel: () -> Unit
) {
    AiCardWrapper {
        Text("登録内容の確認", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        ConfirmRow("予定の種類", when {
            draft.isIndefinite -> "無期限"
            draft.scheduleType == ScheduleType.NORMAL -> "通常"
            draft.scheduleType == ScheduleType.PERIOD -> "期間"
            draft.scheduleType == ScheduleType.RECURRING -> "繰り返し"
            else -> draft.scheduleType.name
        })
        ConfirmRow("タスク名", draft.title)

        if (draft.memo.isNotEmpty()) {
            ConfirmRow("メモ", draft.memo)
        }
        if (draft.startDate.isNotEmpty()) {
            ConfirmRow("日付", draft.startDate +
                if (draft.endDate.isNotEmpty()) " 〜 ${draft.endDate}" else "")
        }
        if (draft.startTime.isNotEmpty()) {
            ConfirmRow("時刻", draft.startTime +
                if (draft.endTime.isNotEmpty()) " 〜 ${draft.endTime}" else "")
        }
        if (draft.scheduleType == ScheduleType.RECURRING) {
            ConfirmRow("繰り返し", recurrenceDisplayLabel(draft.recurrencePattern))
        }

        ConfirmRow("通知", if (draft.notifyEnabled) {
            when (draft.notifyMinutesBefore) {
                0 -> "予定時刻"
                60 -> "1時間前"
                1440 -> "1日前"
                else -> "${draft.notifyMinutesBefore}分前"
            }
        } else "なし")

        ConfirmRow("ロードマップ", if (draft.roadmapEnabled)
            "${draft.roadmapSteps.size}ステップ" else "なし")

        if (draft.tagIds.isNotEmpty()) {
            val tagNames = allTags.filter { it.id in draft.tagIds }.joinToString(", ") { it.name }
            ConfirmRow("タグ", tagNames)
        }
        if (draft.relatedTaskIds.isNotEmpty()) {
            ConfirmRow("関連予定", "${draft.relatedTaskIds.size}件")
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        if (isActive) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("登録する")
                }
                OutlinedButton(
                    onClick = onModify,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("修正する")
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(
                onClick = onCancel,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("キャンセル", color = MaterialTheme.colorScheme.error)
            }
        } else {
            Text("（登録済み）", color = Color.Gray, fontSize = 12.sp)
        }
    }
}

@Composable
fun ConfirmRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(
            label,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            modifier = Modifier.width(90.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, fontSize = 13.sp)
    }
}

// =============================================
// 登録完了カード
// =============================================
@Composable
fun TaskRegisteredCard(title: String) {
    AiCardWrapper {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "「${title}」を登録しました",
                fontWeight = FontWeight.Medium,
                color = Color(0xFF4CAF50)
            )
        }
    }
}

// =============================================
// タイピングインジケーター
// =============================================
@Composable
fun ChatTypingIndicator() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                "考え中…",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                fontSize = 13.sp,
                color = Color.Gray
            )
        }
    }
}

// =============================================
// ヘルパー
// =============================================
private fun recurrenceDisplayLabel(pattern: String): String {
    return when (pattern) {
        "DAILY" -> "毎日"
        "WEEKLY" -> "毎週"
        "BIWEEKLY" -> "隔週"
        "MONTHLY_DATE" -> "毎月（日付）"
        "MONTHLY_WEEK" -> "毎月（曜日）"
        "YEARLY" -> "毎年"
        else -> pattern
    }
}
```

---

### 変更しないファイル

以下のファイルはすべてそのまま維持です: `ChatTaskModels.kt`（今回全置換）、`Task.kt`、`TaskDao.kt`、`TaskRepository.kt`、`Tag.kt`、`TagDao.kt`、`TaskTagCrossRef.kt`、`TaskTagCrossRefDao.kt`、`TaskRelation.kt`、`TaskRelationDao.kt`、`RoadmapStep.kt`、`RoadmapStepDao.kt`、`AlarmScheduler.kt`、`AiEngineManager.kt`、`AiModelManager.kt`、`AppDatabase.kt`、`NavGraph.kt`、`MainActivity.kt`、`SettingsScreen.kt`、`AddTaskBottomSheet.kt`、`AddTaskViewModel.kt`、`ScheduleType.kt`、`RecurrencePattern.kt`

---

### ビルド手順

1. `ChatTaskModels.kt` を上記コードで全置換（または新規作成済みなら上書き）
2. `AiChatViewModel.kt` を上記コードで全置換
3. `AiChatScreen.kt` を上記コードで全置換
4. Gradle Sync → Clean → Rebuild
5. 既存のimportエラーがあれば、旧コードの `DraftTaskData` や `ChatContent` が新ファイル内にすべて定義されているので解消されるはず

### テストチェックリスト

1. AIチャット画面を開く → あいさつメッセージが表示される
2. 「予定を登録」と入力 → 種類選択ボタンが表示される
3. 「通常」をタップ → 通知有無ボタンが表示される
4. 「あり」をタップ → ロードマップ有無ボタンが表示される
5. 「なし」をタップ → タスク名入力プロンプトが表示される
6. テキスト欄にタスク名を入力して送信 → メモ入力プロンプト（スキップ可）
7. 「スキップ」をタップ → 日付選択カード
8. 日付を選択 → 時刻選択カード
9. 時刻を設定して「決定」 → タグ選択カード
10. タグを選択して「決定」 → 関連予定カード
11. 「スキップ」 → 写真カード → 「スキップ」 → 通知タイミングカード
12. 「30分前」をタップ → 確認カード表示（全項目が正しいか確認）
13. 「登録する」をタップ → 登録完了カード＋完了メッセージ
14. スケジュール一覧画面で登録されたタスクが表示されることを確認
15. 「キャンセル」と入力してウィザード中断が動作することを確認
16. 期間タスク → 開始日＋終了日の2段階日付選択が動作する
17. 繰り返しタスク → 繰り返しパターン選択が表示される
18. 無期限タスク → 日付ステップがスキップされる
19. ロードマップあり → ステップ入力カードが表示される
20. 「今日の予定」等で検索が従来通り動作する

### 設計方針の要約

AIへの自然文解釈依存を**完全に排除**し、すべてのデータ入力をUIボタン・ピッカー・テキストフィールドで行うステートマシン方式に変更しました。AIエンジンは自由会話時のみ使用し、タスク登録フロー自体はAIの有無に関係なく動作します。これにより「AIがうまくくみ取れない」問題は発生しません。