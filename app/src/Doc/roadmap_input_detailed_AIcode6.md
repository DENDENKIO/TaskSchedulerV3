新しいフローの全体設計を整理して、3ファイルを提供します。

---

## 新フロー（全7ステップ）

```
ユーザー「予定を登録」等のキーワード
  ↓
Step 1: 予定の種類（通常／期間／無期限／繰り返し）← ボタン【AI不要】
  ↓
Step 2: タスク名入力 ← テキスト入力【AI不要】
  ↓
Step 3: メモ入力 ← テキスト入力 → AIが整形 or スキップ【AI使用】
  ↓
Step 4: 日付選択 ← DatePicker or テキスト入力「4月25日」→ AI解析【AI使用】
         （期間タスク → 終了日も同様に）
         （無期限 → 自動スキップ）
  ↓
Step 5: 時刻選択 ← TimePicker or テキスト入力「7時半」→ AI解析【AI使用】
  ↓
Step 6: タグ選択 ← チップ選択【AI不要】
  ↓
Step 7: 関連予定 ← AIが候補をリストアップ＋手動選択【AI使用】
  ↓
Step 8: 写真追加 ← カメラ撮影 or ギャラリー選択 or スキップ【AI不要】
  ↓
Step 9: 確認カード →「登録する」「修正する」「キャンセル」

※ 通知は全タスク自動で「1日前」に設定
※ ロードマップは後から編集画面で追加
```

---

### ファイル 1: `ChatTaskModels.kt`

**パス**: `app/src/main/java/com/example/taskschedulerv3/ui/aichat/ChatTaskModels.kt`

