package com.example.taskschedulerv3.ui.aichat

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import kotlinx.coroutines.launch
import java.util.Calendar

// =============================================
// メイン画面
// =============================================
@OptIn(ExperimentalMaterial3Api::class)
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
    val scope = rememberCoroutineScope()
    var inputText by remember { mutableStateOf("") }

    // 新しいメッセージが来たらスクロール
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
            // メッセージ一覧
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(messages) { msg ->
                    ChatMessageItem(
                        message = msg,
                        wizardStep = wizardStep,
                        allTags = allTags,
                        allTasks = allTasks,
                        onChoiceSelected = { value ->
                            viewModel.onChoiceSelected(wizardStep, value)
                        },
                        onDateSelected = { viewModel.onDateSelected(it) },
                        onTimeSelected = { s, e -> viewModel.onTimeSelected(s, e) },
                        onTagsSelected = { viewModel.onTagsSelected(it) },
                        onRelationsSelected = { viewModel.onRelationsSelected(it) },
                        onPhotoSelected = { viewModel.onPhotoSelected(it) },
                        onNotifyTimingSelected = { viewModel.onNotifyTimingSelected(it) },
                        onRecurrenceSelected = { p, d, e ->
                            viewModel.onRecurrenceSelected(p, d, e)
                        },
                        onRoadmapStepsSet = { viewModel.onRoadmapStepsSet(it) },
                        onConfirm = { viewModel.confirmRegistration() },
                        onModify = { viewModel.goBackToModify() },
                        onCancel = { viewModel.cancelRegistration() },
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
                            if (wizardStep == WizardStep.IDLE) "なんでも聞いてください…"
                            else "テキストを入力…"
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
// メッセージアイテムの振り分け
// =============================================
@Composable
fun ChatMessageItem(
    message: ChatMessage,
    wizardStep: WizardStep,
    allTags: List<Tag>,
    allTasks: List<Task>,
    onChoiceSelected: (String) -> Unit,
    onDateSelected: (String) -> Unit,
    onTimeSelected: (String, String) -> Unit,
    onTagsSelected: (List<Int>) -> Unit,
    onRelationsSelected: (List<Int>) -> Unit,
    onPhotoSelected: (String?) -> Unit,
    onNotifyTimingSelected: (Int) -> Unit,
    onRecurrenceSelected: (String, String, String) -> Unit,
    onRoadmapStepsSet: (List<DraftRoadmapStep>) -> Unit,
    onConfirm: () -> Unit,
    onModify: () -> Unit,
    onCancel: () -> Unit,
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
                            onClick = { onChoiceSelected(choice.value) },
                            enabled = isLatestAiMessage
                        ) {
                            Text(choice.label)
                        }
                    }
                    if (content.allowSkip) {
                        OutlinedButton(
                            onClick = { onChoiceSelected("スキップ") },
                            enabled = isLatestAiMessage
                        ) {
                            Text("スキップ")
                        }
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
                if (content.allowSkip && isLatestAiMessage) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = { onChoiceSelected("スキップ") }) {
                        Text("スキップ")
                    }
                }
            }
        }

        is ChatContent.DatePickerRequest -> {
            DatePickerCard(
                prompt = content.prompt,
                allowSkip = content.allowSkip,
                enabled = isLatestAiMessage,
                onDateSelected = onDateSelected,
                onSkip = { onChoiceSelected("スキップ") }
            )
        }

        is ChatContent.TimePickerRequest -> {
            TimePickerCard(
                prompt = content.prompt,
                allowSkip = content.allowSkip,
                enabled = isLatestAiMessage,
                onTimeSelected = onTimeSelected,
                onSkip = { onChoiceSelected("スキップ") }
            )
        }

        is ChatContent.TagPickerRequest -> {
            TagPickerCard(
                prompt = content.prompt,
                tags = allTags,
                enabled = isLatestAiMessage,
                onTagsSelected = onTagsSelected,
                onSkip = { onTagsSelected(emptyList()) }
            )
        }

        is ChatContent.RelationPickerRequest -> {
            RelationPickerCard(
                prompt = content.prompt,
                tasks = allTasks,
                enabled = isLatestAiMessage,
                onRelationsSelected = onRelationsSelected,
                onSkip = { onRelationsSelected(emptyList()) }
            )
        }

        is ChatContent.PhotoPickerRequest -> {
            PhotoPickerCard(
                prompt = content.prompt,
                enabled = isLatestAiMessage,
                onPhotoSelected = onPhotoSelected,
                onSkip = { onPhotoSelected(null) }
            )
        }

        is ChatContent.NotifyTimingRequest -> {
            NotifyTimingCard(
                prompt = content.prompt,
                enabled = isLatestAiMessage,
                onSelected = onNotifyTimingSelected
            )
        }

        is ChatContent.RecurrencePickerRequest -> {
            RecurrencePickerCard(
                prompt = content.prompt,
                enabled = isLatestAiMessage,
                onSelected = onRecurrenceSelected
            )
        }

        is ChatContent.RoadmapStepInput -> {
            RoadmapStepCard(
                prompt = content.prompt,
                currentSteps = content.currentSteps,
                enabled = isLatestAiMessage,
                onStepsSet = onRoadmapStepsSet
            )
        }

        is ChatContent.TaskConfirmation -> {
            TaskConfirmationCard(
                draft = content.draft,
                allTags = allTags,
                isActive = content.isActive && isLatestAiMessage,
                onConfirm = onConfirm,
                onModify = onModify,
                onCancel = onCancel
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
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            color = if (isUser)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(12.dp),
                fontSize = 14.sp
            )
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
            Column(
                modifier = Modifier.padding(12.dp),
                content = content
            )
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
    var selectedDate by remember { mutableStateOf("") }

    AiCardWrapper {
        Text(prompt, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
                onClick = {
                    val cal = Calendar.getInstance()
                    DatePickerDialog(context, { _, y, m, d ->
                        val date = String.format("%04d-%02d-%02d", y, m + 1, d)
                        selectedDate = date
                        onDateSelected(date)
                    }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
                        .show()
                },
                enabled = enabled
            ) {
                Text("日付を選択")
            }
            if (allowSkip) {
                OutlinedButton(onClick = onSkip, enabled = enabled) {
                    Text("スキップ")
                }
            }
        }
        if (selectedDate.isNotEmpty()) {
            Text("選択: $selectedDate", fontSize = 12.sp, color = Color.Gray)
        }
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
        Text(prompt, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
                onClick = {
                    TimePickerDialog(context, { _, h, m ->
                        startTime = String.format("%02d:%02d", h, m)
                    }, 9, 0, true).show()
                },
                enabled = enabled
            ) {
                Text(if (startTime.isEmpty()) "開始時刻" else startTime)
            }
            FilledTonalButton(
                onClick = {
                    TimePickerDialog(context, { _, h, m ->
                        endTime = String.format("%02d:%02d", h, m)
                    }, 10, 0, true).show()
                },
                enabled = enabled
            ) {
                Text(if (endTime.isEmpty()) "終了時刻" else endTime)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
                onClick = { onTimeSelected(startTime, endTime) },
                enabled = enabled && startTime.isNotEmpty()
            ) {
                Text("決定")
            }
            if (allowSkip) {
                OutlinedButton(onClick = onSkip, enabled = enabled) {
                    Text("スキップ")
                }
            }
        }
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
                                selectedIds - tag.id
                            else
                                selectedIds + tag.id
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
            ) {
                Text("決定")
            }
            OutlinedButton(onClick = onSkip, enabled = enabled) {
                Text("スキップ")
            }
        }
    }
}

