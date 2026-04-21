package com.example.taskschedulerv3.ui.aichat

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar

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
                items(messages) { msg ->
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
                                WizardStep.INPUT_LOCATION -> "場所を入力…"
                                WizardStep.INPUT_MEMO -> "メモを入力…"
                                WizardStep.SELECT_DATE -> "日付（例: 明日、4月25日）"
                                WizardStep.SELECT_TIME -> "時刻（例: 7時半）"
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
                    Icon(Icons.AutoMirrored.Filled.Send, "送信", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatMessageItem(
    message: ChatMessage,
    wizardStep: WizardStep,
    allTags: List<Tag>,
    allTasks: List<Task>,
    viewModel: AiChatViewModel,
    isLatestAiMessage: Boolean
) {
    if (message.isUser) {
        ChatBubble(text = (message.content as? ChatContent.Text)?.body ?: "", isUser = true)
        return
    }

    when (val content = message.content) {
        is ChatContent.Text -> ChatBubble(text = content.body, isUser = false)
        is ChatContent.ChoiceButtons -> AiCardWrapper {
            Text(content.prompt, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                content.choices.forEach { choice ->
                    FilledTonalButton(
                        onClick = { viewModel.onChoiceSelected(wizardStep, choice.value) },
                        enabled = isLatestAiMessage
                    ) { Text(choice.label) }
                }
            }
        }
        is ChatContent.TextInput, is ChatContent.TextInputWithAi -> {
            val prompt = (content as? ChatContent.TextInput)?.prompt ?: (content as ChatContent.TextInputWithAi).prompt
            AiCardWrapper {
                Text(prompt, fontWeight = FontWeight.Medium)
                if (content is ChatContent.TextInputWithAi && isLatestAiMessage) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { viewModel.onChoiceSelected(wizardStep, "スキップ") }) { Text("スキップ") }
                }
            }
        }
        is ChatContent.DatePickerRequest -> DatePickerCard(
            prompt = content.prompt, enabled = isLatestAiMessage,
            onDateSelected = { viewModel.onDateSelected(it) }
        )
        is ChatContent.TimePickerRequest -> TimePickerCard(
            prompt = content.prompt, enabled = isLatestAiMessage,
            onTimeSelected = { s, e -> viewModel.onTimeSelected(s, e) },
            onSkip = { viewModel.onChoiceSelected(wizardStep, "スキップ") }
        )
        is ChatContent.TagPickerRequest -> TagPickerCard(
            prompt = content.prompt, tags = allTags, enabled = isLatestAiMessage,
            onTagsSelected = { viewModel.onTagsSelected(it) },
            onSkip = { viewModel.onTagsSelected(emptyList()) }
        )
        is ChatContent.RelationPickerRequest -> RelationPickerCard(
            prompt = content.prompt, tasks = allTasks, suggestedIds = content.suggestedTaskIds, enabled = isLatestAiMessage,
            onRelationsSelected = { viewModel.onRelationsSelected(it) },
            onSkip = { viewModel.onRelationsSelected(emptyList()) }
        )
        is ChatContent.PhotoPickerRequest -> PhotoPickerCard(
            prompt = content.prompt, enabled = isLatestAiMessage,
            onPhotoSelected = { viewModel.onPhotoSelected(it) },
            onSkip = { viewModel.onPhotoSelected(null) }
        )
        is ChatContent.TaskConfirmation -> TaskConfirmationCard(
            draft = content.draft, allTags = allTags, isActive = content.isActive && isLatestAiMessage,
            onConfirm = { viewModel.confirmRegistration() },
            onModify = { viewModel.goBackToModify() },
            onCancel = { viewModel.cancelRegistration() }
        )
        is ChatContent.TaskRegistered -> TaskRegisteredCard(title = content.taskTitle)
        is ChatContent.RecurrencePickerRequest -> RecurrencePickerCard(
            prompt = content.prompt, enabled = isLatestAiMessage,
            onSelected = { p, d, e -> viewModel.onRecurrenceSelected(p, d, e) }
        )
    }
}

@Composable
fun ChatBubble(text: String, isUser: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Text(text, modifier = Modifier.padding(12.dp), fontSize = 14.sp)
        }
    }
}

@Composable
fun AiCardWrapper(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.widthIn(max = 320.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp), content = content)
    }
}

