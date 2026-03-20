package com.example.taskschedulerv3.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.taskschedulerv3.data.model.Task
import com.example.taskschedulerv3.navigation.Screen
import com.example.taskschedulerv3.util.DateUtils
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(navController: NavController, vm: CalendarViewModel = viewModel()) {
    val selectedDate by vm.selectedDate.collectAsState()
    val currentYear by vm.currentYear.collectAsState()
    val currentMonth by vm.currentMonth.collectAsState()
    val tasks by vm.tasksForSelectedDate.collectAsState()
    val allTasks by vm.allTasks.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("タスクスケジューラ") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate(Screen.AddTask.createRoute(selectedDate)) }) {
                Icon(Icons.Default.Add, contentDescription = "追加")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            MonthView(
                year = currentYear,
                month = currentMonth,
                selectedDate = selectedDate,
                tasksWithDates = allTasks.map { it.startDate }.toSet(),
                onDateSelected = { vm.selectDate(it) },
                onPreviousMonth = { vm.previousMonth() },
                onNextMonth = { vm.nextMonth() }
            )
            HorizontalDivider()
            Text(
                text = "$selectedDate のタスク",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            LazyColumn {
                items(tasks) { task ->
                    TaskRow(
                        task = task,
                        onDelete = { vm.deleteTask(task) },
                        onToggleComplete = { vm.toggleComplete(task) }
                    )
                }
            }
        }
    }
}

@Composable
fun TaskRow(task: Task, onDelete: () -> Unit, onToggleComplete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = task.isCompleted, onCheckedChange = { onToggleComplete() })
        Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None
            )
            task.startTime?.let { Text(text = it, style = MaterialTheme.typography.bodySmall) }
        }
        PriorityBadge(task.priority)
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "削除")
        }
    }
}

@Composable
fun PriorityBadge(priority: Int) {
    val (color, label) = when (priority) {
        0 -> Pair(Color(0xFFE53935), "高")
        2 -> Pair(Color(0xFF43A047), "低")
        else -> Pair(Color(0xFFFB8C00), "中")
    }
    Surface(color = color, shape = MaterialTheme.shapes.small) {
        Text(text = label, color = Color.White, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
    }
}
