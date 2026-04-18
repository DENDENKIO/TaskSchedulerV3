package com.example.taskschedulerv3.ui.quickdraft

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.taskschedulerv3.data.model.QuickDraftTask
import com.example.taskschedulerv3.data.model.Tag
import com.example.taskschedulerv3.ui.schedulelist.ScheduleListViewModel
import com.example.taskschedulerv3.util.PhotoFileManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickDraftEditScreen(
    navController: NavController,
    draftId: Int,
    vm: QuickDraftViewModel = viewModel()
) {
    var draft by remember { mutableStateOf<QuickDraftTask?>(null) }
    var title by remember { mutableStateOf("") }
    var memo by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf("") }
    var startTime by remember { mutableStateOf("") }
    var selectedTagIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showConvertDialog by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val listVm: ScheduleListViewModel = viewModel()
    val allTags by listVm.allTags.collectAsState()

    val convertSuccess by vm.convertSuccess.collectAsState()

    // ドラフト読み込み
    LaunchedEffect(draftId) {
        val loaded = vm.repo.getById(draftId)
        draft = loaded
        loaded?.let {
            title = it.title
            memo = it.description ?: ""
            selectedTagIds = it.tagIds?.split(",")?.filter { s -> s.isNotBlank() }?.map { s -> s.toInt() }?.toSet() ?: emptySet()
        }
    }

    // 本登録後に前画面へ戻る
    LaunchedEffect(convertSuccess) {
        if (convertSuccess) {
            vm.clearConvertSuccess()
            navController.popBackStack()
        }
    }

    // 削除確認ダイアログ
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("仮登録を削除") },
            text = { Text("この仮登録を削除しますか？") },
            confirmButton = {
                TextButton(onClick = {
                    draft?.let { vm.deleteDraft(it) }
                    showDeleteDialog = false
                    navController.popBackStack()
                }) { Text("削除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("キャンセル") }
            }
        )
    }

    // 本登録確認ダイアログ
    if (showConvertDialog) {
        AlertDialog(
            onDismissRequest = { showConvertDialog = false },
            title = { Text("本登録") },
            text = { Text("「$title」を通常タスクとして登録しますか？") },
            confirmButton = {
                TextButton(onClick = {
                    // まず保存してから変換
                    draft?.let {
                        val updatedTags = if (selectedTagIds.isEmpty()) null else selectedTagIds.joinToString(",")
                        vm.updateDraft(it.copy(title = title, description = memo.ifBlank { null }, tagIds = updatedTags))
                        vm.convertToTask(it.copy(title = title, description = memo.ifBlank { null }, tagIds = updatedTags), startDate.ifBlank { java.time.LocalDate.now().toString() }, 1)
                    }
                    showConvertDialog = false
                }) { Text("本登録する") }
            },
            dismissButton = {
                TextButton(onClick = { showConvertDialog = false }) { Text("キャンセル") }
            }
        )
    }

    // 日付選択ダイアログ
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val local = java.time.Instant.ofEpochMilli(millis)
                            .atZone(java.time.ZoneId.of("UTC")).toLocalDate()
                        startDate = local.toString()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("キャンセル") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // 時刻選択ダイアログ
    if (showTimePicker) {
        val timePickerState = rememberTimePickerState()
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    startTime = "%02d:%02d".format(timePickerState.hour, timePickerState.minute)
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("キャンセル") }
            },
            text = {
                TimePicker(state = timePickerState)
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("仮登録を編集") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                },
                actions = {
                    // 保存ボタン
                    IconButton(onClick = {
                        draft?.let {
                            val updatedTags = if (selectedTagIds.isEmpty()) null else selectedTagIds.joinToString(",")
                            vm.updateDraft(it.copy(title = title, description = memo.ifBlank { null }, tagIds = updatedTags))
                            navController.popBackStack()
                        }
                    }, enabled = title.isNotBlank()) {
                        Icon(Icons.Default.Done, contentDescription = "保存")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 写真サムネイル表示
            draft?.photoPath?.let { path ->
                AsyncImage(
                    model = PhotoFileManager.pathToUri(path),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("タスク名") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = memo,
                onValueChange = { memo = it },
                label = { Text("メモ") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )

            // 日時選択（ツールを使用）
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(12.dp)
                ) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(startDate.ifEmpty { "日付を選択" })
                }
                
                OutlinedButton(
                    onClick = { showTimePicker = true },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(12.dp)
                ) {
                    Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(startTime.ifEmpty { "時刻を選択" })
                }
            }


            // タグ選択
            if (allTags.isNotEmpty()) {
                Column {
                    Text("タグ", style = MaterialTheme.typography.labelLarge)
                    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(allTags.size) { index ->
                            val tag = allTags[index]
                            val isSelected = tag.id in selectedTagIds
                            val tagColor = runCatching {
                                androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(tag.color))
                            }.getOrElse { MaterialTheme.colorScheme.primary }

                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedTagIds = if (isSelected) selectedTagIds - tag.id else selectedTagIds + tag.id
                                },
                                label = { Text(tag.name) },
                                leadingIcon = if (isSelected) {
                                    { Icon(Icons.Default.Done, null, modifier = Modifier.size(16.dp)) }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = tagColor.copy(alpha = 0.2f),
                                    selectedLabelColor = tagColor,
                                    selectedLeadingIconColor = tagColor
                                )
                            )
                        }
                    }
                }
            }

            // OCR結果が存在する場合は表示
            draft?.ocrText?.let { ocr ->
                if (ocr.isNotBlank()) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("OCR読み取り結果", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary)
                            Text(ocr, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            HorizontalDivider()

            // アクションボタン行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("削除")
                }
                Button(
                    onClick = { showConvertDialog = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C6AFF))
                ) {
                    Icon(Icons.Default.Upload, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("本登録")
                }
            }
        }
    }
}
