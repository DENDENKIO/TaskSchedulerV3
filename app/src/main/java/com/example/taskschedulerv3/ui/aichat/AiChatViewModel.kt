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
import com.example.taskschedulerv3.util.PhotoFileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class AiChatViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "AiChatVM"
    }

    private val db = AppDatabase.getInstance(application)
    private val taskDao = db.taskDao()
    private val tagDao = db.tagDao()
    private val crossRefDao = db.taskTagCrossRefDao()
    private val relationDao = db.taskRelationDao()
    private val photoMemoDao = db.photoMemoDao()
    private val photoTagDao = db.photoTagCrossRefDao()
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
    // 日付選択
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
                    addAiMessage(ChatContent.Text("日付を認識できませんでした。カレンダーから選択してください。"))
                }
            } finally {
                _isTyping.value = false
            }
        }
    }

    // =========================================================
    // 時刻選択
    // =========================================================
    fun onTimeSelected(startTime: String, endTime: String = "") {
        val display = if (endTime.isNotEmpty()) "$startTime 〜 $endTime" else startTime
        addUserMessage(display)
        viewModelScope.launch {
            _draft.value = _draft.value.copy(startTime = startTime, endTime = endTime)
            moveToStep(WizardStep.SELECT_TAGS)
        }
    }

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
                    addAiMessage(ChatContent.Text("時刻を認識できませんでした。時計から選択してください。"))
                }
            } finally {
                _isTyping.value = false
            }
        }
    }

    // =========================================================
    // タグ・予定・写真
    // =========================================================
    fun onTagsSelected(tagIds: List<Int>) {
        val names = _allTags.value.filter { it.id in tagIds }.joinToString(", ") { it.name }
        addUserMessage(if (tagIds.isEmpty()) "スキップ" else "タグ: $names")
        viewModelScope.launch {
            _draft.value = _draft.value.copy(tagIds = tagIds)
            _isTyping.value = true
            try {
                suggestRelationsAndMove()
            } finally {
                _isTyping.value = false
            }
        }
    }

    fun onRelationsSelected(taskIds: List<Int>) {
        addUserMessage(if (taskIds.isEmpty()) "スキップ" else "関連予定: ${taskIds.size}件")
        viewModelScope.launch {
            _draft.value = _draft.value.copy(relatedTaskIds = taskIds)
            moveToStep(WizardStep.SELECT_PHOTOS)
        }
    }

    fun onPhotoSelected(path: String?) {
        addUserMessage(if (path == null) "スキップ" else "写真を追加しました")
        viewModelScope.launch {
            _draft.value = _draft.value.copy(photoPath = path)
            moveToStep(WizardStep.CONFIRM)
        }
    }

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
    // 登録・修正・キャンセル
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
                moveToStep(WizardStep.INPUT_LOCATION)
            }
            WizardStep.INPUT_LOCATION -> {
                _draft.value = _draft.value.copy(location = text)
                addAiMessage(ChatContent.Text("場所:「${text}」"))
                moveToStep(WizardStep.INPUT_MEMO)
            }
            WizardStep.INPUT_MEMO -> {
                val formatted = formatMemoWithAi(text)
                _draft.value = _draft.value.copy(memo = formatted)
                addAiMessage(ChatContent.Text("メモを整えました:\n$formatted"))
                moveToDateStep()
            }
            WizardStep.SELECT_DATE, WizardStep.SELECT_END_DATE -> onDateTextInput(text)
            WizardStep.SELECT_TIME -> onTimeTextInput(text)
            else -> {
                if (isRegistrationCancel(text)) {
                    cancelRegistration()
                } else {
                    addAiMessage(ChatContent.Text("登録の途中です。ボタンから選択するか、「キャンセル」で中断できます。"))
                }
            }
        }
    }

    private fun startWizard() {
        _draft.value = DraftTaskData()
        addAiMessage(ChatContent.Text("予定を登録しましょう！"))
        moveToStep(WizardStep.SELECT_TYPE)
    }

    private suspend fun handleWizardChoice(step: WizardStep, value: String) {
        addUserMessage(value)
        when (step) {
            WizardStep.SELECT_TYPE -> {
                when (value) {
                    "通常" -> _draft.value = _draft.value.copy(scheduleType = ScheduleType.NORMAL)
                    "期間" -> _draft.value = _draft.value.copy(scheduleType = ScheduleType.PERIOD)
                    "無期限" -> _draft.value = _draft.value.copy(scheduleType = ScheduleType.NORMAL, isIndefinite = true)
                    "繰り返し" -> _draft.value = _draft.value.copy(scheduleType = ScheduleType.RECURRING)
                }
                addAiMessage(ChatContent.Text("「${value}」を選択しました。"))
                moveToStep(WizardStep.INPUT_TITLE)
            }
            WizardStep.INPUT_LOCATION -> if (value == "スキップ") {
                addAiMessage(ChatContent.Text("場所をスキップしました。"))
                moveToStep(WizardStep.INPUT_MEMO)
            }
            WizardStep.INPUT_MEMO -> if (value == "スキップ") {
                addAiMessage(ChatContent.Text("メモをスキップしました。"))
                moveToDateStep()
            }
            WizardStep.SELECT_TIME -> if (value == "スキップ") {
                addAiMessage(ChatContent.Text("時刻をスキップしました。"))
                _draft.value = _draft.value.copy(startTime = "", endTime = "")
                moveToStep(WizardStep.SELECT_TAGS)
            }
            WizardStep.SELECT_TAGS -> if (value == "スキップ") onTagsSelected(emptyList())
            WizardStep.SELECT_RELATIONS -> if (value == "スキップ") onRelationsSelected(emptyList())
            WizardStep.SELECT_PHOTOS -> if (value == "スキップ") onPhotoSelected(null)
            WizardStep.CONFIRM -> when (value) {
                "登録する" -> confirmRegistration()
                "修正する" -> goBackToModify()
                "キャンセル" -> cancelRegistration()
            }
            else -> {}
        }
    }

    private fun moveToStep(step: WizardStep) {
        _wizardStep.value = step
        when (step) {
            WizardStep.SELECT_TYPE -> addAiMessage(ChatContent.ChoiceButtons(
                prompt = "予定の種類を選んでください",
                choices = listOf(Choice("通常", "通常"), Choice("期間", "期間"), Choice("無期限", "無期限"), Choice("繰り返し", "繰り返し"))
            ))
            WizardStep.INPUT_TITLE -> addAiMessage(ChatContent.TextInput(prompt = "タスク名を入力してください", hint = "例: 歯医者、チームミーティング"))
            WizardStep.INPUT_LOCATION -> addAiMessage(ChatContent.TextInput(prompt = "場所を入力してください", hint = "例: 会議室A、事務所", allowSkip = true))
            WizardStep.INPUT_MEMO -> addAiMessage(ChatContent.TextInputWithAi(prompt = "メモを入力してください", hint = "例: 資料を持参", allowSkip = true, aiDescription = "入力内容をAIがきれいに整えます"))
            WizardStep.SELECT_DATE -> addAiMessage(ChatContent.DatePickerRequest(prompt = if (_draft.value.scheduleType == ScheduleType.PERIOD) "開始日を選んでください" else "日付を選んでください"))
            WizardStep.SELECT_END_DATE -> addAiMessage(ChatContent.DatePickerRequest(prompt = "終了日を選んでください"))
            WizardStep.SELECT_TIME -> addAiMessage(ChatContent.TimePickerRequest(prompt = "時刻を設定してください"))
            WizardStep.SELECT_TAGS -> addAiMessage(ChatContent.TagPickerRequest(prompt = "タグを選択してください（複数可）"))
            WizardStep.SELECT_PHOTOS -> addAiMessage(ChatContent.PhotoPickerRequest(prompt = "写真を追加しますか？"))
            WizardStep.CONFIRM -> {
                addAiMessage(ChatContent.Text("以下の内容で登録してよろしいですか？"))
                addAiMessage(ChatContent.TaskConfirmation(draft = _draft.value))
            }
            WizardStep.SELECT_RELATIONS -> {} // suggestRelationsAndMove で処理
            WizardStep.INPUT_LOCATION -> {} // すでに上で定義済み
            else -> {}
        }
    }

    private fun moveToDateStep() {
        val d = _draft.value
        when {
            d.isIndefinite -> moveToStep(WizardStep.SELECT_TAGS)
            d.scheduleType == ScheduleType.RECURRING -> { /* 実装簡易化のため未サポート */ moveToStep(WizardStep.SELECT_DATE) }
            else -> moveToStep(WizardStep.SELECT_DATE)
        }
    }

    private suspend fun suggestRelationsAndMove() {
        val d = _draft.value
        val tasks = _allTasks.value

        var suggestedIds = emptyList<Int>()
        var aiReason = ""

        if (tasks.isNotEmpty() && d.title.isNotEmpty() && AiEngineManager.isLoaded()) {
            try {
                val taskList = tasks.take(30).joinToString("\n") { t -> "ID:${t.id} タイトル:${t.title} 日付:${t.startDate}" }
                val prompt = """以下のタスク一覧から「${d.title}」に関連しそうなタスクのIDを最大5件、JSON配列で返してください。
{"ids":[1,2,3],"reason":"理由"}
リスト:
$taskList"""
                val response = withContext(Dispatchers.IO) { AiEngineManager.generateResponse(prompt) }
                if (response != null) {
                    val jsonMatch = Regex("""\{[^}]*"ids"\s*:\s*\[([^\]]*)\][^}]*\}""").find(response)
                    if (jsonMatch != null) {
                        val idsStr = jsonMatch.groupValues[1]
                        suggestedIds = Regex("""\d+""").findAll(idsStr).map { it.value.toIntOrNull() }.filterNotNull().filter { id -> tasks.any { it.id == id } }.toList()
                        val reasonMatch = Regex(""""reason"\s*:\s*"([^"]*)"?""").find(jsonMatch.value)
                        aiReason = reasonMatch?.groupValues?.getOrNull(1) ?: ""
                    }
                }
            } catch (e: Exception) { Log.e(TAG, "suggest error", e) }
        }

        _wizardStep.value = WizardStep.SELECT_RELATIONS
        addAiMessage(ChatContent.RelationPickerRequest(
            prompt = if (suggestedIds.isNotEmpty()) "AIが関連しそうな予定を見つけました\n($aiReason)" else "関連する予定を選択してください",
            suggestedTaskIds = suggestedIds
        ))
    }

    private suspend fun formatMemoWithAi(rawMemo: String): String {
        if (!AiEngineManager.isLoaded()) return rawMemo
        return try {
            val prompt = "以下のメモを省略せず、読みやすく整えてください。内容は一切削除しないでください。\n元のメモ:\n$rawMemo\n整えたメモ:"
            withContext(Dispatchers.IO) { AiEngineManager.generateResponse(prompt) }?.trim() ?: rawMemo
        } catch (e: Exception) { rawMemo }
    }

    private suspend fun parseDateWithAi(text: String): String? {
        val ruleBased = parseDateRuleBased(text)
        if (ruleBased != null) return ruleBased
        if (!AiEngineManager.isLoaded()) return null
        return try {
            val today = LocalDate.now()
            val prompt = "今日は${today.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))}です。テキスト「$text」から日付を yyyy-MM-dd で返してください。"
            val response = withContext(Dispatchers.IO) { AiEngineManager.generateResponse(prompt) }
            response?.let { Regex("""\d{4}-\d{2}-\d{2}""").find(it)?.value }
        } catch (e: Exception) { null }
    }

    private fun parseDateRuleBased(text: String): String? {
        val today = LocalDate.now()
        val fmt = DateTimeFormatter.ISO_LOCAL_DATE
        if (text.contains("今日")) return today.format(fmt)
        if (text.contains("明後日")) return today.plusDays(2).format(fmt)
        if (text.contains("明日")) return today.plusDays(1).format(fmt)
        // 他の簡易パターン...
        return null
    }

    private suspend fun parseTimeWithAi(text: String): Pair<String, String>? {
        if (!AiEngineManager.isLoaded()) return null
        return try {
            val prompt = "テキスト「$text」から時刻を HH:mm-HH:mm 形式で返してください。"
            val response = withContext(Dispatchers.IO) { AiEngineManager.generateResponse(prompt) }
            response?.let {
                val range = Regex("""(\d{2}:\d{2})\s*[-〜~]\s*(\d{2}:\d{2})""").find(it)
                if (range != null) Pair(range.groupValues[1], range.groupValues[2])
                else Regex("""\d{2}:\d{2}""").find(it)?.let { m -> Pair(m.value, "") }
            }
        } catch (e: Exception) { null }
    }

    private suspend fun handleNonWizardInput(text: String) {
        if (AiEngineManager.isLoaded()) {
            val response = withContext(Dispatchers.IO) { AiEngineManager.generateResponse(text) }
            addAiMessage(ChatContent.Text(response ?: "応答を生成できませんでした。"))
        } else {
            addAiMessage(ChatContent.Text("「予定を登録」と入力すると登録を開始します。"))
        }
    }

    private suspend fun registerTask(d: DraftTaskData): Int = withContext(Dispatchers.IO) {
        val task = Task(
            title = d.title.ifEmpty { "新規登録" },
            location = d.location.ifEmpty { null },
            description = d.memo.ifEmpty { null },
            startDate = d.startDate.ifEmpty { LocalDate.now().toString() },
            endDate = d.endDate.ifEmpty { null },
            startTime = d.startTime.ifEmpty { null },
            endTime = d.endTime.ifEmpty { null },
            scheduleType = if (d.isIndefinite) ScheduleType.NORMAL else d.scheduleType,
            recurrencePattern = null,
            notifyEnabled = true,
            notifyMinutesBefore = 1440,
            isIndefinite = d.isIndefinite,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        val taskId = taskDao.insert(task).toInt()
        d.tagIds.forEach { crossRefDao.insert(TaskTagCrossRef(taskId = taskId, tagId = it)) }
        d.relatedTaskIds.forEach { relationDao.insert(TaskRelation(taskId1 = minOf(taskId, it), taskId2 = maxOf(taskId, it))) }
        
        // 写真処理
        d.photoPath?.let { path ->
            val photoMemo = com.example.taskschedulerv3.data.model.PhotoMemo(
                taskId = taskId,
                date = d.startDate.ifEmpty { LocalDate.now().toString() },
                imagePath = path,
                title = d.title,
                memo = d.memo,
                createdAt = System.currentTimeMillis()
            )
            val photoId = photoMemoDao.insert(photoMemo).toInt()
            // 写真にも同じタグを紐付ける
            d.tagIds.forEach { tagId ->
                photoTagDao.insert(com.example.taskschedulerv3.data.model.PhotoTagCrossRef(photoId = photoId, tagId = tagId))
            }
        }

        val registered = taskDao.getById(taskId)
        if (registered != null) AlarmScheduler.scheduleForTask(getApplication(), registered)
        taskId
    }

    private fun addAiMessage(content: ChatContent) {
        _messages.value = _messages.value + ChatMessage(content = content, isUser = false)
    }

    private fun addUserMessage(text: String) {
        _messages.value = _messages.value + ChatMessage(content = ChatContent.Text(text), isUser = true)
    }

    private fun isRegistrationIntent(text: String): Boolean = 
        listOf("登録", "予定", "タスク").any { text.contains(it) }

    private fun isRegistrationCancel(text: String): Boolean = 
        listOf("キャンセル", "やめる").any { text.contains(it) }

    private fun recurrenceLabel(pattern: String): String = pattern // 簡易表示
}
