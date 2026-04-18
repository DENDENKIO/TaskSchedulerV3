package com.example.taskschedulerv3.ui.roadmap

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.taskschedulerv3.data.model.RoadmapStep

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoadmapEditScreen(
    navController: NavController,
    taskId: Int,
    vm: RoadmapEditViewModel = viewModel()
) {
    val task by vm.task.collectAsState()
    val steps by vm.steps.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var stepToEdit by remember { mutableStateOf<RoadmapStep?>(null) }

    LaunchedEffect(taskId) { vm.loadTask(taskId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ロードマップ編集") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) { 
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null) 
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, "ステップ追加")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            task?.let { t ->
                ListItem(
                    headlineContent = { Text(t.title, style = MaterialTheme.typography.titleMedium) },
                    supportingContent = { Text("この予定のロードマップを管理します") },
                    overlineContent = { Text("対象タスク") }
                )
            }
            HorizontalDivider()

            if (steps.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("ステップがありません。追加してください。", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(steps) { index, step ->
                        StepItem(
                            step = step,
                            onToggleComplete = { vm.toggleStepCompletion(step) },
                            onDelete = { vm.deleteStep(step) },
                            onEdit = { stepToEdit = step },
                            onMoveUp = if (index > 0) { { 
                                val newList = steps.toMutableList()
                                val target = newList.removeAt(index)
                                newList.add(index - 1, target)
                                vm.updateStepOrders(newList)
                            } } else null,
                            onMoveDown = if (index < steps.size - 1) { { 
                                val newList = steps.toMutableList()
                                val target = newList.removeAt(index)
                                newList.add(index + 1, target)
                                vm.updateStepOrders(newList)
                            } } else null
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        StepEditDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { title, date ->
                vm.addStep(title, date)
                showAddDialog = false
            }
        )
    }

    stepToEdit?.let { step ->
        StepEditDialog(
            initialTitle = step.title,
            initialDate = step.date ?: "",
            onDismiss = { stepToEdit = null },
            onConfirm = { title, date ->
                vm.updateStep(step.copy(title = title, date = date.ifEmpty { null }))
                stepToEdit = null
            }
        )
    }
}

@Composable
fun StepItem(
    step: RoadmapStep,
    onToggleComplete: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?
) {
    ListItem(
        headlineContent = { 
            Text(
                step.title, 
                style = MaterialTheme.typography.bodyLarge,
                color = if (step.isCompleted) Color.Gray else Color.Unspecified
            ) 
        },
        supportingContent = { step.date?.let { Text(it) } },
        leadingContent = {
            Checkbox(checked = step.isCompleted, onCheckedChange = { onToggleComplete() })
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "編集", modifier = Modifier.size(20.dp)) }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "削除", modifier = Modifier.size(20.dp)) }
                Column {
                    IconButton(onClick = { onMoveUp?.invoke() }, enabled = onMoveUp != null, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.ArrowDropUp, "上へ")
                    }
                    IconButton(onClick = { onMoveDown?.invoke() }, enabled = onMoveDown != null, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.ArrowDropDown, "下へ")
                    }
                }
            }
        },
        modifier = Modifier.clickable { onEdit() }
    )
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StepEditDialog(
    initialTitle: String = "",
    initialDate: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }
    var date by remember { mutableStateOf(initialDate) }
    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = if (date.isNotEmpty()) {
                try {
                    java.time.LocalDate.parse(date).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                } catch (_: Exception) { System.currentTimeMillis() }
            } else System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val local = java.time.Instant.ofEpochMilli(millis)
                            .atZone(java.time.ZoneId.of("UTC")).toLocalDate()
                        date = local.toString()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("キャンセル") }
            }
        ) { DatePicker(state = datePickerState) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialTitle.isEmpty()) "ステップ追加" else "ステップ編集") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("名称") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = date,
                    onValueChange = { /* 読み取り専用にしたいので何もしない */ },
                    label = { Text("期限 (任意)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true },
                    readOnly = true,
                    enabled = true,
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Default.CalendarMonth, "日付選択")
                        }
                    }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(title, date) },
                enabled = title.isNotBlank()
            ) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        }
    )
}
