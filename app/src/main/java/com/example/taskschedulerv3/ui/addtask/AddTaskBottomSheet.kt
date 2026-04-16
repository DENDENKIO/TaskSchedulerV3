package com.example.taskschedulerv3.ui.addtask

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.taskschedulerv3.data.model.ScheduleType
import com.example.taskschedulerv3.ui.components.TagSelector
import com.example.taskschedulerv3.ui.photo.OcrResultDialog
import com.example.taskschedulerv3.ui.photo.TaskPhotoViewModel
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
    ocrResult?.let { text ->
        OcrResultDialog(
            text = text,
            onApplyToTitle = { vm.applyOcrToTitle(it) },
            onApplyToDescription = { newText, isAppend -> vm.applyOcrToDescription(newText, isAppend) },
            onDismiss = { photoVm.clearOcrResult() }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                if (taskId == null) "新規タスク" else "タスクを編集",
                style = MaterialTheme.typography.headlineSmall
            )

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
                        Text(startDate.ifEmpty { "日付" })
                    }
                    OutlinedButton(
                        onClick = { showTimePicker = true },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(12.dp)
                    ) {
                        Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(startTime.ifEmpty { "時刻" })
                    }
                }
            }

            Column {
                Text("優先度", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to "高", 1 to "中", 2 to "低").forEach { (v, label) ->
                        FilterChip(
                            selected = priority == v,
                            onClick = { vm.priority.value = v },
                            label = { Text(label) }
                        )
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

            Button(
                onClick = { 
                    if (!isIndefinite && vm.startDate.value.isEmpty()) {
                        vm.startDate.value = LocalDate.now().toString()
                    }
                    vm.save(taskId) 
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = title.isNotBlank()
            ) {
                Text(if (taskId == null) "タスクを保存" else "変更を保存")
            }
        }
    }
}
