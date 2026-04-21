package com.example.taskschedulerv3.ui.aichat

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.taskschedulerv3.data.model.ScheduleType
import com.example.taskschedulerv3.data.model.Tag
import com.example.taskschedulerv3.data.model.Task
import com.example.taskschedulerv3.util.PhotoFileManager
import android.util.Log
import java.io.File
import java.util.Calendar
import java.util.Locale
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// =============================================
// メイン画面
// =============================================
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AiChatScreen(
    navController: NavController,
    viewModel: AiChatViewModel = viewModel()
) {
    val messages by viewModel.messages.collectAsState()
    val isTyping by viewModel.isTyping.collectAsState()
    val wizardStep by viewModel.wizardStep.collectAsState()
    val allTags by viewModel.allTags.collectAsState()
    val allTasks by viewModel.allTasks.collectAsState()
    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI アシスタント") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "戻る")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    ChatMessageItem(
                        message = msg,
                        wizardStep = wizardStep,
                        allTags = allTags,
                        allTasks = allTasks,
                        viewModel = viewModel,
                        isLatestAiMessage = msg == messages.lastOrNull { !it.isUser }
                    )
                }
                if (isTyping) {
                    item { ChatTypingIndicator() }
                }
            }

            // 入力欄
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            when (wizardStep) {
                                WizardStep.IDLE -> "なんでも聞いてください…"
                                WizardStep.INPUT_TITLE -> "タスク名を入力…"
                                WizardStep.INPUT_MEMO -> "メモを入力…"
                                WizardStep.SELECT_DATE, WizardStep.SELECT_END_DATE ->
                                    "4月25日、明日 など…"
                                WizardStep.SELECT_TIME -> "7時半、14:00〜15:30 など…"
                                else -> "テキストを入力…"
                            }
                        )
                    },
                    maxLines = 3,
                    shape = RoundedCornerShape(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            viewModel.sendMessage(inputText)
                            inputText = ""
                        }
                    }
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "送信",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

