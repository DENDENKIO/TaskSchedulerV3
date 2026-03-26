package com.example.taskschedulerv3.ui.indefinite

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.taskschedulerv3.navigation.Screen
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IndefiniteTaskScreen(
    navController: NavController,
    viewModel: IndefiniteTaskViewModel = viewModel()
) {
    val tasks by viewModel.indefiniteTasks.collectAsState()
    var convertTarget by remember { mutableStateOf<Int?>(null) }
    var dateInput by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("無期限予定") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "戻る")
                    }
                }
            )
        }
    ) { padding ->
        if (tasks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("無期限の予定はありません", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(tasks) { task ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(task.title, style = MaterialTheme.typography.titleMedium)
                            task.description?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 4.dp))
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = {
                                    navController.navigate(Screen.EditTask.createRoute(task.id))
                                }) { Text("編集") }
                                IconButton(onClick = {
                                    convertTarget = task.id
                                    dateInput = LocalDate.now().toString()
                                }) {
                                    Icon(Icons.Default.CalendarToday,
                                        contentDescription = "期限付きに変更",
                                        tint = MaterialTheme.colorScheme.primary)
                                }
                                IconButton(onClick = { viewModel.deleteTask(task) }) {
                                    Icon(Icons.Default.Delete,
                                        contentDescription = "削除",
                                        tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (convertTarget != null) {
            val target = tasks.find { it.id == convertTarget }
            if (target != null) {
                AlertDialog(
                    onDismissRequest = { convertTarget = null },
                    title = { Text("期限付きに変更") },
                    text = {
                        Column {
                            Text("開始日を入力してください（yyyy-MM-dd）")
                            OutlinedTextField(
                                value = dateInput,
                                onValueChange = { dateInput = it },
                                label = { Text("開始日") },
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            if (dateInput.isNotBlank()) {
                                viewModel.convertToScheduled(target, dateInput)
                                convertTarget = null
                            }
                        }) { Text("登録") }
                    },
                    dismissButton = {
                        TextButton(onClick = { convertTarget = null }) { Text("キャンセル") }
                    }
                )
            }
        }
    }
}
