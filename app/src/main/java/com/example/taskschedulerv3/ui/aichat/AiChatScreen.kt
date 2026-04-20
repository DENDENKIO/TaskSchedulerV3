package com.example.taskschedulerv3.ui.aichat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.taskschedulerv3.data.model.Tag
import com.example.taskschedulerv3.ui.schedulelist.ScheduleListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(
    navController: NavController,
    vm: AiChatViewModel = viewModel()
) {
    val messages by vm.messages.collectAsState()
    val isTyping by vm.isTyping.collectAsState()
    val pendingDraft by vm.pendingDraft.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, isTyping) {
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .navigationBarsPadding()
                        .imePadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("なんでも聞いてください...") },
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 3,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (inputText.isNotBlank() && !isTyping) {
                                    vm.sendMessage(inputText)
                                    inputText = ""
                                }
                            }
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilledIconButton(
                        onClick = {
                            if (inputText.isNotBlank() && !isTyping) {
                                vm.sendMessage(inputText)
                                inputText = ""
                            }
                        },
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape,
                        enabled = !isTyping
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "送信")
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(messages) { msg ->
                when (msg.content) {
                    is ChatContent.TaskConfirmation -> {
                        // 最新の確認カードのみインタラクティブにする
                        val isLatestConfirmation = msg.id == messages.lastOrNull {
                            it.content is ChatContent.TaskConfirmation
                        }?.id
                        TaskConfirmationCard(
                            draft = if (isLatestConfirmation && pendingDraft != null) {
                                pendingDraft!!
                            } else {
                                (msg.content as ChatContent.TaskConfirmation).draft
                            },
                            isActive = isLatestConfirmation && pendingDraft != null,
                            onConfirm = { vm.confirmRegistration() },
                            onCancel = { vm.cancelDraft() },
                            onUpdateDraft = { vm.updateDraft(it) }
                        )
                    }
                    is ChatContent.TaskRegistered -> {
                        TaskRegisteredCard(
                            title = (msg.content as ChatContent.TaskRegistered).title
                        )
                    }
                    else -> {
                        ChatBubble(message = msg)
                    }
                }
            }
            if (isTyping) {
                item { ChatTypingIndicator() }
            }
        }
    }
}

// ── 通常のチャットバブル ──

