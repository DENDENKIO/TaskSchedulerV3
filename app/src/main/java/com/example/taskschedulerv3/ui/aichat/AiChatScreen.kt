package com.example.taskschedulerv3.ui.aichat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
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
import com.example.taskschedulerv3.data.model.Tag

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(
    navController: NavController,
    viewModel: AiChatViewModel = viewModel()
) {
    val messages by viewModel.messages.collectAsState()
    val isTyping by viewModel.isTyping.collectAsState()
    val wizardStep by viewModel.wizardStep.collectAsState()
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
                        viewModel = viewModel,
                        isLastAiMessage = !msg.isUser && msg == messages.lastOrNull { !it.isUser }
                    )
                }
                if (isTyping) {
                    item {
                        Box(Modifier.padding(8.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }

            // 入力エリア
            Surface(tonalElevation = 2.dp) {
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
                        placeholder = { Text("メッセージを入力...") },
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
}

@Composable
fun ChatMessageItem(
    message: ChatMessage,
    viewModel: AiChatViewModel,
    isLastAiMessage: Boolean
) {
    val isUser = message.isUser
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        when (val content = message.content) {
            is ChatContent.Text -> {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.widthIn(max = 280.dp)
                ) {
                    Text(content.body, modifier = Modifier.padding(12.dp), fontSize = 14.sp)
                }
            }
            is ChatContent.TaskConfirmation -> {
                TaskConfirmationCard(
                    draft = content.draft,
                    isActive = content.isActive && isLastAiMessage,
                    onConfirm = { viewModel.confirmRegistration() },
                    onModify = { /* ユーザーに自然文入力を促す */ },
                    onCancel = { viewModel.cancelRegistration() }
                )
            }
            is ChatContent.PhotoPickerRequest -> {
                val launcher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.PickVisualMedia()
                ) { uri ->
                    if (uri != null) {
                        viewModel.onPhotoSelected(uri.toString())
                    }
                }
                
                AiCard(title = "写真を追加しますか？") {
                    Text(content.prompt, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { 
                            launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }) {
                            Text("ギャラリーから選択")
                        }
                        if (content.allowSkip) {
                            OutlinedButton(onClick = { viewModel.onPhotoSelected(null) }) {
                                Text("スキップ")
                            }
                        }
                    }
                }
            }
            is ChatContent.TaskRegistered -> {
                AiCard(title = "登録完了") {
                    Text("『${content.taskTitle}』を登録しました！", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun TaskConfirmationCard(
    draft: DraftTaskData,
    isActive: Boolean,
    onConfirm: () -> Unit,
    onModify: () -> Unit,
    onCancel: () -> Unit
) {
    AiCard(title = "登録内容の確認") {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("タイトル: ${draft.title}", fontWeight = FontWeight.SemiBold)
            Text("日付: ${draft.startDate}")
            if (!draft.startTime.isNullOrEmpty()) Text("時刻: ${draft.startTime}")
            if (!draft.location.isNullOrEmpty()) Text("場所: ${draft.location}")
            if (!draft.memo.isNullOrEmpty()) Text("メモ: ${draft.memo}")
            
            if (isActive) {
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, null)
                        Spacer(Modifier.width(4.dp))
                        Text("キャンセル")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onConfirm) {
                        Icon(Icons.Default.Check, null)
                        Spacer(Modifier.width(4.dp))
                        Text("これでOK")
                    }
                }
                Text("内容が違う場合は「場所は新宿にして」のように指示してください。", fontSize = 11.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
fun AiCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.widthIn(max = 300.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}
