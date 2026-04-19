package com.example.taskschedulerv3.ui.quickdraft

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.taskschedulerv3.data.model.QuickDraftTask
import com.example.taskschedulerv3.navigation.Screen
import com.example.taskschedulerv3.ui.schedulelist.ScheduleListViewModel
import com.example.taskschedulerv3.util.PhotoFileManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickDraftListScreen(
    navController: NavController,
    vm: QuickDraftViewModel = viewModel(),
    listVm: ScheduleListViewModel = viewModel()
) {
    val drafts by vm.drafts.collectAsState()
    var deleteTarget by remember { mutableStateOf<QuickDraftTask?>(null) }
    var convertTarget by remember { mutableStateOf<QuickDraftTask?>(null) }
    var showCaptureSheet by remember { mutableStateOf(false) }

    val convertSuccess by vm.convertSuccess.collectAsState()

    // 本登録成功時
    LaunchedEffect(convertSuccess) {
        if (convertSuccess) {
            vm.clearConvertSuccess()
        }
    }

    // 削除確認ダイアログ
    deleteTarget?.let { draft ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("仮登録を削除") },
            text = { Text("「${draft.title}」を削除しますか？") },
            confirmButton = {
                TextButton(onClick = { vm.deleteDraft(draft); deleteTarget = null }) {
                    Text("削除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("キャンセル") }
            }
        )
    }

    // 本登録確認ダイアログ
    convertTarget?.let { draft ->
        AlertDialog(
            onDismissRequest = { convertTarget = null },
            title = { Text("本登録") },
            text = { Text("「${draft.title}」を通常タスクとして登録しますか？") },
            confirmButton = {
                TextButton(onClick = { vm.convertToTask(draft); convertTarget = null }) {
                    Text("登録する")
                }
            },
            dismissButton = {
                TextButton(onClick = { convertTarget = null }) { Text("キャンセル") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("仮登録一覧") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCaptureSheet = true }) {
                Icon(Icons.Default.CameraAlt, contentDescription = "仮登録を追加")
            }
        }
    ) { padding ->
        if (drafts.isEmpty()) {
            Box(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("仮登録がありません", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "カメラボタンから写真を撮影して仮登録を作成",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(drafts, key = { it.id }) { draft ->
                    QuickDraftListItem(
                        draft = draft,
                        onEdit = { navController.navigate(Screen.QuickDraftEdit.createRoute(draft.id)) },
                        onConvert = { convertTarget = draft },
                        onDelete = { deleteTarget = draft }
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    if (showCaptureSheet) {
        val allTags by listVm.allTags.collectAsState()
        QuickDraftCaptureSheet(
            allTags = allTags,
            onSave = { photoPath, tagIds ->
                vm.createSmartDraft(photoPath = photoPath, tagIds = tagIds)
            },
            onDismiss = { showCaptureSheet = false }
        )
    }
}

@Composable
private fun QuickDraftListItem(
    draft: QuickDraftTask,
    onEdit: () -> Unit,
    onConvert: () -> Unit,
    onDelete: () -> Unit
) {
    val dateStr = remember(draft.createdAt) {
        SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.JAPANESE).format(Date(draft.createdAt))
    }

    ListItem(
        headlineContent = {
            Text(
                draft.title,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Column {
                Text(
                    dateStr,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!draft.ocrText.isNullOrBlank()) {
                    Text(
                        draft.ocrText!!,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // 設定されているタグを表示
                val listVm: ScheduleListViewModel = viewModel()
                val allTags by listVm.allTags.collectAsState()
                val tagNames = remember(draft.tagIds, allTags) {
                    val ids = draft.tagIds?.split(",")?.filter { it.isNotBlank() }?.map { it.toInt() } ?: emptyList()
                    allTags.filter { it.id in ids }.map { it.name }
                }
                if (tagNames.isNotEmpty()) {
                    Text(
                        tagNames.joinToString(", "),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF8B7CF6)
                    )
                }
            }
        },
        leadingContent = {
            if (!draft.photoPath.isNullOrBlank()) {
                AsyncImage(
                    model = PhotoFileManager.pathToUri(draft.photoPath!!),
                    contentDescription = null,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CameraAlt, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        trailingContent = {
            Row {
                IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Edit, "編集", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onConvert, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Upload, "本登録", tint = Color(0xFF7C6AFF), modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Delete, "削除", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                }
            }
        }
    )
}
