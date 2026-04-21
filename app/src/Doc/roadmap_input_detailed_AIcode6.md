# AI Task Wizard Simplification (Simplified Flow)

## 絶対に守るルール

1. 各ファイルのコードは「全置換」です。既存コードは完全に無視し、以下のコードで丸ごと上書きします。
2. 旧コードのクラス・関数・変数を一切流用しません。
3. 旧コードのWizardStep（SELECT_TYPE等）は全て削除済みです。
4. 旧コードのChatContent（ChoiceButtons等）は全て削除済みです。
5. 旧コードに存在しないComposable（DatePickerCard等）は全て削除済みです。
6. DAOメソッド名遵守（getAll, insert, getById）。
7. 写真はギャラリー限定。

## 新設計概要（全7ステップ）

ユーザーの自然文入力からAIがJSON形式で情報を抽出し、不足分を補完する自然な対話形式へ移行。

```
ユーザー「予定を登録」等のキーワード
  ↓
Step 1: 予定情報の入力（自然文：例「明日10時に新宿で歯医者」）
  ↓
Step 2: AIによる情報の自動抽出（JSON形式）
  ↓
Step 3: 不足情報の補完対話（AIがタイトルや日付が未確定の場合に質問）
  ↓
Step 4: 確認カードの提示
  ↓
Step 5: 必要に応じた修正指示（自然文で追加・変更）
  ↓
Step 6: 写真追加（ギャラリー選択限定・スキップ可）
  ↓
Step 7: 登録完了
```

---

## ファイル 1: `ChatTaskModels.kt`

```kotlin
package com.example.taskschedulerv3.ui.aichat

import com.example.taskschedulerv3.data.model.ScheduleType

/**
 * AI チャット登録のウィザード状態（7ステップ）
 */
enum class WizardStep {
    IDLE, WAITING_INPUT, WAITING_ANSWER, WAITING_MODIFY, PHOTO_SELECT, CONFIRM, COMPLETED
}

data class DraftTaskData(
    val title: String = "",
    val memo: String = "",
    val startDate: String = "",
    val endDate: String? = null,
    val startTime: String? = null,
    val endTime: String? = null,
    val location: String? = null,
    val scheduleType: ScheduleType = ScheduleType.NORMAL,
    val isIndefinite: Boolean = false,
    val tagIds: List<Int> = emptyList(),
    val photoPath: String? = null,
    val recurrencePattern: String? = null
)

sealed class ChatContent {
    data class Text(val body: String) : ChatContent()
    data class PhotoPickerRequest(val prompt: String, val allowSkip: Boolean = true) : ChatContent()
    data class TaskConfirmation(val draft: DraftTaskData, val isActive: Boolean = true) : ChatContent()
    data class TaskRegistered(val taskTitle: String, val taskId: Int) : ChatContent()
}

data class ChatMessage(
    val content: ChatContent,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
```

---

## ファイル 2: `AiChatViewModel.kt`