// =============================================
// メッセージ振り分け
// =============================================
@Composable
fun ChatMessageItem(
    message: ChatMessage,
    wizardStep: WizardStep,
    allTags: List<Tag>,
    allTasks: List<Task>,
    viewModel: AiChatViewModel,
    isLatestAiMessage: Boolean
) {
    when (val content = message.content) {
        is ChatContent.Text -> {
            ChatBubble(text = content.body, isUser = message.isUser)
        }
        is ChatContent.ChoiceButtons -> {
            AiCardWrapper {
                Text(content.prompt, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    content.choices.forEach { choice ->
                        FilledTonalButton(
                            onClick = { viewModel.onChoiceSelected(wizardStep, choice.value) },
                            enabled = isLatestAiMessage
                        ) { Text(choice.label) }
                    }
                    if (content.allowSkip) {
                        OutlinedButton(
                            onClick = { viewModel.onChoiceSelected(wizardStep, "スキップ") },
                            enabled = isLatestAiMessage
                        ) { Text("スキップ") }
                    }
                }
            }
        }
        is ChatContent.TextInput -> {
            AiCardWrapper {
                Text(content.prompt, fontWeight = FontWeight.Medium)
                if (content.hint.isNotEmpty()) {
                    Text(content.hint, color = Color.Gray, fontSize = 12.sp)
                }
            }
        }
        is ChatContent.TextInputWithAi -> {
            AiCardWrapper {
                Text(content.prompt, fontWeight = FontWeight.Medium)
                if (content.hint.isNotEmpty()) {
                    Text(content.hint, color = Color.Gray, fontSize = 12.sp)
                }
                if (content.aiDescription.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            content.aiDescription,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(8.dp),
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
                if (content.allowSkip && isLatestAiMessage) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { viewModel.onChoiceSelected(wizardStep, "スキップ") }
                    ) { Text("スキップ") }
                }
            }
        }
        is ChatContent.DatePickerRequest -> {
            DatePickerCard(
                prompt = content.prompt,
                allowSkip = content.allowSkip,
                enabled = isLatestAiMessage,
                onDateSelected = { viewModel.onDateSelected(it) },
                onSkip = { viewModel.onChoiceSelected(wizardStep, "スキップ") }
            )
        }
        is ChatContent.TimePickerRequest -> {
            TimePickerCard(
                prompt = content.prompt,
                allowSkip = content.allowSkip,
                enabled = isLatestAiMessage,
                onTimeSelected = { s, e -> viewModel.onTimeSelected(s, e) },
                onSkip = { viewModel.onChoiceSelected(wizardStep, "スキップ") }
            )
        }
        is ChatContent.TagPickerRequest -> {
            TagPickerCard(
                prompt = content.prompt,
                tags = allTags,
                enabled = isLatestAiMessage,
                onTagsSelected = { viewModel.onTagsSelected(it) },
                onSkip = { viewModel.onTagsSelected(emptyList()) }
            )
        }
        is ChatContent.RelationPickerRequest -> {
            RelationPickerCard(
                prompt = content.prompt,
                tasks = allTasks,
                suggestedIds = content.suggestedTaskIds,
                enabled = isLatestAiMessage,
                onRelationsSelected = { viewModel.onRelationsSelected(it) },
                onSkip = { viewModel.onRelationsSelected(emptyList()) }
            )
        }
        is ChatContent.PhotoPickerRequest -> {
            PhotoPickerCard(
                prompt = content.prompt,
                enabled = isLatestAiMessage,
                onPhotoSelected = { viewModel.onPhotoSelected(it) },
                onSkip = { viewModel.onPhotoSelected(null) }
            )
        }
        is ChatContent.RecurrencePickerRequest -> {
            RecurrencePickerCard(
                prompt = content.prompt,
                enabled = isLatestAiMessage,
                onSelected = { p, d, e -> viewModel.onRecurrenceSelected(p, d, e) }
            )
        }
        is ChatContent.TaskConfirmation -> {
            TaskConfirmationCard(
                draft = content.draft,
                allTags = allTags,
                isActive = content.isActive && isLatestAiMessage,
                onConfirm = { viewModel.confirmRegistration() },
                onModify = { viewModel.goBackToModify() },
                onCancel = { viewModel.cancelRegistration() }
            )
        }
        is ChatContent.TaskRegistered -> {
            TaskRegisteredCard(title = content.taskTitle)
        }
    }
}

// =============================================
// チャットバブル
// =============================================
@Composable
fun ChatBubble(text: String, isUser: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp, topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            color = if (isUser)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Text(text, modifier = Modifier.padding(12.dp), fontSize = 14.sp)
        }
    }
}

// =============================================
// AIカードラッパー
// =============================================
@Composable
fun AiCardWrapper(content: @Composable ColumnScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Card(
            modifier = Modifier.widthIn(max = 320.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(
                topStart = 16.dp, topEnd = 16.dp,
                bottomStart = 4.dp, bottomEnd = 16.dp
            )
        ) {
            Column(modifier = Modifier.padding(12.dp), content = content)
        }
    }
}

