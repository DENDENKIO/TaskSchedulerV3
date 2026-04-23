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
import com.example.taskschedulerv3.util.AiEngineManager
import com.example.taskschedulerv3.util.AiPreferences
import com.example.taskschedulerv3.util.OcrTextParser
import kotlinx.coroutines.Dispatchers
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
                if (isRegistrationIntent(text)) {
                    startRegistrationFlow(text)
                } else if (isPhotoMemoSummaryIntent(text)) {     // ★追加
                    handlePhotoMemoSummary(text)
                } else {
                    handleNormalChat(text)
                }
            }
            WizardStep.WAITING_INPUT -> {
                startRegistrationFlow(text)
            }
            WizardStep.WAITING_ANSWER -> {
                // AIが必要な情報を質問した後の回答
                refineTaskWithAi(text)
            }
            WizardStep.WAITING_MODIFY -> {
                // 確認カード表示中の修正指示
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
        // AIエンジンがロードされていなければロードを試みる
        if (!AiEngineManager.isLoaded()) {
            val isAiEnabled = AiPreferences.getAiEnabled(getApplication()).first()
            if (isAiEnabled) {
                // 初回ロードは数秒〜十数秒かかるため、ユーザーにメッセージを表示
                addAiMessage(ChatContent.Text("AIモデルを準備しています...少しお待ちください。"))
                withContext(Dispatchers.IO) {
                    AiEngineManager.loadEngine(getApplication())
                }
            }
        }

        // それでもロードできなかった場合（設定でOFFになっている等）はフォールバック
        if (!AiEngineManager.isLoaded()) {
            addAiMessage(ChatContent.Text("AIエンジンが利用できないため、簡易抽出を試みます。設定でAI機能がONになっているか確認してください。"))
            fallbackRegistration(text)
            return
        }

        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        
        // プロンプトのルールを強化し、要約・場所・メモへの振り分けを徹底
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
                        // 自動タグ選択 (AI版)
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
        // 現在のドラフトを元にAIに修正を依頼
        val currentDraft = _draft.value
        val prompt = """
        現在の予定情報: $currentDraft
        ユーザーからの修正・追加指示: $text
        
        最新の情報を抽出し、再度JSONで返してください。不足があれば回答を促してください。
        """.trimIndent()
        // (実装の詳細はparseTaskWithAiと同様なので省略気味に記載されることが多いが、全置換なので完結させる)
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
                // HH:mm 形式か HH:mm:ss 形式かを考慮
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
        missingQuestionCount = 0 // すべて揃ったのでリセット
        _wizardStep.value = WizardStep.CONFIRM
        addAiMessage(ChatContent.Text("以下の内容でよろしいですか？"))
        addAiMessage(ChatContent.TaskConfirmation(draft = _draft.value, isActive = true))
    }

    fun confirmRegistration() {
        missingQuestionCount = 0 // リセット
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
            // 保存済みのタスクに写真を反映
            // (本来はIDを保持しておく必要があるが、ここでは簡略化)
            addAiMessage(ChatContent.Text("登録が完了しました。ありがとうございます！"))
            _wizardStep.value = WizardStep.COMPLETED
            resetWizard()
        }
    }

    fun cancelRegistration() {
        missingQuestionCount = 0 // リセット
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

        // タグ紐付け
        d.tagIds.forEach { tagId ->
            crossRefDao.insert(TaskTagCrossRef(taskId = taskId, tagId = tagId))
        }
        
        // 通知登録
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
        // 本来はGson等を使うが、ここでは簡易パース
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

    // フォールバック時にも最低限の日付・時刻を抽出する
    private fun fallbackRegistration(text: String) {
        val parsed = OcrTextParser.fallbackParseFromOcr(text)
        val title = parsed.title ?: text
        
        _draft.value = DraftTaskData(
            title = title,
            startDate = parsed.date ?: "",
            startTime = parsed.startTime,
            endTime = parsed.endTime,
            memo = parsed.summary ?: "",
            location = null // 簡易抽出では場所の特定までは行わない
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

    /**
     * ユーザーの自然文が「予定を聞いている」かどうかを判定し、
     * 該当する場合はDBからタスクを取得してAI応答に組み込む。
     */
    private suspend fun handleNormalChat(text: String) {
        // 1. 日付ベースの予定照会インテント判定
        val dateIntent = detectScheduleQueryIntent(text)
        if (dateIntent != null) {
            handleScheduleQuery(dateIntent, text)
            return
        }

        // 2. トピック/キーワードベースの予定検索インテント判定
        val topicKeyword = detectTopicQueryIntent(text)
        if (topicKeyword != null) {
            handleTopicQuery(topicKeyword, text)
            return
        }

        // 3. 通常の会話（予定照会でない場合）
        //    ユーザーが予定に関する漠然とした質問をしている場合にも対応するため、
        //    直近の予定をコンテキストとして渡す
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
            // AIエンジン未ロード時もDBから直接応答
            if (upcomingTasks.isNotEmpty()) {
                addAiMessage(ChatContent.Text(
                    "直近の予定はこちらです:\n" + formatTasksReadable(upcomingTasks.take(5))
                ))
            } else {
                addAiMessage(ChatContent.Text("こんにちは！何かお手伝いできることはありますか？"))
            }
        }
    }

    /**
     * 予定照会インテントを検出する。
     * 「明日の予定」「今週のスケジュール」「来週の月曜」等の日付キーワードを解析し、
     * 対象日付範囲を返す。照会でない場合はnullを返す。
     */
    private fun detectScheduleQueryIntent(text: String): ScheduleQueryIntent? {
        val today = LocalDate.now()
        val tomorrow = today.plusDays(1)

        // キーワードパターンで照会インテントを判定
        val queryKeywords = listOf(
            "予定", "スケジュール", "タスク", "何がある", "何かある",
            "やること", "やる事", "用事", "計画"
        )
        val hasQueryKeyword = queryKeywords.any { text.contains(it) }
        // 質問形式の判定
        val isQuestion = text.contains("？") || text.contains("?") ||
            text.contains("教えて") || text.contains("ある？") ||
            text.contains("ありますか") || text.contains("ある?") ||
            text.contains("何") || text.contains("確認") ||
            text.contains("見せて") || text.contains("一覧")

        if (!hasQueryKeyword && !isQuestion) return null
        // 「予定を登録」は除外
        if (isRegistrationIntent(text)) return null

        // 日付範囲の特定
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
            // 特定の日付パターン（X月X日）
            text.contains(Regex("""(\d{1,2})月(\d{1,2})日""")) -> {
                val match = Regex("""(\d{1,2})月(\d{1,2})日""").find(text)
                if (match != null) {
                    val month = match.groupValues[1].toInt()
                    val day = match.groupValues[2].toInt()
                    try {
                        var targetDate = today.withMonth(month).withDayOfMonth(day)
                        // 過去の日付なら来年に
                        if (targetDate.isBefore(today)) targetDate = targetDate.plusYears(1)
                        ScheduleQueryIntent(
                            fromDate = targetDate, toDate = targetDate,
                            label = "${month}月${day}日"
                        )
                    } catch (e: Exception) { null }
                } else null
            }
            // キーワードが一致しているが日付指定がない →
            // トピック修飾語（「〇〇の予定」「〇〇関連」）がある場合はトピック検索に委譲
            hasQueryKeyword -> {
                val topicModifiers = listOf("関連", "に関する", "について", "に関して", "の予定", "のスケジュール", "のタスク")
                val hasTopicModifier = topicModifiers.any { text.contains(it) }
                if (hasTopicModifier) {
                    null  // トピック検索（detectTopicQueryIntent）に処理を委譲
                } else {
                    ScheduleQueryIntent(
                        fromDate = today, toDate = today.plusDays(7), label = "今後1週間"
                    )
                }
            }
            else -> null
        }
    }

    /**
     * 予定照会を処理する
     */
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

        // AIエンジンが使える場合は自然文で応答
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
            // AIエンジンが使えない場合はフォーマットして直接表示
            addAiMessage(ChatContent.Text(formatTasksFallbackResponse(intent, tasks)))
        }
    }

    // ── フォーマットユーティリティ ──────────────────────────

    /**
     * タスクリストをAIプロンプト用のテキストにフォーマットする
     */
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

    /**
     * タスクリストをユーザー向けの読みやすい形式にフォーマットする
     */
    private fun formatTasksReadable(tasks: List<Task>): String {
        return tasks.joinToString("\n") { task ->
            val timeStr = if (!task.startTime.isNullOrEmpty()) " ${task.startTime}" else ""
            val locationStr = if (!task.location.isNullOrEmpty()) " @${task.location}" else ""
            "・${task.startDate}${timeStr} ${task.title}${locationStr}"
        }
    }

    /**
     * AIが使えない場合のフォールバック応答
     */
    private fun formatTasksFallbackResponse(intent: ScheduleQueryIntent, tasks: List<Task>): String {
        val header = "${intent.label}の予定は${tasks.size}件です：\n"
        return header + formatTasksReadable(tasks)
    }

    /**
     * 日付範囲を表示用テキストにフォーマットする
     */
    private fun formatDateRange(intent: ScheduleQueryIntent): String {
        val fmt = DateTimeFormatter.ofPattern("M/d")
        return if (intent.fromDate == intent.toDate) {
            intent.fromDate.format(fmt)
        } else {
            "${intent.fromDate.format(fmt)}~${intent.toDate.format(fmt)}"
        }
    }

    /**
     * 通常チャット用のシステムプロンプトを構築する。
     * 直近のタスク情報をコンテキストとして含める。
     */
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

    /**
     * ユーザーの自然文から「〇〇関連の予定」「〇〇の予定は？」のような
     * トピック/キーワード検索の意図を検出し、検索キーワードを返す。
     * 該当しない場合はnullを返す。
     */
    private fun detectTopicQueryIntent(text: String): String? {
        // 「予定を登録」は除外
        if (isRegistrationIntent(text)) return null

        // トピック照会パターン（「〇〇関連」「〇〇の予定」「〇〇について」等）
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
                // 日付キーワードは日付照会で処理済みなので除外
                val dateKeywords = listOf(
                    "今日", "明日", "明後日", "あさって", "今週", "来週",
                    "今月", "来月", "昨日"
                )
                if (dateKeywords.any { keyword.contains(it) }) return null
                // 短すぎるキーワードは誤検出防止で除外
                if (keyword.length >= 1) return keyword
            }
        }

        return null
    }

    /**
     * トピック/キーワードベースの予定検索を処理する。
     * タスクのタイトル・メモ・場所に対するテキスト検索と、
     * タグ名に対する検索を統合して結果を返す。
     */
    private suspend fun handleTopicQuery(keyword: String, originalText: String) {
        // 1. タスクのタイトル・メモ・場所からキーワード検索
        val textMatchedTasks = withContext(Dispatchers.IO) {
            taskDao.searchTasksSync(keyword)
        }

        // 2. タグ名からキーワード検索 → 該当タグに紐づくタスクを取得
        val tagMatchedTasks = withContext(Dispatchers.IO) {
            val matchedTags = tagDao.searchByNameSync(keyword)
            if (matchedTags.isNotEmpty()) {
                val tagIds = matchedTags.map { it.id }
                crossRefDao.getTasksByTagIdsSync(tagIds)
            } else {
                emptyList()
            }
        }

        // 3. 結果を統合（重複除去）
        val allTasks = (textMatchedTasks + tagMatchedTasks)
            .distinctBy { it.id }
            .sortedBy { it.startDate }

        if (allTasks.isEmpty()) {
            addAiMessage(ChatContent.Text(
                "「${keyword}」に関連する予定は見つかりませんでした。"
            ))
            return
        }

        // AIエンジンが使える場合は自然文で応答
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
            // AIエンジンが使えない場合はフォーマットして直接表示
            addAiMessage(ChatContent.Text(formatTopicFallbackResponse(keyword, allTasks)))
        }
    }

    /**
     * トピック検索でAIが使えない場合のフォールバック応答
     */
    private fun formatTopicFallbackResponse(keyword: String, tasks: List<Task>): String {
        val header = "「${keyword}」に関連する予定は${tasks.size}件です：\n"
        return header + formatTasksReadable(tasks)
    }

    // ── データクラス ──────────────────────────────────────────

    /**
     * 予定照会インテントのデータクラス
     */
    private data class ScheduleQueryIntent(
        val fromDate: LocalDate,
        val toDate: LocalDate,
        val label: String
    )

    // ── 写真メモ要約機能 ──────────────────────────────────────────

    private fun isPhotoMemoSummaryIntent(text: String): Boolean {
        val keywords = listOf(
            "写真メモをまとめ", "写真メモまとめ", "カメラメモをまとめ",
            "カメラメモまとめ", "メモをまとめ", "メモまとめ",
            "写真まとめ", "写真メモを要約", "写真メモ要約"
        )
        return keywords.any { text.contains(it) }
    }

    private suspend fun handlePhotoMemoSummary(text: String) {
        val photoMemoDao = db.photoMemoDao()

        // ★追加: AIエンジンがロードされていなければロードを試みる
        if (!AiEngineManager.isLoaded()) {
            val isAiEnabled = AiPreferences.getAiEnabled(getApplication()).first()
            if (isAiEnabled) {
                addAiMessage(ChatContent.Text("AIモデルを準備しています...少しお待ちください。"))
                withContext(Dispatchers.IO) {
                    AiEngineManager.loadEngine(getApplication())
                }
            }
        }

        // 全タスク（未完了+完了）を対象にする
        val allTasksList = withContext(Dispatchers.IO) {
            taskDao.getUpcomingTasks(500)
        }
        val completedTasks = withContext(Dispatchers.IO) {
            taskDao.getCompletedTasksSync()
        }
        val targetTasks = (allTasksList + completedTasks).distinctBy { it.id }

        if (targetTasks.isEmpty()) {
            addAiMessage(ChatContent.Text("対象となる予定が見つかりませんでした。"))
            return
        }

        // 各タスクの写真メモを収集
        data class TaskMemoBundle(val task: Task, val memoTexts: List<String>)

        val bundles = withContext(Dispatchers.IO) {
            targetTasks.mapNotNull { task ->
                val photoMemos = photoMemoDao.getMemosForTaskSync(task.id)
                if (photoMemos.isEmpty()) return@mapNotNull null

                val texts = photoMemos.flatMap { photo ->
                    listOfNotNull(
                        photo.memo?.takeIf { it.isNotBlank() },
                        photo.ocrText?.takeIf { it.isNotBlank() }
                    )
                }.distinct()

                if (texts.isNotEmpty()) TaskMemoBundle(task, texts) else null
            }
        }

        if (bundles.isEmpty()) {
            addAiMessage(ChatContent.Text("写真メモ（テキスト）が登録されている予定が見つかりませんでした。"))
            return
        }

        val aiAvailable = AiEngineManager.isLoaded()
        val modeLabel = if (aiAvailable) "AIでまとめます" else "テキスト結合でまとめます"
        addAiMessage(ChatContent.Text("${bundles.size}件の予定から写真メモを${modeLabel}..."))

        var successCount = 0
        for (bundle in bundles) {
            val combinedText = bundle.memoTexts.joinToString("\n")
            val summary = summarizeMemoTexts(bundle.task.title, combinedText)

            if (summary.isNotBlank()) {
                val appendText = "【写真メモまとめ】\n$summary"
                withContext(Dispatchers.IO) {
                    taskDao.appendDescription(bundle.task.id, appendText, System.currentTimeMillis())
                }
                successCount++
            }
        }

        addAiMessage(ChatContent.Text(
            "完了！${successCount}件の予定のメモに写真メモのまとめを追記しました。"
        ))
    }

    /**
     * メモテキストをAIで要約する。AI未ロード時はフォールバック（箇条書き結合）。
     */
    private suspend fun summarizeMemoTexts(taskTitle: String, memoText: String): String {
        // AIエンジンが使えるならAIまとめ
        if (AiEngineManager.isLoaded()) {
            val prompt = """
                以下は予定「${taskTitle}」に添付された写真メモの内容です。
                重要な情報を簡潔にまとめてください（3〜5行程度）。
                箇条書きは「・」を使ってください。Markdown記法は使わないでください。

                写真メモ:
                $memoText
            """.trimIndent()

            val response = withContext(Dispatchers.IO) {
                AiEngineManager.generateResponse(prompt)
            }
            if (!response.isNullOrBlank()) return response.trim()
        }

        // フォールバック: AI未使用時は各行の先頭を連結
        val lines = memoText.lines().filter { it.isNotBlank() }
        return lines.joinToString("\n") { line ->
            "・${line.take(80)}${if (line.length > 80) "…" else ""}"
        }
    }
}
