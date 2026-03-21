package com.example.taskschedulerv3.ui.photo

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
    val photosByWeek by vm.photosByWeek.collectAsState()
    val today = remember { DateUtils.today() }
    val searchQuery by vm.searchQuery.collectAsState()
    val allTags by vm.allTags.collectAsState()
    val filterTagId by vm.filterTagId.collectAsState()
    val missingFilter by vm.missingFilter.collectAsState()
    val selectedIds by vm.selectedPhotoIds.collectAsState()
    val isSelectionMode by vm.isSelectionMode.collectAsState()
    val dateFrom by vm.filterDateFrom.collectAsState()
    val dateTo by vm.filterDateTo.collectAsState()

    var showAddMenu by remember { mutableStateOf(false) }
    var showBulkTagDialog by remember { mutableStateOf(false) }
    var showTagFilterDialog by remember { mutableStateOf(false) }
    var showDateRangeDialog by remember { mutableStateOf(false) }
    var tempPhotoFile by remember { mutableStateOf<File?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) tempPhotoFile?.let { vm.savePhotoFromCamera(it, today); tempPhotoFile = null }
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { vm.savePhotoFromGallery(it, today) }
    }
    val cameraPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val (uri, file) = PhotoFileManager.createTempPhotoUri(context)
            tempPhotoFile = file; cameraLauncher.launch(uri)
        }
    }

    if (showBulkTagDialog) {
        BulkTagDialog(allTags = allTags,
            onConfirm = { tagIds -> vm.bulkSetTags(selectedIds, tagIds); showBulkTagDialog = false },
            onDismiss = { showBulkTagDialog = false })
    }
    if (showTagFilterDialog) {
        HierarchicalTagFilterDialog(
            allTags = allTags,
            selectedTagId = filterTagId,
            onSelect = { vm.setTagFilter(it); showTagFilterDialog = false },
            onDismiss = { showTagFilterDialog = false }
        )
    }
    if (showDateRangeDialog) {
        DateRangeDialog(
            initialFrom = dateFrom,
            initialTo = dateTo,
            onConfirm = { from, to -> vm.setDateRange(from, to); showDateRangeDialog = false },
            onDismiss = { showDateRangeDialog = false }
        )
    }

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = { Text("${selectedIds.size}件選択中") },
                    navigationIcon = {
                        IconButton(onClick = { vm.clearSelection() }) { Icon(Icons.Default.Close, null) }
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
                        DropdownMenuItem(text = { Text("カメラで撮影") }, onClick = {
                            showAddMenu = false
                            val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                            if (hasPerm) {
                                val (uri, file) = PhotoFileManager.createTempPhotoUri(context)
                                tempPhotoFile = file; cameraLauncher.launch(uri)
                            } else cameraPermLauncher.launch(Manifest.permission.CAMERA)
                        }, leadingIcon = { Icon(Icons.Default.CameraAlt, null) })
                        DropdownMenuItem(text = { Text("ギャラリーから選択") }, onClick = {
                            showAddMenu = false
                            galleryLauncher.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }, leadingIcon = { Icon(Icons.Default.Photo, null) })
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

            // ── フィルタ行 ──
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // すべて
                item {
                    FilterChip(selected = filterTagId == null && missingFilter == PhotoMissingFilter.NONE,
                        onClick = { vm.setTagFilter(null); vm.missingFilter.value = PhotoMissingFilter.NONE },
                        label = { Text("すべて") })
                }
                // タグ指定（固定・階層ダイアログ）
                item {
                    FilterChip(
                        selected = filterTagId != null,
                        onClick = { showTagFilterDialog = true },
                        label = { Text(if (filterTagId != null) allTags.find { it.id == filterTagId }?.name ?: "タグ指定" else "タグ指定") },
                        leadingIcon = { Icon(Icons.Default.Label, null, modifier = Modifier.size(14.dp)) }
                    )
                }
                // 大カテゴリタグ → 即時フィルタ
                items(allTags.filter { it.level == 1 }) { tag ->
                    FilterChip(
                        selected = filterTagId == tag.id,
                        onClick = {
                            vm.setTagFilter(if (filterTagId == tag.id) null else tag.id)
                        },
                        label = { Text(tag.name) },
                        leadingIcon = {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(parseColor(tag.color)))
                        }
                    )
                }
                // 未登録フィルタ
                item {
                    FilterChip(
                        selected = missingFilter == PhotoMissingFilter.NO_TITLE,
                        onClick = { vm.setMissingFilter(PhotoMissingFilter.NO_TITLE); vm.setTagFilter(null) },
                        label = { Text("タイトル無") }
                    )
                }
                item {
                    FilterChip(
                        selected = missingFilter == PhotoMissingFilter.NO_MEMO,
                        onClick = { vm.setMissingFilter(PhotoMissingFilter.NO_MEMO); vm.setTagFilter(null) },
                        label = { Text("メモ無") }
                    )
                }
                item {
                    FilterChip(
                        selected = missingFilter == PhotoMissingFilter.NO_TAG,
                        onClick = { vm.setMissingFilter(PhotoMissingFilter.NO_TAG); vm.setTagFilter(null) },
                        label = { Text("タグ無") }
                    )
                }
                // 日付範囲
                item {
                    FilterChip(
                        selected = dateFrom.isNotEmpty() || dateTo.isNotEmpty(),
                        onClick = { showDateRangeDialog = true },
                        label = {
                            Text(if (dateFrom.isNotEmpty() || dateTo.isNotEmpty())
                                "${dateFrom.ifEmpty { "〜" }} 〜 ${dateTo.ifEmpty { "〜" }}" else "期間指定")
                        },
                        leadingIcon = { Icon(Icons.Default.DateRange, null, modifier = Modifier.size(14.dp)) }
                    )
                }
                if (dateFrom.isNotEmpty() || dateTo.isNotEmpty()) {
                    item {
                        TextButton(onClick = { vm.clearDateRange() }) { Text("期間解除") }
                    }
                }
            }

            HorizontalDivider()

            // ── 週別写真グリッド ──
            if (photosByWeek.isEmpty()) {
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
                    photosByWeek.forEach { section ->
                        item {
                            Text(
                                text = section.weekLabel,
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
                        Box(modifier = Modifier.weight(1f).aspectRatio(1f).padding(2.dp)) {
                            AsyncImage(
                                model = PhotoFileManager.pathToUri(photo.imagePath),
                                contentDescription = photo.title ?: photo.date,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp))
                                    .then(if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)) else Modifier)
                                    .combinedClickable(
                                        onClick = { onPhotoClick(photo) },
                                        onLongClick = { onPhotoLongClick(photo) }
                                    )
                            )
                            if (isSelectionMode) {
                                Box(modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(20.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) Icon(Icons.Default.Check, null, modifier = Modifier.size(14.dp), tint = Color.White)
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

// ── 階層展開式タグフィルタダイアログ ──
@Composable
fun HierarchicalTagFilterDialog(
    allTags: List<Tag>,
    selectedTagId: Int?,
    onSelect: (Int?) -> Unit,
    onDismiss: () -> Unit
) {
    val expandedLarge = remember { mutableStateOf<Int?>(null) }
    val expandedMid   = remember { mutableStateOf<Int?>(null) }
    val largeTags = allTags.filter { it.level == 1 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("タグで絞り込む") },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                item {
                    Row(modifier = Modifier.fillMaxWidth().clickable { onSelect(null) }.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selectedTagId == null, onClick = { onSelect(null) })
                        Spacer(Modifier.width(8.dp))
                        Text("すべて")
                    }
                }
                items(largeTags) { large ->
                    val midTags = allTags.filter { it.parentId == large.id }
                    val isExpanded = expandedLarge.value == large.id
                    // 大カテゴリ行
                    Row(modifier = Modifier.fillMaxWidth().clickable {
                        if (midTags.isEmpty()) onSelect(large.id)
                        else expandedLarge.value = if (isExpanded) null else large.id
                    }.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selectedTagId == large.id, onClick = { onSelect(large.id) })
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(parseColor(large.color)))
                        Spacer(Modifier.width(8.dp))
                        Text(large.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        if (midTags.isNotEmpty()) {
                            Icon(if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, modifier = Modifier.size(18.dp))
                        }
                    }
                    // 中カテゴリ（展開時）
                    if (isExpanded) {
                        midTags.forEach { mid ->
                            val smallTags = allTags.filter { it.parentId == mid.id }
                            val isMidExpanded = expandedMid.value == mid.id
                            Row(modifier = Modifier.fillMaxWidth().clickable {
                                if (smallTags.isEmpty()) onSelect(mid.id)
                                else expandedMid.value = if (isMidExpanded) null else mid.id
                            }.padding(start = 24.dp, top = 4.dp, bottom = 4.dp, end = 8.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = selectedTagId == mid.id, onClick = { onSelect(mid.id) })
                                Text(mid.name, modifier = Modifier.weight(1f))
                                if (smallTags.isNotEmpty()) {
                                    Icon(if (isMidExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, modifier = Modifier.size(16.dp))
                                }
                            }
                            // 小カテゴリ（展開時）
                            if (isMidExpanded) {
                                smallTags.forEach { small ->
                                    Row(modifier = Modifier.fillMaxWidth().clickable { onSelect(small.id) }
                                        .padding(start = 48.dp, top = 2.dp, bottom = 2.dp, end = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        RadioButton(selected = selectedTagId == small.id, onClick = { onSelect(small.id) })
                                        Text(small.name, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } }
    )
}

// ── 日付範囲ダイアログ ──
@Composable
fun DateRangeDialog(
    initialFrom: String,
    initialTo: String,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var from by remember { mutableStateOf(initialFrom) }
    var to   by remember { mutableStateOf(initialTo) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("期間を指定") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = from, onValueChange = { from = it },
                    label = { Text("開始日 (yyyy-MM-dd)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = to, onValueChange = { to = it },
                    label = { Text("終了日 (yyyy-MM-dd)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(from, to) }) { Text("適用") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } }
    )
}

// ── 一括タグ付けダイアログ ──
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
                            selectedTagIds.value = selectedTagIds.value.toggle(large.id)
                        }
                    } else {
                        Text(large.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 2.dp))
                        midTags.forEach { mid ->
                            val smallTags = allTags.filter { it.parentId == mid.id }
                            if (smallTags.isEmpty()) {
                                TagCheckRow(tag = mid, selected = mid.id in selectedTagIds.value, indent = 16.dp) {
                                    selectedTagIds.value = selectedTagIds.value.toggle(mid.id)
                                }
                            } else {
                                Text(mid.name, style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(start = 24.dp, top = 4.dp))
                                smallTags.forEach { small ->
                                    TagCheckRow(tag = small, selected = small.id in selectedTagIds.value, indent = 32.dp) {
                                        selectedTagIds.value = selectedTagIds.value.toggle(small.id)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedTagIds.value.toList()) },
                enabled = selectedTagIds.value.isNotEmpty()) {
                Text("付ける (${selectedTagIds.value.size})")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } }
    )
}

@Composable
private fun TagCheckRow(tag: Tag, selected: Boolean, indent: Dp = 0.dp, onToggle: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable { onToggle() }
        .padding(start = indent + 8.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Checkbox(checked = selected, onCheckedChange = { onToggle() })
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(parseColor(tag.color)))
        Text(tag.name, style = MaterialTheme.typography.bodyMedium)
    }
}

// ── TagFilterPickerDialog (後方互換) ──
@Composable
fun TagFilterPickerDialog(
    allTags: List<Tag>,
    selectedTagId: Int?,
    onSelect: (Int?) -> Unit,
    onDismiss: () -> Unit
) = HierarchicalTagFilterDialog(allTags, selectedTagId, onSelect, onDismiss)

private fun Set<Int>.toggle(id: Int): Set<Int> {
    val s = toMutableSet()
    if (id in s) s.remove(id) else s.add(id)
    return s
}
