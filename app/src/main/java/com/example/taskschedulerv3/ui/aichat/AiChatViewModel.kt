package com.example.taskschedulerv3.ui.aichat

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskschedulerv3.data.db.AppDatabase
import com.example.taskschedulerv3.data.model.RecurrencePattern
import com.example.taskschedulerv3.data.model.ScheduleType
import com.example.taskschedulerv3.data.model.Tag
import com.example.taskschedulerv3.data.model.Task
import com.example.taskschedulerv3.data.model.TaskRelation
import com.example.taskschedulerv3.data.model.TaskTagCrossRef
import com.example.taskschedulerv3.data.repository.TaskRepository
import com.example.taskschedulerv3.notification.AlarmScheduler
import com.example.taskschedulerv3.util.AiEngineManager
import com.example.taskschedulerv3.util.AiModelManager
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
import java.util.Locale

class AiChatViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "AiChatVM"
    }

    private val db = AppDatabase.getInstance(application)
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
            tagDao.getAll().collect { _allTags.value = it }
        }
        viewModelScope.launch {
            taskDao.getAll().collect {
                _allTasks.value = it.filter { t -> !t.isDeleted && !t.isCompleted }
            }
        }
        // AIエンジンのロード
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                if (AiModelManager.checkModelExists(context) &&
                    !AiEngineManager.isLoaded()) {
                    Log.d(TAG, "Loading AI engine for chat...")
                    AiEngineManager.loadEngine(context)
                    if (AiEngineManager.isLoaded()) {
                        Log.d(TAG, "AI engine loaded successfully")
                    } else {
                        Log.w(TAG, "AI engine failed to load: ${AiEngineManager.getInitError()}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "AI engine load error", e)
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
                        (if (parsed.second.isNotEmpty()) " 〜 ${parsed.second}" else "") +
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
            val s = String.format(Locale.getDefault(), "%02d:%02d", it.groupValues[1].toInt(), it.groupValues[2].toInt())
            val e = String.format(Locale.getDefault(), "%02d:%02d", it.groupValues[3].toInt(), it.groupValues[4].toInt())
            return Pair(s, e)
        }

        // 「HH:mm」
        Regex("""(\d{1,2}):(\d{2})""").find(text)?.let {
            return Pair(
                String.format(Locale.getDefault(), "%02d:%02d", it.groupValues[1].toInt(), it.groupValues[2].toInt()),
                ""
            )
        }

        // 「7時半」「7時30分」「14時」
        Regex("""(\d{1,2})\s*時\s*半""").find(text)?.let {
            return Pair(String.format(Locale.getDefault(), "%02d:30", it.groupValues[1].toInt()), "")
        }
        Regex("""(\d{1,2})\s*時\s*(\d{1,2})\s*分""").find(text)?.let {
            return Pair(
                String.format(Locale.getDefault(), "%02d:%02d", it.groupValues[1].toInt(), it.groupValues[2].toInt()),
                ""
            )
        }
        Regex("""(\d{1,2})\s*時""").find(text)?.let {
            return Pair(String.format(Locale.getDefault(), "%02d:00", it.groupValues[1].toInt()), "")
        }

        // 「午前9時」「午後3時半」
        Regex("""午前\s*(\d{1,2})\s*時\s*半""").find(text)?.let {
            return Pair(String.format(Locale.getDefault(), "%02d:30", it.groupValues[1].toInt()), "")
        }
        Regex("""午後\s*(\d{1,2})\s*時\s*半""").find(text)?.let {
            val h = it.groupValues[1].toInt().let { v -> if (v < 12) v + 12 else v }
            return Pair(String.format(Locale.getDefault(), "%02d:30", h), "")
        }
        Regex("""午前\s*(\d{1,2})\s*時""").find(text)?.let {
            return Pair(String.format(Locale.getDefault(), "%02d:00", it.groupValues[1].toInt()), "")
        }
        Regex("""午後\s*(\d{1,2})\s*時""").find(text)?.let {
            val h = it.groupValues[1].toInt().let { v -> if (v < 12) v + 12 else v }
            return Pair(String.format(Locale.getDefault(), "%02d:00", h), "")
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
                taskDao.getByDate(dateMatch).first()
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

        // recurrencePattern の安全な変換
        val recPattern = if (d.scheduleType == ScheduleType.RECURRING) {
            try {
                RecurrencePattern.valueOf(d.recurrencePattern)
            } catch (_: Exception) {
                RecurrencePattern.NONE
            }
        } else {
            null
        }

        // 写真パスがある場合は description に追記（Task に photoPath フィールドがないため）
        val memoWithPhoto = if (d.photoPath != null) {
            val base = d.memo.ifEmpty { "" }
            if (base.isNotEmpty()) "$base\n\n📷 ${d.photoPath}" else "📷 ${d.photoPath}"
        } else {
            d.memo.ifEmpty { null }
        }

        val task = Task(
            title = d.title.ifEmpty {
                now.format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")) + " 登録"
            },
            description = memoWithPhoto,
            startDate = d.startDate.ifEmpty {
                LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            },
            endDate = d.endDate.ifEmpty { null },
            startTime = d.startTime.ifEmpty { null },
            endTime = d.endTime.ifEmpty { null },
            scheduleType = if (d.isIndefinite) ScheduleType.NORMAL else d.scheduleType,
            recurrencePattern = recPattern,
            recurrenceDays = d.recurrenceDays.ifEmpty { null },
            recurrenceEndDate = d.recurrenceEndDate.ifEmpty { null },
            priority = 1,
            notifyEnabled = true,
            notifyMinutesBefore = 1440,
            isIndefinite = d.isIndefinite,
            roadmapEnabled = false,
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

        // 通知スケジュール
        val registeredTask = taskDao.getById(taskId)
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
