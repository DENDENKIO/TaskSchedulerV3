package com.example.taskschedulerv3.ui.addtask

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.taskschedulerv3.data.model.RecurrencePattern
import com.example.taskschedulerv3.data.model.ScheduleType
import com.example.taskschedulerv3.data.model.Task
import com.example.taskschedulerv3.navigation.Screen
import com.example.taskschedulerv3.ui.components.TagSelector
import com.example.taskschedulerv3.util.PhotoFileManager
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskScreen(
    navController: NavController,
    initialDate: String = "",
    editTaskId: Int? = null,
    vm: AddTaskViewModel = viewModel()
) {
    val context = LocalContext.current
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
    val recurrenceDays by vm.recurrenceDays.collectAsState()
    val recurrenceEndDate by vm.recurrenceEndDate.collectAsState()
    val selectedTagIds by vm.selectedTagIds.collectAsState()
    val allTags by vm.allTags.collectAsState()
    val pendingPhotoPaths by vm.pendingPhotoPaths.collectAsState()
    val existingPhotos by vm.existingPhotos.collectAsState()
    val relatedTasks by vm.relatedTasks.collectAsState()
    val allTasksForPicker by vm.allTasks.collectAsState()
    val isIndefinite by vm.isIndefinite.collectAsState()

    var showDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }
    var showNotifyMenu by remember { mutableStateOf(false) }
    var showPhotoMenu by remember { mutableStateOf(false) }
    var showRelationDialog by remember { mutableStateOf(false) }
    var tempPhotoFile by remember { mutableStateOf<File?>(null) }

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            tempPhotoFile?.let { file -> vm.addPhotoFromCamera(file); tempPhotoFile = null }
        }
    }

    // Gallery launcher
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { vm.addPhotoFromGallery(it) }
    }

    // Camera permission launcher
    val cameraPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val (uri, file) = PhotoFileManager.createTempPhotoUri(context)
            tempPhotoFile = file
            cameraLauncher.launch(uri)
        }
    }

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
            // ── 無期限トグル ──────────────────────────────
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isIndefinite)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            "無期限予定",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            "日付を決めない予定として登録します",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    Switch(
                        checked = isIndefinite,
                        onCheckedChange = { vm.isIndefinite.value = it }
                    )
                }
            }

            // Schedule type selector（無期限がOFFの場合のみ表示）
            if (!isIndefinite) {
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

            // Date/time by schedule type（無期限がOFFの場合のみ表示）
            if (!isIndefinite) {
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
                        if (recurrencePattern == RecurrencePattern.WEEKLY || recurrencePattern == RecurrencePattern.BIWEEKLY) {
                            Text("曜日指定", style = MaterialTheme.typography.labelLarge)
                            val selectedDays = remember(recurrenceDays) {
                                recurrenceDays.split(",")
                                    .mapNotNull { it.trim().toIntOrNull() }
                                    .toSet()
                            }
                            val dayOptions = listOf(
                                1 to "月", 2 to "火", 3 to "水", 4 to "木", 5 to "金", 6 to "土", 7 to "日"
                            )
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                dayOptions.forEach { (value, label) ->
                                    FilterChip(
                                        selected = value in selectedDays,
                                        onClick = {
                                            val newSet = if (value in selectedDays) {
                                                selectedDays - value
                                            } else {
                                                selectedDays + value
                                            }
                                            vm.recurrenceDays.value = newSet.sorted().joinToString(",")
                                        },
                                        label = { Text(label) }
                                    )
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

            // Related tasks section
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("関連予定", style = MaterialTheme.typography.labelLarge)
                TextButton(onClick = { showRelationDialog = true }) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("追加")
                }
            }
            if (relatedTasks.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    relatedTasks.forEach { relTask ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(relTask.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                Text(
                                    "${relTask.startDate}${relTask.startTime?.let { " $it" } ?: ""}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                            IconButton(onClick = { vm.removeRelatedTask(relTask.id) }) {
                                Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            // Relation picker dialog
            if (showRelationDialog) {
                RelationPickerDialog(
                    allTasks = allTasksForPicker.filter { it.id != editTaskId && it.id !in relatedTasks.map { t -> t.id } },
                    onConfirm = { selectedIds ->
                        selectedIds.forEach { vm.addRelatedTask(it) }
                        showRelationDialog = false
                    },
                    onDismiss = { showRelationDialog = false }
                )
            }

            // Photo attachment
            Text("写真メモ", style = MaterialTheme.typography.labelLarge)
            val allPhotoUris = existingPhotos.map { it.imagePath to true } +
                               pendingPhotoPaths.map { it to false }
            if (allPhotoUris.isNotEmpty()) {
                val cols = 3
                val rows = (allPhotoUris.size + cols - 1) / cols
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (row in 0 until rows) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (col in 0 until cols) {
                                val idx = row * cols + col
                                if (idx < allPhotoUris.size) {
                                    val (path, isExisting) = allPhotoUris[idx]
                                    Box(modifier = Modifier.weight(1f).aspectRatio(1f)) {
                                        AsyncImage(
                                            model = PhotoFileManager.pathToUri(path),
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp))
                                        )
                                        IconButton(
                                            onClick = {
                                                if (isExisting) {
                                                    existingPhotos.find { it.imagePath == path }?.let { vm.removeExistingPhoto(it) }
                                                } else {
                                                    vm.removePendingPhoto(path)
                                                }
                                            },
                                            modifier = Modifier.align(Alignment.TopEnd).size(24.dp)
                                        ) {
                                            Icon(Icons.Default.Close, null,
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                        if (hasPerm) {
                            val (uri, file) = PhotoFileManager.createTempPhotoUri(context)
                            tempPhotoFile = file
                            cameraLauncher.launch(uri)
                        } else {
                            cameraPermLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.CameraAlt, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("カメラ")
                }
                OutlinedButton(
                    onClick = {
                        galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Photo, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("ギャラリー")
                }
            }

            // Notification（無期限の場合は通知不要のため非表示）
            if (!isIndefinite) {
                HorizontalDivider()
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
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { vm.save(editTaskId) },
                modifier = Modifier.fillMaxWidth(),
                enabled = title.isNotBlank() && (isIndefinite || (startDate.isNotBlank() &&
                    (scheduleType != ScheduleType.RECURRING || recurrencePattern != null)))
            ) { Text("保存") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelationPickerDialog(
    allTasks: List<Task>,
    onConfirm: (Set<Int>) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val selected = remember { mutableStateOf(setOf<Int>()) }

    val filtered = if (searchQuery.isBlank()) allTasks
    else allTasks.filter { it.title.contains(searchQuery, ignoreCase = true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("関連予定を選択") },
        text = {
            Column {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("タスク名で検索") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier.heightIn(max = 320.dp)
                ) {
                    items(filtered) { task ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = task.id in selected.value,
                                onCheckedChange = { checked ->
                                    selected.value = if (checked) selected.value + task.id
                                                    else selected.value - task.id
                                }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(task.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                Text(
                                    "${task.startDate}${task.startTime?.let { " $it" } ?: ""}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selected.value) },
                enabled = selected.value.isNotEmpty()
            ) { Text("追加 (${selected.value.size})") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        }
    )
}
