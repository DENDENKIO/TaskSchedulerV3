package com.example.taskschedulerv3.ui.addtask

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController

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
    val startTime by vm.startTime.collectAsState()
    val endTime by vm.endTime.collectAsState()
    val priority by vm.priority.collectAsState()
    val notifyEnabled by vm.notifyEnabled.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editTaskId == null) "タスク追加" else "タスク編集") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = title, onValueChange = { vm.title.value = it },
                label = { Text("タイトル *") }, modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            OutlinedTextField(
                value = description, onValueChange = { vm.description.value = it },
                label = { Text("メモ") }, modifier = Modifier.fillMaxWidth(), maxLines = 4
            )
            OutlinedTextField(
                value = startDate, onValueChange = { vm.startDate.value = it },
                label = { Text("日付 (yyyy-MM-dd) *") }, modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = startTime, onValueChange = { vm.startTime.value = it },
                    label = { Text("開始時刻 (HH:mm)") }, modifier = Modifier.weight(1f), singleLine = true
                )
                OutlinedTextField(
                    value = endTime, onValueChange = { vm.endTime.value = it },
                    label = { Text("終了時刻 (HH:mm)") }, modifier = Modifier.weight(1f), singleLine = true
                )
            }
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
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("通知", modifier = Modifier.weight(1f))
                Switch(checked = notifyEnabled, onCheckedChange = { vm.notifyEnabled.value = it })
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { vm.save(editTaskId) },
                modifier = Modifier.fillMaxWidth(),
                enabled = title.isNotBlank() && startDate.isNotBlank()
            ) {
                Text("保存")
            }
        }
    }
}
