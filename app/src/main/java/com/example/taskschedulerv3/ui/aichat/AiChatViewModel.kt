package com.example.taskschedulerv3.ui.aichat

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskschedulerv3.data.db.AppDatabase
import com.example.taskschedulerv3.data.model.RecurrencePattern
import com.example.taskschedulerv3.data.model.RoadmapStep
import com.example.taskschedulerv3.data.model.ScheduleType
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
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val content: ChatContent = ChatContent.Text(text),
    val timestamp: Long = System.currentTimeMillis()
)

class AiChatViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.getInstance(app)
    private val taskRepo = TaskRepository(db.taskDao(), db.roadmapStepDao())
    private val tagDao = db.tagDao()
    private val crossRefDao = db.taskTagCrossRefDao()
    private val relationDao = db.taskRelationDao()
    private val roadmapStepDao = db.roadmapStepDao()

    private val _messages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                text = "こんにちは！予定について聞いたり、自由に質問してください。\n\n予定を登録したい場合は「予定を登録」や、直接「明日14時に会議」のように入力できます。",
                isUser = false
            )
        )
    )
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()

    /** 現在構築中のタスクドラフト */
    private val _pendingDraft = MutableStateFlow<DraftTaskData?>(null)
    val pendingDraft: StateFlow<DraftTaskData?> = _pendingDraft.asStateFlow()

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        addMessage(ChatMessage(text = text, isUser = true))

        viewModelScope.launch {
            _isTyping.value = true
            try {
                val response = processInput(text)
                addMessage(response)
            } catch (e: Exception) {
                Log.e(TAG, "sendMessage error", e)
                addMessage(ChatMessage(
                    text = "エラーが発生しました。もう一度お試しください。",
                    isUser = false
                ))
            } finally {
                _isTyping.value = false
            }
        }
    }

    /**
     * 確認カードから「登録する」が押された場合
     */
    fun confirmRegistration() {
        val draft = _pendingDraft.value ?: return
        viewModelScope.launch {
            _isTyping.value = true
            try {
                val taskId = registerTask(draft)
                _pendingDraft.value = null
                val msg = ChatMessage(
                    text = "「${draft.title}」を登録しました！",
                    isUser = false,
                    content = ChatContent.TaskRegistered(taskId, draft.title)
                )
                addMessage(msg)
            } catch (e: Exception) {
                Log.e(TAG, "Registration error", e)
                addMessage(ChatMessage(text = "登録に失敗しました: ${e.localizedMessage}", isUser = false))
            } finally {
                _isTyping.value = false
            }
        }
    }

    /**
     * 確認カードの各フィールドを更新する
     */
    fun updateDraft(updatedDraft: DraftTaskData) {
        _pendingDraft.value = updatedDraft
    }

    /**
     * ドラフトを破棄する
     */
    fun cancelDraft() {
        _pendingDraft.value = null
        addMessage(ChatMessage(text = "登録をキャンセルしました。", isUser = false))
    }

    // ── 入力処理の振り分け ──

    private suspend fun processInput(input: String): ChatMessage {
        val today = LocalDate.now()
        val todayStr = today.format(DateTimeFormatter.ISO_LOCAL_DATE)

        // 1. 構築中のドラフトがある場合 → 修正・補完として処理
        val currentDraft = _pendingDraft.value
        if (currentDraft != null) {
            return handleDraftModification(input, currentDraft, todayStr)
        }

        // 2. 登録意図の検出（ルールベース）
        val registerKeywords = listOf("登録", "追加", "作成", "予定を入れ", "スケジュールを入れ", "リマインダー")
        val hasRegisterIntent = registerKeywords.any { input.contains(it) }

        // 自然文にタスク情報が含まれているか（日付・時刻の存在チェック）
        val hasDateInfo = Regex("(明日|明後日|今日|来週|\\d{1,2}月\\d{1,2}日|\\d{4}[/\\-]\\d{1,2}[/\\-]\\d{1,2})").containsMatchIn(input)
        val hasTimeInfo = Regex("(\\d{1,2}時|\\d{1,2}:\\d{2})").containsMatchIn(input)

        if (hasRegisterIntent || (hasDateInfo && hasTimeInfo)) {
            return handleTaskCreation(input, todayStr)
        }

        // 3. 予定検索（ルールベース）
        val searchResult = tryRuleBasedScheduleSearch(input, today)
        if (searchResult != null) return ChatMessage(text = searchResult, isUser = false)

        // 4. LLM意図分類
        if (AiEngineManager.isLoaded() || tryLoadEngine()) {
            val intentResult = classifyIntentWithLlm(input, todayStr)
            if (intentResult != null) {
                when (intentResult.intent) {
                    "task_register" -> return handleTaskCreation(input, todayStr)
                    "schedule_search" -> {
                        val result = searchTasks(intentResult.targetDate, intentResult.keyword, intentResult.dateLabel)
                        if (result != null) return ChatMessage(text = result, isUser = false)
                    }
                    "general_chat" -> {
                        val chatResponse = generateFreeResponse(input, todayStr)
                        if (chatResponse != null) return ChatResponseText(chatResponse)
                    }
                }
            }

            // フォールスルー: 自由応答
            val chatResponse = generateFreeResponse(input, todayStr)
            if (chatResponse != null) return ChatMessage(text = chatResponse, isUser = false)
        }

        return ChatMessage(
            text = "AIモデルが読み込まれていません。設定画面でAI機能をONにしてください。\n\n予定の検索は「明日の予定」のように聞いていただけます。",
            isUser = false
        )
    }

    private fun ChatResponseText(text: String) = ChatMessage(text = text, isUser = false)

    // ── タスク登録フロー ──

    /**
     * ユーザーの自然文からタスク情報を一括抽出し、確認カードを返す。
     */
    private suspend fun handleTaskCreation(input: String, todayStr: String): ChatMessage {
        // LLMでタスク情報を一括抽出
        val draft = extractTaskFromText(input, todayStr)

        // 必須項目の不足チェック
        val missing = draft.nextMissingField()
        if (missing != null) {
            _pendingDraft.value = draft
            val question = when (missing) {
                "title" -> "タスク名を教えてください。"
                "date" -> "日付はいつですか？（例: 明日、5月1日、2026-05-01）"
                else -> "情報が不足しています。詳しく教えてください。"
            }
            return ChatMessage(text = question, isUser = false)
        }

        // 確認カードを表示
        _pendingDraft.value = draft
        val confirmText = buildConfirmationText(draft)
        return ChatMessage(
            text = confirmText,
            isUser = false,
            content = ChatContent.TaskConfirmation(draft)
        )
    }

    /**
     * 構築中ドラフトに対する修正や補完を処理する。
     */
    private suspend fun handleDraftModification(
        input: String,
        currentDraft: DraftTaskData,
        todayStr: String
    ): ChatMessage {
        // LLMに修正内容を解析させる
        val updatedDraft = applyModification(input, currentDraft, todayStr)
        _pendingDraft.value = updatedDraft

        val missing = updatedDraft.nextMissingField()
        if (missing != null) {
            val question = when (missing) {
                "title" -> "タスク名を教えてください。"
                "date" -> "日付はいつですか？"
                else -> "もう少し情報を教えてください。"
            }
            return ChatMessage(text = question, isUser = false)
        }

        val confirmText = buildConfirmationText(updatedDraft)
        return ChatMessage(
            text = confirmText,
            isUser = false,
            content = ChatContent.TaskConfirmation(updatedDraft)
        )
    }

    /**
     * LLMを使ってテキストからタスク情報を一括抽出する。
     */
    private suspend fun extractTaskFromText(input: String, todayStr: String): DraftTaskData {
        if (!AiEngineManager.isLoaded() && !tryLoadEngine()) {
            return parseTaskRuleBased(input, todayStr)
        }

        try {
            val allTags = tagDao.getAll().first()
            val tagListStr = if (allTags.isNotEmpty()) {
                allTags.joinToString(", ") { "${it.id}:${it.name}" }
            } else "なし"

            val prompt = """今日は $todayStr です。
ユーザーが予定を登録したいと言っています。以下のテキストからタスク情報を抽出し、JSONのみを出力してください。

【出力フォーマット（JSONのみ、他のテキスト不要）】
{"title":"タスク名","schedule_type":"NORMAL","start_date":"YYYY-MM-DD","end_date":"","start_time":"HH:mm","end_time":"","is_indefinite":false,"notify_enabled":true,"notify_minutes_before":60,"recurrence_pattern":"","recurrence_days":"","description":"","priority":1,"roadmap_enabled":false}

【schedule_typeの判定ルール】
- 通常の1回の予定 → "NORMAL"
- 開始日〜終了日がある期間予定 → "PERIOD"（end_dateも設定）
- 毎日/毎週/毎月など繰り返し → "RECURRING"（recurrence_patternも設定）
- 「いつか」「そのうち」など日付未定 → "NORMAL" + is_indefinite=true

【recurrence_patternの値】
DAILY, WEEKLY, BIWEEKLY, MONTHLY_DATE, YEARLY, WEEKLY_MULTI
WEEKLY_MULTIの場合はrecurrence_daysに曜日番号（月=1〜日=7）をカンマ区切りで設定

【priorityの値】0=高, 1=中, 2=低

【通知】
「通知あり」「30分前に通知」→ notify_enabled=true, notify_minutes_before=数値
「通知なし」「通知不要」→ notify_enabled=false
指定なし → notify_enabled=true, notify_minutes_before=60

【利用可能なタグ】
$tagListStr

タグが指定されている場合は "tag_ids":[数値の配列] を追加

【ユーザーの入力】
$input"""

            val response = AiEngineManager.generateResponse(prompt)
            if (!response.isNullOrBlank()) {
                val jsonStr = extractJsonFromText(response)
                if (jsonStr != null) {
                    return parseDraftFromJson(jsonStr)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "extractTaskFromText error", e)
        }

        return parseTaskRuleBased(input, todayStr)
    }

    /**
     * 修正テキストを既存ドラフトに適用する。
     */
    private suspend fun applyModification(
        input: String,
        current: DraftTaskData,
        todayStr: String
    ): DraftTaskData {
        if (!AiEngineManager.isLoaded()) return applyModificationRuleBased(input, current, todayStr)

        try {
            val currentJson = draftToJsonString(current)
            val prompt = """今日は $todayStr です。
現在の登録内容:
$currentJson

ユーザーが以下の修正を求めています。修正後のJSONのみを出力してください。フォーマットは同じです。

【ユーザーの修正】
$input"""

            val response = AiEngineManager.generateResponse(prompt)
            if (!response.isNullOrBlank()) {
                val jsonStr = extractJsonFromText(response)
                if (jsonStr != null) {
                    return parseDraftFromJson(jsonStr)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "applyModification error", e)
        }

        return applyModificationRuleBased(input, current, todayStr)
    }

    // ── ルールベースフォールバック ──

    private fun parseTaskRuleBased(input: String, todayStr: String): DraftTaskData {
        val today = LocalDate.parse(todayStr)
        var title = ""
        var startDate = ""
        var startTime: String? = null
        var isIndefinite = false
        var scheduleType = ScheduleType.NORMAL
        var notifyEnabled = true
        var notifyMinutes = 60

        // 日付検出
        when {
            input.contains("今日") -> startDate = todayStr
            input.contains("明後日") -> startDate = today.plusDays(2).toString()
            input.contains("明日") -> startDate = today.plusDays(1).toString()
            input.contains("いつか") || input.contains("そのうち") -> {
                isIndefinite = true
                startDate = todayStr
            }
        }

        // 日付パターン
        Regex("""(\d{1,2})月(\d{1,2})日""").find(input)?.let { m ->
            val mo = m.groupValues[1].toInt()
            val d = m.groupValues[2].toInt()
            startDate = LocalDate.of(today.year, mo, d).toString()
        }

        // 時刻検出
        Regex("""(\d{1,2})[時:](\d{2})?""").find(input)?.let { m ->
            val h = m.groupValues[1].toInt()
            val min = m.groupValues[2].toIntOrNull() ?: 0
            startTime = "%02d:%02d".format(h, min)
        }

        // 通知検出
        Regex("""(\d+)分前""").find(input)?.let { m ->
            notifyMinutes = m.groupValues[1].toInt()
        }
        if (input.contains("通知なし") || input.contains("通知不要")) {
            notifyEnabled = false
        }

        // 繰り返し検出
        if (input.contains("毎日")) {
            scheduleType = ScheduleType.RECURRING
        } else if (input.contains("毎週")) {
            scheduleType = ScheduleType.RECURRING
        } else if (input.contains("毎月")) {
            scheduleType = ScheduleType.RECURRING
        }

        // タイトル: 日付・時刻・通知の文言を除去した残りをタイトルとする
        title = input
            .replace(Regex("(今日|明日|明後日|来週|いつか|そのうち)の?"), "")
            .replace(Regex("\\d{1,2}月\\d{1,2}日の?"), "")
            .replace(Regex("\\d{1,2}[時:]\\d{0,2}分?(から|に|〜)?"), "")
            .replace(Regex("通知\\d*分前(に)?"), "")
            .replace(Regex("(通知あり|通知なし|通知不要)"), "")
            .replace(Regex("(予定を?|を?)(登録|追加|作成|入れ)(して|する|したい)?"), "")
            .replace(Regex("[、。を]"), "")
            .trim()

        return DraftTaskData(
            title = title,
            startDate = startDate,
            startTime = startTime,
            isIndefinite = isIndefinite,
            scheduleType = scheduleType,
            notifyEnabled = notifyEnabled,
            notifyMinutesBefore = notifyMinutes
        )
    }

    private fun applyModificationRuleBased(
        input: String,
        current: DraftTaskData,
        todayStr: String
    ): DraftTaskData {
        var draft = current
        val today = LocalDate.parse(todayStr)

        // 時刻変更
        Regex("""(\d{1,2})[時:](\d{2})?""").find(input)?.let { m ->
            val h = m.groupValues[1].toInt()
            val min = m.groupValues[2].toIntOrNull() ?: 0
            draft = draft.copy(startTime = "%02d:%02d".format(h, min))
        }

        // 日付変更
        when {
            input.contains("今日") -> draft = draft.copy(startDate = todayStr)
            input.contains("明後日") -> draft = draft.copy(startDate = today.plusDays(2).toString())
            input.contains("明日") -> draft = draft.copy(startDate = today.plusDays(1).toString())
        }
        Regex("""(\d{1,2})月(\d{1,2})日""").find(input)?.let { m ->
            val mo = m.groupValues[1].toInt()
            val d = m.groupValues[2].toInt()
            draft = draft.copy(startDate = LocalDate.of(today.year, mo, d).toString())
        }

        // タイトル変更（「タイトルをXXに」パターン）
        Regex("""(タスク名|タイトル|名前)を?「?(.+?)」?に""").find(input)?.let { m ->
            draft = draft.copy(title = m.groupValues[2].trim())
        }

        // 通知変更
        Regex("""(\d+)分前""").find(input)?.let { m ->
            draft = draft.copy(notifyMinutesBefore = m.groupValues[1].toInt(), notifyEnabled = true)
        }
        if (input.contains("通知なし") || input.contains("通知不要")) {
            draft = draft.copy(notifyEnabled = false)
        }

        return draft
    }

    // ── JSON解析 ──

    private fun parseDraftFromJson(jsonStr: String): DraftTaskData {
        return try {
            val json = JSONObject(jsonStr)
            DraftTaskData(
                title = json.optString("title", ""),
                description = json.optString("description", "").ifBlank { null },
                scheduleType = try {
                    ScheduleType.valueOf(json.optString("schedule_type", "NORMAL"))
                } catch (_: Exception) { ScheduleType.NORMAL },
                startDate = json.optString("start_date", ""),
                endDate = json.optString("end_date", "").ifBlank { null },
                startTime = json.optString("start_time", "").ifBlank { null },
                endTime = json.optString("end_time", "").ifBlank { null },
                isIndefinite = json.optBoolean("is_indefinite", false),
                notifyEnabled = json.optBoolean("notify_enabled", true),
                notifyMinutesBefore = json.optInt("notify_minutes_before", 60),
                recurrencePattern = try {
                    val p = json.optString("recurrence_pattern", "")
                    if (p.isNotBlank()) RecurrencePattern.valueOf(p) else null
                } catch (_: Exception) { null },
                recurrenceDays = json.optString("recurrence_days", "").ifBlank { null },
                priority = json.optInt("priority", 1),
                roadmapEnabled = json.optBoolean("roadmap_enabled", false),
                tagIds = try {
                    val arr = json.optJSONArray("tag_ids")
                    if (arr != null) (0 until arr.length()).map { arr.getInt(it) } else emptyList()
                } catch (_: Exception) { emptyList() }
            )
        } catch (e: Exception) {
            Log.e(TAG, "parseDraftFromJson error", e)
            DraftTaskData()
        }
    }

    private fun draftToJsonString(draft: DraftTaskData): String {
        return """{"title":"${draft.title}","schedule_type":"${draft.scheduleType}","start_date":"${draft.startDate}","end_date":"${draft.endDate ?: ""}","start_time":"${draft.startTime ?: ""}","end_time":"${draft.endTime ?: ""}","is_indefinite":${draft.isIndefinite},"notify_enabled":${draft.notifyEnabled},"notify_minutes_before":${draft.notifyMinutesBefore},"priority":${draft.priority},"roadmap_enabled":${draft.roadmapEnabled}}"""
    }

    // ── 確認テキスト生成 ──

    private suspend fun buildConfirmationText(draft: DraftTaskData): String {
        val sb = StringBuilder()
        sb.append("この内容で登録してよろしいですか？\n\n")

        val typeLabel = when {
            draft.isIndefinite -> "無期限"
            draft.scheduleType == ScheduleType.PERIOD -> "期間"
            draft.scheduleType == ScheduleType.RECURRING -> "繰り返し"
            else -> "通常"
        }
        sb.append("予定の種類: $typeLabel\n")
        sb.append("タスク名: ${draft.title}\n")

        if (!draft.isIndefinite) {
            sb.append("日付: ${draft.startDate}\n")
            if (draft.endDate != null) sb.append("終了日: ${draft.endDate}\n")
        }

        if (draft.startTime != null) sb.append("開始時刻: ${draft.startTime}\n")
        if (draft.endTime != null) sb.append("終了時刻: ${draft.endTime}\n")

        if (draft.description != null) sb.append("メモ: ${draft.description}\n")

        val notifyStr = if (draft.notifyEnabled) "${draft.notifyMinutesBefore}分前" else "なし"
        sb.append("通知: $notifyStr\n")

        val priorityLabel = when (draft.priority) { 0 -> "高"; 1 -> "中"; 2 -> "低"; else -> "中" }
        sb.append("優先度: $priorityLabel\n")

        if (draft.recurrencePattern != null) {
            sb.append("繰り返し: ${recurrenceLabel(draft.recurrencePattern, draft.recurrenceDays)}\n")
        }

        sb.append("ロードマップ: ${if (draft.roadmapEnabled) "あり" else "なし"}\n")

        // タグ名を表示
        if (draft.tagIds.isNotEmpty()) {
            val allTags = tagDao.getAll().first()
            val tagNames = allTags.filter { it.id in draft.tagIds }.map { it.name }
            if (tagNames.isNotEmpty()) {
                sb.append("タグ: ${tagNames.joinToString(", ")}\n")
            }
        }

        return sb.toString().trimEnd()
    }

    private fun recurrenceLabel(pattern: RecurrencePattern, days: String?): String {
        return when (pattern) {
            RecurrencePattern.DAILY -> "毎日"
            RecurrencePattern.WEEKLY -> "毎週"
            RecurrencePattern.BIWEEKLY -> "隔週"
            RecurrencePattern.MONTHLY_DATE -> "毎月(日付)"
            RecurrencePattern.YEARLY -> "毎年"
            RecurrencePattern.WEEKLY_MULTI -> {
                val dayNames = mapOf(1 to "月", 2 to "火", 3 to "水", 4 to "木", 5 to "金", 6 to "土", 7 to "日")
                val names = days?.split(",")?.mapNotNull { dayNames[it.trim().toIntOrNull()] } ?: emptyList()
                "毎週 ${names.joinToString("・")}"
            }
            RecurrencePattern.MONTHLY_DATES -> {
                "毎月 ${days ?: ""}日"
            }
            else -> pattern.name
        }
    }

    // ── DB登録 ──

    /**
     * ドラフトから実際のTaskをDBに保存し、タグ・関連予定・通知を設定する。
     * @return 登録されたタスクのID
     */
    private suspend fun registerTask(draft: DraftTaskData): Int = withContext(Dispatchers.IO) {
        val task = Task(
            title = draft.title,
            description = draft.description,
            startDate = if (draft.isIndefinite) LocalDate.now().toString() else draft.startDate,
            endDate = draft.endDate,
            startTime = draft.startTime,
            endTime = draft.endTime,
            scheduleType = draft.scheduleType,
            recurrencePattern = draft.recurrencePattern,
            recurrenceDays = draft.recurrenceDays,
            recurrenceEndDate = draft.recurrenceEndDate,
            priority = draft.priority,
            isIndefinite = draft.isIndefinite,
            notifyEnabled = draft.notifyEnabled,
            notifyMinutesBefore = draft.notifyMinutesBefore,
            roadmapEnabled = draft.roadmapEnabled
        )

        val taskId = taskRepo.insert(task).toInt()

        // タグの紐付け
        for (tagId in draft.tagIds) {
            crossRefDao.insert(TaskTagCrossRef(taskId = taskId, tagId = tagId))
        }

        // 関連予定の紐付け
        for (relatedId in draft.relatedTaskIds) {
            val id1 = minOf(taskId, relatedId)
            val id2 = maxOf(taskId, relatedId)
            if (relationDao.getRelation(id1, id2) == null) {
                relationDao.insert(TaskRelation(taskId1 = id1, taskId2 = id2))
            }
        }

        // ロードマップステップの登録
        if (draft.roadmapEnabled && draft.roadmapSteps.isNotEmpty()) {
            for (step in draft.roadmapSteps) {
                roadmapStepDao.insert(
                    RoadmapStep(
                        taskId = taskId,
                        title = step.title,
                        date = step.date,
                        sortOrder = step.sortOrder
                    )
                )
            }
        }

        // 通知スケジュール
        if (draft.notifyEnabled && !draft.isIndefinite) {
            val savedTask = taskRepo.getById(taskId)
            if (savedTask != null) {
                AlarmScheduler.scheduleForTask(getApplication(), savedTask)
            }
        }

        Log.d(TAG, "Task registered: id=$taskId, title=${draft.title}")
        taskId
    }

    private fun addMessage(message: ChatMessage) {
        _messages.value = _messages.value + message
    }

    // ── 既存の予定検索・自由会話機能（維持） ──

    private suspend fun tryRuleBasedScheduleSearch(query: String, today: LocalDate): String? {
        var targetDateStr = ""
        var dateLabel = ""

        when {
            query.contains("今日") && query.contains("予定") -> {
                targetDateStr = today.format(DateTimeFormatter.ISO_LOCAL_DATE); dateLabel = "今日"
            }
            query.contains("明後日") && query.contains("予定") -> {
                targetDateStr = today.plusDays(2).format(DateTimeFormatter.ISO_LOCAL_DATE); dateLabel = "明後日"
            }
            query.contains("明日") && query.contains("予定") -> {
                targetDateStr = today.plusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE); dateLabel = "明日"
            }
        }
        if (targetDateStr.isBlank()) return null

        var keyword = ""
        val keywordMatch = Regex("「(.+)」").find(query) ?: Regex("(.+)の予定").find(query)
        if (keywordMatch != null) {
            keyword = keywordMatch.groupValues[1]
                .replace("今日", "").replace("明日", "").replace("明後日", "")
                .replace("の", "").trim()
        }

        return searchTasks(targetDateStr, keyword, dateLabel)
    }

    private suspend fun classifyIntentWithLlm(query: String, currentDate: String): IntentResult? {
        try {
            val prompt = """今日は ${currentDate} です。

ユーザーの入力を分析し、以下のJSONのみを出力してください。

【分類ルール】
- 予定を登録・追加・作成したい → intent = "task_register"
- 予定を検索・確認したい → intent = "schedule_search"
- それ以外 → intent = "general_chat"

【出力フォーマット（JSONのみ）】
{"intent":"task_register","target_date":"","keyword":""}
{"intent":"schedule_search","target_date":"YYYY-MM-DD","keyword":"検索語"}
{"intent":"general_chat","target_date":"","keyword":""}

【ユーザーの入力】
$query"""

            val response = AiEngineManager.generateResponse(prompt) ?: return null
            val jsonStr = extractJsonFromText(response) ?: return null
            val json = JSONObject(jsonStr)

            return IntentResult(
                intent = json.optString("intent", "general_chat"),
                targetDate = json.optString("target_date", "").replace("/", "-"),
                keyword = json.optString("keyword", ""),
                dateLabel = ""
            )
        } catch (e: Exception) {
            Log.e(TAG, "classifyIntentWithLlm error", e)
            return null
        }
    }

    private suspend fun generateFreeResponse(query: String, currentDate: String): String? {
        try {
            val taskContext = buildTaskContext(currentDate)
            val systemPrompt = """あなたはTaskSchedulerV3アプリのAIアシスタントです。
現在の日付は ${currentDate} です。

あなたの役割:
- ユーザーの質問に日本語で丁寧に答える
- 予定やタスク管理に関するアドバイスを提供する
- 一般的な質問にも知識の範囲で回答する
- 回答は簡潔で分かりやすくする（200文字以内を目安）

${if (taskContext.isNotBlank()) "【ユーザーの直近の予定】\n$taskContext" else ""}

注意:
- Markdown装飾は使わない
- 予定の詳細を聞かれた場合は上記の予定情報を参照する
- 予定情報がない場合は「予定が登録されていません」と正直に答える
- 予定の登録を頼まれたら「『予定を登録』と入力するか、直接内容を教えてください」と案内する"""

            return AiEngineManager.generateChatResponse(query, systemPrompt)
        } catch (e: Exception) {
            Log.e(TAG, "generateFreeResponse error", e)
            return null
        }
    }

    private suspend fun buildTaskContext(currentDate: String): String {
        return try {
            val today = LocalDate.parse(currentDate)
            val allTasks = taskRepo.getAll().first()
            val upcoming = allTasks.filter { task ->
                !task.isCompleted && !task.isDeleted && task.startDate.isNotBlank() &&
                try {
                    val taskDate = LocalDate.parse(task.startDate)
                    !taskDate.isBefore(today) && !taskDate.isAfter(today.plusDays(7))
                } catch (_: Exception) { false }
            }.sortedWith(compareBy({ it.startDate }, { it.startTime ?: "23:59" }))

            if (upcoming.isEmpty()) return ""

            val sb = StringBuilder()
            upcoming.take(20).forEach { task ->
                val time = task.startTime ?: "終日"
                sb.append("${task.startDate} $time: ${task.title}\n")
            }
            sb.toString()
        } catch (e: Exception) {
            Log.e(TAG, "buildTaskContext error", e)
            ""
        }
    }

    private suspend fun searchTasks(targetDateStr: String, keyword: String, dateLabel: String): String? {
        if (targetDateStr.isBlank() && keyword.isBlank()) return null

        val allTasks = taskRepo.getAll().first()
        val matchedTasks = allTasks.filter { task ->
            val matchDate = targetDateStr.isBlank() || task.startDate == targetDateStr
            val matchKeyword = keyword.isBlank() || (
                task.title.contains(keyword, ignoreCase = true) ||
                task.description?.contains(keyword, ignoreCase = true) == true
            )
            !task.isCompleted && !task.isDeleted && matchDate && matchKeyword
        }.sortedBy { it.startTime ?: "23:59" }

        val dLabel = if (dateLabel.isNotBlank()) dateLabel
                     else if (targetDateStr.isNotBlank()) "${targetDateStr}の" else ""
        val kwLabel = if (keyword.isNotBlank()) "「${keyword}」に関する" else ""

        if (matchedTasks.isEmpty()) {
            return "${dLabel}${kwLabel}予定は見つかりませんでした。"
        }

        val sb = StringBuilder()
        sb.append("${dLabel}${kwLabel}予定は ${matchedTasks.size}件 あります。\n\n")
        matchedTasks.forEach { task ->
            val time = task.startTime ?: "終日"
            sb.append("・ $time : ${task.title}\n")
        }
        return sb.toString()
    }

    private suspend fun tryLoadEngine(): Boolean {
        return try {
            AiEngineManager.loadEngine(getApplication())
            AiEngineManager.isLoaded()
        } catch (e: Exception) {
            Log.e(TAG, "Engine load failed", e)
            false
        }
    }

    private fun extractJsonFromText(text: String): String? {
        val start = text.indexOf('{')
        if (start == -1) return null
        var depth = 0
        for (i in start until text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
        return null
    }

    private data class IntentResult(
        val intent: String,
        val targetDate: String,
        val keyword: String,
        val dateLabel: String
    )

    override fun onCleared() {
        super.onCleared()
    }

    companion object {
        private const val TAG = "AiChatVM"
    }
}
