package com.example.taskschedulerv3.ui.schedulelist

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Sort
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
import com.example.taskschedulerv3.data.model.*
import com.example.taskschedulerv3.navigation.Screen
import com.example.taskschedulerv3.ui.calendar.PriorityBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleListScreen(navController: NavController, vm: ScheduleListViewModel = viewModel()) {
    val tasks by vm.tasks.collectAsState()
    val searchQuery by vm.searchQuery.collectAsState()
    val sortOption by vm.sortOption.collectAsState()
    val filterOption by vm.filterOption.collectAsState()
    var showSortMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("スケジュール一覧") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate(Screen.AddTask.createRoute()) }) {
                Icon(Icons.Default.Add, contentDescription = "追加")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { vm.setSearchQuery(it) },
                label = { Text("検索") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                singleLine = true
            )

            // Filter chips row
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Completion filter
                item {
                    FilterChip(
                        selected = filterOption.completionStatus == CompletionFilter.INCOMPLETE,
                        onClick = {
                            vm.setCompletionFilter(
                                if (filterOption.completionStatus == CompletionFilter.INCOMPLETE) CompletionFilter.ALL
                                else CompletionFilter.INCOMPLETE
                            )
                        },
                        label = { Text("未完了") }
                    )
                }
                item {
                    FilterChip(
                        selected = filterOption.completionStatus == CompletionFilter.COMPLETE,
                        onClick = {
                            vm.setCompletionFilter(
                                if (filterOption.completionStatus == CompletionFilter.COMPLETE) CompletionFilter.ALL
                                else CompletionFilter.COMPLETE
                            )
                        },
                        label = { Text("完了済") }
                    )
                }
                // Priority filters
                listOf(0 to "高", 1 to "中", 2 to "低").forEach { (p, label) ->
                    item {
                        FilterChip(
                            selected = p in filterOption.priorities,
                            onClick = { vm.togglePriorityFilter(p) },
                            label = { Text("優先度:$label") }
                        )
                    }
                }
                // Schedule type filters
                listOf(ScheduleType.NORMAL to "通常", ScheduleType.PERIOD to "期間", ScheduleType.RECURRING to "繰返").forEach { (t, label) ->
                    item {
                        FilterChip(
                            selected = t in filterOption.scheduleTypes,
                            onClick = { vm.toggleScheduleTypeFilter(t) },
                            label = { Text(label) }
                        )
                    }
                }
                // Clear filters
                item {
                    if (filterOption != FilterOption()) {
                        AssistChip(onClick = { vm.clearFilters() }, label = { Text("クリア") })
                    }
                }
            }

            // Sort bar
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${tasks.size}件", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.weight(1f))
                Box {
                    TextButton(
                        onClick = { showSortMenu = true },
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Default.Sort, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            when (sortOption) {
                                SortOption.DATE_ASC -> "日付↑"; SortOption.DATE_DESC -> "日付↓"
                                SortOption.PRIORITY_HIGH -> "優先度高"; SortOption.PRIORITY_LOW -> "優先度低"
                                SortOption.TITLE_ASC -> "名前↑"; SortOption.TITLE_DESC -> "名前↓"
                                SortOption.CREATED_ASC -> "作成↑"; SortOption.CREATED_DESC -> "作成↓"
                            },
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                        listOf(
                            SortOption.DATE_ASC to "日付（昇順）",
                            SortOption.DATE_DESC to "日付（降順）",
                            SortOption.PRIORITY_HIGH to "優先度（高→低）",
                            SortOption.PRIORITY_LOW to "優先度（低→高）",
                            SortOption.TITLE_ASC to "タスク名（昇順）",
                            SortOption.TITLE_DESC to "タスク名（降順）",
                            SortOption.CREATED_DESC to "作成日（新しい順）",
                            SortOption.CREATED_ASC to "作成日（古い順）"
                        ).forEach { (opt, label) ->
                            DropdownMenuItem(
                                text = { Text(label, style = if (sortOption == opt) MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.bodyMedium) },
                                onClick = { vm.setSortOption(opt); showSortMenu = false }
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            // Task list with swipe-to-dismiss
            LazyColumn {
                // Group by date
                val grouped = tasks.groupBy { it.startDate }
                grouped.forEach { (date, dateTasks) ->
                    item {
                        Text(
                            text = date,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                    items(dateTasks, key = { it.id }) { task ->
                        SwipeToDismissTaskItem(
                            task = task,
                            onDelete = { vm.softDelete(task) },
                            onComplete = { vm.toggleComplete(task) },
                            onClick = { navController.navigate(Screen.TaskDetail.createRoute(task.id)) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToDismissTaskItem(
    task: Task,
    onDelete: () -> Unit,
    onComplete: () -> Unit,
    onClick: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> { onDelete(); true }
                SwipeToDismissBoxValue.StartToEnd -> { onComplete(); false }
                else -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val color by animateColorAsState(
                when (dismissState.targetValue) {
                    SwipeToDismissBoxValue.EndToStart -> Color(0xFFE53935)
                    SwipeToDismissBoxValue.StartToEnd -> Color(0xFF43A047)
                    else -> Color.Transparent
                },
                label = "swipe_bg"
            )
            Box(
                modifier = Modifier.fillMaxSize().background(color).padding(horizontal = 20.dp),
                contentAlignment = when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                    else -> Alignment.CenterEnd
                }
            ) {
                Text(
                    text = when (direction) {
                        SwipeToDismissBoxValue.StartToEnd -> if (task.isCompleted) "未完了に戻す" else "完了"
                        else -> "削除"
                    },
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    ) {
        Surface(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val priorityColor = when (task.priority) {
                    0 -> Color(0xFFE53935); 2 -> Color(0xFF43A047); else -> Color(0xFFFB8C00)
                }
                Box(modifier = Modifier.width(4.dp).height(44.dp).background(priorityColor, MaterialTheme.shapes.extraSmall))
                Spacer(Modifier.width(8.dp))
                Checkbox(checked = task.isCompleted, onCheckedChange = { onComplete() })
                Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.bodyMedium,
                        textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                        maxLines = 1
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        task.startTime?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                        val typeLabel = when (task.scheduleType) {
                            ScheduleType.PERIOD -> "期間"
                            ScheduleType.RECURRING -> "繰返"
                            else -> null
                        }
                        typeLabel?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
                PriorityBadge(task.priority)
            }
            HorizontalDivider()
        }
    }
}
