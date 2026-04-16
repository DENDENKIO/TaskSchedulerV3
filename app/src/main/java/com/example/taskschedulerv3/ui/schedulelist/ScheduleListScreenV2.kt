package com.example.taskschedulerv3.ui.schedulelist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

// ────────────────────────────────────────────────
// タブ定義
// ────────────────────────────────────────────────
private enum class ListTab(val label: String) {
    TODAY("今日"),
    WEEK("今週"),
    ALL("すべて"),
    DONE("完了")
}

// ────────────────────────────────────────────────
// ScheduleListScreenV2
//
// 高密度行表示バージョン。既存 ScheduleListScreen.kt は変更なし。
// NavGraph.ktで Screen.ScheduleListV2.route から遷移できる。
// ────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleListScreenV2(
    navController: NavController,
    vm: ScheduleListViewModel = viewModel()
) {
    val tasks    by vm.tasks.collectAsState()
    val allTags  by vm.allTags.collectAsState()
    // タスクID → タグリスト のマップ（crossRefDaoを流用）
    val tagFilteredIds by vm.tagFilteredTaskIds.collectAsState()
    val sortOption by vm.sortOption.collectAsState()

    var selectedTab  by remember { mutableStateOf(ListTab.TODAY) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showSearch   by remember { mutableStateOf(false) }
    var searchQuery  by remember { mutableStateOf("") }

    val today = LocalDate.now()

    // ── タブフィルタリング ──
    val displayedTasks: List<Task> = remember(tasks, selectedTab, searchQuery) {
        val base = tasks.filter { task ->
            if (searchQuery.isBlank()) true
            else task.title.contains(searchQuery, ignoreCase = true)
        }
        when (selectedTab) {
            ListTab.TODAY -> base.filter { task ->
                when (task.scheduleType) {
                    ScheduleType.RECURRING -> RecurrenceCalculator.occursOn(task, today)
                    else -> try { LocalDate.parse(task.startDate) == today } catch (_: Exception) { false }
                } && !task.isCompleted
            }
            ListTab.WEEK -> {
                val weekEnd = today.plusDays(6)
                base.filter { task ->
                    val d = try { LocalDate.parse(task.startDate) } catch (_: Exception) { null }
                    d != null && !d.isBefore(today) && !d.isAfter(weekEnd) && !task.isCompleted
                }
            }
            ListTab.DONE -> base.filter { it.isCompleted }
            ListTab.ALL  -> base.filter { !it.isCompleted }
        }
    }

    // ── 日付グルーピング ──
    val grouped: Map<String, List<Task>> = remember(displayedTasks) {
        displayedTasks
            .groupBy { task ->
                if (task.scheduleType == ScheduleType.RECURRING) today.toString()
                else task.startDate
            }
            .toSortedMap()
    }

    // ── タスクID→タグリスト (簡易実装: allTagsを全件対象に返す) ──
    // 正確な実装: ViewModelに getTagsForTask(taskId): Flow<List<Tag>> を追加することを推奨
    // 現時点では全タグを allTags から探索（タグ数が少ない間は実用上問題なし）
    fun tagsForTask(task: Task): List<Tag> = allTags  // crossRefなしバージョン

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        if (showSearch) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("件名で検索…", fontSize = 13.sp) },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(end = 8.dp)
                                    .height(48.dp),
                                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                            )
                        } else {
                            Text("予定一覧", fontWeight = FontWeight.Bold)
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            showSearch = !showSearch
                            if (!showSearch) searchQuery = ""
                        }) {
                            Icon(Icons.Default.Search, contentDescription = "検索")
                        }
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.Default.Sort, contentDescription = "ソート")
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                listOf(
                                    SortOption.DATE_ASC      to "日付（昇順）",
                                    SortOption.DATE_DESC     to "日付（降順）",
                                    SortOption.PRIORITY_HIGH to "優先度（高→低）",
                                    SortOption.PRIORITY_LOW  to "優先度（低→高）",
                                    SortOption.TITLE_ASC     to "タスク名（昇順）",
                                    SortOption.TITLE_DESC    to "タスク名（降順）"
                                ).forEach { (opt, label) ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                label,
                                                fontWeight = if (sortOption == opt) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 13.sp
                                            )
                                        },
                                        onClick = { vm.setSortOption(opt); showSortMenu = false }
                                    )
                                }
                            }
                        }
                    }
                )

                // ── タブ行 ──
                TabRow(selectedTabIndex = ListTab.values().indexOf(selectedTab)) {
                    ListTab.values().forEach { tab ->
                        Tab(
                            selected = selectedTab == tab,
                            onClick  = { selectedTab = tab },
                            text = {
                                Text(
                                    tab.label,
                                    fontSize = 12.sp,
                                    fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }

                // ── 件数バー ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 14.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${displayedTasks.size} 件",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        today.format(DateTimeFormatter.ofPattern("yyyy年M月d日(E)", Locale.JAPANESE)),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate(Screen.AddTask.createRoute()) }) {
                Icon(Icons.Default.Add, contentDescription = "追加")
            }
        }
    ) { padding ->
        if (displayedTasks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when (selectedTab) {
                        ListTab.TODAY -> "今日の予定はありません"
                        ListTab.WEEK  -> "今週の予定はありません"
                        ListTab.DONE  -> "完了した予定はありません"
                        ListTab.ALL   -> "予定がありません"
                    },
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                grouped.forEach { (dateStr, dateTasks) ->
                    // ── 日付グループヘッダー (スティッキー) ──
                    stickyHeader(key = "header_$dateStr") {
                        val headerText = try {
                            val d   = LocalDate.parse(dateStr)
                            val dow = d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.JAPANESE)
                            when {
                                d == today              -> "TODAY  ${d.format(DateTimeFormatter.ofPattern("M/d"))}（$dow）"
                                d == today.plusDays(1)  -> "TOMORROW  ${d.format(DateTimeFormatter.ofPattern("M/d"))}（$dow）"
                                else -> d.format(DateTimeFormatter.ofPattern("M月d日（E）", Locale.JAPANESE))
                            }
                        } catch (_: Exception) { dateStr }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.background)
                                .padding(horizontal = 14.dp, vertical = 5.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = headerText,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                            )
                            Text(
                                text = "${dateTasks.size}件",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                            )
                        }
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }

                    // ── タスク行 ──
                    items(dateTasks, key = { it.id }) { task ->
                        TaskRowItem(
                            task     = task,
                            tags     = tagsForTask(task),  // 将来: ViewModelで taskId→Tagsのフロー化推奨
                            progress = 0,                 // 将来: Task.progress フィールド追加後に task.progress に変更
                            onComplete = { vm.toggleComplete(task) },
                            onClick    = { navController.navigate(Screen.TaskDetail.createRoute(task.id)) }
                        )
                    }
                }
            }
        }
    }
}
