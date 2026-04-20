package com.example.taskschedulerv3.ui.aichat

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskschedulerv3.data.db.AppDatabase
import com.example.taskschedulerv3.data.repository.TaskRepository
import com.example.taskschedulerv3.util.AiEngineManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

class AiChatViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.getInstance(app)
    private val taskRepo = TaskRepository(db.taskDao(), db.roadmapStepDao())

    private val _messages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                text = "こんにちは！予定について聞いたり、何でも自由に質問してください。\n\n例：\n・「明日の予定は？」\n・「今週の会議を教えて」\n・「タスク管理のコツは？」\n・「集中力を上げる方法は？」",
                isUser = false
            )
        )
    )
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        addMessage(ChatMessage(text = text, isUser = true))

        viewModelScope.launch {
            _isTyping.value = true
            try {
                val responseText = processQuery(text)
                addMessage(ChatMessage(text = responseText, isUser = false))
            } catch (e: Exception) {
                Log.e(TAG, "sendMessage error", e)
                addMessage(ChatMessage(text = "エラーが発生しました。もう一度お試しください。", isUser = false))
            } finally {
                _isTyping.value = false
            }
        }
    }

    private fun addMessage(message: ChatMessage) {
        _messages.value = _messages.value + message
    }

    /**
     * ユーザーの質問を処理する。
     * 1. ルールベースで予定検索キーワードを検出
     * 2. ルールベースで判定できない場合、LLMで意図を分類
     * 3. 意図に応じて予定検索 or 自由会話応答を返す
     */
    private suspend fun processQuery(query: String): String {
        val today = LocalDate.now()
        val todayStr = today.format(DateTimeFormatter.ISO_LOCAL_DATE)

        // ── 1. ルールベース判定：明確な予定検索パターン ──
        val ruleResult = tryRuleBasedScheduleSearch(query, today)
        if (ruleResult != null) return ruleResult

        // ── 2. LLMベースの意図分類 ──
        if (AiEngineManager.isLoaded() || tryLoadEngine()) {
            val intentResult = classifyIntentWithLlm(query, todayStr)

            if (intentResult != null) {
                when (intentResult.intent) {
                    "schedule_search" -> {
                        // LLMが予定検索と判断した場合
                        val searchResult = searchTasks(
                            intentResult.targetDate,
                            intentResult.keyword,
                            intentResult.dateLabel
                        )
                        if (searchResult != null) return searchResult
                        // 検索結果なしの場合はフォールスルーして自由応答へ
                    }
                    "general_chat" -> {
                        // 自由会話と判断された場合 → LLM で直接応答
                        val chatResponse = generateFreeResponse(query, todayStr)
                        if (chatResponse != null) return chatResponse
                    }
                }
            }

            // 意図分類が失敗しても、予定関連の可能性があれば自由応答を試みる
            val chatResponse = generateFreeResponse(query, todayStr)
            if (chatResponse != null) return chatResponse
        }

        // ── 3. エンジン未ロード時のフォールバック ──
        return "AIモデルが読み込まれていません。設定画面でAI機能をONにしてください。\n\n" +
               "予定の検索は「明日の予定」「今日の予定」のように聞いていただけます。"
    }

    /**
     * ルールベースで明確な予定検索パターンを検出する。
     * 「今日」「明日」「明後日」などのキーワードと「予定」「スケジュール」の組み合わせ。
     */
    private suspend fun tryRuleBasedScheduleSearch(query: String, today: LocalDate): String? {
        var targetDateStr = ""
        var dateLabel = ""

        // 日付キーワード検出
        when {
            query.contains("今日") -> {
                targetDateStr = today.format(DateTimeFormatter.ISO_LOCAL_DATE)
                dateLabel = "今日"
            }
            query.contains("明後日") -> {
                targetDateStr = today.plusDays(2).format(DateTimeFormatter.ISO_LOCAL_DATE)
                dateLabel = "明後日"
            }
            query.contains("明日") -> {
                targetDateStr = today.plusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE)
                dateLabel = "明日"
            }
        }

        // 予定関連キーワードが含まれている場合のみルールベースで処理
        val scheduleKeywords = listOf("予定", "スケジュール", "タスク", "やること", "何がある")
        val isScheduleQuery = scheduleKeywords.any { query.contains(it) }

        if (targetDateStr.isBlank() && !isScheduleQuery) return null
        if (targetDateStr.isBlank()) return null  // 日付が特定できない場合はLLMに委ねる

        // キーキーワード抽出
        var keyword = ""
        val keywordMatch = Regex("「(.+)」").find(query) ?: Regex("(.+)の予定").find(query)
        if (keywordMatch != null) {
            val rawKeyword = keywordMatch.groupValues[1]
            keyword = rawKeyword
                .replace("今日", "").replace("明日", "").replace("明後日", "")
                .replace("の", "").trim()
        }

        return searchTasks(targetDateStr, keyword, dateLabel)
    }

    /**
     * LLMでユーザーの意図を分類する。
     * 返り値: IntentResult（intent, targetDate, keyword, dateLabel）
     */
    private suspend fun classifyIntentWithLlm(query: String, currentDate: String): IntentResult? {
        try {
            val prompt = """今日は ${currentDate} です。

ユーザーの入力を分析し、以下のJSONのみを出力してください。

【分類ルール】
- 予定・スケジュール・タスクを聞いている → intent = "schedule_search"
- それ以外（雑談・質問・相談・アドバイスなど） → intent = "general_chat"

【出力フォーマット（JSONのみ、他のテキストは不要）】
{"intent":"schedule_search","target_date":"YYYY-MM-DD","keyword":"検索語"}
または
{"intent":"general_chat","target_date":"","keyword":""}

【注意】
- 「来週の月曜」「3日後」なども日付を計算して target_date に入れる
- キーワードがない場合は空文字にする
- 必ずJSON形式のみを出力する

【ユーザーの入力】
$query"""

            val response = AiEngineManager.generateResponse(prompt) ?: return null

            // JSONを抽出
            val jsonStr = extractJsonFromText(response) ?: return null
            val json = JSONObject(jsonStr)

            val intent = json.optString("intent", "general_chat")
            val targetDate = json.optString("target_date", "").replace("/", "-")
            val keyword = json.optString("keyword", "")

            Log.d(TAG, "Intent classification: intent=$intent, date=$targetDate, keyword=$keyword")

            return IntentResult(
                intent = intent,
                targetDate = targetDate,
                keyword = keyword,
                dateLabel = if (targetDate.isNotBlank()) "${targetDate}の" else ""
            )
        } catch (e: Exception) {
            Log.e(TAG, "classifyIntentWithLlm error", e)
            return null
        }
    }

    /**
     * 自由会話応答を生成する。
     * 直近7日間の予定をコンテキストとして渡し、予定に関する質問にも対応可能にする。
     */
    private suspend fun generateFreeResponse(query: String, currentDate: String): String? {
        try {
            // 直近7日間の予定をコンテキストとして取得
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
- Markdown装飾は使わない（**太字**や##見出しなど）
- 予定の詳細を聞かれた場合は上記の予定情報を参照する
- 予定情報がない場合は「予定が登録されていません」と正直に答える"""

            return AiEngineManager.generateChatResponse(query, systemPrompt)
        } catch (e: Exception) {
            Log.e(TAG, "generateFreeResponse error", e)
            return null
        }
    }

    /**
     * 直近7日間の予定をテキスト形式で構築する。
     */
    private suspend fun buildTaskContext(currentDate: String): String {
        return try {
            val today = LocalDate.parse(currentDate)
            val allTasks = taskRepo.getAll().first()
            val upcoming = allTasks.filter { task ->
                !task.isCompleted && !task.isDeleted && task.startDate != null &&
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

    /**
     * タスクを検索して結果を文字列で返す。
     */
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

    /**
     * Engine未ロードの場合にロードを試みる。
     */
    private suspend fun tryLoadEngine(): Boolean {
        return try {
            AiEngineManager.loadEngine(getApplication())
            AiEngineManager.isLoaded()
        } catch (e: Exception) {
            Log.e(TAG, "Engine load failed", e)
            false
        }
    }

    /**
     * テキストから最初の {...} ブロックを抽出する。
     */
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

    override fun onCleared() {
        super.onCleared()
        // Engineの解放はアプリ全体で管理するため、ここでは行わない
    }

    /**
     * 意図分類の結果データクラス
     */
    private data class IntentResult(
        val intent: String,       // "schedule_search" or "general_chat"
        val targetDate: String,   // "YYYY-MM-DD" or ""
        val keyword: String,      // 検索キーワード or ""
        val dateLabel: String     // 表示用ラベル（例: "明日の"）
    )

    companion object {
        private const val TAG = "AiChatVM"
    }
}
