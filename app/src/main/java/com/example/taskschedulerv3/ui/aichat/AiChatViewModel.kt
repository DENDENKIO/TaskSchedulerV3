package com.example.taskschedulerv3.ui.aichat

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskschedulerv3.data.db.AppDatabase
import com.example.taskschedulerv3.data.model.ScheduleType
import com.example.taskschedulerv3.data.model.Tag
import com.example.taskschedulerv3.data.model.Task
import com.example.taskschedulerv3.data.model.TaskTagCrossRef
import com.example.taskschedulerv3.notification.AlarmScheduler
import com.example.taskschedulerv3.notification.NotificationHelper
import com.example.taskschedulerv3.util.AiEngineManager
import com.example.taskschedulerv3.util.AiPreferences
import com.example.taskschedulerv3.util.OcrTextParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.DayOfWeek
import java.time.Duration
import java.time.temporal.TemporalAdjusters

class AiChatViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "AiChatVM"
    }

    private val db = AppDatabase.getInstance(application)
    private val taskDao = db.taskDao()
    private val crossRefDao = db.taskTagCrossRefDao()
    private val tagDao = db.tagDao()

    // ★追加: 画面を閉じてもキャンセルされないスコープ
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var missingQuestionCount = 0

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

    init {
        addAiMessage(ChatContent.Text(
            "こんにちは！AIアシスタントです。\n\n" +
            "【使える機能】\n" +
            "・予定の確認：「明日の予定は？」「今週の予定を教えて」\n" +
            "・予定の検索：「仕事関連の予定」「歯医者の予定は？」\n" +
            "・予定の登録：「予定を登録」と入力\n" +
            "・写真メモまとめ：「写真メモをまとめる」と入力すると、写真メモの内容をAIでまとめて予定のメモに追記します"
        ))
        viewModelScope.launch {
            tagDao.getAll().collect { _allTags.value = it }
        }
    }

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

    private suspend fun processInput(text: String) {
        val step = _wizardStep.value

        when (step) {
            WizardStep.IDLE -> {
                // ★写真メモまとめを最優先で判定（他のインテントに横取りされないように）
                if (isPhotoMemoSummaryIntent(text)) {
                    handlePhotoMemoSummary(text)
                } else if (isRegistrationIntent(text)) {
                    startRegistrationFlow(text)
                } else {
                    handleNormalChat(text)
                }
            }
            WizardStep.WAITING_INPUT -> {
                startRegistrationFlow(text)
            }
            WizardStep.WAITING_ANSWER -> {
                refineTaskWithAi(text)
            }
            WizardStep.WAITING_MODIFY -> {
                refineTaskWithAi(text)
            }
            WizardStep.CONFIRM -> {
                if (text.contains("OK") || text.contains("登録") || text.contains("はい")) {
                    confirmRegistration()
                } else if (text.contains("キャンセル") || text.contains("やめる")) {
                    cancelRegistration()
                } else {
                    refineTaskWithAi(text)
                }
            }
            else -> {
                if (isCancelIntent(text)) cancelRegistration()
                else handleNormalChat(text)
            }
        }
    }

    private fun startRegistrationFlow(text: String) {
        val cleanText = text.replace(Regex("予定を登録|登録して|タスクを登録|新しい予定"), "").trim()
        if (cleanText.isEmpty()) {
            _wizardStep.value = WizardStep.WAITING_INPUT
            addAiMessage(ChatContent.Text("どのような予定を登録しますか？例：『明日10時に新宿で歯医者』"))
            return
        }

        viewModelScope.launch {
            parseTaskWithAi(cleanText)
        }
    }

    private suspend fun parseTaskWithAi(text: String) {
        if (!AiEngineManager.isLoaded()) {
            val isAiEnabled = AiPreferences.getAiEnabled(getApplication()).first()
            if (isAiEnabled) {
                addAiMessage(ChatContent.Text("AIモデルを準備しています...少しお待ちください。"))
                withContext(Dispatchers.IO) {
                    AiEngineManager.loadEngine(getApplication())
                }
            }
        }

        if (!AiEngineManager.isLoaded()) {
            addAiMessage(ChatContent.Text("AIエンジンが利用できないため、簡易抽出を試みます。設定でAI機能がONになっているか確認してください。"))
            fallbackRegistration(text)
            return
        }

        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        
        val prompt = """
        ユーザーの自然文から予定情報を抽出し、JSON形式で返してください。
        今日の日付: $today
        
        フォーマット:
        {
          "title": "タスク名",
          "startDate": "yyyy-MM-dd",
          "startTime": "HH:mm",
          "endTime": "HH:mm",
          "memo": "内容",
          "location": "場所",
          "isPeriod": boolean,
          "isIndefinite": boolean,
          "isRecurrence": boolean,
          "missingInfo": "不足している重要な情報（日付やタイトルなど）があれば質問文"
        }
        
        入力テキスト: $text
        
        ルール:
        - titleは予定の内容を端的に表す短いフレーズに要約する（例:「ミーティング」「歯医者」）
        - 事務所、会議室、新宿などの場所を示す単語は location に入れる
        - ユーザーの入力した詳細な内容や文脈はわかりやすく memo に入れる
        - 該当する情報がない場合は空文字""にする
        - 日付や時刻のフォーマットは必ず上記形式に従う
        - 出力はJSONのみ。説明文やMarkdown装飾は不要
        """.trimIndent()

        val response = withContext(Dispatchers.IO) {
            AiEngineManager.generateResponse(prompt)
        }

        if (response != null) {
            val json = extractJson(response)
            if (json != null) {
                try {
                    val parsed = parseJsonToDraft(json)
                    _draft.value = parsed
                    
                    if (parsed.title.isEmpty()) {
                        handleMissingInfo("予定の名前（タイトル）は何にしますか？")
                    } else if (parsed.startDate.isEmpty() && !parsed.isIndefinite) {
                        handleMissingInfo("いつの予定ですか？（日付を教えてください）")
                    } else {
                        val suggested = suggestTagsWithAi(parsed.title + " " + parsed.memo, _allTags.value)
                        _draft.value = _draft.value.copy(tagIds = suggested)
                        showConfirmation()
                    }
                } catch (e: Exception) {
                    fallbackRegistration(text)
                }
            } else {
                fallbackRegistration(text)
            }
        } else {
            fallbackRegistration(text)
        }
    }

    private suspend fun refineTaskWithAi(text: String) {
        val currentDraft = _draft.value
        parseTaskWithAi("現在: $currentDraft 指示: $text")
    }

    private fun handleMissingInfo(prompt: String) {
        missingQuestionCount++
        if (missingQuestionCount > 3) {
            addAiMessage(ChatContent.Text("必要な項目が揃いませんでしたが、現在の内容で確認します。"))
            showConfirmation()
        } else {
            _wizardStep.value = WizardStep.WAITING_ANSWER
            addAiMessage(ChatContent.Text(prompt))
        }
    }

    private suspend fun suggestTagsWithAi(content: String, tags: List<Tag>): List<Int> {
        if (!AiEngineManager.isLoaded() || tags.isEmpty()) return emptyList()
        
        val tagListStr = tags.joinToString("\n") { "ID: ${it.id}, Name: ${it.name}" }
        val prompt = """
            以下のタスク内容に最も適したタグをリストから選び、IDをJSON配列の形式（例: [1, 2]）で出力してください。
            該当するものがない場合は空の配列 [] を返してください。
            
            タスク内容: $content
            
            タグリスト:
            $tagListStr
        """.trimIndent()

        return try {
            val response = withContext(Dispatchers.IO) {
                AiEngineManager.generateResponse(prompt)
            }
            val match = Regex("""\[(\s*\d+\s*,?)*\]""").find(response ?: "")
            match?.value?.let { json ->
                Regex("""\d+""").findAll(json).map { it.value.toInt() }.toList()
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun calculateNotifyMinutes(startDate: String?, startTime: String?): Int? {
        if (startDate.isNullOrEmpty()) return null
        return try {
            val date = LocalDate.parse(startDate)
            val time = if (startTime.isNullOrEmpty()) LocalTime.of(9, 0) else {
                val parts = startTime.split(":")
                LocalTime.of(parts[0].toInt(), parts[1].toInt())
            }
            val target = LocalDateTime.of(date, time)
            val now = LocalDateTime.now()
            
            if (target.isBefore(now)) return null
            
            val diffMinutes = Duration.between(now, target).toMinutes()
            when {
                diffMinutes >= 1440 -> 1440
                diffMinutes >= 60 -> 60
                else -> 0
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun showConfirmation() {
        missingQuestionCount = 0
        _wizardStep.value = WizardStep.CONFIRM
        addAiMessage(ChatContent.Text("以下の内容でよろしいですか？"))
        addAiMessage(ChatContent.TaskConfirmation(draft = _draft.value, isActive = true))
    }

    fun confirmRegistration() {
        missingQuestionCount = 0
        viewModelScope.launch {
            _isTyping.value = true
            try {
                val taskId = registerTask(_draft.value)
                _wizardStep.value = WizardStep.PHOTO_SELECT
                addAiMessage(ChatContent.Text("登録しました！写真を追加しますか？（スキップ可）"))
                addAiMessage(ChatContent.PhotoPickerRequest("ギャラリーから選択できます", allowSkip = true))
            } catch (e: Exception) {
                addAiMessage(ChatContent.Text("登録エラー: ${e.message}"))
            } finally {
                _isTyping.value = false
            }
        }
    }

    fun onPhotoSelected(path: String?) {
        viewModelScope.launch {
            _draft.value = _draft.value.copy(photoPath = path)
            addAiMessage(ChatContent.Text("登録が完了しました。ありがとうございます！"))
            _wizardStep.value = WizardStep.COMPLETED
            resetWizard()
        }
    }

    fun cancelRegistration() {
        missingQuestionCount = 0
        resetWizard()
        addAiMessage(ChatContent.Text("キャンセルしました。"))
    }

    private fun resetWizard() {
        _draft.value = DraftTaskData()
        _wizardStep.value = WizardStep.IDLE
    }

    private suspend fun registerTask(d: DraftTaskData): Int = withContext(Dispatchers.IO) {
        val task = Task(
            title = d.title,
            description = d.memo,
            startDate = d.startDate.ifEmpty { LocalDate.now().toString() },
            endDate = d.endDate,
            startTime = d.startTime,
            endTime = d.endTime,
            location = d.location,
            scheduleType = d.scheduleType,
            isIndefinite = d.isIndefinite,
            notifyEnabled = true,
            notifyMinutesBefore = calculateNotifyMinutes(d.startDate, d.startTime) ?: 0,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        val taskId = taskDao.insert(task).toInt()

        d.tagIds.forEach { tagId ->
            crossRefDao.insert(TaskTagCrossRef(taskId = taskId, tagId = tagId))
        }
        
        val registered = taskDao.getById(taskId)
        if (registered != null) {
            AlarmScheduler.scheduleForTask(getApplication(), registered)
        }
        taskId
    }

    private fun extractJson(text: String): String? {
        val regex = Regex("""\{.*\}""", RegexOption.DOT_MATCHES_ALL)
        return regex.find(text)?.value
    }

    private fun parseJsonToDraft(json: String): DraftTaskData {
        val title = Regex("\"title\"\\s*:\\s*\"([^\"]*)\"").find(json)?.groupValues?.get(1) ?: ""
        val startDate = Regex("\"startDate\"\\s*:\\s*\"([^\"]*)\"").find(json)?.groupValues?.get(1) ?: ""
        val startTime = Regex("\"startTime\"\\s*:\\s*\"([^\"]*)\"").find(json)?.groupValues?.get(1) ?: ""
        val memo = Regex("\"memo\"\\s*:\\s*\"([^\"]*)\"").find(json)?.groupValues?.get(1) ?: ""
        val location = Regex("\"location\"\\s*:\\s*\"([^\"]*)\"").find(json)?.groupValues?.get(1) ?: ""
        
        return DraftTaskData(
            title = title,
            startDate = startDate,
            startTime = startTime.ifEmpty { null },
            memo = memo,
            location = location.ifEmpty { null }
        )
    }

    private fun fallbackRegistration(text: String) {
        val parsed = OcrTextParser.fallbackParseFromOcr(text)
        val title = parsed.title ?: text
        
        _draft.value = DraftTaskData(
            title = title,
            startDate = parsed.date ?: "",
            startTime = parsed.startTime,
            endTime = parsed.endTime,
            memo = parsed.summary ?: "",
            location = null
        )
        
        addAiMessage(ChatContent.Text("自動抽出で登録準備をしました。詳細を教えていただくか、このまま確認にお進みください。"))
        showConfirmation()
    }

    private fun addAiMessage(content: ChatContent) {
        _messages.value = _messages.value + ChatMessage(content, false)
    }

    private fun addUserMessage(text: String) {
        _messages.value = _messages.value + ChatMessage(ChatContent.Text(text), true)
    }

    private fun isRegistrationIntent(text: String): Boolean {
        return text.contains("予定を登録")
    }

    private fun isCancelIntent(text: String): Boolean {
        return text.contains("キャンセル") || text.contains("やめる")
    }

    // ── 予定照会機能 ──────────────────────────────────────────

    private suspend fun handleNormalChat(text: String) {
        val dateIntent = detectScheduleQueryIntent(text)
        if (dateIntent != null) {
            handleScheduleQuery(dateIntent, text)
            return
        }

        val topicKeyword = detectTopicQueryIntent(text)
        if (topicKeyword != null) {
            handleTopicQuery(topicKeyword, text)
            return
        }

        val upcomingTasks = withContext(Dispatchers.IO) {
            taskDao.getUpcomingTasks(20)
        }
        val taskContext = if (upcomingTasks.isNotEmpty()) {
            formatTasksForAi(upcomingTasks)
        } else ""

        if (AiEngineManager.isLoaded()) {
            val systemPrompt = buildChatSystemPrompt(taskContext)
            val resp = withContext(Dispatchers.IO) {
                AiEngineManager.generateChatResponse(text, systemPrompt)
            }
            addAiMessage(ChatContent.Text(resp ?: "すみません、よくわかりませんでした。"))
        } else {
            if (upcomingTasks.isNotEmpty()) {
                addAiMessage(ChatContent.Text(
                    "直近の予定はこちらです:\n" + formatTasksReadable(upcomingTasks.take(5))
                ))
            } else {
                addAiMessage(ChatContent.Text("こんにちは！何かお手伝いできることはありますか？"))
            }
        }
    }

    private fun detectScheduleQueryIntent(text: String): ScheduleQueryIntent? {
        val today = LocalDate.now()
        val tomorrow = today.plusDays(1)

        val queryKeywords = listOf(
            "予定", "スケジュール", "タスク", "何がある", "何かある",
            "やること", "やる事", "用事", "計画"
        )
        val hasQueryKeyword = queryKeywords.any { text.contains(it) }
        val isQuestion = text.contains("？") || text.contains("?") ||
            text.contains("教えて") || text.contains("ある？") ||
            text.contains("ありますか") || text.contains("ある?") ||
            text.contains("何") || text.contains("確認") ||
            text.contains("見せて") || text.contains("一覧")

        if (!hasQueryKeyword && !isQuestion) return null
        if (isRegistrationIntent(text)) return null

        return when {
            text.contains("今日") -> ScheduleQueryIntent(
                fromDate = today, toDate = today, label = "今日"
            )
            text.contains("明日") -> ScheduleQueryIntent(
                fromDate = tomorrow, toDate = tomorrow, label = "明日"
            )
            text.contains("明後日") || text.contains("あさって") -> ScheduleQueryIntent(
                fromDate = today.plusDays(2), toDate = today.plusDays(2), label = "明後日"
            )
            text.contains("今週") -> ScheduleQueryIntent(
                fromDate = today,
                toDate = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)),
                label = "今週"
            )
            text.contains("来週") -> {
                val nextMon = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                ScheduleQueryIntent(
                    fromDate = nextMon,
                    toDate = nextMon.plusDays(6),
                    label = "来週"
                )
            }
            text.contains("今月") -> ScheduleQueryIntent(
                fromDate = today,
                toDate = today.withDayOfMonth(today.lengthOfMonth()),
                label = "今月"
            )
            text.contains("来月") -> {
                val nextMonth = today.plusMonths(1)
                ScheduleQueryIntent(
                    fromDate = nextMonth.withDayOfMonth(1),
                    toDate = nextMonth.withDayOfMonth(nextMonth.lengthOfMonth()),
                    label = "来月"
                )
            }
            text.contains(Regex("""(\d{1,2})月(\d{1,2})日""")) -> {
                val match = Regex("""(\d{1,2})月(\d{1,2})日""").find(text)
                if (match != null) {
                    val month = match.groupValues[1].toInt()
                    val day = match.groupValues[2].toInt()
                    try {
                        var targetDate = today.withMonth(month).withDayOfMonth(day)
                        if (targetDate.isBefore(today)) targetDate = targetDate.plusYears(1)
                        ScheduleQueryIntent(
                            fromDate = targetDate, toDate = targetDate,
                            label = "${month}月${day}日"
                        )
                    } catch (e: Exception) { null }
                } else null
            }
            hasQueryKeyword -> {
                val topicModifiers = listOf("関連", "に関する", "について", "に関して", "の予定", "のスケジュール", "のタスク")
                val hasTopicModifier = topicModifiers.any { text.contains(it) }
                if (hasTopicModifier) {
                    null
                } else {
                    ScheduleQueryIntent(
                        fromDate = today, toDate = today.plusDays(7), label = "今後1週間"
                    )
                }
            }
            else -> null
        }
    }

    private suspend fun handleScheduleQuery(intent: ScheduleQueryIntent, originalText: String) {
        val tasks = withContext(Dispatchers.IO) {
            taskDao.getTasksBetweenDates(
                intent.fromDate.toString(),
                intent.toDate.toString()
            )
        }

        if (tasks.isEmpty()) {
            addAiMessage(ChatContent.Text(
                "${intent.label}（${formatDateRange(intent)}）の予定は登録されていません。"
            ))
            return
        }

        if (AiEngineManager.isLoaded()) {
            val taskListStr = formatTasksForAi(tasks)
            val systemPrompt = """
                あなたはタスク管理アプリ「TaskScheduler」のAIアシスタントです。
                ユーザーの登録済み予定データを参照して質問に答えてください。
                
                【重要ルール】
                - 以下の予定データは実際にユーザーが登録した予定です。必ず参照して回答してください
                - 予定がある場合は具体的にタイトル・日付・時刻・場所を含めて回答してください
                - 予定の件数を正確に伝えてください
                - 回答は簡潔で親しみやすい日本語にしてください
                - Markdown記法は使わず、箇条書きは「・」を使ってください
                
                【登録済み予定データ】
                $taskListStr
                
                今日の日付: ${LocalDate.now()}
            """.trimIndent()

            val resp = withContext(Dispatchers.IO) {
                AiEngineManager.generateChatResponse(originalText, systemPrompt)
            }
            addAiMessage(ChatContent.Text(resp ?: formatTasksFallbackResponse(intent, tasks)))
        } else {
            addAiMessage(ChatContent.Text(formatTasksFallbackResponse(intent, tasks)))
        }
    }

    // ── フォーマットユーティリティ ──────────────────────────

    private fun formatTasksForAi(tasks: List<Task>): String {
        return tasks.mapIndexed { index, task ->
            val timeStr = buildString {
                if (!task.startTime.isNullOrEmpty()) append(task.startTime)
                if (!task.endTime.isNullOrEmpty()) append("~${task.endTime}")
            }
            val locationStr = if (!task.location.isNullOrEmpty()) " [場所: ${task.location}]" else ""
            val memoStr = if (!task.description.isNullOrEmpty()) " (メモ: ${task.description})" else ""
            val completedStr = if (task.isCompleted) " [完了]" else ""

            "${index + 1}. ${task.startDate} ${timeStr} - ${task.title}${locationStr}${memoStr}${completedStr}"
        }.joinToString("\n")
    }

    private fun formatTasksReadable(tasks: List<Task>): String {
        return tasks.joinToString("\n") { task ->
            val timeStr = if (!task.startTime.isNullOrEmpty()) " ${task.startTime}" else ""
            val locationStr = if (!task.location.isNullOrEmpty()) " @${task.location}" else ""
            "・${task.startDate}${timeStr} ${task.title}${locationStr}"
        }
    }

    private fun formatTasksFallbackResponse(intent: ScheduleQueryIntent, tasks: List<Task>): String {
        val header = "${intent.label}の予定は${tasks.size}件です：\n"
        return header + formatTasksReadable(tasks)
    }

    private fun formatDateRange(intent: ScheduleQueryIntent): String {
        val fmt = DateTimeFormatter.ofPattern("M/d")
        return if (intent.fromDate == intent.toDate) {
            intent.fromDate.format(fmt)
        } else {
            "${intent.fromDate.format(fmt)}~${intent.toDate.format(fmt)}"
        }
    }

    private fun buildChatSystemPrompt(taskContext: String): String {
        val basePrompt = """
            あなたはタスク管理アプリ「TaskScheduler」のAIアシスタントです。
            ユーザーの予定管理を手伝ってください。
            
            【重要ルール】
            - ユーザーが予定について質問した場合は、以下の登録済み予定を参照して正確に回答してください
            - 「予定を登録」と言われたら予定登録の手続きに進んでください
            - 回答は簡潔で親しみやすい日本語にしてください
            - Markdown記法は使わず、箇条書きは「・」を使ってください
            
            今日の日付: ${LocalDate.now()}
        """.trimIndent()

        return if (taskContext.isNotEmpty()) {
            "$basePrompt\n\n【登録済み予定データ】\n$taskContext"
        } else {
            "$basePrompt\n\n現在登録されている予定はありません。"
        }
    }

    // ── トピック/キーワード検索機能 ──────────────────────────────

    private fun detectTopicQueryIntent(text: String): String? {
        if (isRegistrationIntent(text)) return null

        val topicPatterns = listOf(
            Regex("(.+?)関連の?(予定|スケジュール|タスク|用事)"),
            Regex("(.+?)(?:の|に関する)(予定|スケジュール|タスク|用事)"),
            Regex("(.+?)(?:について|に関して).*(?:予定|スケジュール|タスク|教えて|ある)"),
            Regex("(.+?)(?:の|って).*(?:ある|ありますか|教えて|見せて|確認)"),
            Regex("(.+?)(?:関連|関係).*(?:教えて|ある|確認|見せて)"),
        )

        for (pattern in topicPatterns) {
            val match = pattern.find(text)
            if (match != null) {
                val keyword = match.groupValues[1].trim()
                val dateKeywords = listOf(
                    "今日", "明日", "明後日", "あさって", "今週", "来週",
                    "今月", "来月", "昨日"
                )
                if (dateKeywords.any { keyword.contains(it) }) return null
                if (keyword.length >= 1) return keyword
            }
        }

        return null
    }

    private suspend fun handleTopicQuery(keyword: String, originalText: String) {
        val textMatchedTasks = withContext(Dispatchers.IO) {
            taskDao.searchTasksSync(keyword)
        }

        val tagMatchedTasks = withContext(Dispatchers.IO) {
            val matchedTags = tagDao.searchByNameSync(keyword)
            if (matchedTags.isNotEmpty()) {
                val tagIds = matchedTags.map { it.id }
                crossRefDao.getTasksByTagIdsSync(tagIds)
            } else {
                emptyList()
            }
        }

        val allTasks = (textMatchedTasks + tagMatchedTasks)
            .distinctBy { it.id }
            .sortedBy { it.startDate }

        if (allTasks.isEmpty()) {
            addAiMessage(ChatContent.Text(
                "「${keyword}」に関連する予定は見つかりませんでした。"
            ))
            return
        }

        if (AiEngineManager.isLoaded()) {
            val taskListStr = formatTasksForAi(allTasks)
            val systemPrompt = """
                あなたはタスク管理アプリ「TaskScheduler」のAIアシスタントです。
                ユーザーが「${keyword}」に関連する予定について質問しています。
                以下の検索結果を参照して質問に答えてください。
                
                【重要ルール】
                - 以下の予定データはユーザーが登録した予定のうち「${keyword}」に関連するものです
                - 予定がある場合は具体的にタイトル・日付・時刻・場所を含めて回答してください
                - 該当件数を伝えてください
                - 回答は簡潔で親しみやすい日本語にしてください
                - Markdown記法は使わず、箇条書きは「・」を使ってください
                
                【「${keyword}」関連の予定データ】
                $taskListStr
                
                今日の日付: ${LocalDate.now()}
            """.trimIndent()

            val resp = withContext(Dispatchers.IO) {
                AiEngineManager.generateChatResponse(originalText, systemPrompt)
            }
            addAiMessage(ChatContent.Text(
                resp ?: formatTopicFallbackResponse(keyword, allTasks)
            ))
        } else {
            addAiMessage(ChatContent.Text(formatTopicFallbackResponse(keyword, allTasks)))
        }
    }

    private fun formatTopicFallbackResponse(keyword: String, tasks: List<Task>): String {
        val header = "「${keyword}」に関連する予定は${tasks.size}件です：\n"
        return header + formatTasksReadable(tasks)
    }

    // ── データクラス ──────────────────────────────────────────

    private data class ScheduleQueryIntent(
        val fromDate: LocalDate,
        val toDate: LocalDate,
        val label: String
    )

    // ── 写真メモまとめ機能 ──────────────────────────────────────────

    private fun isPhotoMemoSummaryIntent(text: String): Boolean {
        val keywords = listOf(
            "写真メモをまとめ", "写真メモまとめ", "カメラメモをまとめ",
            "カメラメモまとめ", "メモをまとめ", "メモまとめ",
            "写真まとめ", "写真メモを要約", "写真メモ要約"
        )
        return keywords.any { text.contains(it) }
    }

    /**
     * 写真メモまとめ処理。
     * - 未完了タスクのみ対象（完了済みは除外）
     * - ハッシュで差分管理（同じ内容ならスキップ、変更があれば再まとめ）
     * - appScope で実行し、画面を閉じても処理継続
     * - 完了後に端末通知を送信
     */
    private suspend fun handlePhotoMemoSummary(text: String) {
        val photoMemoDao = db.photoMemoDao()

        // AIエンジンのロード
        if (!AiEngineManager.isLoaded()) {
            val isAiEnabled = AiPreferences.getAiEnabled(getApplication()).first()
            if (isAiEnabled) {
                addAiMessage(ChatContent.Text("AIモデルを準備しています...少しお待ちください。"))
                withContext(Dispatchers.IO) {
                    AiEngineManager.loadEngine(getApplication())
                }
            }
        }

        // ★ 未完了タスクのみを対象（完了済みは除外）
        val targetTasks = withContext(Dispatchers.IO) {
            taskDao.getUpcomingTasks(500)
        }.filter { !it.isCompleted }

        if (targetTasks.isEmpty()) {
            addAiMessage(ChatContent.Text("対象となる未完了の予定が見つかりませんでした。"))
            return
        }

        // 各タスクの写真メモを収集し、ハッシュを生成
        data class TaskMemoBundle(
            val task: Task,
            val combinedText: String,
            val contentHash: String
        )

        val bundles = withContext(Dispatchers.IO) {
            targetTasks.mapNotNull { task ->
                val photoMemos = photoMemoDao.getMemosForTaskSync(task.id)
                if (photoMemos.isEmpty()) return@mapNotNull null

                // 写真メモを番号付きで連結（memo と ocrText の両方を含む）
                val parts = photoMemos.mapIndexedNotNull { index, photo ->
                    val memoLines = listOfNotNull(
                        photo.memo?.takeIf { it.isNotBlank() },
                        photo.ocrText?.takeIf { it.isNotBlank() }
                    )
                    if (memoLines.isEmpty()) null
                    else "写真${index + 1}: ${memoLines.joinToString(" / ")}"
                }
                if (parts.isEmpty()) return@mapNotNull null

                val combined = parts.joinToString("\n")
                val hash = combined.hashCode().toUInt().toString(16)

                TaskMemoBundle(task, combined, hash)
            }
        }

        if (bundles.isEmpty()) {
            addAiMessage(ChatContent.Text(
                "写真メモ（テキスト）が登録されている未完了の予定が見つかりませんでした。"
            ))
            return
        }

        // ハッシュ比較で差分があるものだけを処理対象にする
        val newOrUpdated = bundles.filter { bundle ->
            val desc = bundle.task.description ?: ""
            !desc.contains("hash:${bundle.contentHash}")
        }

        if (newOrUpdated.isEmpty()) {
            addAiMessage(ChatContent.Text(
                "${bundles.size}件の予定すべて、写真メモのまとめは最新の状態です。変更はありません。"
            ))
            return
        }

        val skippedCount = bundles.size - newOrUpdated.size
        val aiAvailable = AiEngineManager.isLoaded()
        val modeLabel = if (aiAvailable) "AIでまとめます" else "テキスト結合でまとめます"

        val statusMsg = buildString {
            append("${newOrUpdated.size}件の予定から写真メモを${modeLabel}。")
            if (skippedCount > 0) {
                append("\n（${skippedCount}件は変更がないためスキップ）")
            }
            append("\n画面を閉じても処理は継続します。完了後に通知でお知らせします。")
        }
        addAiMessage(ChatContent.Text(statusMsg))

        // ★ appScope で起動 → 画面を閉じても処理が中断されない
        val context = getApplication<Application>()
        appScope.launch {
            try {
                var successCount = 0
                for (bundle in newOrUpdated) {
                    val summary = summarizeMemoTexts(bundle.task.title, bundle.combinedText)
                    if (summary.isBlank()) continue

                    val newBlock = "【写真メモまとめ hash:${bundle.contentHash}】\n$summary"

                    withContext(Dispatchers.IO) {
                        // 最新の description を取得
                        val currentDesc = taskDao.getById(bundle.task.id)?.description ?: ""

                        val updatedDesc = if (currentDesc.contains("【写真メモまとめ hash:")) {
                            // 古いまとめブロックを削除して新しいものに置換
                            val cleaned = currentDesc.replace(
                                Regex("【写真メモまとめ hash:[a-f0-9]+】[\\s\\S]*?(?=\\n\\n【|\\n\\n[^【]|$)"),
                                ""
                            ).trimEnd()
                            if (cleaned.isBlank()) newBlock
                            else "$cleaned\n\n$newBlock"
                        } else {
                            // まとめ未実施 → 追記
                            if (currentDesc.isBlank()) newBlock
                            else "$currentDesc\n\n$newBlock"
                        }

                        val task = taskDao.getById(bundle.task.id)
                        if (task != null) {
                            taskDao.update(task.copy(
                                description = updatedDesc,
                                updatedAt = System.currentTimeMillis()
                            ))
                        }
                    }
                    successCount++
                }

                // 処理完了 → チャットにメッセージ（画面が開いていれば表示される）
                withContext(Dispatchers.Main) {
                    addAiMessage(ChatContent.Text(
                        "写真メモまとめ完了！${successCount}件の予定のメモに追記しました。"
                    ))
                }

                // ★ 端末通知を送信（画面を閉じていても届く）
                NotificationHelper.showMemoSummaryComplete(context, successCount)

            } catch (e: Exception) {
                Log.e(TAG, "写真メモまとめ処理エラー", e)
                withContext(Dispatchers.Main) {
                    addAiMessage(ChatContent.Text(
                        "写真メモまとめ処理中にエラーが発生しました: ${e.message}"
                    ))
                }
                NotificationHelper.showMemoSummaryComplete(context, -1)
            }
        }
    }

    /**
     * メモテキストをAIでまとめる。精度重視で全文をAIに渡す。
     * AI未ロード時はフォールバック（箇条書き整形）。
     */
    private suspend fun summarizeMemoTexts(taskTitle: String, memoText: String): String {
        if (AiEngineManager.isLoaded()) {
            val prompt = """
                あなたは優秀な日本語の要約アシスタントです。

                以下は予定「${taskTitle}」に添付された写真メモの内容です。
                複数の写真メモが含まれる場合があります。

                【指示】
                - すべての写真メモの内容を漏れなく読み取ってください
                - 重要な情報（日付、金額、人名、場所、数値、期限、条件など）は省略せず正確に残してください
                - 重複する情報は1つにまとめてください
                - 箇条書きは「・」を使い、関連する情報はグループ化してください
                - 写真ごとに内容が異なる場合は区別がつくようにまとめてください
                - Markdown記法は使わないでください

                【写真メモ内容】
                $memoText
            """.trimIndent()

            val response = withContext(Dispatchers.IO) {
                AiEngineManager.generateResponse(prompt)
            }
            if (!response.isNullOrBlank()) return response.trim()
        }

        // フォールバック: 各行を箇条書きに整形（文字数制限なし）
        return memoText.lines()
            .filter { it.isNotBlank() }
            .joinToString("\n") { line ->
                if (line.startsWith("写真")) line
                else "・$line"
            }
    }
}
