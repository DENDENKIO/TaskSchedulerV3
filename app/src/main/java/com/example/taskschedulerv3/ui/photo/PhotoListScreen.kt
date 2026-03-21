package com.example.taskschedulerv3.ui.photo

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.taskschedulerv3.data.model.PhotoMemo
import com.example.taskschedulerv3.data.model.Tag
import com.example.taskschedulerv3.navigation.Screen
import com.example.taskschedulerv3.ui.tag.parseColor
import com.example.taskschedulerv3.util.DateUtils
import com.example.taskschedulerv3.util.PhotoFileManager
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PhotoListScreen(navController: NavController, vm: PhotoListViewModel = viewModel()) {
    val context = LocalContext.current
    val photosByMonth by vm.photosByMonth.collectAsState()
    val today = remember { DateUtils.today() }
    val searchQuery by vm.searchQuery.collectAsState()
    val allTags by vm.allTags.collectAsState()
    val filterTagId by vm.filterTagId.collectAsState()
    val selectedIds by vm.selectedPhotoIds.collectAsState()
    val isSelectionMode by vm.isSelectionMode.collectAsState()

    var showAddMenu by remember { mutableStateOf(false) }
    var showBulkTagDialog by remember { mutableStateOf(false) }
    var showTagFilterDialog by remember { mutableStateOf(false) }
    var tempPhotoFile by remember { mutableStateOf<File?>(null) }

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            tempPhotoFile?.let { file ->
                vm.savePhotoFromCamera(file, today)
                tempPhotoFile = null
            }
        }
    }
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { vm.savePhotoFromGallery(it, today) } }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val (uri, file) = PhotoFileManager.createTempPhotoUri(context)
            tempPhotoFile = file
            cameraLauncher.launch(uri)
        }
    }

    // Bulk tag dialog
    if (showBulkTagDialog) {
        BulkTagDialog(
            allTags = allTags,
            onConfirm = { tagIds -> vm.bulkSetTags(selectedIds, tagIds); showBulkTagDialog = false },
            onDismiss = { showBulkTagDialog = false }
        )
    }

    // Tag filter dialog
    if (showTagFilterDialog) {
        TagFilterPickerDialog(
            allTags = allTags,
            selectedTagId = filterTagId,
            onSelect = { vm.setTagFilter(it); showTagFilterDialog = false },
            onDismiss = { showTagFilterDialog = false }
        )
    }

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = { Text("${selectedIds.size}件選択中") },
                    navigationIcon = {
                        IconButton(onClick = { vm.clearSelection() }) {
                            Icon(Icons.Default.Close, null)
                        }
                    },
                    actions = {
                        TextButton(onClick = { vm.selectAll() }) { Text("全選択") }
                        IconButton(onClick = { showBulkTagDialog = true }) {
                            Icon(Icons.Default.Label, contentDescription = "タグ付け")
                        }
                    }
                )
            } else {
                TopAppBar(title = { Text("写真メモ") })
            }
        },
        floatingActionButton = {
            if (!isSelectionMode) {
                Box {
                    FloatingActionButton(onClick = { showAddMenu = true }) {
                        Icon(Icons.Default.Add, contentDescription = "写真追加")
                    }
                    DropdownMenu(expanded = showAddMenu, onDismissRequest = { showAddMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("カメラで撮影") },
                            onClick = {
                                showAddMenu = false
                                val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                                if (hasPerm) {
                                    val (uri, file) = PhotoFileManager.createTempPhotoUri(context)
                                    tempPhotoFile = file; cameraLauncher.launch(uri)
                                } else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            },
                            leadingIcon = { Icon(Icons.Default.CameraAlt, null) }
                        )
                        DropdownMenuItem(
                            text = { Text("ギャラリーから選択") },
                            onClick = {
                                showAddMenu = false
                                galleryLauncher.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            leadingIcon = { Icon(Icons.Default.Photo, null) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {

            // ── 検索バー ──
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { vm.setSearchQuery(it) },
                placeholder = { Text("タイトル・メモを検索") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) IconButton(onClick = { vm.setSearchQuery("") }) {
                        Icon(Icons.Default.Close, null)
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
            )

            // ── タグフィルタ行 ──
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item {
                    FilterChip(
                        selected = filterTagId == null,
                        onClick = { vm.setTagFilter(null) },
                        label = { Text("すべて") }
                    )
                }
                items(allTags.filter { it.level == 1 }) { tag ->
                    FilterChip(
                        selected = filterTagId == tag.id,
                        onClick = { if (filterTagId == tag.id) vm.setTagFilter(null) else showTagFilterDialog = true },
                        label = { Text(tag.name) },
                        leadingIcon = {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(parseColor(tag.color)))
                        }
                    )
                }
                item {
                    FilterChip(
                        selected = filterTagId != null,
                        onClick = { showTagFilterDialog = true },
                        label = { Text(if (filterTagId != null) "タグ変更" else "タグ指定") },
                        leadingIcon = { Icon(Icons.Default.Label, null, modifier = Modifier.size(14.dp)) }
                    )
                }
            }

            if (filterTagId != null) {
                val tagName = allTags.find { it.id == filterTagId }?.name ?: ""
                Row(
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("タグ: $tagName", style = MaterialTheme.typography.labelMedium)
                    TextButton(onClick = { vm.setTagFilter(null) }) { Text("解除") }
                }
            }

            HorizontalDivider()

            // ── 写真グリッド ──
            if (photosByMonth.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Photo, null, modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                        Spacer(Modifier.height(12.dp))
                        Text("写真メモがありません",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    photosByMonth.forEach { section ->
                        item {
                            Text(
                                text = section.yearMonth.replace("-", "年") + "月",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                        }
                        item {
                            SelectablePhotoGrid(
                                photos = section.photos,
                                selectedIds = selectedIds,
                                isSelectionMode = isSelectionMode,
                                onPhotoClick = { photo ->
                                    if (isSelectionMode) vm.toggleSelection(photo.id)
                                    else navController.navigate(Screen.PhotoDetail.createRoute(photo.id))
                                },
                                onPhotoLongClick = { photo -> vm.toggleSelection(photo.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SelectablePhotoGrid(
    photos: List<PhotoMemo>,
    selectedIds: Set<Int>,
    isSelectionMode: Boolean,
    onPhotoClick: (PhotoMemo) -> Unit,
    onPhotoLongClick: (PhotoMemo) -> Unit
) {
    val columns = 3
    val rows = (photos.size + columns - 1) / columns
    Column(modifier = Modifier.padding(4.dp)) {
        for (row in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (col in 0 until columns) {
                    val idx = row * columns + col
                    if (idx < photos.size) {
                        val photo = photos[idx]
                        val isSelected = photo.id in selectedIds
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .padding(2.dp)
                        ) {
                            AsyncImage(
                                model = PhotoFileManager.pathToUri(photo.imagePath),
                                contentDescription = photo.title ?: photo.date,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(4.dp))
                                    .then(if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)) else Modifier)
                                    .combinedClickable(
                                        onClick = { onPhotoClick(photo) },
                                        onLongClick = { onPhotoLongClick(photo) }
                                    )
                            )
                            if (isSelectionMode) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primary
                                            else Color.White.copy(alpha = 0.7f)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) Icon(
                                        Icons.Default.Check, null,
                                        modifier = Modifier.size(14.dp),
                                        tint = Color.White
                                    )
                                }
                            }
                        }
                    } else {
                        Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun BulkTagDialog(
    allTags: List<Tag>,
    onConfirm: (List<Int>) -> Unit,
    onDismiss: () -> Unit
) {
    val selectedTagIds = remember { mutableStateOf(setOf<Int>()) }
    val largeTags = allTags.filter { it.level == 1 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("タグを付ける") },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                items(largeTags) { large ->
                    val midTags = allTags.filter { it.parentId == large.id }
                    if (midTags.isEmpty()) {
                        TagCheckRow(tag = large, selected = large.id in selectedTagIds.value) {
                            val s = selectedTagIds.value.toMutableSet()
                            if (large.id in s) s.remove(large.id) else s.add(large.id)
                            selectedTagIds.value = s
                        }
                    } else {
                        Text(large.name, style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 2.dp))
                        midTags.forEach { mid ->
                            val smallTags = allTags.filter { it.parentId == mid.id }
                            if (smallTags.isEmpty()) {
                                TagCheckRow(tag = mid, selected = mid.id in selectedTagIds.value,
                                    indent = 16.dp) {
                                    val s = selectedTagIds.value.toMutableSet()
                                    if (mid.id in s) s.remove(mid.id) else s.add(mid.id)
                                    selectedTagIds.value = s
                                }
                            } else {
                                Text(mid.name, style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(start = 24.dp, top = 4.dp))
                                smallTags.forEach { small ->
                                    TagCheckRow(tag = small, selected = small.id in selectedTagIds.value,
                                        indent = 32.dp) {
                                        val s = selectedTagIds.value.toMutableSet()
                                        if (small.id in s) s.remove(small.id) else s.add(small.id)
                                        selectedTagIds.value = s
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selectedTagIds.value.toList()) },
                enabled = selectedTagIds.value.isNotEmpty()
            ) { Text("付ける (${selectedTagIds.value.size})") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } }
    )
}

@Composable
private fun TagCheckRow(tag: Tag, selected: Boolean, indent: androidx.compose.ui.unit.Dp = 0.dp, onToggle: () -> Unit) {
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

@Composable
fun TagFilterPickerDialog(
    allTags: List<Tag>,
    selectedTagId: Int?,
    onSelect: (Int?) -> Unit,
    onDismiss: () -> Unit
) {
    val largeTags = allTags.filter { it.level == 1 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("タグで絞り込む") },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                item {
                    TextButton(onClick = { onSelect(null) }, modifier = Modifier.fillMaxWidth()) {
                        Text("すべて表示")
                    }
                }
                items(largeTags) { large ->
                    val children = allTags.filter { it.parentId == large.id }
                    Row(modifier = Modifier.fillMaxWidth().clickable { onSelect(large.id) }
                        .padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selectedTagId == large.id, onClick = { onSelect(large.id) })
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(parseColor(large.color)))
                        Spacer(Modifier.width(8.dp))
                        Text(large.name)
                    }
                    children.forEach { mid ->
                        Row(modifier = Modifier.fillMaxWidth().clickable { onSelect(mid.id) }
                            .padding(start = 24.dp, top = 4.dp, bottom = 4.dp, end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = selectedTagId == mid.id, onClick = { onSelect(mid.id) })
                            Text(mid.name, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } }
    )
}
