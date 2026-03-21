package com.example.taskschedulerv3.ui.photo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.taskschedulerv3.data.model.Tag
import com.example.taskschedulerv3.ui.tag.parseColor
import com.example.taskschedulerv3.util.PhotoFileManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoDetailScreen(
    navController: NavController,
    photoId: Int,
    vm: PhotoDetailViewModel = viewModel()
) {
    val photo by vm.photo.collectAsState()
    val photoTags by vm.photoTags.collectAsState()
    val allTags by vm.allTags.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showTagDialog by remember { mutableStateOf(false) }

    LaunchedEffect(photoId) { vm.loadPhoto(photoId) }

    // Pinch-zoom state
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // Delete dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("写真を削除") },
            text = { Text("この写真を削除しますか？この操作は元に戻せません。") },
            confirmButton = {
                TextButton(onClick = {
                    photo?.let { vm.deletePhoto(it) }
                    showDeleteDialog = false
                    navController.popBackStack()
                }) { Text("削除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("キャンセル") } }
        )
    }

    // Memo/title edit dialog
    if (showEditDialog && photo != null) {
        MemoEditDialog(
            initialTitle = photo!!.title ?: "",
            initialMemo = photo!!.memo ?: "",
            onConfirm = { title, memo ->
                vm.updateMemo(title, memo)
                showEditDialog = false
            },
            onDismiss = { showEditDialog = false }
        )
    }

    // Tag edit dialog
    if (showTagDialog) {
        PhotoTagEditDialog(
            allTags = allTags,
            currentTagIds = photoTags.map { it.id }.toSet(),
            onConfirm = { tagIds -> vm.setTags(tagIds); showTagDialog = false },
            onDismiss = { showTagDialog = false }
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (photo != null) {
            AsyncImage(
                model = PhotoFileManager.pathToUri(photo!!.imagePath),
                contentDescription = photo!!.title ?: photo!!.date,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            offset = if (scale > 1f) offset + pan else Offset.Zero
                        }
                    }
            )

            // Top: back / edit / tag / delete
            Row(
                modifier = Modifier.fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp)
                    .align(Alignment.TopStart),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                }
                Row {
                    IconButton(onClick = { showTagDialog = true }) {
                        Icon(Icons.Default.Label, contentDescription = "タグ編集", tint = Color.White)
                    }
                    IconButton(onClick = { showEditDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "メモ編集", tint = Color.White)
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, null, tint = Color.White)
                    }
                }
            }

            // Bottom: date, title, tags, memo
            Column(
                modifier = Modifier.fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .align(Alignment.BottomStart)
            ) {
                Text(photo!!.date, style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.7f))
                photo!!.title?.let {
                    Text(it, style = MaterialTheme.typography.bodyLarge, color = Color.White,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                // Tag chips
                if (photoTags.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        photoTags.forEach { tag ->
                            val bgColor = try { Color(android.graphics.Color.parseColor(tag.color)) }
                                catch (e: Exception) { Color.Gray }
                            Surface(shape = RoundedCornerShape(4.dp), color = bgColor) {
                                Text(tag.name, color = Color.White,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                photo!!.memo?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 3, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp))
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        }
    }
}

@Composable
fun MemoEditDialog(
    initialTitle: String,
    initialMemo: String,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }
    var memo by remember { mutableStateOf(initialMemo) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("メモ編集") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("タイトル") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = memo,
                    onValueChange = { memo = it },
                    label = { Text("メモ") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                    maxLines = 5
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(title, memo) }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } }
    )
}

@Composable
fun PhotoTagEditDialog(
    allTags: List<Tag>,
    currentTagIds: Set<Int>,
    onConfirm: (List<Int>) -> Unit,
    onDismiss: () -> Unit
) {
    val selected = remember { mutableStateOf(currentTagIds) }
    val largeTags = allTags.filter { it.level == 1 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("タグを設定") },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                items(largeTags) { large ->
                    val midTags = allTags.filter { it.parentId == large.id }
                    if (midTags.isEmpty()) {
                        TagCheckRowSimple(tag = large, selected = large.id in selected.value) {
                            val s = selected.value.toMutableSet()
                            if (large.id in s) s.remove(large.id) else s.add(large.id)
                            selected.value = s
                        }
                    } else {
                        Text(large.name, style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 8.dp, top = 8.dp))
                        midTags.forEach { mid ->
                            val smallTags = allTags.filter { it.parentId == mid.id }
                            if (smallTags.isEmpty()) {
                                TagCheckRowSimple(tag = mid, selected = mid.id in selected.value,
                                    indent = 16.dp) {
                                    val s = selected.value.toMutableSet()
                                    if (mid.id in s) s.remove(mid.id) else s.add(mid.id)
                                    selected.value = s
                                }
                            } else {
                                Text(mid.name, style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(start = 24.dp, top = 4.dp))
                                smallTags.forEach { small ->
                                    TagCheckRowSimple(tag = small, selected = small.id in selected.value,
                                        indent = 32.dp) {
                                        val s = selected.value.toMutableSet()
                                        if (small.id in s) s.remove(small.id) else s.add(small.id)
                                        selected.value = s
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected.value.toList()) }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } }
    )
}

@Composable
private fun TagCheckRowSimple(
    tag: Tag,
    selected: Boolean,
    indent: androidx.compose.ui.unit.Dp = 0.dp,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onToggle() }
            .padding(start = indent + 8.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Checkbox(checked = selected, onCheckedChange = { onToggle() })
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(parseColor(tag.color)))
        Text(tag.name, style = MaterialTheme.typography.bodyMedium)
    }
}
