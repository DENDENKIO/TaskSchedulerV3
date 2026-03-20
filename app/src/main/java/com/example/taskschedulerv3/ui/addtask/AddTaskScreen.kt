package com.example.taskschedulerv3.ui.addtask

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.taskschedulerv3.data.model.RecurrencePattern
import com.example.taskschedulerv3.data.model.ScheduleType
import com.example.taskschedulerv3.navigation.Screen
import com.example.taskschedulerv3.ui.components.TagSelector

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskScreen(
    navController: NavController,
    initialDate: String = "",
    editTaskId: Int? = null,
    vm: AddTaskViewModel = viewModel()
) {
    val saveSuccess by vm.saveSuccess.collectAsState()

    LaunchedEffect(Unit) {
        if (initialDate.isNotEmpty()) vm.startDate.value = initialDate
        editTaskId?.let { vm.loadTask(it) }
    }
    LaunchedEffect(saveSuccess) {
        if (saveSuccess) navController.popBackStack()
    }

    val title by vm.title.collectAsState()
    val description by vm.description.collectAsState()
    val startDate by vm.startDate.collectAsState()
    val endDate by vm.endDate.collectAsState()
    val startTime by vm.startTime.collectAsState()
    val endTime by vm.endTime.collectAsState()
    val scheduleType by vm.scheduleType.collectAsState()
    val priority by vm.priority.collectAsState()
    val notifyEnabled by vm.notifyEnabled.collectAsState()
    val notifyMinutesBefore by vm.notifyMinutesBefore.collectAsState()
    val recurrencePattern by vm.recurrencePattern.collectAsState()
    val recurrenceEndDate by vm.recurrenceEndDate.collectAsState()
    val selectedTagIds by vm.selectedTagIds.collectAsState()
    val allTags by vm.allTags.collectAsState()

    var showDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }
    var showNotifyMenu by remember { mutableStateOf(false) }

    // DatePicker dialogs
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val local = java.time.Instant.ofEpochMilli(millis)
                            .atZone(java.time.ZoneId.of("UTC")).toLocalDate()
                        vm.startDate.value = "%04d-%02d-%02d".format(local.year, local.monthValue, local.dayOfMonth)
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("キャンセル") } }
        ) { DatePicker(state = datePickerState) }
    }

    if (showEndDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val local = java.time.Instant.ofEpochMilli(millis)
                            .atZone(java.time.ZoneId.of("UTC")).toLocalDate()
                        vm.endDate.value = "%04d-%02d-%02d".format(local.year, local.monthValue, local.dayOfMonth)
                    }
                    showEndDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showEndDatePicker = false }) { Text("キャンセル") } }
        ) { DatePicker(state = datePickerState) }
    }

    // TimePicker dialogs
    if (showStartTimePicker) {
        val timePickerState = rememberTimePickerState()
        AlertDialog(
            onDismissRequest = { showStartTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    vm.startTime.value = "%02d:%02d".format(timePickerState.hour, timePickerState.minute)
                    showStartTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showStartTimePicker = false }) { Text("キャンセル") } },
            text = { TimePicker(state = timePickerState) }
        )
    }

    if (showEndTimePicker) {
        val timePickerState = rememberTimePickerState()
        AlertDialog(
            onDismissRequest = { showEndTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    vm.endTime.value = "%02d:%02d".format(timePickerState.hour, timePickerState.minute)
                    showEndTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showEndTimePicker = false }) { Text("キャンセル") } },
            text = { TimePicker(state = timePickerState) }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editTaskId == null) "タスク追加" else "タスク編集") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, null) }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Schedule type selector
            Text("スケジュール種別", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(ScheduleType.NORMAL to "通常", ScheduleType.PERIOD to "期間", ScheduleType.RECURRING to "繰り返し").forEach { (type, label) ->
                    FilterChip(
                        selected = scheduleType == type,
                        onClick = { vm.scheduleType.value = type },
                        label = { Text(label) }
                    )
                }
            }

            // Title
            OutlinedTextField(
                value = title, onValueChange = { vm.title.value = it },
                label = { Text("タイトル *") }, modifier = Modifier.fillMaxWidth(), singleLine = true
            )

            // Description
            OutlinedTextField(
                value = description, onValueChange = { vm.description.value = it },
                label = { Text("メモ（任意）") }, modifier = Modifier.fillMaxWidth(), maxLines = 4
            )

            // Date/time by schedule type
            when (scheduleType) {
                ScheduleType.NORMAL -> {
                    OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (startDate.isBlank()) "日付を選択 *" else "日付: $startDate")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showStartTimePicker = true }, modifier = Modifier.weight(1f)) {
                            Text(if (startTime.isBlank()) "開始時刻" else startTime)
                        }
                        OutlinedButton(onClick = { showEndTimePicker = true }, modifier = Modifier.weight(1f)) {
                            Text(if (endTime.isBlank()) "終了時刻" else endTime)
                        }
                    }
                }
                ScheduleType.PERIOD -> {
                    OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (startDate.isBlank()) "開始日を選択 *" else "開始日: $startDate")
                    }
                    OutlinedButton(onClick = { showEndDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (endDate.isBlank()) "終了日を選択 *" else "終了日: $endDate")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showStartTimePicker = true }, modifier = Modifier.weight(1f)) {
                            Text(if (startTime.isBlank()) "開始時刻" else startTime)
                        }
                        OutlinedButton(onClick = { showEndTimePicker = true }, modifier = Modifier.weight(1f)) {
                            Text(if (endTime.isBlank()) "終了時刻" else endTime)
                        }
                    }
                }
                ScheduleType.RECURRING -> {
                    Text("繰り返しパターン", style = MaterialTheme.typography.labelLarge)
                    var expandPattern by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = expandPattern, onExpandedChange = { expandPattern = it }) {
                        OutlinedTextField(
                            value = when (recurrencePattern) {
                                RecurrencePattern.DAILY -> "毎日"; RecurrencePattern.WEEKLY -> "毎週"
                                RecurrencePattern.BIWEEKLY -> "隔週"; RecurrencePattern.MONTHLY_DATE -> "毎月（日付指定）"
                                RecurrencePattern.MONTHLY_WEEK -> "毎月（曜日指定）"; RecurrencePattern.YEARLY -> "毎年"
                                null -> "選択してください"
                            },
                            onValueChange = {}, readOnly = true, label = { Text("パターン") },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = expandPattern, onDismissRequest = { expandPattern = false }) {
                            listOf(
                                RecurrencePattern.DAILY to "毎日", RecurrencePattern.WEEKLY to "毎週",
                                RecurrencePattern.BIWEEKLY to "隔週", RecurrencePattern.MONTHLY_DATE to "毎月（日付指定）",
                                RecurrencePattern.MONTHLY_WEEK to "毎月（曜日指定）", RecurrencePattern.YEARLY to "毎年"
                            ).forEach { (p, label) ->
                                DropdownMenuItem(text = { Text(label) }, onClick = { vm.recurrencePattern.value = p; expandPattern = false })
                            }
                        }
                    }
                    OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (startDate.isBlank()) "開始日を選択 *" else "開始日: $startDate")
                    }
                    OutlinedButton(onClick = { showEndDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (recurrenceEndDate.isBlank()) "繰り返し終了日（未設定=無期限）" else "終了日: $recurrenceEndDate")
                    }
                    OutlinedButton(onClick = { showStartTimePicker = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (startTime.isBlank()) "時刻を選択" else "時刻: $startTime")
                    }
                }
            }

            // Priority
            Text("優先度", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "高", 1 to "中", 2 to "低").forEach { (value, label) ->
                    FilterChip(
                        selected = priority == value,
                        onClick = { vm.priority.value = value },
                        label = { Text(label) }
                    )
                }
            }

            // Tag selector
            HorizontalDivider()
            TagSelector(
                allTags = allTags,
                selectedTagIds = selectedTagIds,
                onTagsChanged = { vm.selectedTagIds.value = it },
                onNavigateToTagManage = { navController.navigate(Screen.TagManage.route) }
            )
            HorizontalDivider()

            // Notification
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("通知", style = MaterialTheme.typography.labelLarge)
                Switch(checked = notifyEnabled, onCheckedChange = { vm.notifyEnabled.value = it })
            }
            if (notifyEnabled) {
                Box {
                    OutlinedButton(onClick = { showNotifyMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(when (notifyMinutesBefore) {
                            0 -> "時刻ちょうど"; 5 -> "5分前"; 10 -> "10分前"
                            30 -> "30分前"; 60 -> "1時間前"; 1440 -> "1日前"
                            else -> "${notifyMinutesBefore}分前"
                        })
                    }
                    DropdownMenu(expanded = showNotifyMenu, onDismissRequest = { showNotifyMenu = false }) {
                        listOf(0 to "時刻ちょうど", 5 to "5分前", 10 to "10分前", 30 to "30分前", 60 to "1時間前", 1440 to "1日前").forEach { (min, label) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = { vm.notifyMinutesBefore.value = min; showNotifyMenu = false })
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { vm.save(editTaskId) },
                modifier = Modifier.fillMaxWidth(),
                enabled = title.isNotBlank() && startDate.isNotBlank()
            ) { Text("保存") }
        }
    }
}