```kotlin
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

    private val db = AppDatabase.getDatabase(application)
    private val taskDao = db.taskDao()
    private val tagDao = db.tagDao()
    private val crossRefDao = db.taskTagCrossRefDao()
    private val relationDao = db.taskRelationDao()
    private val roadmapStepDao = db.roadmapStepDao()
    private val repository = TaskRepository(taskDao, roadmapStepDao)

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()

    private val _wizardStep = MutableStateFlow(WizardStep.IDLE)
    val wizardStep: StateFlow<WizardStep> = _wizardStep.asStateFlow()

    private val _draft = MutableStateFlow(DraftTaskData())
    val draft: StateFlow<DraftTaskData> = _draft.asStateFlow()

    private val _allTags = MutableStateFlow<List<Tag>>(emptyList())
    val allTags: StateFlow<List<Tag>> = _allTags.asStateFlow()

    private val _allTasks = MutableStateFlow<List<Task>>(emptyList())
    val allTasks: StateFlow<List<Task>> = _allTasks.asStateFlow()

    init {
        addAiMessage(ChatContent.Text(
            "こんにちは！予定の検索や登録のお手伝いをします。\n" +
            "「予定を登録」と入力するか、質問を自由にどうぞ。"
        ))
        viewModelScope.launch {
            tagDao.getAllTags().collect { _allTags.value = it }
        }
        viewModelScope.launch {
            taskDao.getAllTasks().collect {
                _allTasks.value = it.filter { t -> !t.isDeleted && !t.isCompleted }
            }
        }
    }

    // =========================================================
    // テキスト送信
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
    // ボタン選択
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
    // 日付選択（DatePickerから）
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
    // 日付テキスト入力（AI解析）
    // =========================================================
    fun onDateTextInput(text: String) {
        val step = _wizardStep.value
        addUserMessage(text)
        viewModelScope.launch {
            _isTyping.value = true
            try {
                val parsed = parseDateWithAi(text)
                if (parsed != null) {
                    addAiMessage(ChatContent.Text("${parsed} に設定しました。"))
                    when (step) {
                        WizardStep.SELECT_DATE -> {
                            _draft.value = _draft.value.copy(startDate = parsed)
                            if (_draft.value.scheduleType == ScheduleType.PERIOD) {
                                moveToStep(WizardStep.SELECT_END_DATE)
                            } else {
                                moveToStep(WizardStep.SELECT_TIME)
                            }
                        }
                        WizardStep.SELECT_END_DATE -> {
                            _draft.value = _draft.value.copy(endDate = parsed)
                            moveToStep(WizardStep.SELECT_TIME)
                        }
                        else -> {}
                    }
                } else {
                    addAiMessage(ChatContent.Text(
                        "日付を認識できませんでした。カレンダーから選択してください。"
                    ))
                }
            } finally {
                _isTyping.value = false
            }
        }
    }

    // =========================================================
    // 時刻選択（TimePickerから）
    // =========================================================
    fun onTimeSelected(startTime: String, endTime: String = "") {
        val display = if (endTime.isNotEmpty()) "$startTime 〜 $endTime" else startTime
        addUserMessage(display)
        viewModelScope.launch {
            _draft.value = _draft.value.copy(startTime = startTime, endTime = endTime)
            moveToStep(WizardStep.SELECT_TAGS)
        }
    }

    // =========================================================
    // 時刻テキスト入力（AI解析）
    // =========================================================
    fun onTimeTextInput(text: String) {
        addUserMessage(text)
        viewModelScope.launch {
            _isTyping.value = true
            try {
                val parsed = parseTimeWithAi(text)
                if (parsed != null) {
                    addAiMessage(ChatContent.Text("${parsed.first}" +
                        if (parsed.second.isNotEmpty()) " 〜 ${parsed.second}" else "" +
                        " に設定しました。"))
                    _draft.value = _draft.value.copy(
                        startTime = parsed.first,
                        endTime = parsed.second
                    )
                    moveToStep(WizardStep.SELECT_TAGS)
                } else {
                    addAiMessage(ChatContent.Text(
                        "時刻を認識できませんでした。時計から選択してください。"
                    ))
                }
            } finally {
                _isTyping.value = false
            }
        }
    }

    // =========================================================
    // タグ選択
    // =========================================================
    fun onTagsSelected(tagIds: List<Int>) {
        val names = _allTags.value.filter { it.id in tagIds }.joinToString(", ") { it.name }
        addUserMessage(if (tagIds.isEmpty()) "スキップ" else "タグ: $names")
        viewModelScope.launch {
            _draft.value = _draft.value.copy(tagIds = tagIds)
            // 関連予定ステップへ → AI候補をリストアップ
            _isTyping.value = true
            try {
                suggestRelationsAndMove()
            } finally {
                _isTyping.value = false
            }
        }
    }

    // =========================================================
    // 関連予定選択
    // =========================================================
    fun onRelationsSelected(taskIds: List<Int>) {
        addUserMessage(if (taskIds.isEmpty()) "スキップ" else "関連予定: ${taskIds.size}件")
        viewModelScope.launch {
            _draft.value = _draft.value.copy(relatedTaskIds = taskIds)
            moveToStep(WizardStep.SELECT_PHOTOS)
        }
    }

    // =========================================================
    // 写真選択
    // =========================================================
    fun onPhotoSelected(path: String?) {
        addUserMessage(if (path == null) "スキップ" else "写真を追加しました")
        viewModelScope.launch {
            _draft.value = _draft.value.copy(photoPath = path)
            moveToStep(WizardStep.CONFIRM)
        }
    }

    // =========================================================
    // 繰り返しパターン
    // =========================================================
    fun onRecurrenceSelected(pattern: String, days: String = "", endDate: String = "") {
        addUserMessage("繰り返し: ${recurrenceLabel(pattern)}")
        viewModelScope.launch {
            _draft.value = _draft.value.copy(
                recurrencePattern = pattern,
                recurrenceDays = days,
                recurrenceEndDate = endDate
            )
            moveToStep(WizardStep.SELECT_DATE)
        }
    }

    // =========================================================
    // 確認アクション
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
                    "「${_draft.value.title}」を登録しました！\n" +
                    "通知は1日前に設定されています。\n" +
                    "ロードマップが必要な場合は編集画面から追加できます。\n\n" +
                    "他にご用件はありますか？"
                ))
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
        addAiMessage(ChatContent.Text("最初からやり直します。"))
        moveToStep(WizardStep.SELECT_TYPE)
    }

    // =========================================================
    // 内部: テキスト入力振り分け
    // =========================================================
    private suspend fun processInput(text: String) {
        val step = _wizardStep.value

        when (step) {
            WizardStep.IDLE -> {
                if (isRegistrationIntent(text)) {
                    startWizard()
                } else {
                    handleNonWizardInput(text)
                }
            }
            WizardStep.INPUT_TITLE -> {
                _draft.value = _draft.value.copy(title = text)
                addAiMessage(ChatContent.Text("タスク名:「${text}」"))
                moveToStep(WizardStep.INPUT_MEMO)
            }
            WizardStep.INPUT_MEMO -> {
                // AI でメモを整形
                val formatted = formatMemoWithAi(text)
                _draft.value = _draft.value.copy(memo = formatted)
                addAiMessage(ChatContent.Text("メモを整えました:\n$formatted"))
                moveToDateStep()
            }
            WizardStep.SELECT_DATE, WizardStep.SELECT_END_DATE -> {
                // テキストで日付入力された場合
                onDateTextInput(text)
            }
            WizardStep.SELECT_TIME -> {
                // テキストで時刻入力された場合
                onTimeTextInput(text)
            }
            WizardStep.INPUT_ROADMAP_STEPS -> {
                // 旧ステップ（使わないが安全のため）
                addAiMessage(ChatContent.Text("上のボタンから操作してください。"))
            }
            else -> {
                if (isRegistrationCancel(text)) {
                    cancelRegistration()
                } else {
                    addAiMessage(ChatContent.Text(
                        "登録の途中です。ボタンから選択するか、「キャンセル」で中断できます。"
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
        addAiMessage(ChatContent.Text("予定を登録しましょう！"))
        moveToStep(WizardStep.SELECT_TYPE)
    }

    // =========================================================
    // ボタン選択処理
    // =========================================================
    private suspend fun handleWizardChoice(step: WizardStep, value: String) {
        addUserMessage(value)

        when (step) {
            WizardStep.SELECT_TYPE -> {
                when (value) {
                    "通常" -> {
                        _draft.value = _draft.value.copy(scheduleType = ScheduleType.NORMAL)
                    }
                    "期間" -> {
                        _draft.value = _draft.value.copy(scheduleType = ScheduleType.PERIOD)
                    }
                    "無期限" -> {
                        _draft.value = _draft.value.copy(
                            scheduleType = ScheduleType.NORMAL,
                            isIndefinite = true
                        )
                    }
                    "繰り返し" -> {
                        _draft.value = _draft.value.copy(scheduleType = ScheduleType.RECURRING)
                    }
                }
                addAiMessage(ChatContent.Text("「${value}」を選択しました。"))
                moveToStep(WizardStep.INPUT_TITLE)
            }

            WizardStep.INPUT_MEMO -> {
                if (value == "スキップ") {
                    addAiMessage(ChatContent.Text("メモをスキップしました。"))
                    moveToDateStep()
                }
            }

            WizardStep.SELECT_TIME -> {
                if (value == "スキップ") {
                    addAiMessage(ChatContent.Text("時刻をスキップしました。"))
                    _draft.value = _draft.value.copy(startTime = "", endTime = "")
                    moveToStep(WizardStep.SELECT_TAGS)
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

            WizardStep.SELECT_RECURRENCE -> {
                // 繰り返しパターンの簡易選択はonRecurrenceSelectedで処理
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
    // ステップ遷移
    // =========================================================
    private fun moveToStep(step: WizardStep) {
        _wizardStep.value = step
        when (step) {
            WizardStep.SELECT_TYPE -> {
                addAiMessage(ChatContent.ChoiceButtons(
                    prompt = "予定の種類を選んでください",
                    choices = listOf(
                        Choice("通常", "通常"),
                        Choice("期間", "期間"),
                        Choice("無期限", "無期限"),
                        Choice("繰り返し", "繰り返し")
                    )
                ))
            }

            WizardStep.INPUT_TITLE -> {
                addAiMessage(ChatContent.TextInput(
                    prompt = "タスク名を入力してください",
                    hint = "例: 歯医者、チームミーティング",
                    allowSkip = false
                ))
            }

            WizardStep.INPUT_MEMO -> {
                addAiMessage(ChatContent.TextInputWithAi(
                    prompt = "メモを入力してください",
                    hint = "例: 資料を持参、場所は3階会議室",
                    allowSkip = true,
                    aiDescription = "入力内容をAIがきれいに整えます"
                ))
            }

            WizardStep.SELECT_DATE -> {
                val label = if (_draft.value.scheduleType == ScheduleType.PERIOD)
                    "開始日を選んでください" else "日付を選んでください"
                addAiMessage(ChatContent.DatePickerRequest(
                    prompt = label + "\nカレンダーから選択、または「4月25日」のようにテキスト入力もできます",
                    allowSkip = false,
                    allowTextInput = true
                ))
            }

            WizardStep.SELECT_END_DATE -> {
                addAiMessage(ChatContent.DatePickerRequest(
                    prompt = "終了日を選んでください\nカレンダーまたはテキスト入力で指定できます",
                    allowSkip = false,
                    allowTextInput = true
                ))
            }

            WizardStep.SELECT_TIME -> {
                addAiMessage(ChatContent.TimePickerRequest(
                    prompt = "時刻を設定してください\n時計から選択、または「7時半」「14:00〜15:30」のように入力もできます",
                    allowSkip = true,
                    allowTextInput = true
                ))
            }

            WizardStep.SELECT_RECURRENCE -> {
                addAiMessage(ChatContent.RecurrencePickerRequest(
                    prompt = "繰り返しパターンを選択してください"
                ))
            }

            WizardStep.SELECT_TAGS -> {
                addAiMessage(ChatContent.TagPickerRequest(
                    prompt = "タグを選択してください（複数可）",
                    allowSkip = true
                ))
            }

            WizardStep.SELECT_RELATIONS -> {
                // suggestRelationsAndMove() で直接カード追加するのでここは空
            }

            WizardStep.SELECT_PHOTOS -> {
                addAiMessage(ChatContent.PhotoPickerRequest(
                    prompt = "写真を追加しますか？\nカメラで撮影、またはギャラリーから選択できます",
                    allowSkip = true
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

    /** メモの後→日付へ */
    private fun moveToDateStep() {
        val d = _draft.value
        when {
            d.isIndefinite -> moveToStep(WizardStep.SELECT_TIME)
            d.scheduleType == ScheduleType.RECURRING -> moveToStep(WizardStep.SELECT_RECURRENCE)
            else -> moveToStep(WizardStep.SELECT_DATE)
        }
    }

    /** 関連予定AI候補 → SELECT_RELATIONS */
    private suspend fun suggestRelationsAndMove() {
        val d = _draft.value
        val tasks = _allTasks.value

        var suggestedIds = emptyList<Int>()
        var aiReason = ""

        if (tasks.isNotEmpty() && d.title.isNotEmpty() && AiEngineManager.isLoaded()) {
            try {
                val taskList = tasks.take(30).joinToString("\n") { t ->
                    "ID:${t.id} タイトル:${t.title} 日付:${t.startDate}"
                }
                val prompt = """以下のタスク一覧から「${d.title}」に関連しそうなタスクのIDを最大5件、
JSON配列で返してください。関連がなければ空配列[]を返してください。
理由も一言添えてください。

フォーマット:
{"ids":[1,2,3],"reason":"理由"}

タスク一覧:
$taskList"""

                val response = withContext(Dispatchers.IO) {
                    AiEngineManager.generateResponse(prompt)
                }
                if (response != null) {
                    val jsonMatch = Regex("""\{[^}]*"ids"\s*:\s*\[([^\]]*)\][^}]*\}""")
                        .find(response)
                    if (jsonMatch != null) {
                        val idsStr = jsonMatch.groupValues[1]
                        suggestedIds = Regex("""\d+""").findAll(idsStr)
                            .map { it.value.toIntOrNull() }
                            .filterNotNull()
                            .filter { id -> tasks.any { it.id == id } }
                            .toList()
                        val reasonMatch = Regex(""""reason"\s*:\s*"([^"]*)"?""")
                            .find(jsonMatch.value)
                        aiReason = reasonMatch?.groupValues?.getOrNull(1) ?: ""
                    }
                }
                Log.d(TAG, "AI suggested relations: $suggestedIds reason=$aiReason")
            } catch (e: Exception) {
                Log.e(TAG, "relation suggestion error", e)
            }
        }

        _wizardStep.value = WizardStep.SELECT_RELATIONS
        addAiMessage(ChatContent.RelationPickerRequest(
            prompt = if (suggestedIds.isNotEmpty())
                "AIが関連しそうな予定を見つけました" +
                    if (aiReason.isNotEmpty()) "\n($aiReason)" else ""
            else
                "関連する予定を選択してください（複数可）",
            suggestedTaskIds = suggestedIds,
            allowSkip = true
        ))
    }

    // =========================================================
    // AI: メモ整形
    // =========================================================
    private suspend fun formatMemoWithAi(rawMemo: String): String {
        if (!AiEngineManager.isLoaded()) {
            Log.d(TAG, "AI not loaded, returning raw memo")
            return rawMemo
        }
        return try {
            val prompt = """以下のメモを省略せず、読みやすく整えてください。
内容は一切削除・要約しないでください。
箇条書きがあればそのまま維持してください。
元のメモ:
$rawMemo

整えたメモ:"""

            val response = withContext(Dispatchers.IO) {
                AiEngineManager.generateResponse(prompt)
            }
            if (response != null && response.trim().isNotEmpty()) {
                response.trim()
            } else {
                rawMemo
            }
        } catch (e: Exception) {
            Log.e(TAG, "memo formatting error", e)
            rawMemo
        }
    }

    // =========================================================
    // AI: 日付テキスト解析
    // =========================================================
    private suspend fun parseDateWithAi(text: String): String? {
        // まずルールベースで試行
        val ruleBased = parseDateRuleBased(text)
        if (ruleBased != null) return ruleBased

        // AIで解析
        if (!AiEngineManager.isLoaded()) return null
        return try {
            val today = LocalDate.now()
            val prompt = """今日は${today.format(DateTimeFormatter.ofPattern("yyyy年M月d日"))}です。
以下のテキストから日付を読み取り、yyyy-MM-dd形式で返してください。
日付だけを返し、他の文字は含めないでください。
読み取れない場合は「不明」と返してください。

テキスト: $text"""

            val response = withContext(Dispatchers.IO) {
                AiEngineManager.generateResponse(prompt)
            }
            if (response != null) {
                val dateMatch = Regex("""\d{4}-\d{2}-\d{2}""").find(response.trim())
                dateMatch?.value
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "date parsing error", e)
            null
        }
    }

    /** ルールベース日付パース */
    private fun parseDateRuleBased(text: String): String? {
        val today = LocalDate.now()
        val fmt = DateTimeFormatter.ISO_LOCAL_DATE

        // 今日, 明日, 明後日
        if (text.contains("今日")) return today.format(fmt)
        if (text.contains("明後日")) return today.plusDays(2).format(fmt)
        if (text.contains("明日")) return today.plusDays(1).format(fmt)

        // 「4月25日」「4/25」「2026年4月25日」
        val patterns = listOf(
            Regex("""(\d{4})\s*年\s*(\d{1,2})\s*月\s*(\d{1,2})\s*日"""),
            Regex("""(\d{1,2})\s*月\s*(\d{1,2})\s*日"""),
            Regex("""(\d{1,2})/(\d{1,2})"""),
            Regex("""(\d{4})[/-](\d{1,2})[/-](\d{1,2})""")
        )

        // yyyy年M月d日
        patterns[0].find(text)?.let {
            return try {
                LocalDate.of(
                    it.groupValues[1].toInt(),
                    it.groupValues[2].toInt(),
                    it.groupValues[3].toInt()
                ).format(fmt)
            } catch (_: Exception) { null }
        }

        // M月d日
        patterns[1].find(text)?.let {
            return try {
                var date = LocalDate.of(
                    today.year, it.groupValues[1].toInt(), it.groupValues[2].toInt()
                )
                if (date.isBefore(today)) date = date.plusYears(1)
                date.format(fmt)
            } catch (_: Exception) { null }
        }

        // M/d
        patterns[2].find(text)?.let {
            return try {
                var date = LocalDate.of(
                    today.year, it.groupValues[1].toInt(), it.groupValues[2].toInt()
                )
                if (date.isBefore(today)) date = date.plusYears(1)
                date.format(fmt)
            } catch (_: Exception) { null }
        }

        // yyyy-MM-dd
        patterns[3].find(text)?.let {
            return try {
                LocalDate.of(
                    it.groupValues[1].toInt(),
                    it.groupValues[2].toInt(),
                    it.groupValues[3].toInt()
                ).format(fmt)
            } catch (_: Exception) { null }
        }

        return null
    }

    // =========================================================
    // AI: 時刻テキスト解析
    // =========================================================
    private suspend fun parseTimeWithAi(text: String): Pair<String, String>? {
        // ルールベース
        val ruleBased = parseTimeRuleBased(text)
        if (ruleBased != null) return ruleBased

        // AI
        if (!AiEngineManager.isLoaded()) return null
        return try {
            val prompt = """以下のテキストから時刻を読み取り、HH:mm形式で返してください。
範囲がある場合は「HH:mm-HH:mm」の形式で返してください。
時刻だけを返し、他の文字は含めないでください。
読み取れない場合は「不明」と返してください。

テキスト: $text"""

            val response = withContext(Dispatchers.IO) {
                AiEngineManager.generateResponse(prompt)
            }
            if (response != null) {
                val rangeMatch = Regex("""(\d{2}:\d{2})\s*[-〜~]\s*(\d{2}:\d{2})""")
                    .find(response.trim())
                if (rangeMatch != null) {
                    Pair(rangeMatch.groupValues[1], rangeMatch.groupValues[2])
                } else {
                    val singleMatch = Regex("""\d{2}:\d{2}""").find(response.trim())
                    if (singleMatch != null) Pair(singleMatch.value, "") else null
                }
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "time parsing error", e)
            null
        }
    }

    /** ルールベース時刻パース */
    private fun parseTimeRuleBased(text: String): Pair<String, String>? {
        // 「HH:mm〜HH:mm」
        Regex("""(\d{1,2}):(\d{2})\s*[-〜~]\s*(\d{1,2}):(\d{2})""").find(text)?.let {
            val s = String.format("%02d:%02d", it.groupValues[1].toInt(), it.groupValues[2].toInt())
            val e = String.format("%02d:%02d", it.groupValues[3].toInt(), it.groupValues[4].toInt())
            return Pair(s, e)
        }

        // 「HH:mm」
        Regex("""(\d{1,2}):(\d{2})""").find(text)?.let {
            return Pair(
                String.format("%02d:%02d", it.groupValues[1].toInt(), it.groupValues[2].toInt()),
                ""
            )
        }

        // 「7時半」「7時30分」「14時」
        Regex("""(\d{1,2})\s*時\s*半""").find(text)?.let {
            return Pair(String.format("%02d:30", it.groupValues[1].toInt()), "")
        }
        Regex("""(\d{1,2})\s*時\s*(\d{1,2})\s*分""").find(text)?.let {
            return Pair(
                String.format("%02d:%02d", it.groupValues[1].toInt(), it.groupValues[2].toInt()),
                ""
            )
        }
        Regex("""(\d{1,2})\s*時""").find(text)?.let {
            return Pair(String.format("%02d:00", it.groupValues[1].toInt()), "")
        }

        // 「午前9時」「午後3時半」
        Regex("""午前\s*(\d{1,2})\s*時\s*半""").find(text)?.let {
            return Pair(String.format("%02d:30", it.groupValues[1].toInt()), "")
        }
        Regex("""午後\s*(\d{1,2})\s*時\s*半""").find(text)?.let {
            val h = it.groupValues[1].toInt().let { v -> if (v < 12) v + 12 else v }
            return Pair(String.format("%02d:30", h), "")
        }
        Regex("""午前\s*(\d{1,2})\s*時""").find(text)?.let {
            return Pair(String.format("%02d:00", it.groupValues[1].toInt()), "")
        }
        Regex("""午後\s*(\d{1,2})\s*時""").find(text)?.let {
            val h = it.groupValues[1].toInt().let { v -> if (v < 12) v + 12 else v }
            return Pair(String.format("%02d:00", h), "")
        }

        return null
    }

    // =========================================================
    // 非ウィザード（検索/自由会話）
    // =========================================================
    private suspend fun handleNonWizardInput(text: String) {
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
                    "・ ${t.title}$time"
                }
                addAiMessage(ChatContent.Text("${dateMatch} の予定:\n$summary"))
            }
            return
        }

        if (AiEngineManager.isLoaded()) {
            val response = withContext(Dispatchers.IO) {
                AiEngineManager.generateResponse(text)
            }
            if (response != null) {
                addAiMessage(ChatContent.Text(response))
            } else {
                addAiMessage(ChatContent.Text("応答を生成できませんでした。"))
            }
        } else {
            addAiMessage(ChatContent.Text(
                "「予定を登録」と入力するとAI無しでも予定登録ができます。\n" +
                "AI機能を使うには設定画面から有効にしてください。"
            ))
        }
    }

    // =========================================================
    // DB登録
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
            priority = 1, // 中（デフォルト）
            notifyEnabled = true,           // 全タスク通知あり
            notifyMinutesBefore = 1440,     // 1日前（1440分）
            isIndefinite = d.isIndefinite,
            roadmapEnabled = false,         // 後で編集画面から追加
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

        // 通知スケジュール（常に1日前）
        val registeredTask = taskDao.getTaskById(taskId)
        if (registeredTask != null) {
            AlarmScheduler.scheduleForTask(getApplication(), registeredTask)
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
            content = ChatContent.Text(text), isUser = true
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
        val fmt = DateTimeFormatter.ISO_LOCAL_DATE
        return when {
            text.contains("今日") -> today.format(fmt)
            text.contains("明後日") -> today.plusDays(2).format(fmt)
            text.contains("明日") -> today.plusDays(1).format(fmt)
            else -> {
                Regex("""(\d{4})[/-](\d{1,2})[/-](\d{1,2})""").find(text)?.let {
                    try {
                        LocalDate.of(
                            it.groupValues[1].toInt(),
                            it.groupValues[2].toInt(),
                            it.groupValues[3].toInt()
                        ).format(fmt)
                    } catch (_: Exception) { null }
                }
            }
        }
    }

    private fun recurrenceLabel(pattern: String): String = when (pattern) {
        "DAILY" -> "毎日"; "WEEKLY" -> "毎週"; "BIWEEKLY" -> "隔週"
        "MONTHLY_DATE" -> "毎月（日付）"; "MONTHLY_WEEK" -> "毎月（曜日）"
        "YEARLY" -> "毎年"; else -> pattern
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
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.taskschedulerv3.data.model.ScheduleType
import com.example.taskschedulerv3.data.model.Tag
import com.example.taskschedulerv3.data.model.Task
import com.example.taskschedulerv3.util.PhotoFileManager
import java.io.File
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
    var inputText by remember { mutableStateOf("") }

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
                        viewModel = viewModel,
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
                            when (wizardStep) {
                                WizardStep.IDLE -> "なんでも聞いてください…"
                                WizardStep.INPUT_TITLE -> "タスク名を入力…"
                                WizardStep.INPUT_MEMO -> "メモを入力…"
                                WizardStep.SELECT_DATE, WizardStep.SELECT_END_DATE ->
                                    "4月25日、明日 など…"
                                WizardStep.SELECT_TIME -> "7時半、14:00〜15:30 など…"
                                else -> "テキストを入力…"
                            }
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
// メッセージ振り分け
// =============================================
@Composable
fun ChatMessageItem(
    message: ChatMessage,
    wizardStep: WizardStep,
    allTags: List<Tag>,
    allTasks: List<Task>,
    viewModel: AiChatViewModel,
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
                            onClick = { viewModel.onChoiceSelected(wizardStep, choice.value) },
                            enabled = isLatestAiMessage
                        ) { Text(choice.label) }
                    }
                    if (content.allowSkip) {
                        OutlinedButton(
                            onClick = { viewModel.onChoiceSelected(wizardStep, "スキップ") },
                            enabled = isLatestAiMessage
                        ) { Text("スキップ") }
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
            }
        }
        is ChatContent.TextInputWithAi -> {
            AiCardWrapper {
                Text(content.prompt, fontWeight = FontWeight.Medium)
                if (content.hint.isNotEmpty()) {
                    Text(content.hint, color = Color.Gray, fontSize = 12.sp)
                }
                if (content.aiDescription.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            content.aiDescription,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(8.dp),
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
                if (content.allowSkip && isLatestAiMessage) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { viewModel.onChoiceSelected(wizardStep, "スキップ") }
                    ) { Text("スキップ") }
                }
            }
        }
        is ChatContent.DatePickerRequest -> {
            DatePickerCard(
                prompt = content.prompt,
                allowSkip = content.allowSkip,
                enabled = isLatestAiMessage,
                onDateSelected = { viewModel.onDateSelected(it) },
                onSkip = { viewModel.onChoiceSelected(wizardStep, "スキップ") }
            )
        }
        is ChatContent.TimePickerRequest -> {
            TimePickerCard(
                prompt = content.prompt,
                allowSkip = content.allowSkip,
                enabled = isLatestAiMessage,
                onTimeSelected = { s, e -> viewModel.onTimeSelected(s, e) },
                onSkip = { viewModel.onChoiceSelected(wizardStep, "スキップ") }
            )
        }
        is ChatContent.TagPickerRequest -> {
            TagPickerCard(
                prompt = content.prompt,
                tags = allTags,
                enabled = isLatestAiMessage,
                onTagsSelected = { viewModel.onTagsSelected(it) },
                onSkip = { viewModel.onTagsSelected(emptyList()) }
            )
        }
        is ChatContent.RelationPickerRequest -> {
            RelationPickerCard(
                prompt = content.prompt,
                tasks = allTasks,
                suggestedIds = content.suggestedTaskIds,
                enabled = isLatestAiMessage,
                onRelationsSelected = { viewModel.onRelationsSelected(it) },
                onSkip = { viewModel.onRelationsSelected(emptyList()) }
            )
        }
        is ChatContent.PhotoPickerRequest -> {
            PhotoPickerCard(
                prompt = content.prompt,
                enabled = isLatestAiMessage,
                onPhotoSelected = { viewModel.onPhotoSelected(it) },
                onSkip = { viewModel.onPhotoSelected(null) }
            )
        }
        is ChatContent.RecurrencePickerRequest -> {
            RecurrencePickerCard(
                prompt = content.prompt,
                enabled = isLatestAiMessage,
                onSelected = { p, d, e -> viewModel.onRecurrenceSelected(p, d, e) }
            )
        }
        is ChatContent.TaskConfirmation -> {
            TaskConfirmationCard(
                draft = content.draft,
                allTags = allTags,
                isActive = content.isActive && isLatestAiMessage,
                onConfirm = { viewModel.confirmRegistration() },
                onModify = { viewModel.goBackToModify() },
                onCancel = { viewModel.cancelRegistration() }
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
                topStart = 16.dp, topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            color = if (isUser)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Text(text, modifier = Modifier.padding(12.dp), fontSize = 14.sp)
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
            Column(modifier = Modifier.padding(12.dp), content = content)
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

    AiCardWrapper {
        Text(prompt, fontWeight = FontWeight.Medium, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
                onClick = {
                    val cal = Calendar.getInstance()
                    DatePickerDialog(context, { _, y, m, d ->
                        onDateSelected(String.format("%04d-%02d-%02d", y, m + 1, d))
                    }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH),
                        cal.get(Calendar.DAY_OF_MONTH)).show()
                },
                enabled = enabled
            ) { Text("カレンダー") }
            if (allowSkip) {
                OutlinedButton(onClick = onSkip, enabled = enabled) { Text("スキップ") }
            }
        }
        Text(
            "またはテキスト入力欄に日付を入力してください",
            fontSize = 11.sp, color = Color.Gray,
            modifier = Modifier.padding(top = 4.dp)
        )
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
        Text(prompt, fontWeight = FontWeight.Medium, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
                onClick = {
                    TimePickerDialog(context, { _, h, m ->
                        startTime = String.format("%02d:%02d", h, m)
                    }, 9, 0, true).show()
                },
                enabled = enabled
            ) { Text(if (startTime.isEmpty()) "開始" else startTime) }
            FilledTonalButton(
                onClick = {
                    TimePickerDialog(context, { _, h, m ->
                        endTime = String.format("%02d:%02d", h, m)
                    }, 10, 0, true).show()
                },
                enabled = enabled
            ) { Text(if (endTime.isEmpty()) "終了" else endTime) }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
                onClick = { onTimeSelected(startTime, endTime) },
                enabled = enabled && startTime.isNotEmpty()
            ) { Text("決定") }
            if (allowSkip) {
                OutlinedButton(onClick = onSkip, enabled = enabled) { Text("スキップ") }
            }
        }
        Text(
            "またはテキスト入力欄に「7時半」等と入力できます",
            fontSize = 11.sp, color = Color.Gray,
            modifier = Modifier.padding(top = 4.dp)
        )
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
                                selectedIds - tag.id else selectedIds + tag.id
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
            ) { Text("決定") }
            OutlinedButton(onClick = onSkip, enabled = enabled) { Text("スキップ") }
        }
    }
}

// =============================================
// 関連予定選択カード（AI候補付き）
// =============================================
@Composable
fun RelationPickerCard(
    prompt: String,
    tasks: List<Task>,
    suggestedIds: List<Int>,
    enabled: Boolean,
    onRelationsSelected: (List<Int>) -> Unit,
    onSkip: () -> Unit
) {
    // AI候補を初期選択状態にする
    var selectedIds by remember { mutableStateOf(suggestedIds.toSet()) }
    var showAll by remember { mutableStateOf(false) }

    AiCardWrapper {
        Text(prompt, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(8.dp))

        // AI候補表示
        if (suggestedIds.isNotEmpty()) {
            Text("AI おすすめ:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary)
            suggestedIds.forEach { id ->
                val t = tasks.find { it.id == id }
                if (t != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = enabled) {
                                selectedIds = if (id in selectedIds)
                                    selectedIds - id else selectedIds + id
                            }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = id in selectedIds,
                            onCheckedChange = null, enabled = enabled
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Column {
                            Text(t.title, fontSize = 13.sp, maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                            Text(t.startDate, fontSize = 11.sp, color = Color.Gray)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        // 全タスク一覧（展開式）
        if (tasks.isNotEmpty()) {
            OutlinedButton(
                onClick = { showAll = !showAll },
                enabled = enabled
            ) { Text(if (showAll) "一覧を閉じる" else "すべてから選択") }

            if (showAll) {
                Column(modifier = Modifier.heightIn(max = 200.dp)) {
                    tasks.filter { it.id !in suggestedIds }.take(30).forEach { task ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = enabled) {
                                    selectedIds = if (task.id in selectedIds)
                                        selectedIds - task.id else selectedIds + task.id
                                }
                                .padding(vertical = 2.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = task.id in selectedIds,
                                onCheckedChange = null, enabled = enabled
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(task.title, fontSize = 12.sp, maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        } else {
            Text("関連付けできる予定がありません", fontSize = 12.sp, color = Color.Gray)
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
                onClick = { onRelationsSelected(selectedIds.toList()) },
                enabled = enabled
            ) { Text("決定") }
            OutlinedButton(onClick = onSkip, enabled = enabled) { Text("スキップ") }
        }
    }
}

// =============================================
// 写真追加カード（カメラ＋ギャラリー対応）
// =============================================
@Composable
fun PhotoPickerCard(
    prompt: String,
    enabled: Boolean,
    onPhotoSelected: (String?) -> Unit,
    onSkip: () -> Unit
) {
    val context = LocalContext.current
    var photoPath by remember { mutableStateOf<String?>(null) }
    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var tempPhotoFile by remember { mutableStateOf<File?>(null) }

    // カメラ起動結果
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempPhotoFile != null) {
            val saved = PhotoFileManager.saveResizedPhotoFromFile(context, tempPhotoFile!!)
            if (saved != null) {
                photoPath = saved
            }
        }
    }

    // ギャラリー選択結果
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val saved = PhotoFileManager.saveResizedPhoto(context, uri)
            if (saved != null) {
                photoPath = saved
            }
        }
    }

    AiCardWrapper {
        Text(prompt, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(8.dp))

        if (photoPath != null) {
            Text("写真を選択済み", fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(4.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
                onClick = {
                    val result = PhotoFileManager.createTempPhotoUri(context)
                    tempPhotoUri = result.first
                    tempPhotoFile = result.second
                    cameraLauncher.launch(result.first)
                },
                enabled = enabled
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null,
                    modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("カメラ")
            }
            FilledTonalButton(
                onClick = {
                    galleryLauncher.launch(
                        androidx.activity.result.PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly
                        )
                    )
                },
                enabled = enabled
            ) {
                Icon(Icons.Default.Photo, contentDescription = null,
                    modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("ギャラリー")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (photoPath != null) {
                FilledTonalButton(
                    onClick = { onPhotoSelected(photoPath) },
                    enabled = enabled
                ) { Text("この写真で決定") }
            }
            OutlinedButton(onClick = onSkip, enabled = enabled) { Text("スキップ") }
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
        "毎日" to "DAILY", "毎週" to "WEEKLY", "隔週" to "BIWEEKLY",
        "毎月（日付）" to "MONTHLY_DATE", "毎月（曜日）" to "MONTHLY_WEEK",
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
                ) { Text(label, fontSize = 12.sp) }
            }
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
        Text("この内容で登録してよろしいですか？",
            fontWeight = FontWeight.Bold, fontSize = 15.sp)
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        ConfirmRow("種類", when {
            draft.isIndefinite -> "無期限"
            draft.scheduleType == ScheduleType.NORMAL -> "通常"
            draft.scheduleType == ScheduleType.PERIOD -> "期間"
            draft.scheduleType == ScheduleType.RECURRING -> "繰り返し"
            else -> draft.scheduleType.name
        })
        ConfirmRow("タスク名", draft.title)
        if (draft.memo.isNotEmpty()) ConfirmRow("メモ", draft.memo)
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
        ConfirmRow("通知", "1日前")
        if (draft.tagIds.isNotEmpty()) {
            val names = allTags.filter { it.id in draft.tagIds }.joinToString(", ") { it.name }
            ConfirmRow("タグ", names)
        }
        if (draft.relatedTaskIds.isNotEmpty()) {
            ConfirmRow("関連予定", "${draft.relatedTaskIds.size}件")
        }
        if (draft.photoPath != null) {
            ConfirmRow("写真", "あり")
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        if (isActive) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(onClick = onConfirm, modifier = Modifier.weight(1f)) {
                    Text("登録する")
                }
                OutlinedButton(onClick = onModify, modifier = Modifier.weight(1f)) {
                    Text("修正する")
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(
                onClick = onCancel,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) { Text("キャンセル", color = MaterialTheme.colorScheme.error) }
        } else {
            Text("（処理済み）", color = Color.Gray, fontSize = 12.sp)
        }
    }
}

@Composable
fun ConfirmRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
    ) {
        Text(label, fontWeight = FontWeight.Medium, fontSize = 13.sp,
            modifier = Modifier.width(80.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 13.sp)
    }
}

@Composable
fun TaskRegisteredCard(title: String) {
    AiCardWrapper {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Check, contentDescription = null,
                tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("「${title}」を登録しました",
                fontWeight = FontWeight.Medium, color = Color(0xFF4CAF50))
        }
    }
}

@Composable
fun ChatTypingIndicator() {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text("考え中…", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                fontSize = 13.sp, color = Color.Gray)
        }
    }
}

private fun recurrenceDisplayLabel(pattern: String): String = when (pattern) {
    "DAILY" -> "毎日"; "WEEKLY" -> "毎週"; "BIWEEKLY" -> "隔週"
    "MONTHLY_DATE" -> "毎月（日付）"; "MONTHLY_WEEK" -> "毎月（曜日）"
    "YEARLY" -> "毎年"; else -> pattern
}
```

