package com.example.taskschedulerv3.ui.quickdraft

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.taskschedulerv3.data.model.QuickDraftTask
import com.example.taskschedulerv3.util.PhotoFileManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickDraftEditScreen(
    navController: NavController,
    draftId: Int,
    vm: QuickDraftViewModel = viewModel()
) {
    var draft by remember { mutableStateOf<QuickDraftTask?>(null) }
    var title by remember { mutableStateOf("") }
    var memo by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf("") }
    var startTime by remember { mutableStateOf("") }
    var priority by remember { mutableIntStateOf(1) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showConvertDialog by remember { mutableStateOf(false) }

    val convertSuccess by vm.convertSuccess.collectAsState()

    // ドラフト読み込み
    LaunchedEffect(draftId) {
        val loaded = vm.repo.getById(draftId)
        draft = loaded
        loaded?.let {
            title = it.title
            memo = it.description ?: ""
        }
    }

    // 本登録後に前画面へ戻る
    LaunchedEffect(convertSuccess) {
        if (convertSuccess) {
            vm.clearConvertSuccess()
            navController.popBackStack()
        }
    }

    // 削除確認ダイアログ
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("仮登録を削除") },
            text = { Text("この仮登録を削除しますか？") },
            confirmButton = {
                TextButton(onClick = {
                    draft?.let { vm.deleteDraft(it) }
                    showDeleteDialog = false
                    navController.popBackStack()
                }) { Text("削除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("キャンセル") }
            }
        )
    }

    // 本登録確認ダイアログ
    if (showConvertDialog) {
        AlertDialog(
            onDismissRequest = { showConvertDialog = false },
            title = { Text("本登録") },
            text = { Text("「$title」を通常タスクとして登録しますか？") },
            confirmButton = {
                TextButton(onClick = {
                    // まず保存してから変換
                    draft?.let {
                        vm.updateDraft(it.copy(title = title, description = memo.ifBlank { null }))
                        vm.convertToTask(it, startDate.ifBlank { java.time.LocalDate.now().toString() }, priority)
                    }
                    showConvertDialog = false
                }) { Text("本登録する") }
            },
            dismissButton = {
                TextButton(onClick = { showConvertDialog = false }) { Text("キャンセル") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("仮登録を編集") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                },
                actions = {
                    // 保存ボタン
                    IconButton(onClick = {
                        draft?.let {
                            vm.updateDraft(it.copy(title = title, description = memo.ifBlank { null }))
                            navController.popBackStack()
                        }
                    }, enabled = title.isNotBlank()) {
                        Icon(Icons.Default.Done, contentDescription = "保存")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 写真サムネイル表示
            draft?.photoPath?.let { path ->
                AsyncImage(
                    model = PhotoFileManager.pathToUri(path),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("タスク名") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = memo,
                onValueChange = { memo = it },
                label = { Text("メモ") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )

            OutlinedTextField(
                value = startDate,
                onValueChange = { startDate = it },
                label = { Text("日付 (yyyy-MM-dd)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(java.time.LocalDate.now().toString()) }
            )

            OutlinedTextField(
                value = startTime,
                onValueChange = { startTime = it },
                label = { Text("時刻 (HH:mm)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("09:00") }
            )

            // 優先度
            Column {
                Text("優先度", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to "高", 1 to "中", 2 to "低").forEach { (v, label) ->
                        FilterChip(
                            selected = priority == v,
                            onClick = { priority = v },
                            label = { Text(label) }
                        )
                    }
                }
            }

            // OCR結果が存在する場合は表示
            draft?.ocrText?.let { ocr ->
                if (ocr.isNotBlank()) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("OCR読み取り結果", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary)
                            Text(ocr, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            HorizontalDivider()

            // アクションボタン行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("削除")
                }
                Button(
                    onClick = { showConvertDialog = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C6AFF))
                ) {
                    Icon(Icons.Default.Upload, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("本登録")
                }
            }
        }
    }
}
