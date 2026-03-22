package com.example.taskschedulerv3.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
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
import com.example.taskschedulerv3.data.model.ScheduleType
import com.example.taskschedulerv3.data.model.Task
import com.example.taskschedulerv3.navigation.Screen
import com.example.taskschedulerv3.util.DateUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(navController: NavController, vm: CalendarViewModel = viewModel()) {
    val viewMode by vm.viewMode.collectAsState()
    val selectedDate by vm.selectedDate.collectAsState()
    val currentYear by vm.currentYear.collectAsState()
    val currentMonth by vm.currentMonth.collectAsState()
    val tasksForDate by vm.tasksForSelectedDate.collectAsState()
    val allTasks by vm.allTasks.collectAsState()
    val allTaskDates by vm.allTaskDates.collectAsState()
    val summary by vm.todaySummary.collectAsState()
    val monthDayRows by vm.monthDayRows.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("タスクスケジューラ") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate(Screen.AddTask.createRoute(selectedDate)) }) {
                Icon(Icons.Default.Add, contentDescription = "追加")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Today summary card
            TodaySummaryCard(summary)

            // Mode tab row
            val modes = listOf(CalendarViewMode.YEAR to "年", CalendarViewMode.MONTH to "月", CalendarViewMode.WEEK to "週", CalendarViewMode.DAY to "日")
            TabRow(selectedTabIndex = modes.indexOfFirst { it.first == viewMode }) {
                modes.forEach { (mode, label) ->
                    Tab(
                        selected = viewMode == mode,
                        onClick = { vm.setViewMode(mode) },
                        text = { Text(label) }
                    )
                }
            }

            // Calendar body
            when (viewMode) {
                CalendarViewMode.YEAR -> YearView(
                    year = currentYear,
                    taskDates = allTaskDates,
                    onMonthSelected = { month ->
                        vm.selectDate("%04d-%02d-01".format(currentYear, month))
                        vm.setViewMode(CalendarViewMode.MONTH)
                    },
                    onPreviousYear = { vm.previousYear() },
                    onNextYear = { vm.nextYear() }
                )
                CalendarViewMode.MONTH -> MonthView(
                    year = currentYear,
                    month = currentMonth,
                    selectedDate = selectedDate,
                    dayRows = monthDayRows,
                    onDateSelected = { date ->
                        vm.selectDate(date)
                        navController.navigate(Screen.ScheduleList.createRoute(date))
                    },
                    onPreviousMonth = { vm.previousMonth() },
                    onNextMonth = { vm.nextMonth() }
                )
                CalendarViewMode.WEEK -> WeekView(
                    selectedDate = selectedDate,
                    tasks = allTasks.filter { it.scheduleType != ScheduleType.RECURRING },
                    onDateSelected = { date ->
                        vm.selectDate(date)
                        navController.navigate(Screen.ScheduleList.createRoute(date))
                    },
                    onPreviousWeek = { vm.previousWeek() },
                    onNextWeek = { vm.nextWeek() }
                )
                CalendarViewMode.DAY -> Column(modifier = Modifier.fillMaxSize()) {
                    DayView(
                        selectedDate = selectedDate,
                        tasks = tasksForDate,
                        onPreviousDay = { vm.previousDay() },
                        onNextDay = { vm.nextDay() }
                    )
                }
            }
        }
    }
}

@Composable
fun TodaySummaryCard(summary: TodaySummary) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("今日 ${summary.date}", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("未完了タスク: ${summary.incompleteCount}件", style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            summary.nextTaskTitle?.let { title ->
                Column(horizontalAlignment = Alignment.End) {
                    Text("直近", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                    Text(title, style = MaterialTheme.typography.bodySmall, maxLines = 1,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                    summary.nextTaskTime?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                    }
                }
            }
        }
    }
}

@Composable
fun SelectedDateTaskList(
    date: String,
    tasks: List<Task>,
    onDelete: (Task) -> Unit,
    onToggleComplete: (Task) -> Unit,
    onTaskClick: (Task) -> Unit
) {
    Column {
        Text(
            text = "${date} のタスク (${tasks.size}件)",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )
        LazyColumn {
            items(tasks) { task ->
                TaskRow(
                    task = task,
                    onDelete = { onDelete(task) },
                    onToggleComplete = { onToggleComplete(task) },
                    onClick = { onTaskClick(task) }
                )
            }
        }
    }
}

@Composable
fun TaskRow(
    task: Task,
    onDelete: () -> Unit,
    onToggleComplete: () -> Unit,
    onClick: () -> Unit = {}
) {
    val priorityColor = when (task.priority) {
        0 -> Color(0xFFE53935); 2 -> Color(0xFF43A047); else -> Color(0xFFFB8C00)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.width(4.dp).height(40.dp).background(priorityColor, MaterialTheme.shapes.extraSmall))
        Spacer(Modifier.width(8.dp))
        Checkbox(checked = task.isCompleted, onCheckedChange = { onToggleComplete() })
        Column(
            modifier = Modifier.weight(1f).padding(start = 4.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Schedule type icon
                when (task.scheduleType) {
                    ScheduleType.PERIOD -> Icon(
                        Icons.Default.DateRange, contentDescription = "期間",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    ScheduleType.RECURRING -> Icon(
                        Icons.Default.Refresh, contentDescription = "繰り返し",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    else -> {}
                }
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyMedium,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                    maxLines = 1
                )
            }
            task.startTime?.let {
                Text(
                    text = "${it}${task.endTime?.let { e -> " 〜 $e" } ?: ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
        PriorityBadge(task.priority)
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "削除", modifier = Modifier.size(18.dp))
        }
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
}

@Composable
fun PriorityBadge(priority: Int) {
    val (color, label) = when (priority) {
        0 -> Pair(Color(0xFFE53935), "高")
        2 -> Pair(Color(0xFF43A047), "低")
        else -> Pair(Color(0xFFFB8C00), "中")
    }
    Surface(color = color, shape = MaterialTheme.shapes.small) {
        Text(
            text = label, color = Color.White,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall
        )
    }
}
