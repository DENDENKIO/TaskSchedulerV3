package com.example.taskschedulerv3.ui.schedulelist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.taskschedulerv3.navigation.Screen
import com.example.taskschedulerv3.ui.calendar.PriorityBadge
import com.example.taskschedulerv3.data.model.Task
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextDecoration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleListScreen(navController: NavController, vm: ScheduleListViewModel = viewModel()) {
    val tasks by vm.tasks.collectAsState()
    val searchQuery by vm.searchQuery.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("スケジュール一覧") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate(Screen.AddTask.createRoute()) }) {
                Icon(Icons.Default.Add, contentDescription = "追加")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { vm.setSearchQuery(it) },
                label = { Text("検索") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true
            )
            LazyColumn {
                items(tasks) { task ->
                    ListItem(
                        headlineContent = {
                            Text(
                                task.title,
                                textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                            )
                        },
                        supportingContent = {
                            Text("${task.startDate}${task.startTime?.let { " $it" } ?: ""}")
                        },
                        trailingContent = { PriorityBadge(task.priority) },
                        modifier = androidx.compose.ui.Modifier.fillMaxWidth()
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
