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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.example.taskschedulerv3.data.model.ScheduleType
import com.example.taskschedulerv3.ui.components.TagSelector
import com.example.taskschedulerv3.ui.photo.OcrResultDialog
import com.example.taskschedulerv3.ui.photo.TaskPhotoViewModel
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import java.time.LocalDate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import com.example.taskschedulerv3.data.model.RecurrencePattern
import androidx.compose.foundation.background

import androidx.compose.material3.SheetValue
import androidx.compose.material3.ExperimentalMaterial3Api

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddTaskBottomSheet(
    taskId: Int? = null,
    onDismiss: () -> Unit,
    vm: AddTaskViewModel = viewModel()
) {
    var showCloseConfirmation by remember { mutableStateOf(false) }
    var sheetHeight by remember { mutableFloatStateOf(0f) }

    val title by vm.title.collectAsState()
    val description by vm.description.collectAsState()
    val startDate by vm.startDate.collectAsState()
    val startTime by vm.startTime.collectAsState()
    val isIndefinite by vm.isIndefinite.collectAsState()
    val selectedTagIds by vm.selectedTagIds.collectAsState()
    val allTags by vm.allTags.collectAsState()
    val saveSuccess by vm.saveSuccess.collectAsState()
    val roadmapEnabled by vm.roadmapEnabled.collectAsState()
    val notifyEnabled by vm.notifyEnabled.collectAsState()
    val notifyMinutesBefore by vm.notifyMinutesBefore.collectAsState()
    val relatedTasks by vm.relatedTasks.collectAsState()
    val relatedTaskIds by vm.relatedTaskIds.collectAsState()

    // 繰り返し・期間用追加
    val scheduleType by vm.scheduleType.collectAsState()
    val recurrencePattern by vm.recurrencePattern.collectAsState()
    val recurrenceDays by vm.recurrenceDays.collectAsState()
    val endDate by vm.endDate.collectAsState()
    val recurrenceEndDate by vm.recurrenceEndDate.collectAsState()

    // OCR ViewModel
    val photoVm: TaskPhotoViewModel = viewModel()
    val ocrResult by photoVm.ocrResult.collectAsState()
    val isOcrProcessing by photoVm.isProcessing.collectAsState()

    // ==========================================
    // 修正：シートを安全に閉じるための仕組み（アニメーションを待ってから消す）
    // ==========================================
    val scope = rememberCoroutineScope()
    var forceClose by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { newValue ->
            if (newValue == SheetValue.Hidden && !forceClose) {
                // スワイプや外側タップで閉じようとしたら一旦ブロックして確認ダイアログを出す
                showCloseConfirmation = true
                false 
            } else {
                true
            }
        }
    )

    // アニメーションを完了させてから安全に破棄する関数
    fun closeSheetSafely() {
        scope.launch {
            forceClose = true
            try {
                sheetState.hide()
            } catch (e: Exception) {
                // ignore
            } finally {
                onDismiss()
            }
        }
    }

    LaunchedEffect(taskId) {
        if (taskId != null) {
            vm.loadTask(taskId)
        } else {
            vm.resetState()
        }
    }

    LaunchedEffect(saveSuccess) {
        if (saveSuccess) {
            closeSheetSafely()
        }
    }

    // 破棄確認ダイアログ
    if (showCloseConfirmation) {
        AlertDialog(
            onDismissRequest = { 
                showCloseConfirmation = false 
                scope.launch { sheetState.show() } // 状態リセット
            },
            title = { Text("内容の破棄") },
            text = { Text("入力中の内容は保存されません。破棄して閉じますか？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCloseConfirmation = false
                        closeSheetSafely()
                    }
                ) { Text("破棄", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showCloseConfirmation = false 
                    scope.launch { sheetState.show() } // 状態リセット
                }) { Text("キャンセル") }
            }
        )
    }

    // DatePicker/TimePicker state
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showRelatedSelector by remember { mutableStateOf(false) }

    // EndDate Picker (Period or Recurrence End)
    var showEndDatePicker by remember { mutableStateOf(false) }
    var isPickingRecurrenceEnd by remember { mutableStateOf(false) }

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

    if (showEndDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val local = java.time.Instant.ofEpochMilli(millis)
                            .atZone(java.time.ZoneId.of("UTC")).toLocalDate()
                        if (isPickingRecurrenceEnd) {
                            vm.recurrenceEndDate.value = local.toString()
                        } else {
                            vm.endDate.value = local.toString()
                        }
                    }
                    showEndDatePicker = false
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

    // 関連予定選択ダイアログ
    if (showRelatedSelector) {
        AlertDialog(
            onDismissRequest = { showRelatedSelector = false },
            title = { Text("関連予定を選択") },
            text = {
                val availableTasks = vm.allTasks.collectAsState().value.filter { it.id != taskId && !it.isCompleted }
                if (availableTasks.isEmpty()) {
                    Text("選択可能な予定がありません")
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                        items(availableTasks) { t ->
                            val isSelected = t.id in relatedTaskIds
                            ListItem(
                                headlineContent = { Text(t.title) },
                                supportingContent = { Text(t.startDate) },
                                trailingContent = {
                                    if (isSelected) {
                                        Icon(Icons.Default.Done, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                },
                                modifier = Modifier.clickable {
                                    if (isSelected) vm.removeRelatedTask(t.id)
                                    else vm.addRelatedTask(t.id)
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showRelatedSelector = false }) { Text("完了") }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = {
            showCloseConfirmation = true
        },
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coords ->
                    sheetHeight = coords.size.height.toFloat()
                }
        ) {
            Column {
                // ─── 右上保存ボタン ───
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { showCloseConfirmation = true }) {
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

            // スケジュールタイプ選択 (通常/繰り返し/期間)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("予定の種類", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ScheduleType.values().filter { it != ScheduleType.ROADMAP }.forEach { type ->
                        val label = when(type) {
                            ScheduleType.NORMAL -> "通常"
                            ScheduleType.RECURRING -> "繰り返し"
                            ScheduleType.PERIOD -> "期間"
                            else -> "" // ROADMAP is filtered out
                        }
                        FilterChip(
                            selected = scheduleType == type,
                            onClick = { 
                                vm.scheduleType.value = type
                                if (type == ScheduleType.RECURRING && recurrencePattern == null) {
                                    vm.recurrencePattern.value = RecurrencePattern.DAILY
                                }
                            },
                            label = { 
                                Text(
                                    label, 
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                ) 
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            if (scheduleType == ScheduleType.RECURRING) {
                // 繰り返し設定セクション
                Column(
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp)).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("繰り返し設定", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    
                    // パターン選択
                    val patterns: List<Pair<RecurrencePattern, String>> = listOf(
                        RecurrencePattern.DAILY to "毎日",
                        RecurrencePattern.EVERY_N_DAYS to "N日ごと",
                        RecurrencePattern.WEEKLY_MULTI to "曜日指定",
                        RecurrencePattern.MONTHLY_DATES to "日付指定"
                    )
                    
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        patterns.forEach { (p, label) ->
                            FilterChip(
                                selected = recurrencePattern == p,
                                onClick = { vm.recurrencePattern.value = p },
                                label = { Text(label) }
                            )
                        }
                    }

                    when (recurrencePattern) {
                        RecurrencePattern.EVERY_N_DAYS -> {
                            OutlinedTextField(
                                value = recurrenceDays,
                                onValueChange = { vm.recurrenceDays.value = it },
                                label = { Text("間隔（例: 3, 5）") },
                                supportingText = { Text("数字をカンマ区切りで入力してください") },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                            )
                        }
                        RecurrencePattern.WEEKLY_MULTI -> {
                            val weekDays = listOf("月", "火", "水", "木", "金", "土", "日")
                            val selectedDays = recurrenceDays.split(",").filter { it.isNotBlank() }.toSet()
                            
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                maxItemsInEachRow = 4
                            ) {
                                weekDays.forEachIndexed { index, day ->
                                    val dayId = (index + 1).toString()
                                    FilterChip(
                                        selected = dayId in selectedDays,
                                        onClick = { vm.toggleWeeklyDay(index + 1) },
                                        label = { Text(day) }
                                    )
                                }
                            }
                        }
                        RecurrencePattern.MONTHLY_DATES -> {
                            val selectedDates = recurrenceDays.split(",").filter { it.isNotBlank() }.toSet()
                            Text("日付を選択（複数可）", style = MaterialTheme.typography.labelSmall)
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                maxItemsInEachRow = 7
                            ) {
                                (1..31).forEach { date ->
                                    val dateId = date.toString()
                                    FilterChip(
                                        selected = dateId in selectedDates,
                                        onClick = { vm.toggleMonthlyDate(date) },
                                        label = { Text(dateId, fontSize = 10.sp) },
                                        modifier = Modifier.size(width = 38.dp, height = 32.dp)
                                    )
                                }
                            }
                        }
                        else -> { /* DAILY etc */ }
                    }

                    // 終了期限（任意）
                    OutlinedButton(
                        onClick = { 
                            isPickingRecurrenceEnd = true
                            showEndDatePicker = true 
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (recurrenceEndDate.isEmpty()) "終了期限を設定（任意）" else "終了期限: $recurrenceEndDate")
                    }
                }
            } else if (scheduleType == ScheduleType.PERIOD) {
                // 期間予定用終了日
                OutlinedButton(
                    onClick = { 
                        isPickingRecurrenceEnd = false
                        showEndDatePicker = true 
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (endDate.isEmpty()) "終了日を選択" else "終了日: $endDate")
                }
            }

            // 無期限トグル (通常予定または期間予定のときのみ表示。繰り返しには馴染まない)
            if (scheduleType != ScheduleType.RECURRING) {
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

            // 関連予定セクション
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("関連予定", style = MaterialTheme.typography.labelLarge)
                    TextButton(onClick = { showRelatedSelector = true }) {
                        Text("追加 / 編集")
                    }
                }
                
                if (relatedTasks.isEmpty()) {
                    Text("関連する予定はありません", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        relatedTasks.forEach { t ->
                            AssistChip(
                                onClick = { /* Navigate to detail? Maybe not helpful here */ },
                                label = { Text(t.title, maxLines = 1) },
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "削除",
                                        modifier = Modifier.size(16.dp).clickable { vm.removeRelatedTask(t.id) }
                                    )
                                }
                            )
                        }
                    }
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
        } // inner Column
    } // outer Column
} // Box
} // ModalBottomSheet
}
