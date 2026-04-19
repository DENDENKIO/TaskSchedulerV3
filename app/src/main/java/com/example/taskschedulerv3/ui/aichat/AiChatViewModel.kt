package com.example.taskschedulerv3.ui.aichat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskschedulerv3.data.db.AppDatabase
import com.example.taskschedulerv3.data.model.Task
import com.example.taskschedulerv3.data.repository.TaskRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
     * 第一段階：ルールベースでの意図解析とDB検索
     */
    private suspend fun processQuery(query: String): String {
        val today = LocalDate.now()
        var targetDate: LocalDate? = null
        var dateLabel = ""

        // 軽量なルールベース抽出
        if (query.contains("今日")) {
            targetDate = today
            dateLabel = "今日"
        } else if (query.contains("明日")) {
            targetDate = today.plusDays(1)
            dateLabel = "明日"
        } else if (query.contains("明後日")) {
            targetDate = today.plusDays(2)
            dateLabel = "明後日"
        }

        if (targetDate == null) {
            return "すみません、まだ「今日」「明日」「明後日」の予定検索にしか対応していません。いつの予定か教えていただけますか？"
        }

        // DBからタスクを取得
        val targetDateStr = targetDate.format(DateTimeFormatter.ISO_LOCAL_DATE) // "yyyy-MM-dd"
        // Flow を first() で1回だけ取得
        val allTasks = taskRepo.getAll().first() 
        
        val matchedTasks = allTasks.filter { task ->
            task.startDate == targetDateStr && !task.isCompleted
        }.sortedBy { it.startTime ?: "23:59" } // 時間順にソート

        // 返答文の組み立て
        if (matchedTasks.isEmpty()) {
            return "${dateLabel}の予定は入っていません。ゆっくり休めそうですね！"
        }

        val sb = StringBuilder()
        sb.append("${dateLabel}は **${matchedTasks.size}件** の予定があります。\n\n")
        matchedTasks.forEach { task ->
            val time = task.startTime ?: "終日"
            sb.append("・ $time : ${task.title}\n")
        }
        return sb.toString()
    }
}
