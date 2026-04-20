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
        listOf(ChatMessage(text = "こんにちは！「明日の予定は？」のように聞いてみてください。", isUser = false))
    )
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        addMessage(ChatMessage(text = text, isUser = true))

        viewModelScope.launch {
            _isTyping.value = true
            kotlinx.coroutines.delay(600)
            val responseText = processQuery(text)
            addMessage(ChatMessage(text = responseText, isUser = false))
            _isTyping.value = false
        }
    }

    private fun addMessage(message: ChatMessage) {
        _messages.value = _messages.value + message
    }

    private suspend fun processQuery(query: String): String {
        var targetDateStr = ""
        var keyword = ""
        val today = LocalDate.now()
        var dateLabel = ""

        // 1. ルールベース判定
        if (query.contains("今日")) {
            targetDateStr = today.format(DateTimeFormatter.ISO_LOCAL_DATE)
            dateLabel = "今日"
        } else if (query.contains("明日")) {
            targetDateStr = today.plusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE)
            dateLabel = "明日"
        } else if (query.contains("明後日")) {
            targetDateStr = today.plusDays(2).format(DateTimeFormatter.ISO_LOCAL_DATE)
            dateLabel = "明後日"
        }

        val keywordMatch = Regex("「(.+)」").find(query) ?: Regex("(.+)の予定").find(query)
        if (keywordMatch != null) {
            val rawKeyword = keywordMatch.groupValues[1]
            keyword = rawKeyword.replace("今日", "").replace("明日", "").replace("明後日", "").trim()
        }

        // 2. ルールベースで判定できなかった場合のみ、LLM に頼る
        if (targetDateStr.isBlank() && keyword.isBlank()) {
            try {
                // Engine未ロードなら初期化
                if (!AiEngineManager.isLoaded()) {
                    AiEngineManager.loadEngine(getApplication())
                }

                if (AiEngineManager.isLoaded()) {
                    val todayStr = today.format(DateTimeFormatter.ISO_LOCAL_DATE)
                    val jsonResult = parseChatIntentWithLlm(query, todayStr)

                    if (!jsonResult.isNullOrBlank()) {
                        try {
                            val json = JSONObject(jsonResult)
                            targetDateStr = json.optString("target_date", "")
                            keyword = json.optString("keyword", "")
                            targetDateStr = targetDateStr.replace("/", "-")
                        } catch (e: Exception) {
                            Log.e(TAG, "JSON parse error", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "LLM chat intent error", e)
            }
        }

        // 3. 検索の実行
        if (targetDateStr.isBlank() && keyword.isBlank()) {
            return "いつの予定か、または何の予定か教えていただけますか？（例：「明日の予定」「会議の予定」）"
        }

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
        sb.append("${dLabel}${kwLabel}予定は **${matchedTasks.size}件** あります。\n\n")
        matchedTasks.forEach { task ->
            val time = task.startTime ?: "終日"
            sb.append("・ $time : ${task.title}\n")
        }
        return sb.toString()
    }

    /**
     * LiteRT-LM を使ってチャット意図を解析する。
     * AiTextExtractor.parseChatIntent() の置き換え。
     */
    private suspend fun parseChatIntentWithLlm(query: String, currentDate: String): String? {
        return try {
            val prompt = """今日は $currentDate です。
ユーザーの質問から、検索したい予定の「日付」と「キーワード」を抽出し、以下のJSONのみを出力してください。Markdown装飾は不要です。

【出力フォーマット】
{"target_date":"YYYY-MM-DD","keyword":"会議"}
※日付が特定できない場合は target_date を "" に、特定のキーワードがない場合は keyword を "" にすること。

【質問】
$query"""

            AiEngineManager.generateResponse(prompt)
        } catch (e: Exception) {
            Log.e(TAG, "parseChatIntent error", e)
            null
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Engineの解放はアプリ全体で管理するため、ここでは行わない
    }

    companion object {
        private const val TAG = "AiChatVM"
    }
}
