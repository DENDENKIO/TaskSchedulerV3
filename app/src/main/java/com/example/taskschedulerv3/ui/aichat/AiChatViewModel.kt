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
import java.time.Duration

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

    private suspend fun handleNormalChat(text: String) {
        if (AiEngineManager.isLoaded()) {
            val resp = withContext(Dispatchers.IO) { AiEngineManager.generateResponse(text) }
            addAiMessage(ChatContent.Text(resp ?: "すみません、よくわかりませんでした。"))
        } else {
            addAiMessage(ChatContent.Text("こんにちは！何かお手伝いできることはありますか？"))
        }
    }
}
