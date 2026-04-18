package com.example.taskschedulerv3.ui.addtask

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.taskschedulerv3.data.model.ScheduleType
import com.example.taskschedulerv3.ui.components.TagSelector
import com.example.taskschedulerv3.ui.photo.OcrResultDialog
import com.example.taskschedulerv3.ui.photo.TaskPhotoViewModel
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskBottomSheet(
    taskId: Int? = null,
    onDismiss: () -> Unit,
    vm: AddTaskViewModel = viewModel()
) {
    val title by vm.title.collectAsState()
    val description by vm.description.collectAsState()
    val startDate by vm.startDate.collectAsState()
    val startTime by vm.startTime.collectAsState()
    val priority by vm.priority.collectAsState()
    val isIndefinite by vm.isIndefinite.collectAsState()
    val selectedTagIds by vm.selectedTagIds.collectAsState()
    val allTags by vm.allTags.collectAsState()
    val saveSuccess by vm.saveSuccess.collectAsState()
    val parentTask by vm.parentTask.collectAsState() // ステップ5
    val allTasks by vm.allTasks.collectAsState()     // ステップ5
    val roadmapEnabled by vm.roadmapEnabled.collectAsState() // ステップ6
    val notifyEnabled by vm.notifyEnabled.collectAsState()
    val notifyMinutesBefore by vm.notifyMinutesBefore.collectAsState()

    // OCR ViewModel
    val photoVm: TaskPhotoViewModel = viewModel()
    val ocrResult by photoVm.ocrResult.collectAsState()
    val isOcrProcessing by photoVm.isProcessing.collectAsState()

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(taskId) {
        if (taskId != null) {
            vm.loadTask(taskId)
        } else {
            vm.resetState()
        }
    }

    LaunchedEffect(saveSuccess) {
        if (saveSuccess) onDismiss()
    }

    // DatePicker/TimePicker state
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showParentSelector by remember { mutableStateOf(false) } // ステップ5

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
                        vm.startDate.value = local.toString()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            }
        ) { DatePicker(state = datePickerState) }
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState()
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    vm.startTime.value = "%02d:%02d".format(timePickerState.hour, timePickerState.minute)
                    showTimePicker = false
                }) { Text("OK") }
            },
            text = { TimePicker(state = timePickerState) }
        )
    }

    // OCR Result Dialog
    if (ocrResult != null) {
        OcrResultDialog(
            text = ocrResult!!,
            onApplyToTitle = { vm.title.value = it },
            onApplyToDescription = { text, append -> 
                if (append) vm.description.value += "\n$text"
                else vm.description.value = text
            },
            onDismiss = { photoVm.clearOcrResult() }
        )
    }

    // 親予定選択ダイアログ (ステップ5)
    if (showParentSelector) {
        AlertDialog(
            onDismissRequest = { showParentSelector = false },
            title = { Text("親予定を選択") },
            text = {
                val availableTasks = allTasks.filter { it.id != taskId && !it.isCompleted }
                if (availableTasks.isEmpty()) {
                    Text("選択可能な予定がありません")
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                        item {
                            ListItem(
                                headlineContent = { Text("指定なし") },
                                modifier = Modifier.clickable {
                                    vm.parentTaskId.value = null
                                    showParentSelector = false
                                }
                            )
                        }
                        items(availableTasks) { t ->
                            ListItem(
                                headlineContent = { Text(t.title) },
                                supportingContent = { Text(t.startDate) },
                                modifier = Modifier.clickable {
                                    vm.parentTaskId.value = t.id
                                    showParentSelector = false
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showParentSelector = false }) { Text("閉じる") }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        // ─── 右上保存ボタン ───
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "閉じる")
            }
            Text(
                if (taskId == null) "新規タスク" else "タスクを編集",
                style = MaterialTheme.typography.titleMedium
            )
            FilledTonalButton(
                onClick = {
                    if (!isIndefinite && vm.startDate.value.isEmpty()) {
                        vm.startDate.value = LocalDate.now().toString()
                    }
                    vm.save(taskId)
                },
                enabled = title.isNotBlank()
            ) {
                Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (taskId == null) "保存" else "更新")
            }
        }
        HorizontalDivider()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            OutlinedTextField(
                value = title,
                onValueChange = { vm.title.value = it },
                label = { Text("タスク名") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = description,
                onValueChange = { vm.description.value = it },
                label = { Text("メモ") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )

            // 無期限トグル
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text("無期限（期限を指定しない）", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = isIndefinite,
                    onCheckedChange = { vm.isIndefinite.value = it }
                )
            }

            if (!isIndefinite) {
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

                // 通知設定セクション
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("通知を有効にする", style = MaterialTheme.typography.bodyLarge)
                        Switch(checked = notifyEnabled, onCheckedChange = { vm.notifyEnabled.value = it })
                    }

                    if (notifyEnabled) {
                        val presets = listOf(
                            0 to "ちょうど",
                            10 to "10分前",
                            60 to "1時間前",
                            1440 to "1日前"
                        )
                        // 状態を直接計算して一貫性を保つ
                        val isPreset = presets.any { it.first == notifyMinutesBefore }
                        var forceCustom by remember { mutableStateOf(false) }
                        val currentlyCustom = forceCustom || !isPreset

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            presets.forEach { (mins, label) ->
                                FilterChip(
                                    selected = !currentlyCustom && notifyMinutesBefore == mins,
                                    onClick = {
                                        vm.notifyMinutesBefore.value = mins
                                        forceCustom = false
                                    },
                                    label = { Text(label, fontSize = 11.sp) }
                                )
                            }
                            FilterChip(
                                selected = currentlyCustom,
                                onClick = { forceCustom = true },
                                label = { Text("カスタム", fontSize = 11.sp) }
                            )
                        }

                        if (currentlyCustom) {
                            OutlinedTextField(
                                value = if (notifyMinutesBefore == 0 && forceCustom) "" else notifyMinutesBefore.toString(),
                                onValueChange = {
                                    val newVal = it.toIntOrNull() ?: 0
                                    vm.notifyMinutesBefore.value = newVal
                                },
                                label = { Text("通知タイミング（分前）") },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                                )
                            )
                        }
                    }
                }
            }



            // ロードマップトグル (ステップ6)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text("ロードマップ機能を有効にする", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = roadmapEnabled,
                    onCheckedChange = { vm.roadmapEnabled.value = it }
                )
            }

            // 親予定選択部 (ステップ5)
            Column {
                Text("親予定", style = MaterialTheme.typography.labelLarge)
                OutlinedButton(
                    onClick = { showParentSelector = true },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(12.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(20.dp).rotate(if (showParentSelector) 90f else 0f))
                    Spacer(Modifier.width(8.dp))
                    Text(parentTask?.title ?: "指定なし")
                }
            }

            TagSelector(
                allTags = allTags,
                selectedTagIds = selectedTagIds,
                onTagsChanged = { vm.selectedTagIds.value = it },
                onNavigateToTagManage = { /* 画面遷移はMainActivity等でハンドリング */ }
            )

            if (isOcrProcessing) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.width(8.dp))
                    Text("読み取り中...")
                }
            }

            AddTaskPhotoSection(
                viewModel = vm,
                onOcrRequested = { uri -> photoVm.processImageForOcr(uri) }
            )

            Spacer(Modifier.height(8.dp))
        } // Column
    } // ModalBottomSheet
}
