package com.example.taskschedulerv3.ui.schedulelist

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.taskschedulerv3.data.model.*
import com.example.taskschedulerv3.navigation.Screen
import com.example.taskschedulerv3.ui.components.TaskRowItem
import com.example.taskschedulerv3.util.RecurrenceCalculator
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private enum class ListTab(val label: String) {
    TODAY("今日"), WEEK("今週"), INDEFINITE("無期限"), ALL("すべて"), DONE("完了")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ScheduleListScreenHighDensity(
    navController: NavController,
    onAddTask: () -> Unit,
    vm: ScheduleListViewModel = viewModel()
) {
    val tasks by vm.tasks.collectAsState()
    val allTags by vm.allTags.collectAsState()
    val sortOption by vm.sortOption.collectAsState()

    var selectedTab by remember { mutableStateOf(ListTab.TODAY) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var filteredPriority by remember { mutableStateOf<Int?>(null) }

    val today = LocalDate.now()

    val displayedTasks = remember(tasks, selectedTab, searchQuery, filteredPriority) {
        val withSearch = tasks.filter { task ->
            if (searchQuery.isBlank()) true
            else task.title.contains(searchQuery, ignoreCase = true)
        }
        val withTab = when (selectedTab) {
            ListTab.TODAY -> withSearch.filter { task ->
                when (task.scheduleType) {
                    ScheduleType.RECURRING -> RecurrenceCalculator.occursOn(task, today)
                    else -> try { LocalDate.parse(task.startDate) == today } catch (_: Exception) { false }
                } && !task.isCompleted && !task.isIndefinite
            }
            ListTab.WEEK -> {
                val weekEnd = today.plusDays(6)
                withSearch.filter { task ->
                    val d = try { LocalDate.parse(task.startDate) } catch (_: Exception) { null }
                    d != null && !d.isBefore(today) && !d.isAfter(weekEnd) && !task.isCompleted && !task.isIndefinite
                }
            }
            ListTab.INDEFINITE -> withSearch.filter { it.isIndefinite && !it.isCompleted }
            ListTab.DONE -> withSearch.filter { it.isCompleted }
            ListTab.ALL -> withSearch.filter { !it.isCompleted }
        }
        withTab.filter { task ->
            filteredPriority == null || task.priority == filteredPriority
        }
    }

    val grouped = remember(displayedTasks) {
        displayedTasks.groupBy { task ->
            when {
                task.isIndefinite -> "無期限"
                task.scheduleType == ScheduleType.RECURRING -> today.toString()
                else -> task.startDate
            }
        }.toSortedMap { a, b ->
            when {
                a == b -> 0
                a == "無期限" -> 1
                b == "無期限" -> -1
                else -> a.compareTo(b)
            }
        }
    }

    Column {
        TopAppBar(
            title = {
                if (showSearch) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("件名で検索…", fontSize = 13.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(end = 8.dp).height(48.dp),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp)
                    )
                } else {
                    Text("予定一覧", fontWeight = FontWeight.Bold)
                }
            },
            actions = {
                IconButton(onClick = { showSearch = !showSearch; if (!showSearch) searchQuery = "" }) {
                    Icon(Icons.Default.Search, null)
                }
                Box {
                    IconButton(onClick = { showSortMenu = true }) { Icon(Icons.Default.Sort, null) }
                    DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                        listOf(
                            SortOption.DATE_ASC to "日付（昇順）", SortOption.DATE_DESC to "日付（降順）",
                            SortOption.PRIORITY_HIGH to "優先度（高→低）", SortOption.PRIORITY_LOW to "優先度（低→高）",
                            SortOption.TITLE_ASC to "名前（昇順）", SortOption.TITLE_DESC to "名前（降順）"
                        ).forEach { (opt, label) ->
                            DropdownMenuItem(
                                text = { Text(label, fontSize = 13.sp, fontWeight = if (sortOption == opt) FontWeight.Bold else FontWeight.Normal) },
                                onClick = { vm.setSortOption(opt); showSortMenu = false }
                            )
                        }
                    }
                }
            }
        )

        TabRow(selectedTabIndex = selectedTab.ordinal, containerColor = MaterialTheme.colorScheme.surface, divider = {}) {
            ListTab.entries.forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    text = { Text(tab.label, fontSize = 12.sp, fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal) }
                )
            }
        }

        ScrollableTabRow(selectedTabIndex = 0, edgePadding = 14.dp, containerColor = MaterialTheme.colorScheme.surface, divider = {}, indicator = {}, modifier = Modifier.height(44.dp)) {
            FilterChip(selected = filteredPriority == null, onClick = { filteredPriority = null }, label = { Text("すべて", fontSize = 11.sp) }, modifier = Modifier.padding(end = 4.dp))
            listOf(0 to "🔴 高", 1 to "🟡 中", 2 to "🟢 低").forEach { (pri, label) ->
                FilterChip(selected = filteredPriority == pri, onClick = { filteredPriority = if (filteredPriority == pri) null else pri }, label = { Text(label, fontSize = 11.sp) }, modifier = Modifier.padding(end = 4.dp))
            }
        }

        Row(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)).padding(horizontal = 14.dp, vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("${displayedTasks.size} 件", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(today.format(DateTimeFormatter.ofPattern("yyyy年M月d日(E)", Locale.JAPANESE)), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (displayedTasks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "予定がありません", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp) // 下部に余白を追加
            ) {
                grouped.forEach { (dateStr, dateTasks) ->
                    stickyHeader(key = "header_$dateStr") {
                        val headerText = try {
                            if (dateStr == "無期限") "無期限"
                            else {
                                val d = LocalDate.parse(dateStr)
                                val dow = d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.JAPANESE)
                                when {
                                    d == today -> "今日  ${d.format(DateTimeFormatter.ofPattern("M/d"))}（$dow）"
                                    d == today.plusDays(1) -> "明日  ${d.format(DateTimeFormatter.ofPattern("M/d"))}（$dow）"
                                    else -> d.format(DateTimeFormatter.ofPattern("M月d日（E）", Locale.JAPANESE))
                                }
                            }
                        } catch (_: Exception) { dateStr }
                        Row(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(horizontal = 14.dp, vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(text = headerText, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
                            Text(text = "${dateTasks.size}件", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f))
                        }
                        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                    items(dateTasks, key = { it.id }) { task ->
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                when (value) {
                                    SwipeToDismissBoxValue.EndToStart -> { vm.softDelete(task); true }
                                    SwipeToDismissBoxValue.StartToEnd -> { vm.toggleComplete(task); false }
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
                            TaskRowItem(task = task, tags = allTags, onComplete = { vm.toggleComplete(task) }, onClick = { navController.navigate(Screen.TaskDetail.createRoute(task.id)) })
                        }
                    }
                }
            }
        }
    }
}