// =============================================
// 日付選択カード
// =============================================
@Composable
fun DatePickerCard(
    prompt: String,
    allowSkip: Boolean,
    enabled: Boolean,
    onDateSelected: (String) -> Unit,
    onSkip: () -> Unit
) {
    val context = LocalContext.current

    AiCardWrapper {
        Text(prompt, fontWeight = FontWeight.Medium, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
                onClick = {
                    val cal = Calendar.getInstance()
                    DatePickerDialog(context, { _, y, m, d ->
                        onDateSelected(String.format(Locale.getDefault(), "%04d-%02d-%02d", y, m + 1, d))
                    }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH),
                        cal.get(Calendar.DAY_OF_MONTH)).show()
                },
                enabled = enabled
            ) { Text("カレンダー") }
            if (allowSkip) {
                OutlinedButton(onClick = onSkip, enabled = enabled) { Text("スキップ") }
            }
        }
        Text(
            "またはテキスト入力欄に日付を入力してください",
            fontSize = 11.sp, color = Color.Gray,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

// =============================================
// 時刻選択カード
// =============================================
@Composable
fun TimePickerCard(
    prompt: String,
    allowSkip: Boolean,
    enabled: Boolean,
    onTimeSelected: (String, String) -> Unit,
    onSkip: () -> Unit
) {
    val context = LocalContext.current
    var startTime by remember { mutableStateOf("") }
    var endTime by remember { mutableStateOf("") }

    AiCardWrapper {
        Text(prompt, fontWeight = FontWeight.Medium, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
                onClick = {
                    TimePickerDialog(context, { _, h, m ->
                        startTime = String.format(Locale.getDefault(), "%02d:%02d", h, m)
                    }, 9, 0, true).show()
                },
                enabled = enabled
            ) { Text(if (startTime.isEmpty()) "開始" else startTime) }
            FilledTonalButton(
                onClick = {
                    TimePickerDialog(context, { _, h, m ->
                        endTime = String.format(Locale.getDefault(), "%02d:%02d", h, m)
                    }, 10, 0, true).show()
                },
                enabled = enabled
            ) { Text(if (endTime.isEmpty()) "終了" else endTime) }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
                onClick = { onTimeSelected(startTime, endTime) },
                enabled = enabled && startTime.isNotEmpty()
            ) { Text("決定") }
            if (allowSkip) {
                OutlinedButton(onClick = onSkip, enabled = enabled) { Text("スキップ") }
            }
        }
        Text(
            "またはテキスト入力欄に「7時半」等と入力できます",
            fontSize = 11.sp, color = Color.Gray,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

// =============================================
// タグ選択カード
// =============================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagPickerCard(
    prompt: String,
    tags: List<Tag>,
    enabled: Boolean,
    onTagsSelected: (List<Int>) -> Unit,
    onSkip: () -> Unit
) {
    var selectedIds by remember { mutableStateOf(setOf<Int>()) }

    AiCardWrapper {
        Text(prompt, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(8.dp))
        if (tags.isEmpty()) {
            Text("タグがありません", fontSize = 12.sp, color = Color.Gray)
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                tags.forEach { tag ->
                    FilterChip(
                        selected = tag.id in selectedIds,
                        onClick = {
                            selectedIds = if (tag.id in selectedIds)
                                selectedIds - tag.id else selectedIds + tag.id
                        },
                        label = { Text(tag.name, fontSize = 12.sp) },
                        enabled = enabled
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
                onClick = { onTagsSelected(selectedIds.toList()) },
                enabled = enabled
            ) { Text("決定") }
            OutlinedButton(onClick = onSkip, enabled = enabled) { Text("スキップ") }
        }
    }
}

// =============================================
// 関連予定選択カード（AI候補付き）
// =============================================
@Composable
fun RelationPickerCard(
    prompt: String,
    tasks: List<Task>,
    suggestedIds: List<Int>,
    enabled: Boolean,
    onRelationsSelected: (List<Int>) -> Unit,
    onSkip: () -> Unit
) {
    // AI候補を初期選択状態にする
    var selectedIds by remember { mutableStateOf(suggestedIds.toSet()) }
    var showAll by remember { mutableStateOf(false) }

    AiCardWrapper {
        Text(prompt, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(8.dp))

        // AI候補表示
        if (suggestedIds.isNotEmpty()) {
            Text("AI おすすめ:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary)
            suggestedIds.forEach { id ->
                val t = tasks.find { it.id == id }
                if (t != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = enabled) {
                                selectedIds = if (id in selectedIds)
                                    selectedIds - id else selectedIds + id
                            }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = id in selectedIds,
                            onCheckedChange = null, enabled = enabled
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Column {
                            Text(t.title, fontSize = 13.sp, maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                            Text(t.startDate, fontSize = 11.sp, color = Color.Gray)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        // 全タスク一覧（展開式）
        if (tasks.isNotEmpty()) {
            OutlinedButton(
                onClick = { showAll = !showAll },
                enabled = enabled
            ) { Text(if (showAll) "一覧を閉じる" else "すべてから選択") }

            if (showAll) {
                Column(modifier = Modifier.heightIn(max = 200.dp)) {
                    tasks.filter { it.id !in suggestedIds }.take(30).forEach { task ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = enabled) {
                                    selectedIds = if (task.id in selectedIds)
                                        selectedIds - task.id else selectedIds + task.id
                                }
                                .padding(vertical = 2.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = task.id in selectedIds,
                                onCheckedChange = null, enabled = enabled
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(task.title, fontSize = 12.sp, maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        } else {
            Text("関連付けできる予定がありません", fontSize = 12.sp, color = Color.Gray)
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
                onClick = { onRelationsSelected(selectedIds.toList()) },
                enabled = enabled
            ) { Text("決定") }
            OutlinedButton(onClick = onSkip, enabled = enabled) { Text("スキップ") }
        }
    }
}

// =============================================
// 写真追加カード（カメラ＋ギャラリー対応）— 修正版
// =============================================
@Composable
fun PhotoPickerCard(
    prompt: String,
    enabled: Boolean,
    onPhotoSelected: (String?) -> Unit,
    onSkip: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var photoPath by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    // カメラ用の一時ファイル情報を state で保持（リコンポジション安全）
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }

    // カメラ起動結果
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val file = pendingCameraFile
        if (success && file != null && file.exists()) {
            isSaving = true
            scope.launch(Dispatchers.IO) {
                val saved = PhotoFileManager.saveResizedPhotoFromFile(context, file)
                withContext(Dispatchers.Main) {
                    photoPath = saved
                    isSaving = false
                }
            }
        }
    }

    // ギャラリー選択結果
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            isSaving = true
            scope.launch(Dispatchers.IO) {
                val saved = PhotoFileManager.saveResizedPhoto(context, uri)
                withContext(Dispatchers.Main) {
                    photoPath = saved
                    isSaving = false
                }
            }
        }
    }

    AiCardWrapper {
        Text(prompt, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(8.dp))

        if (isSaving) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("写真を処理中…", fontSize = 12.sp, color = Color.Gray)
            }
            Spacer(modifier = Modifier.height(4.dp))
        } else if (photoPath != null) {
            Text("写真を選択済み", fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(4.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
                onClick = {
                    try {
                        val result = PhotoFileManager.createTempPhotoUri(context)
                        pendingCameraFile = result.second
                        cameraLauncher.launch(result.first)
                    } catch (e: Exception) {
                        Log.e("PhotoPickerCard", "Camera launch error", e)
                    }
                },
                enabled = enabled && !isSaving
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null,
                    modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("カメラ")
            }
            FilledTonalButton(
                onClick = {
                    try {
                        galleryLauncher.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    } catch (e: Exception) {
                        Log.e("PhotoPickerCard", "Gallery launch error", e)
                    }
                },
                enabled = enabled && !isSaving
            ) {
                Icon(Icons.Default.Photo, contentDescription = null,
                    modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("ギャラリー")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (photoPath != null) {
                FilledTonalButton(
                    onClick = { onPhotoSelected(photoPath) },
                    enabled = enabled && !isSaving
                ) { Text("この写真で決定") }
            }
            OutlinedButton(onClick = onSkip, enabled = enabled && !isSaving) { Text("スキップ") }
        }
    }
}

// =============================================
// 繰り返しパターン選択カード
// =============================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecurrencePickerCard(
    prompt: String,
    enabled: Boolean,
    onSelected: (String, String, String) -> Unit
) {
    val patterns = listOf(
        "毎日" to "DAILY", "毎週" to "WEEKLY", "隔週" to "BIWEEKLY",
        "毎月（日付）" to "MONTHLY_DATE", "毎月（曜日）" to "MONTHLY_WEEK",
        "毎年" to "YEARLY"
    )

    AiCardWrapper {
        Text(prompt, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            patterns.forEach { (label, value) ->
                FilledTonalButton(
                    onClick = { onSelected(value, "", "") },
                    enabled = enabled
                ) { Text(label, fontSize = 12.sp) }
            }
        }
    }
}

// =============================================
// 確認カード
// =============================================
@Composable
fun TaskConfirmationCard(
    draft: DraftTaskData,
    allTags: List<Tag>,
    isActive: Boolean,
    onConfirm: () -> Unit,
    onModify: () -> Unit,
    onCancel: () -> Unit
) {
    AiCardWrapper {
        Text("この内容で登録してよろしいですか？",
            fontWeight = FontWeight.Bold, fontSize = 15.sp)
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        ConfirmRow("種類", when {
            draft.isIndefinite -> "無期限"
            draft.scheduleType == ScheduleType.NORMAL -> "通常"
            draft.scheduleType == ScheduleType.PERIOD -> "期間"
            draft.scheduleType == ScheduleType.RECURRING -> "繰り返し"
            else -> draft.scheduleType.name
        })
        ConfirmRow("タスク名", draft.title)
        if (draft.memo.isNotEmpty()) ConfirmRow("メモ", draft.memo)
        if (draft.startDate.isNotEmpty()) {
            ConfirmRow("日付", draft.startDate +
                if (draft.endDate.isNotEmpty()) " 〜 ${draft.endDate}" else "")
        }
        if (draft.startTime.isNotEmpty()) {
            ConfirmRow("時刻", draft.startTime +
                if (draft.endTime.isNotEmpty()) " 〜 ${draft.endTime}" else "")
        }
        if (draft.scheduleType == ScheduleType.RECURRING) {
            ConfirmRow("繰り返し", recurrenceDisplayLabel(draft.recurrencePattern))
        }
        ConfirmRow("通知", "1日前")
        if (draft.tagIds.isNotEmpty()) {
            val names = allTags.filter { it.id in draft.tagIds }.joinToString(", ") { it.name }
            ConfirmRow("タグ", names)
        }
        if (draft.relatedTaskIds.isNotEmpty()) {
            ConfirmRow("関連予定", "${draft.relatedTaskIds.size}件")
        }
        if (draft.photoPath != null) {
            ConfirmRow("写真", "あり")
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        if (isActive) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(onClick = onConfirm, modifier = Modifier.weight(1f)) {
                    Text("登録する")
                }
                OutlinedButton(onClick = onModify, modifier = Modifier.weight(1f)) {
                    Text("修正する")
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(
                onClick = onCancel,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) { Text("キャンセル", color = MaterialTheme.colorScheme.error) }
        } else {
            Text("（処理済み）", color = Color.Gray, fontSize = 12.sp)
        }
    }
}

@Composable
fun ConfirmRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
    ) {
        Text(label, fontWeight = FontWeight.Medium, fontSize = 13.sp,
            modifier = Modifier.width(80.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 13.sp)
    }
}

@Composable
fun TaskRegisteredCard(title: String) {
    AiCardWrapper {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Check, contentDescription = null,
                tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("「${title}」を登録しました",
                fontWeight = FontWeight.Medium, color = Color(0xFF4CAF50))
        }
    }
}

@Composable
fun ChatTypingIndicator() {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text("考え中…", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                fontSize = 13.sp, color = Color.Gray)
        }
    }
}

private fun recurrenceDisplayLabel(pattern: String): String = when (pattern) {
    "DAILY" -> "毎日"; "WEEKLY" -> "毎週"; "BIWEEKLY" -> "隔週"
    "MONTHLY_DATE" -> "毎月（日付）"; "MONTHLY_WEEK" -> "毎月（曜日）"
    "YEARLY" -> "毎年"; else -> pattern
}