```kotlin
package com.example.taskschedulerv3.ui.aichat

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskschedulerv3.data.db.AppDatabase
import com.example.taskschedulerv3.data.model.*
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
        addAiMessage(ChatContent.Text("こんにちは！AIアシスタントです。予定の登録をお手伝いします。"))
        viewModelScope.launch { tagDao.getAll().collect { _allTags.value = it } }
    }

    fun sendMessage(text: String) {
        addUserMessage(text)
        viewModelScope.launch {
            _isTyping.value = true
            try { processInput(text) } finally { _isTyping.value = false }
        }
    }

    private suspend fun processInput(text: String) {
        val step = _wizardStep.value
        when {
            step == WizardStep.IDLE && isRegistrationIntent(text) -> startRegistrationFlow(text)
            step == WizardStep.WAITING_INPUT -> startRegistrationFlow(text)
            step == WizardStep.WAITING_ANSWER || step == WizardStep.WAITING_MODIFY || step == WizardStep.CONFIRM -> refineTaskWithAi(text)
            else -> handleNormalChat(text)
        }
    }

    private fun startRegistrationFlow(text: String) {
        val cleanText = text.replace(Regex("予定を登録|登録"), "").trim()
        if (cleanText.isEmpty()) {
            _wizardStep.value = WizardStep.WAITING_INPUT
            addAiMessage(ChatContent.Text("どのような予定を登録しますか？"))
        } else {
            viewModelScope.launch { parseTaskWithAi(cleanText) }
        }
    }

    private suspend fun parseTaskWithAi(text: String) {
        if (!AiEngineManager.isLoaded()) { fallbackRegistration(text); return }
        val prompt = "自然文からタスク情報をJSONで抽出せよ: $text"
        val resp = withContext(Dispatchers.IO) { AiEngineManager.generateResponse(prompt) }
        val json = extractJson(resp ?: "")
        if (json != null) {
            val d = parseJsonToDraft(json)
            _draft.value = d
            autoSelectTags(d.title)
            showConfirmation()
        } else { fallbackRegistration(text) }
    }

    private suspend fun refineTaskWithAi(text: String) {
        if (text.contains("OK") || text.contains("登録")) { confirmRegistration(); return }
        parseTaskWithAi("現在: ${_draft.value} 修正: $text")
    }

    private fun autoSelectTags(content: String) {
        val matched = _allTags.value.filter { content.contains(it.name) }.map { it.id }
        _draft.value = _draft.value.copy(tagIds = matched)
    }

    private fun showConfirmation() {
        _wizardStep.value = WizardStep.CONFIRM
        addAiMessage(ChatContent.TaskConfirmation(_draft.value))
    }

    fun confirmRegistration() {
        viewModelScope.launch {
            val id = registerTask(_draft.value)
            _wizardStep.value = WizardStep.PHOTO_SELECT
            addAiMessage(ChatContent.PhotoPickerRequest("登録しました。写真を追加しますか？"))
        }
    }

    fun onPhotoSelected(path: String?) {
        _draft.value = _draft.value.copy(photoPath = path)
        addAiMessage(ChatContent.Text("完了しました！"))
        _wizardStep.value = WizardStep.COMPLETED
        resetWizard()
    }

    private fun resetWizard() { _draft.value = DraftTaskData(); _wizardStep.value = WizardStep.IDLE }

    private suspend fun registerTask(d: DraftTaskData): Int = withContext(Dispatchers.IO) {
        val task = Task(title = d.title, description = d.memo, startDate = d.startDate, location = d.location)
        val id = taskDao.insert(task).toInt()
        d.tagIds.forEach { crossRefDao.insert(TaskTagCrossRef(id, it)) }
        taskDao.getById(id)?.let { AlarmScheduler.scheduleForTask(getApplication(), it) }
        id
    }

    private fun extractJson(text: String) = Regex("\\{.*\\}", RegexOption.DOT_MATCHES_ALL).find(text)?.value
    private fun parseJsonToDraft(json: String): DraftTaskData {
        val t = Regex("\"title\"\\s*:\\s*\"([^\"]*)\"").find(json)?.groupValues?.get(1) ?: "無題"
        val d = Regex("\"startDate\"\\s*:\\s*\"([^\"]*)\"").find(json)?.groupValues?.get(1) ?: LocalDate.now().toString()
        return DraftTaskData(title = t, startDate = d)
    }

    private fun fallbackRegistration(text: String) {
        _draft.value = DraftTaskData(title = text)
        showConfirmation()
    }

    private fun addAiMessage(c: ChatContent) { _messages.value += ChatMessage(c, false) }
    private fun addUserMessage(t: String) { _messages.value += ChatMessage(ChatContent.Text(t), true) }
    private fun isRegistrationIntent(t: String) = t.contains("登録")
    private fun handleNormalChat(t: String) {
        viewModelScope.launch {
            val resp = withContext(Dispatchers.IO) { AiEngineManager.generateResponse(t) }
            addAiMessage(ChatContent.Text(resp ?: "はい、わかりました。"))
        }
    }
}
```

---

## ファイル 3: `AiChatScreen.kt`

```kotlin
package com.example.taskschedulerv3.ui.aichat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(navController: NavController, viewModel: AiChatViewModel = viewModel()) {
    val messages by viewModel.messages.collectAsState()
    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("AI アシスタント") },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } })
        }
    ) { p ->
        Column(Modifier.padding(p).fillMaxSize()) {
            LazyColumn(state = listState, modifier = Modifier.weight(1f).padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(messages) { msg -> ChatMessageItem(msg, viewModel, msg == messages.findLast { !it.isUser }) }
            }
            Surface(tonalElevation = 2.dp) {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = inputText, onValueChange = { inputText = it }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(24.dp))
                    IconButton(onClick = { if (inputText.isNotBlank()) { viewModel.sendMessage(inputText); inputText = "" } }) {
                        Icon(Icons.AutoMirrored.Filled.Send, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
fun ChatMessageItem(message: ChatMessage, viewModel: AiChatViewModel, isLatest: Boolean) {
    val isUser = message.isUser
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        when (val content = message.content) {
            is ChatContent.Text -> Surface(shape = RoundedCornerShape(12.dp), color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant) {
                Text(content.body, Modifier.padding(12.dp), fontSize = 14.sp)
            }
            is ChatContent.TaskConfirmation -> TaskConfirmationCard(content.draft, isLatest, { viewModel.confirmRegistration() }, { viewModel.cancelRegistration() })
            is ChatContent.PhotoPickerRequest -> {
                val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { viewModel.onPhotoSelected(it?.toString()) }
                AiCard("写真の追加") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("選択") }
                        OutlinedButton(onClick = { viewModel.onPhotoSelected(null) }) { Text("スキップ") }
                    }
                }
            }
            is ChatContent.TaskRegistered -> AiCard("登録完了") { Text("『${content.taskTitle}』を登録しました。") }
        }
    }
}

@Composable
fun TaskConfirmationCard(draft: DraftTaskData, isActive: Boolean, onOK: () -> Unit, onCancel: () -> Unit) {
    AiCard("内容確認") {
        Text("タイトル: ${draft.title}", fontWeight = FontWeight.Bold)
        Text("日付: ${draft.startDate}")
        if (draft.location != null) Text("場所: ${draft.location}")
        if (isActive) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onCancel) { Text("キャンセル") }
                Button(onClick = onOK) { Text("OK") }
            }
        }
    }
}

@Composable
fun AiCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.widthIn(max = 300.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(12.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
            content()
        }
    }
}
```