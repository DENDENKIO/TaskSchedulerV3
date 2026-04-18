package com.example.taskschedulerv3.ui.schedulelist

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.automirrored.filled.Sort
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
import com.example.taskschedulerv3.ui.components.DisplayMode
import com.example.taskschedulerv3.ui.components.TaskRowItem
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ScheduleListScreenHighDensity(
    navController: NavController,
    onAddTask: () -> Unit,
    vm: ScheduleListViewModel = viewModel()
) {
    val uiTasks by vm.uiTasks.collectAsState() // ステップ8
    val drafts by vm.drafts.collectAsState()
    val allTags by vm.allTags.collectAsState()
    val sortOption by vm.sortOption.collectAsState()
    val displayMode by vm.displayMode.collectAsState()
    val selectedTagId by vm.selectedTagId.collectAsState()
    val taskTagMap by vm.taskTagMap.collectAsState()

    var showSortMenu by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    val searchQuery by vm.searchQuery.collectAsState()

    val today = LocalDate.now()

    val finalTasks = remember(uiTasks) {
        uiTasks.map { it.task }
    }

    val grouped = remember(uiTasks, displayMode) {
        if (displayMode == DisplayMode.DRAFT) emptyMap<String, List<TaskListItemUiModel>>()
        else {
            uiTasks.groupBy { model ->
                val task = model.task
                when {
                    task.isIndefinite -> "無期限"
                    task.scheduleType == ScheduleType.RECURRING -> "繰り返し"
                    else -> task.startDate
                }
            }.toSortedMap { a, b ->
                when {
                    a == b -> 0
                    a == "無期限" -> 1
                    b == "無期限" -> -1
                    a == "繰り返し" -> if (b == "無期限") -1 else 1
                    b == "繰り返し" -> if (a == "無期限") 1 else -1
                    else -> a.compareTo(b)
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            title = {
                if (showSearch) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { vm.setSearchQuery(it) },
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
                IconButton(onClick = { navController.navigate(Screen.CompletedTasks.route) }) {
                    Icon(Icons.Default.History, "完了した予定")
                }
                IconButton(onClick = { showSearch = !showSearch; if (!showSearch) vm.setSearchQuery("") }) {
                    Icon(Icons.Default.Search, null)
                }
                Box {
                    IconButton(onClick = { showSortMenu = true }) { Icon(Icons.AutoMirrored.Filled.Sort, null) }
                    DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                        Text("   表示モード", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        DisplayMode.entries.forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(mode.label, fontSize = 13.sp, fontWeight = if (displayMode == mode) FontWeight.Bold else FontWeight.Normal) },
                                onClick = { vm.setDisplayMode(mode); showSortMenu = false }
                            )
                        }
                        HorizontalDivider()
                        Text("   並べ替え", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        listOf(
                            SortOption.DATE_ASC to "日付（昇順）", SortOption.DATE_DESC to "日付（降順）",
                            SortOption.PRIORITY_HIGH to "優先度（高→低）", SortOption.PRIORITY_LOW to "優先度（低→高）",
                            SortOption.TITLE_ASC to "名前（昇順）", SortOption.TITLE_DESC to "名前（順不問）"
                        ).forEach { (opt, label) ->
                            DropdownMenuItem(
                                text = { Text(label, fontSize = 13.sp, fontWeight = if (sortOption == opt) FontWeight.Bold else FontWeight.Normal) },
                                onClick = { vm.setSortOption(opt); showSortMenu = false }
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("完了した予定を表示", fontSize = 13.sp) },
                            onClick = { navController.navigate(Screen.CompletedTasks.route); showSortMenu = false }
                        )
                    }
                }
            }
        )

        // 表示モード TabRow を削除 (メニューに移動)

        // フィルタ部分 (タグ・優先度のみ)
        ScrollableTabRow(
            selectedTabIndex = 0,
            edgePadding = 12.dp,
            containerColor = MaterialTheme.colorScheme.surface,
            divider = {},
            indicator = {},
            modifier = Modifier.height(44.dp)
        ) {
            Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                allTags.forEach { tag ->
                    FilterChip(
                        selected = selectedTagId == tag.id,
                        onClick = { vm.setTagFilter(if (selectedTagId == tag.id) null else tag.id) },
                        label = { Text(tag.name, fontSize = 11.sp) },
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)).padding(horizontal = 14.dp, vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            val count = if (displayMode == DisplayMode.DRAFT) drafts.size else finalTasks.size
            Text("$count 件", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(today.format(DateTimeFormatter.ofPattern("yyyy年M月d日(E)", Locale.JAPANESE)), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (displayMode == DisplayMode.DRAFT) {
            if (drafts.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "仮登録はありません", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
                    items(drafts, key = { "draft_${it.id}" }) { draft ->
                        ListItem(
                            headlineContent = { Text(draft.title, fontSize = 13.sp, fontWeight = FontWeight.Bold) },
                            supportingContent = { Text(draft.tagIds?.takeIf { it.isNotEmpty() } ?: "タグなし", fontSize = 10.sp) },
                            modifier = Modifier.clickable { navController.navigate(Screen.QuickDraftEdit.createRoute(draft.id)) }
                        )
                        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    }
                }
            }
        } else {
            if (uiTasks.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "予定がありません", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    grouped.forEach { (dateStr, dateTasks) ->
                        stickyHeader(key = "header_$dateStr") {
                            val headerText = try {
                                when (dateStr) {
                                    "無期限" -> "無期限"
                                    "繰り返し" -> "繰り返し予定"
                                    else -> {
                                        val d = LocalDate.parse(dateStr)
                                        val dow = d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.JAPANESE)
                                        when {
                                            d == today -> "今日  ${d.format(DateTimeFormatter.ofPattern("M/d"))}（$dow）"
                                            d == today.plusDays(1) -> "明日  ${d.format(DateTimeFormatter.ofPattern("M/d"))}（$dow）"
                                            else -> d.format(DateTimeFormatter.ofPattern("M月d日（E）", Locale.JAPANESE))
                                        }
                                    }
                                }
                            } catch (_: Exception) { dateStr }
                            Row(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(horizontal = 14.dp, vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(text = headerText, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
                                Text(text = "${dateTasks.size}件", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f))
                            }
                            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                        items(dateTasks, key = { it.task.id }) { model ->
                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { value ->
                                    when (value) {
                                        SwipeToDismissBoxValue.EndToStart -> { vm.softDelete(model.task); true }
                                        SwipeToDismissBoxValue.StartToEnd -> { vm.toggleComplete(model.task); false }
                                        else -> false
                                    }
                                }
                            )
                            SwipeToDismissBox(
                                state = dismissState,
                                backgroundContent = {
                                    val color by animateColorAsState(
                                        when (dismissState.targetValue) {
                                            SwipeToDismissBoxValue.EndToStart -> Color(0xFFE53935)
                                            SwipeToDismissBoxValue.StartToEnd -> Color(0xFF43A047)
                                            else -> Color.Transparent
                                        }, label = "swipe_bg"
                                    )
                                    Box(modifier = Modifier.fillMaxSize().background(color).padding(horizontal = 24.dp), contentAlignment = if (dismissState.targetValue == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd) {
                                        val icon = if (dismissState.targetValue == SwipeToDismissBoxValue.StartToEnd) Icons.Default.Check else Icons.Default.Delete
                                        if (color != Color.Transparent) {
                                            Icon(icon, null, tint = Color.White)
                                        }
                                    }
                                },
                                content = {
                                    com.example.taskschedulerv3.ui.components.TaskRowItemHighDensity(
                                        uiModel = model,
                                        tags = taskTagMap[model.task.id] ?: emptyList(),
                                        onComplete = { vm.toggleComplete(model.task) },
                                        onClick = { navController.navigate(Screen.TaskDetail.createRoute(model.task.id)) }
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