@Composable
fun DatePickerCard(prompt: String, enabled: Boolean, onDateSelected: (String) -> Unit) {
    val context = LocalContext.current
    AiCardWrapper {
        Text(prompt, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                val cal = Calendar.getInstance()
                DatePickerDialog(context, { _, y, m, d ->
                    onDateSelected(String.format("%04d-%02d-%02d", y, m + 1, d))
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
            },
            enabled = enabled
        ) { Text("カレンダー") }
    }
}

@Composable
fun TimePickerCard(prompt: String, enabled: Boolean, onTimeSelected: (String, String) -> Unit, onSkip: () -> Unit) {
    val context = LocalContext.current
    var startTime by remember { mutableStateOf("") }
    AiCardWrapper {
        Text(prompt, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                TimePickerDialog(context, { _, h, m -> startTime = String.format("%02d:%02d", h, m) }, 9, 0, true).show()
            }, enabled = enabled) { Text(if (startTime.isEmpty()) "時刻" else startTime) }
            Button(onClick = { onTimeSelected(startTime, "") }, enabled = enabled && startTime.isNotEmpty()) { Text("決定") }
            OutlinedButton(onClick = onSkip, enabled = enabled) { Text("スキップ") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagPickerCard(prompt: String, tags: List<Tag>, enabled: Boolean, onTagsSelected: (List<Int>) -> Unit, onSkip: () -> Unit) {
    var selected by remember { mutableStateOf(setOf<Int>()) }
    AiCardWrapper {
        Text(prompt, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tags.forEach { tag ->
                FilterChip(selected = tag.id in selected, onClick = {
                    selected = if (tag.id in selected) selected - tag.id else selected + tag.id
                }, label = { Text(tag.name) }, enabled = enabled)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onTagsSelected(selected.toList()) }, enabled = enabled) { Text("決定") }
            OutlinedButton(onClick = onSkip, enabled = enabled) { Text("スキップ") }
        }
    }
}

@Composable
fun RelationPickerCard(prompt: String, tasks: List<Task>, suggestedIds: List<Int>, enabled: Boolean, onRelationsSelected: (List<Int>) -> Unit, onSkip: () -> Unit) {
    var selected by remember { mutableStateOf(suggestedIds.toSet()) }
    AiCardWrapper {
        Text(prompt, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        tasks.filter { it.id in suggestedIds }.forEach { task ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = task.id in selected, onCheckedChange = {
                    selected = if (task.id in selected) selected - task.id else selected + task.id
                }, enabled = enabled)
                Text(task.title, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onRelationsSelected(selected.toList()) }, enabled = enabled) { Text("決定") }
            OutlinedButton(onClick = onSkip, enabled = enabled) { Text("スキップ") }
        }
    }
}

@Composable
fun PhotoPickerCard(prompt: String, enabled: Boolean, onPhotoSelected: (String?) -> Unit, onSkip: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isProcessing by remember { mutableStateOf(false) }
    var photoPath by remember { mutableStateOf<String?>(null) }
    var tempUri by remember { mutableStateOf<Uri?>(null) }
    var tempFile by remember { mutableStateOf<File?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && tempFile != null) {
            isProcessing = true
            scope.launch {
                val saved = withContext(Dispatchers.IO) {
                    PhotoFileManager.saveResizedPhotoFromFile(context, tempFile!!)
                }
                photoPath = saved
                isProcessing = false
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            isProcessing = true
            scope.launch {
                val saved = withContext(Dispatchers.IO) {
                    PhotoFileManager.saveResizedPhoto(context, uri)
                }
                photoPath = saved
                isProcessing = false
            }
        }
    }
    
    AiCardWrapper {
        Text(prompt, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        
        if (photoPath != null) {
            Text("写真を選択済み", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
        }

        if (isProcessing) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { 
                    val res = PhotoFileManager.createTempPhotoUri(context)
                    tempUri = res.first
                    tempFile = res.second
                    cameraLauncher.launch(res.first)
                }, enabled = enabled) { 
                    Icon(Icons.Default.CameraAlt, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("カメラ") 
                }
                OutlinedButton(onClick = {
                    galleryLauncher.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }, enabled = enabled) {
                    Icon(Icons.Default.Photo, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("ギャラリー")
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (photoPath != null) {
                Button(onClick = { onPhotoSelected(photoPath) }, enabled = enabled && !isProcessing) {
                    Text("この写真で決定")
                }
            }
            OutlinedButton(onClick = onSkip, enabled = enabled && !isProcessing) { Text("スキップ") }
        }
    }
}

@Composable
fun RecurrencePickerCard(prompt: String, enabled: Boolean, onSelected: (String, String, String) -> Unit) {
    AiCardWrapper {
        Text(prompt, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Button(onClick = { onSelected("DAILY", "", "") }, enabled = enabled) { Text("毎日") }
    }
}

@Composable
fun TaskConfirmationCard(draft: DraftTaskData, allTags: List<Tag>, isActive: Boolean, onConfirm: () -> Unit, onModify: () -> Unit, onCancel: () -> Unit) {
    AiCardWrapper {
        Text("確認", fontWeight = FontWeight.Bold)
        Text("タイトル: ${draft.title}")
        if (draft.location.isNotEmpty()) Text("場所: ${draft.location}")
        Text("日付: ${draft.startDate}")
        if (draft.startTime.isNotEmpty()) Text("時刻: ${draft.startTime}")
        if (isActive) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onConfirm, modifier = Modifier.weight(1f)) { Text("登録") }
                OutlinedButton(onClick = onModify, modifier = Modifier.weight(1f)) { Text("修正") }
            }
            TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("キャンセル", color = Color.Red) }
        }
    }
}

@Composable
fun TaskRegisteredCard(title: String) {
    AiCardWrapper {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Check, null, tint = Color.Green)
            Text("「$title」を登録しました", color = Color.Green, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun ChatTypingIndicator() {
    Text("AIが考え中...", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(start = 8.dp))
}