---

### 変更点まとめ

| 変更 | 内容 |
|---|---|
| **通知** | 全タスク自動で `notifyEnabled=true`, `notifyMinutesBefore=1440`（1日前） |
| **ロードマップ** | 削除。`roadmapEnabled=false` 固定。後から編集画面で追加 |
| **メモ（Step 3）** | AIが内容を省かず整形。AI未ロード時はそのまま保存 |
| **日付（Step 4）** | DatePicker ＋ テキスト入力（ルールベース→AI→失敗時はPicker誘導） |
| **時刻（Step 5）** | TimePicker ＋ テキスト入力（「7時半」「午後3時」等をルールベース→AIで解析） |
| **関連予定（Step 7）** | AIがタスク名から関連候補をリストアップ、初期チェック済みで表示 |
| **写真（Step 8）** | カメラ撮影＋ギャラリー選択を `ActivityResultContracts` で実装 |

### AI使用箇所（4箇所のみ）

| 箇所 | AI処理 | フォールバック |
|---|---|---|
| メモ整形 | `generateResponse` で整形 | そのまま保存 |
| 日付テキスト | ルールベース失敗時にAI解析 | DatePicker誘導 |
| 時刻テキスト | ルールベース失敗時にAI解析 | TimePicker誘導 |
| 関連予定候補 | タスク一覧からAIが候補抽出 | 手動選択 |

すべてのAI処理にフォールバックがあるため、AIエンジン未ロードでもウィザード全体は正常に動作します。