// =============================================
// 関連予定選択カード
// =============================================
@Composable
fun RelationPickerCard(
    prompt: String,
    tasks: List<Task>,
    enabled: Boolean,
    onRelationsSelected: (List<Int>) -> Unit,
    onSkip: () -> Unit
) {
    var selectedIds by remember { mutableStateOf(setOf<Int>()) }
    var expanded by remember { mutableStateOf(false) }

    AiCardWrapper {
        Text(prompt, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(8.dp))

        if (tasks.isEmpty()) {
            Text("関連付けできる予定がありません", fontSize = 12.sp, color = Color.Gray)
        } else {
            // 選択済み表示
            if (selectedIds.isNotEmpty()) {
                selectedIds.forEach { id ->
                    val t = tasks.find { it.id == id }
                    if (t != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(t.title, fontSize = 12.sp, maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            // タスクリスト（折りたたみ）
            OutlinedButton(
                onClick = { expanded = !expanded },
                enabled = enabled
            ) {
                Text(if (expanded) "一覧を閉じる" else "一覧から選択")
            }

            if (expanded) {
                Column(
                    modifier = Modifier
                        .heightIn(max = 200.dp)
                ) {
                    tasks.take(30).forEach { task ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = enabled) {
                                    selectedIds = if (task.id in selectedIds)
                                        selectedIds - task.id
                                    else
                                        selectedIds + task.id
                                }
                                .padding(vertical = 4.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = task.id in selectedIds,
                                onCheckedChange = null,
                                enabled = enabled
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                task.title,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
                onClick = { onRelationsSelected(selectedIds.toList()) },
                enabled = enabled
            ) {
                Text("決定")
            }
            OutlinedButton(onClick = onSkip, enabled = enabled) {
                Text("スキップ")
            }
        }
    }
}

// =============================================
// 写真追加カード
// =============================================
@Composable
fun PhotoPickerCard(
    prompt: String,
    enabled: Boolean,
    onPhotoSelected: (String?) -> Unit,
    onSkip: () -> Unit
) {
    // 簡易版: カメラ／ギャラリー起動は親Activityに委譲する想定
    // ここではスキップのみ対応。写真機能は既存のCaptureSheetを流用
    AiCardWrapper {
        Text(prompt, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "※ 写真はタスク登録後に編集画面から追加できます",
            fontSize = 11.sp,
            color = Color.Gray
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onSkip, enabled = enabled) {
                Text("スキップ")
            }
        }
    }
}

// =============================================
// 通知タイミング選択カード
// =============================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NotifyTimingCard(
    prompt: String,
    enabled: Boolean,
    onSelected: (Int) -> Unit
) {
    val options = listOf(
        "予定時刻" to 0,
        "5分前" to 5,
        "10分前" to 10,
        "15分前" to 15,
        "30分前" to 30,
        "1時間前" to 60,
        "1日前" to 1440
    )

    AiCardWrapper {
        Text(prompt, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { (label, minutes) ->
                FilledTonalButton(
                    onClick = { onSelected(minutes) },
                    enabled = enabled
                ) {
                    Text(label, fontSize = 12.sp)
                }
            }
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
        "毎日" to "DAILY",
        "毎週" to "WEEKLY",
        "隔週" to "BIWEEKLY",
        "毎月（日付）" to "MONTHLY_DATE",
        "毎月（曜日）" to "MONTHLY_WEEK",
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
                ) {
                    Text(label, fontSize = 12.sp)
                }
            }
        }
    }
}

// =============================================
// ロードマップステップ入力カード
// =============================================
@Composable
fun RoadmapStepCard(
    prompt: String,
    currentSteps: List<DraftRoadmapStep>,
    enabled: Boolean,
    onStepsSet: (List<DraftRoadmapStep>) -> Unit
) {
    var steps by remember { mutableStateOf(currentSteps.toMutableList()) }
    var newStepText by remember { mutableStateOf("") }

    AiCardWrapper {
        Text(prompt, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(8.dp))

        // 既存ステップ表示
        steps.forEachIndexed { index, step ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${index + 1}. ${step.title}",
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        steps = steps.toMutableList().apply { removeAt(index) }
                    },
                    modifier = Modifier.size(24.dp),
                    enabled = enabled
                ) {
                    Icon(Icons.Default.Close, contentDescription = "削除",
                        modifier = Modifier.size(16.dp))
                }
            }
        }

        // 新規追加
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 4.dp)
        ) {
            OutlinedTextField(
                value = newStepText,
                onValueChange = { newStepText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("ステップ名", fontSize = 12.sp) },
                singleLine = true,
                enabled = enabled,
                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
            )
            IconButton(
                onClick = {
                    if (newStepText.isNotBlank()) {
                        steps = steps.toMutableList().apply {
                            add(DraftRoadmapStep(
                                title = newStepText.trim(),
                                sortOrder = size
                            ))
                        }
                        newStepText = ""
                    }
                },
                enabled = enabled && newStepText.isNotBlank()
            ) {
                Icon(Icons.Default.Add, contentDescription = "追加")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        FilledTonalButton(
            onClick = { onStepsSet(steps) },
            enabled = enabled && steps.isNotEmpty()
        ) {
            Text("ステップ確定")
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
        Text("登録内容の確認", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        ConfirmRow("予定の種類", when {
            draft.isIndefinite -> "無期限"
            draft.scheduleType == ScheduleType.NORMAL -> "通常"
            draft.scheduleType == ScheduleType.PERIOD -> "期間"
            draft.scheduleType == ScheduleType.RECURRING -> "繰り返し"
            else -> draft.scheduleType.name
        })
        ConfirmRow("タスク名", draft.title)

        if (draft.memo.isNotEmpty()) {
            ConfirmRow("メモ", draft.memo)
        }
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

        ConfirmRow("通知", if (draft.notifyEnabled) {
            when (draft.notifyMinutesBefore) {
                0 -> "予定時刻"
                60 -> "1時間前"
                1440 -> "1日前"
                else -> "${draft.notifyMinutesBefore}分前"
            }
        } else "なし")

        ConfirmRow("ロードマップ", if (draft.roadmapEnabled)
            "${draft.roadmapSteps.size}ステップ" else "なし")

        if (draft.tagIds.isNotEmpty()) {
            val tagNames = allTags.filter { it.id in draft.tagIds }.joinToString(", ") { it.name }
            ConfirmRow("タグ", tagNames)
        }
        if (draft.relatedTaskIds.isNotEmpty()) {
            ConfirmRow("関連予定", "${draft.relatedTaskIds.size}件")
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        if (isActive) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("登録する")
                }
                OutlinedButton(
                    onClick = onModify,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("修正する")
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(
                onClick = onCancel,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("キャンセル", color = MaterialTheme.colorScheme.error)
            }
        } else {
            Text("（登録済み）", color = Color.Gray, fontSize = 12.sp)
        }
    }
}

@Composable
fun ConfirmRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(
            label,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            modifier = Modifier.width(90.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, fontSize = 13.sp)
    }
}

// =============================================
// 登録完了カード
// =============================================
@Composable
fun TaskRegisteredCard(title: String) {
    AiCardWrapper {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "「${title}」を登録しました",
                fontWeight = FontWeight.Medium,
                color = Color(0xFF4CAF50)
            )
        }
    }
}

// =============================================
// タイピングインジケーター
// =============================================
@Composable
fun ChatTypingIndicator() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                "考え中…",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                fontSize = 13.sp,
                color = Color.Gray
            )
        }
    }
}

// =============================================
// ヘルパー
// =============================================
private fun recurrenceDisplayLabel(pattern: String): String {
    return when (pattern) {
        "DAILY" -> "毎日"
        "WEEKLY" -> "毎週"
        "BIWEEKLY" -> "隔週"
        "MONTHLY_DATE" -> "毎月（日付）"
        "MONTHLY_WEEK" -> "毎月（曜日）"
        "YEARLY" -> "毎年"
        else -> pattern
    }
}
