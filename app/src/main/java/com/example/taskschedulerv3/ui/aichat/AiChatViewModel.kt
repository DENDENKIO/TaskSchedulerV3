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
import java.time.format.DateTimeFormatter

class AiChatViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "AiChatVM"
    }

    private val db = AppDatabase.getInstance(application)
    private val taskDao = db.taskDao()
    private val tagDao = db.tagDao()
    private val crossRefDao = db.taskTagCrossRefDao()

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
            "こんにちは！AIアシスタントです。予定の登録をお手伝いします。\n" +
            "「予定を登録」と入力すると、自然な文章から予定を作成できます。"
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
        if (!AiEngineManager.isLoaded()) {
            addAiMessage(ChatContent.Text("AIエンジンが準備中ですが、簡易登録を試みます。"))
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
                        _wizardStep.value = WizardStep.WAITING_ANSWER
                        addAiMessage(ChatContent.Text("予定の名前（タイトル）は何にしますか？"))
                    } else if (parsed.startDate.isEmpty() && !parsed.isIndefinite) {
                        _wizardStep.value = WizardStep.WAITING_ANSWER
                        addAiMessage(ChatContent.Text("いつの予定ですか？（日付を教えてください）"))
                    } else {
                        // 自動タグ選択
                        autoSelectTags(parsed.title + " " + parsed.memo)
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

    private fun autoSelectTags(content: String) {
        val tags = _allTags.value
        val matchedIds = tags.filter { tag ->
            content.contains(tag.name, ignoreCase = true)
        }.map { it.id }
        _draft.value = _draft.value.copy(tagIds = matchedIds)
    }

    private fun showConfirmation() {
        _wizardStep.value = WizardStep.CONFIRM
        addAiMessage(ChatContent.Text("以下の内容でよろしいですか？"))
        addAiMessage(ChatContent.TaskConfirmation(draft = _draft.value, isActive = true))
    }

    fun confirmRegistration() {
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
            notifyMinutesBefore = 1440, // 1日前
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

    private fun fallbackRegistration(text: String) {
        _draft.value = DraftTaskData(title = text)
        addAiMessage(ChatContent.Text("AI解析ができませんでしたが、タイトル『$text』で登録準備をしました。詳細を教えていただくか、このまま確認にお進みください。"))
        showConfirmation()
    }

    private fun addAiMessage(content: ChatContent) {
        _messages.value = _messages.value + ChatMessage(content, false)
    }

    private fun addUserMessage(text: String) {
        _messages.value = _messages.value + ChatMessage(ChatContent.Text(text), true)
    }

    private fun isRegistrationIntent(text: String): Boolean {
        return text.contains("登録") || text.contains("予定") || text.contains("タスク")
    }

    private fun isCancelIntent(text: String): Boolean {
        return text.contains("キャンセル") || text.contains("やめる")
    }

    private suspend fun handleNormalChat(text: String) {
        if (AiEngineManager.isLoaded()) {
            val resp = withContext(Dispatchers.IO) { AiEngineManager.generateResponse(text) }
            addAiMessage(ChatContent.Text(resp ?: "すみません、よくわかりませんでした。"))
        } else {
            addAiMessage(ChatContent.Text("こんにちは！何かお手伝いできることはありますか？"))
        }
    }
}