@Composable
fun ChatBubble(message: ChatMessage) {
    val alignment = if (message.isUser) Alignment.CenterEnd else Alignment.CenterStart
    val bgColor = if (message.isUser) MaterialTheme.colorScheme.primaryContainer
                  else MaterialTheme.colorScheme.secondaryContainer
    val textColor = if (message.isUser) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSecondaryContainer
    val shape = if (message.isUser) {
        RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp)
    } else {
        RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp)
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Surface(
            shape = shape,
            color = bgColor,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Text(
                text = message.text,
                color = textColor,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

// ── 確認カード ──

@Composable
fun TaskConfirmationCard(
    draft: DraftTaskData,
    isActive: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onUpdateDraft: (DraftTaskData) -> Unit
) {
    val listVm: ScheduleListViewModel = viewModel()
    val allTags by listVm.allTags.collectAsState()
    var showTagDialog by remember { mutableStateOf(false) }
    var showNotifyDialog by remember { mutableStateOf(false) }
    var showPriorityDialog by remember { mutableStateOf(false) }
    var showMemoDialog by remember { mutableStateOf(false) }

    // タグ選択ダイアログ
    if (showTagDialog && isActive) {
        TagSelectionDialog(
            allTags = allTags,
            selectedTagIds = draft.tagIds,
            onDismiss = { showTagDialog = false },
            onConfirm = { newIds ->
                onUpdateDraft(draft.copy(tagIds = newIds))
                showTagDialog = false
            }
        )
    }

    // 通知設定ダイアログ
    if (showNotifyDialog && isActive) {
        NotifySettingDialog(
            enabled = draft.notifyEnabled,
            minutes = draft.notifyMinutesBefore,
            onDismiss = { showNotifyDialog = false },
            onConfirm = { enabled, minutes ->
                onUpdateDraft(draft.copy(notifyEnabled = enabled, notifyMinutesBefore = minutes))
                showNotifyDialog = false
            }
        )
    }

    // 優先度選択ダイアログ
    if (showPriorityDialog && isActive) {
        PriorityDialog(
            currentPriority = draft.priority,
            onDismiss = { showPriorityDialog = false },
            onSelect = { priority ->
                onUpdateDraft(draft.copy(priority = priority))
                showPriorityDialog = false
            }
        )
    }

    // メモ入力ダイアログ
    if (showMemoDialog && isActive) {
        MemoInputDialog(
            currentMemo = draft.description ?: "",
            onDismiss = { showMemoDialog = false },
            onConfirm = { memo ->
                onUpdateDraft(draft.copy(description = memo.ifBlank { null }))
                showMemoDialog = false
            }
        )
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart
    ) {
        Card(
            modifier = Modifier.widthIn(max = 320.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // ヘッダー
                Text(
                    "登録内容の確認",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                // 各項目
                val typeLabel = when {
                    draft.isIndefinite -> "無期限"
                    draft.scheduleType == com.example.taskschedulerv3.data.model.ScheduleType.PERIOD -> "期間"
                    draft.scheduleType == com.example.taskschedulerv3.data.model.ScheduleType.RECURRING -> "繰り返し"
                    else -> "通常"
                }
                ConfirmRow("予定の種類", typeLabel)
                ConfirmRow("タスク名", draft.title)

                if (!draft.isIndefinite) {
                    ConfirmRow("日付", draft.startDate)
                    if (draft.endDate != null) ConfirmRow("終了日", draft.endDate)
                }

                if (draft.startTime != null) ConfirmRow("開始時刻", draft.startTime)
                if (draft.endTime != null) ConfirmRow("終了時刻", draft.endTime)
                if (draft.description != null) ConfirmRow("メモ", draft.description)

                val notifyLabel = if (draft.notifyEnabled) "${draft.notifyMinutesBefore}分前" else "なし"
                ConfirmRow("通知", notifyLabel)

                val priorityLabel = when (draft.priority) { 0 -> "高"; 2 -> "低"; else -> "中" }
                ConfirmRow("優先度", priorityLabel)

                ConfirmRow("ロードマップ", if (draft.roadmapEnabled) "あり" else "なし")

                // タグ表示
                if (draft.tagIds.isNotEmpty()) {
                    val tagNames = allTags.filter { it.id in draft.tagIds }.map { it.name }
                    if (tagNames.isNotEmpty()) {
                        ConfirmRow("タグ", tagNames.joinToString(", "))
                    }
                }

                // オプション追加ボタン
                if (isActive) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        item {
                            AssistChip(
                                onClick = { showTagDialog = true },
                                label = { Text("タグ", fontSize = 12.sp) },
                                leadingIcon = { Icon(Icons.Default.Add, null, Modifier.size(14.dp)) }
                            )
                        }
                        item {
                            AssistChip(
                                onClick = { showMemoDialog = true },
                                label = { Text("メモ", fontSize = 12.sp) },
                                leadingIcon = { Icon(Icons.Default.Edit, null, Modifier.size(14.dp)) }
                            )
                        }
                        item {
                            AssistChip(
                                onClick = { showNotifyDialog = true },
                                label = { Text("通知", fontSize = 12.sp) },
                                leadingIcon = { Icon(Icons.Default.Edit, null, Modifier.size(14.dp)) }
                            )
                        }
                        item {
                            AssistChip(
                                onClick = { showPriorityDialog = true },
                                label = { Text("優先度", fontSize = 12.sp) },
                                leadingIcon = { Icon(Icons.Default.Edit, null, Modifier.size(14.dp)) }
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    // アクションボタン
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Close, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("キャンセル", fontSize = 13.sp)
                        }
                        Button(
                            onClick = onConfirm,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("登録する", fontSize = 13.sp)
                        }
                    }

                    Text(
                        "修正はチャットで伝えてください（例:「時間を15時に変えて」）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfirmRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(80.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

// ── 登録完了カード ──

@Composable
fun TaskRegisteredCard(title: String) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart
    ) {
        Card(
            modifier = Modifier.widthIn(max = 300.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Column {
                    Text(
                        "登録完了",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

// ── ダイアログ群 ──

@Composable
fun TagSelectionDialog(
    allTags: List<Tag>,
    selectedTagIds: List<Int>,
    onDismiss: () -> Unit,
    onConfirm: (List<Int>) -> Unit
) {
    var currentSelection by remember { mutableStateOf(selectedTagIds.toSet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("タグを選択") },
        text = {
            if (allTags.isEmpty()) {
                Text("タグがまだ登録されていません。設定画面のタグ管理から作成できます。")
            } else {
                Column {
                    allTags.forEach { tag ->
                        val isSelected = tag.id in currentSelection
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    currentSelection = if (isSelected) {
                                        currentSelection - tag.id
                                    } else {
                                        currentSelection + tag.id
                                    }
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = {
                                    currentSelection = if (it) {
                                        currentSelection + tag.id
                                    } else {
                                        currentSelection - tag.id
                                    }
                                }
                            )
                            val tagColor = runCatching {
                                Color(android.graphics.Color.parseColor(tag.color))
                            }.getOrElse { MaterialTheme.colorScheme.primary }
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(tagColor, CircleShape)
                            )
                            Text(tag.name)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(currentSelection.toList()) }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        }
    )
}

@Composable
fun NotifySettingDialog(
    enabled: Boolean,
    minutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Boolean, Int) -> Unit
) {
    var isEnabled by remember { mutableStateOf(enabled) }
    var selectedMinutes by remember { mutableStateOf(minutes) }

    val options = listOf(0 to "予定時刻", 5 to "5分前", 10 to "10分前", 15 to "15分前",
                         30 to "30分前", 60 to "1時間前", 120 to "2時間前", 1440 to "1日前")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("通知設定") },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("通知を有効にする")
                    Switch(checked = isEnabled, onCheckedChange = { isEnabled = it })
                }
                if (isEnabled) {
                    Spacer(Modifier.height(12.dp))
                    Text("通知タイミング", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(8.dp))
                    options.forEach { (min, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedMinutes = min }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            RadioButton(
                                selected = selectedMinutes == min,
                                onClick = { selectedMinutes = min }
                            )
                            Text(label)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(isEnabled, selectedMinutes) }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        }
    )
}

@Composable
fun PriorityDialog(
    currentPriority: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("優先度") },
        text = {
            Column {
                listOf(0 to "高", 1 to "中", 2 to "低").forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(value) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        RadioButton(
                            selected = currentPriority == value,
                            onClick = { onSelect(value) }
                        )
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("閉じる") }
        }
    )
}

@Composable
fun MemoInputDialog(
    currentMemo: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(currentMemo) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("メモ") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp),
                placeholder = { Text("メモを入力...") },
                maxLines = 6
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        }
    )
}

// ── タイピングインジケーター ──

@Composable
fun ChatTypingIndicator() {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp),
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Text(
                text = "AIが考え中...",
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
