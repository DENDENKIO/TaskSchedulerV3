package com.example.taskschedulerv3.ui.roadmap

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.taskschedulerv3.data.model.Task
import java.time.LocalDate

/**
 * ロードマップ編集画面 (仕様書 20 章準拠)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoadmapEditScreen(
    navController: NavController,
    taskId: Int,
    vm: RoadmapEditViewModel = viewModel()
) {
    val task by vm.task.collectAsState()
    val editorItems by vm.editorItems.collectAsState()
    val hasUnsavedChanges by vm.hasUnsavedChanges.collectAsState()
    val focusedItemLocalId by vm.focusedItemLocalId.collectAsState()
    val saveSuccess by vm.saveSuccess.collectAsState()
    val isSaving by vm.isSaving.collectAsState()

    var showDiscardDialog by remember { mutableStateOf(false) }
    val lazyListState = rememberLazyListState()

    LaunchedEffect(taskId) { vm.loadTask(taskId) }

    // 離脱保護 (仕様書 17 章)
    BackHandler(enabled = hasUnsavedChanges) {
        showDiscardDialog = true
    }

    LaunchedEffect(saveSuccess) {
        if (saveSuccess) navController.popBackStack()
    }

    if (showDiscardDialog) {
        DiscardChangesDialog(
            onConfirm = {
                showDiscardDialog = false
                navController.popBackStack()
            },
            onDismiss = { showDiscardDialog = false }
        )
    }

    Scaffold(
        topBar = {
            RoadmapEditTopBar(
                hasUnsavedChanges = hasUnsavedChanges,
                isSaving = isSaving,
                onBack = {
                    if (hasUnsavedChanges) showDiscardDialog = true
                    else navController.popBackStack()
                },
                onSave = { vm.save() }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                // START (仕様書 11.1)
                task?.let { t ->
                    item {
                        StartRoadmapRow(task = t, hasSteps = editorItems.isNotEmpty())
                    }
                }

                // Intermediate & GOAL steps (仕様書 11.2, 11.3)
                val visibleItems = editorItems.filter { !it.isDeleted }
                itemsIndexed(visibleItems, key = { _, item -> item.localId }) { index, item ->
                    val isGoal = index == visibleItems.size - 1
                    EditableRoadmapStepRow(
                        item = item,
                        index = index,
                        isGoal = isGoal,
                        isFocused = item.localId == focusedItemLocalId,
                        onTitleChange = { vm.updateTitle(item.localId, it) },
                        onDateUpdate = { vm.updateDate(item.localId, it) },
                        onDelete = { vm.deleteStep(item.localId) },
                        onMove = { from, to -> vm.moveStep(from, to) },
                        onFocusConsumed = { vm.clearFocusedItem() }
                    )
                }

                // 仕様書 8.1: 追加ボタン
                item {
                    AddRoadmapStepButton(onClick = { vm.addStep() })
                }
                
                item { Spacer(modifier = Modifier.height(100.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoadmapEditTopBar(
    hasUnsavedChanges: Boolean,
    isSaving: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit
) {
    TopAppBar(
        title = { Text("ロードマップ編集") },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
            }
        },
        actions = {
            if (isSaving) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(16.dp))
            } else {
                TextButton(
                    onClick = onSave,
                    enabled = hasUnsavedChanges
                ) {
                    Text("保存", fontWeight = FontWeight.Bold)
                }
            }
        }
    )
}

@Composable
fun StartRoadmapRow(task: Task, hasSteps: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(48.dp)) {
            RoadmapTimelineIcon(icon = Icons.Default.PlayArrow, color = MaterialTheme.colorScheme.primary)
            if (hasSteps) RoadmapConnector()
        }
        Column(modifier = Modifier.padding(bottom = 16.dp).weight(1f)) {
            Text(task.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("開始点: ${task.startDate}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
    }
}

@Composable
fun EditableRoadmapStepRow(
    item: RoadmapStepEditorItem,
    index: Int,
    isGoal: Boolean,
    isFocused: Boolean,
    onTitleChange: (String) -> Unit,
    onDateUpdate: (LocalDate?) -> Unit,
    onDelete: () -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onFocusConsumed: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    var showDatePicker by remember { mutableStateOf(false) }
    
    // ドラッグ状態管理
    var dragOffsetY by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    val itemHeight = 72.dp // およその行高さ

    if (isFocused) {
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
            onFocusConsumed()
        }
    }

    if (showDatePicker) {
        RoadmapDatePickerDialog(
            initialDate = item.targetDate ?: LocalDate.now(),
            onDismiss = { showDatePicker = false },
            onDateSelected = { 
                onDateUpdate(it)
                showDatePicker = false
            }
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .offset(y = dragOffsetY.dp)
            .zIndex(if (isDragging) 1f else 0f)
            .background(if (isDragging) MaterialTheme.colorScheme.surface.copy(alpha = 0.9f) else Color.Transparent),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // アイコン・コネクタ (仕様書 11.2, 11.3)
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(48.dp)) {
            RoadmapConnector()
            RoadmapTimelineIcon(
                icon = if (isGoal) Icons.Default.Adjust else Icons.Default.RadioButtonUnchecked,
                color = if (item.isCompleted) Color.Gray else MaterialTheme.colorScheme.secondary
            )
            if (!isGoal) RoadmapConnector() else Spacer(Modifier.height(16.dp))
        }

        // 入力フィールド (仕様書 6.1)
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 4.dp, horizontal = 4.dp)
                .background(
                    if (isDragging) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    MaterialTheme.shapes.small
                )
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.DragHandle, null,
                modifier = Modifier
                    .size(24.dp)
                    .pointerInput(Unit) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { isDragging = true },
                            onDragEnd = { 
                                isDragging = false
                                dragOffsetY = 0f
                            },
                            onDragCancel = { 
                                isDragging = false
                                dragOffsetY = 0f
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                // ピクセル量を密度(density)で割って dp に変換 (仕様書 1:1 同期)
                                val deltaDp = dragAmount.y / density
                                dragOffsetY += deltaDp
                                
                                // しきい値を超えたら移動 (アイテム高さ 72dp の約 8 割)
                                val threshold = 56f
                                if (dragOffsetY > threshold) {
                                    onMove(index, index + 1)
                                    // 下へ移動したので、1行分(72dp)のオフセットを戻す
                                    dragOffsetY -= 72f
                                } else if (dragOffsetY < -threshold) {
                                    onMove(index, index - 1)
                                    // 上へ移動したので、1行分(72dp)のオフセットを戻す
                                    dragOffsetY += 72f
                                }
                            }
                        )
                    },
                tint = if (isDragging) MaterialTheme.colorScheme.primary else Color.Gray
            )
            Spacer(Modifier.width(8.dp))
            
            BasicTextField(
                value = item.title,
                onValueChange = onTitleChange,
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = if (item.isCompleted) Color.Gray else MaterialTheme.colorScheme.onSurface
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                singleLine = true
            )

            // 日付バッジ (仕様書 7.1)
            Surface(
                onClick = { showDatePicker = true },
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.extraSmall,
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                Text(
                    item.targetDate?.let { "${it.monthValue}/${it.dayOfMonth}" } ?: "日付",
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall
                )
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun RoadmapTimelineIcon(icon: ImageVector, color: Color) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(24.dp),
        tint = color
    )
}

@Composable
fun RoadmapConnector() {
    Box(
        modifier = Modifier
            .width(2.dp)
            .fillMaxHeight()
            .background(Color.LightGray)
    )
}

@Composable
fun AddRoadmapStepButton(onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.padding(start = 32.dp, top = 8.dp)
    ) {
        Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("ステップを追加")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoadmapDatePickerDialog(
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onDateSelected: (LocalDate) -> Unit
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialDate.atStartOfDay(java.time.ZoneId.of("UTC")).toInstant().toEpochMilli()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                datePickerState.selectedDateMillis?.let {
                    val date = java.time.Instant.ofEpochMilli(it)
                        .atZone(java.time.ZoneId.of("UTC")).toLocalDate()
                    onDateSelected(date)
                }
            }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}

@Composable
fun DiscardChangesDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("情報の破棄") },
        text = { Text("入力中のロードマップ設定を破棄して閉じますか？") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("破棄", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        }
    )
}
