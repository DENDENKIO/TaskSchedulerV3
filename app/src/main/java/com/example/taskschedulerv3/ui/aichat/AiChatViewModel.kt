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
    private val db = AppDatabase.getInstance(application)
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
            tagDao.getAll().collect { _allTags.value = it }
        }
        viewModelScope.launch {
            taskDao.getAll().collect { _allTasks.value = it.filter { t -> !t.isDeleted && !t.isCompleted } }
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
                taskDao.getByDate(dateMatch).first()
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
                RecurrencePattern.valueOf(d.recurrencePattern) else null,
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
        val taskId = taskDao.insert(task).toInt()
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
            val registeredTask = taskDao.getById(taskId)
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
