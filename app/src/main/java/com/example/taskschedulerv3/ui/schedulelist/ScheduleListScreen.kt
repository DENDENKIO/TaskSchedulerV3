package com.example.taskschedulerv3.ui.schedulelist

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
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
import com.example.taskschedulerv3.ui.components.buildPath
import com.example.taskschedulerv3.ui.tag.parseColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleListScreen(navController: NavController, vm: ScheduleListViewModel = viewModel()) {
    val tasks by vm.tasks.collectAsState()
    val searchQuery by vm.searchQuery.collectAsState()
    val sortOption by vm.sortOption.collectAsState()
    val filterOption by vm.filterOption.collectAsState()
    val allTags by vm.allTags.collectAsState()
    val filterTagId by vm.filterTagId.collectAsState()
    val tagFilteredTaskIds by vm.tagFilteredTaskIds.collectAsState()

    var showSortMenu by remember { mutableStateOf(false) }
    var showTagFilterDialog by remember { mutableStateOf(false) }

    // Apply tag filter in UI
    val displayedTasks = if (tagFilteredTaskIds != null) {
        tasks.filter { it.id in tagFilteredTaskIds!! }
    } else tasks

    // Tag filter dialog
    if (showTagFilterDialog) {
        TagFilterDialog(
            allTags = allTags,
            selectedTagId = filterTagId,
            onSelect = { vm.setTagFilter(it); showTagFilterDialog = false },
            onDismiss = { showTagFilterDialog = false }
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("スケジュール一覧") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate(Screen.AddTask.createRoute()) }) {
                Icon(Icons.Default.Add, contentDescription = "追加")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
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
                item {
                    FilterChip(
                        selected = filterOption.completionStatus == CompletionFilter.INCOMPLETE,
                        onClick = { vm.setCompletionFilter(if (filterOption.completionStatus == CompletionFilter.INCOMPLETE) CompletionFilter.ALL else CompletionFilter.INCOMPLETE) },
                        label = { Text("未完了") }
                    )
                }
                item {
                    FilterChip(
                        selected = filterOption.completionStatus == CompletionFilter.COMPLETE,
                        onClick = { vm.setCompletionFilter(if (filterOption.completionStatus == CompletionFilter.COMPLETE) CompletionFilter.ALL else CompletionFilter.COMPLETE) },
                        label = { Text("完了済") }
                    )
                }
                listOf(0 to "高", 1 to "中", 2 to "低").forEach { (p, label) ->
                    item {
                        FilterChip(selected = p in filterOption.priorities, onClick = { vm.togglePriorityFilter(p) }, label = { Text("優先:$label") })
                    }
                }
                // Schedule type filter chips with icons
                item {
                    FilterChip(
                        selected = ScheduleType.PERIOD in filterOption.scheduleTypes,
                        onClick = { vm.toggleScheduleTypeFilter(ScheduleType.PERIOD) },
                        label = { Text("期間") },
                        leadingIcon = { Icon(Icons.Default.DateRange, null, modifier = Modifier.size(14.dp)) }
                    )
                }
                item {
                    FilterChip(
                        selected = ScheduleType.RECURRING in filterOption.scheduleTypes,
                        onClick = { vm.toggleScheduleTypeFilter(ScheduleType.RECURRING) },
                        label = { Text("繰り返し") },
                        leadingIcon = { Icon(Icons.Default.Refresh, null, modifier = Modifier.size(14.dp)) }
                    )
                }
                // Tag filter chip
                item {
                    val tagLabel = filterTagId?.let { id ->
                        allTags.find { it.id == id }?.let { tag -> buildPath(tag, allTags) }
                    }
                    FilterChip(
                        selected = filterTagId != null,
                        onClick = { showTagFilterDialog = true },
                        label = { Text(tagLabel ?: "タグ") },
                        leadingIcon = { Icon(Icons.Default.Label, null, modifier = Modifier.size(16.dp)) }
                    )
                }
                // Clear
                item {
                    if (filterOption != FilterOption() || filterTagId != null) {
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
                Text("${displayedTasks.size}件", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.weight(1f))
                Box {
                    TextButton(onClick = { showSortMenu = true }, contentPadding = PaddingValues(horizontal = 8.dp)) {
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
                            SortOption.DATE_ASC to "日付（昇順）", SortOption.DATE_DESC to "日付（降順）",
                            SortOption.PRIORITY_HIGH to "優先度（高→低）", SortOption.PRIORITY_LOW to "優先度（低→高）",
                            SortOption.TITLE_ASC to "タスク名（昇順）", SortOption.TITLE_DESC to "タスク名（降順）",
                            SortOption.CREATED_DESC to "作成日（新しい順）", SortOption.CREATED_ASC to "作成日（古い順）"
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

            // Task list
            if (displayedTasks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "タスクがありません",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    val grouped = displayedTasks.groupBy { it.startDate }
                    grouped.forEach { (date, dateTasks) ->
                        item {
                            Text(
                                text = date,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.fillMaxWidth()
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
}

@Composable
fun TagFilterDialog(
    allTags: List<Tag>,
    selectedTagId: Int?,
    onSelect: (Int?) -> Unit,
    onDismiss: () -> Unit
) {
    val largeTags = allTags.filter { it.level == 1 }.sortedBy { it.sortOrder }
    var expandedId by remember { mutableStateOf<Int?>(null) }
    var expandedMediumId by remember { mutableStateOf<Int?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("タグで絞り込む") },
        text = {
            LazyColumn {
                item {
                    TextButton(onClick = { onSelect(null) }) { Text("すべて表示") }
                }
                largeTags.forEach { large ->
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selectedTagId == large.id, onClick = { onSelect(large.id) })
                            Text(large.name, modifier = Modifier.weight(1f))
                            if (allTags.any { it.parentId == large.id }) {
                                TextButton(onClick = {
                                    expandedId = if (expandedId == large.id) null else large.id
                                }) { Text(if (expandedId == large.id) "折りたたむ" else "展開") }
                            }
                        }
                    }
                    if (expandedId == large.id) {
                        val mediums = allTags.filter { it.parentId == large.id }
                        mediums.forEach { medium ->
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(selected = selectedTagId == medium.id, onClick = { onSelect(medium.id) })
                                    Text(medium.name, modifier = Modifier.weight(1f))
                                    if (allTags.any { it.parentId == medium.id }) {
                                        TextButton(onClick = {
                                            expandedMediumId = if (expandedMediumId == medium.id) null else medium.id
                                        }) { Text(if (expandedMediumId == medium.id) "折りたたむ" else "展開") }
                                    }
                                }
                            }
                            if (expandedMediumId == medium.id) {
                                val smalls = allTags.filter { it.parentId == medium.id }
                                smalls.forEach { small ->
                                    item {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(start = 32.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(selected = selectedTagId == small.id, onClick = { onSelect(small.id) })
                                            Text(small.name)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("閉じる") }
        }
    )
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
                }, label = "swipe_bg"
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
                val priorityColor = when (task.priority) { 0 -> Color(0xFFE53935); 2 -> Color(0xFF43A047); else -> Color(0xFFFB8C00) }
                Box(modifier = Modifier.width(4.dp).height(44.dp).background(priorityColor, MaterialTheme.shapes.extraSmall))
                Spacer(Modifier.width(8.dp))
                Checkbox(checked = task.isCompleted, onCheckedChange = { onComplete() })
                Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
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
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        task.startTime?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                        // Period: show end date
                        if (task.scheduleType == ScheduleType.PERIOD && task.endDate != null) {
                            Text(
                                "〜 ${task.endDate}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        // Recurring: show pattern name
                        if (task.scheduleType == ScheduleType.RECURRING && task.recurrencePattern != null) {
                            Text(
                                when (task.recurrencePattern) {
                                    RecurrencePattern.DAILY -> "毎日"
                                    RecurrencePattern.WEEKLY -> "毎週"
                                    RecurrencePattern.BIWEEKLY -> "隔週"
                                    RecurrencePattern.MONTHLY_DATE -> "毎月（日付）"
                                    RecurrencePattern.MONTHLY_WEEK -> "毎月（曜日）"
                                    RecurrencePattern.YEARLY -> "毎年"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        // Notification icon
                        if (task.notifyEnabled) {
                            Icon(
                                Icons.Default.Notifications, contentDescription = "通知",
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
                PriorityBadge(task.priority)
            }
            HorizontalDivider()
        }
    }
}
