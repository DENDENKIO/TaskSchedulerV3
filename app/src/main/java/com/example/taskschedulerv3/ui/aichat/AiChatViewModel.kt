package com.example.taskschedulerv3.ui.aichat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskschedulerv3.data.db.AppDatabase
import com.example.taskschedulerv3.data.model.Task
import com.example.taskschedulerv3.data.repository.TaskRepository
import com.example.taskschedulerv3.util.AiTextExtractor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

// チャットメッセージのデータクラス
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

class AiChatViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.getInstance(app)
    // 既存のリポジトリ定義に合わせる (dao, roadmapDao)
    private val taskRepo = TaskRepository(db.taskDao(), db.roadmapStepDao())

    private val _messages = MutableStateFlow<List<ChatMessage>>(
        listOf(ChatMessage(text = "こんにちは！「明日の予定は？」のように聞いてみてください。", isUser = false))
    )
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        // ユーザーのメッセージを追加
        addMessage(ChatMessage(text = text, isUser = true))
        
        // AIの応答を生成
        viewModelScope.launch {
            _isTyping.value = true
            
            // 少し思考中のウェイトを入れる（UX向上）
            kotlinx.coroutines.delay(600)
            
            val responseText = processQuery(text)
            addMessage(ChatMessage(text = responseText, isUser = false))
            
            _isTyping.value = false
        }
    }

    private fun addMessage(message: ChatMessage) {
        _messages.value = _messages.value + message
    }

    /**
     * 【第2段階：ハイブリッド方式】プログラム判定（ルールベース）＋ LLMフォールバック
     */
    private suspend fun processQuery(query: String): String {
        var targetDateStr = ""
        var keyword = ""
        val today = LocalDate.now()
        var dateLabel = ""

        // ==========================================
        // 1. 高速で確実なルールベース（正規表現・キーワード検索）
        // ==========================================
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

        // キーワードの簡易抽出（例：「会議の予定」→「会議」）
        val keywordMatch = Regex("「(.+)」").find(query) ?: Regex("(.+)の予定").find(query)
        if (keywordMatch != null) {
            val rawKeyword = keywordMatch.groupValues[1]
            keyword = rawKeyword.replace("今日", "").replace("明日", "").replace("明後日", "").trim()
        }

        // ==========================================
        // 2. ルールベースで判定できなかった場合のみ、LLM（AI）に頼る
        // ==========================================
        if (targetDateStr.isBlank() && keyword.isBlank()) {
            AiTextExtractor.initialize(getApplication())
            val todayStr = today.format(DateTimeFormatter.ISO_LOCAL_DATE)
            val jsonResult = AiTextExtractor.parseChatIntent(query, todayStr)
            
            if (!jsonResult.isNullOrBlank()) {
                try {
                    val json = JSONObject(jsonResult)
                    targetDateStr = json.optString("target_date", "")
                    keyword = json.optString("keyword", "")
                    
                    // 日付フォーマットの表記揺れ（/ を - に置換）を吸収
                    targetDateStr = targetDateStr.replace("/", "-")
                } catch (e: Exception) {
                    // JSONパース失敗時はフォールバックを表示せずに続行
                }
            }
        }

        // ==========================================
        // 3. 検索の実行と結果の返却
        // ==========================================
        if (targetDateStr.isBlank() && keyword.isBlank()) {
            return "いつの予定か、または何の予定か教えていただけますか？（例：「明日の予定」「会議の予定」）"
        }

        // DBから検索
        val allTasks = taskRepo.getAll().first()
        val matchedTasks = allTasks.filter { task ->
            val matchDate = targetDateStr.isBlank() || task.startDate == targetDateStr
            val matchKeyword = keyword.isBlank() || (
                task.title.contains(keyword, ignoreCase = true) || 
                task.description?.contains(keyword, ignoreCase = true) == true
            )
            !task.isCompleted && !task.isDeleted && matchDate && matchKeyword
        }.sortedBy { it.startTime ?: "23:59" }

        // ラベルの準備
        val dLabel = if (dateLabel.isNotBlank()) dateLabel else if (targetDateStr.isNotBlank()) "${targetDateStr}の" else ""
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

    // ViewModelが破棄される時にAIをメモリから解放する
    override fun onCleared() {
        super.onCleared()
        AiTextExtractor.close()
    }
}
