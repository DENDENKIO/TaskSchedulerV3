package com.example.taskschedulerv3.ui.quickdraft

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.taskschedulerv3.data.model.Tag
import com.example.taskschedulerv3.ui.settings.SettingsViewModel
import com.example.taskschedulerv3.util.PhotoFileManager
import kotlinx.coroutines.launch
import java.io.File

/**
 * 一括仮登録シート。
 * - カメラ連続撮影: 1枚撮影→即座にカメラ再起動を繰り返し、「処理開始」で一括送信
 * - ギャラリー複数選択: PickMultipleVisualMedia で複数選択
 * - 処理はバックグラウンドに委譲してシートは即閉じ
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickDraftCaptureSheet(
    viewModel: QuickDraftViewModel = viewModel(),
    settingsVm: SettingsViewModel = viewModel(),
    allTags: List<Tag>,
    autoMode: Boolean = false,
    onNavigateToEdit: (Int) -> Unit = {},
    onSaveFallback: (photoPath: String?, tagIds: List<Int>) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val useAi by settingsVm.aiEnabled.collectAsState()

    // 撮影済み写真パスのリスト
    var capturedPaths by remember { mutableStateOf(listOf<String>()) }
    var tempCameraFile by remember { mutableStateOf<File?>(null) }
    var selectedTagIds by remember { mutableStateOf(setOf<Int>()) }
    var sheetHeight by remember { mutableFloatStateOf(0f) }

    // autoMode の場合、撮影→即処理→閉じる（旧動作互換）
    var autoModeTriggered by remember { mutableStateOf(false) }

    // ─── カメラ ───
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            tempCameraFile?.let { file ->
                val path = PhotoFileManager.saveResizedPhotoFromFile(context, file)
                tempCameraFile = null

                if (path != null) {
                    if (autoMode && !autoModeTriggered) {
                        // autoMode: 1枚撮って即処理して閉じる
                        autoModeTriggered = true
                        viewModel.enqueueBatch(
                            context = context,
                            photoPaths = listOf(path),
                            tagIds = selectedTagIds.toList(),
                            useAi = useAi
                        )
                        Toast.makeText(context, "バックグラウンドで処理中...", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    } else {
                        // 通常モード: リストに追加して続行
                        capturedPaths = capturedPaths + path
                    }
                }
            }
        } else {
            // カメラキャンセル
            if (autoMode && capturedPaths.isEmpty()) {
                onDismiss()
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val (uri, file) = PhotoFileManager.createTempPhotoUri(context)
            tempCameraFile = file
            cameraLauncher.launch(uri)
        }
    }

    // ─── ギャラリー（複数選択） ───
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val newPaths = uris.mapNotNull { uri ->
                PhotoFileManager.saveResizedPhoto(context, uri)
            }
            capturedPaths = capturedPaths + newPaths
        }
    }

    fun launchCamera() {
        val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (hasPerm) {
            val (uri, file) = PhotoFileManager.createTempPhotoUri(context)
            tempCameraFile = file
            cameraLauncher.launch(uri)
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // autoMode時にカメラを自動起動
    LaunchedEffect(autoMode) {
        if (autoMode && capturedPaths.isEmpty() && !autoModeTriggered) {
            launchCamera()
        }
    }

    // ─── シート状態管理 ───
    var forceClose by remember { mutableStateOf(false) }
    val draftSheetState = rememberModalBottomSheetState(
        confirmValueChange = { newValue ->
            if (newValue == SheetValue.Hidden && !forceClose) {
                // 写真がある場合は確認
                capturedPaths.isEmpty()
            } else {
                true
            }
        }
    )

    fun closeSheetSafely() {
        scope.launch {
            forceClose = true
            try {
                draftSheetState.hide()
            } catch (_: Exception) {}
            finally {
                onDismiss()
            }
        }
    }

    /**
     * バッチ処理を開始してシートを閉じる。
     */
    fun startBatchAndClose() {
        if (capturedPaths.isEmpty()) {
            closeSheetSafely()
            return
        }
        viewModel.enqueueBatch(
            context = context,
            photoPaths = capturedPaths,
            tagIds = selectedTagIds.toList(),
            useAi = useAi
        )
        Toast.makeText(
            context,
            "${capturedPaths.size}件をバックグラウンドで処理中...",
            Toast.LENGTH_SHORT
        ).show()
        // キャプチャ済みリストをクリアしてからシートを閉じる
        capturedPaths = emptyList()
        closeSheetSafely()
    }

    // autoMode以外のみシートを表示
    if (autoMode && autoModeTriggered) return

    ModalBottomSheet(
        onDismissRequest = {
            if (capturedPaths.isEmpty()) {
                onDismiss()
            }
            // 写真がある場合はconfirmValueChangeで制御
        },
        sheetState = draftSheetState,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { sheetHeight = it.size.height.toFloat() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ─── ヘッダー ───
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            if (useAi) "一括仮登録 (AI解析ON)" else "一括仮登録",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (useAi) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface
                        )
                        if (capturedPaths.isNotEmpty()) {
                            Text(
                                "${capturedPaths.size}枚選択中",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    // 処理開始ボタン
                    Button(
                        onClick = { startBatchAndClose() },
                        enabled = capturedPaths.isNotEmpty(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            if (capturedPaths.isEmpty()) "写真を追加"
                            else "${capturedPaths.size}件を処理開始",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // ─── 写真追加ボタン（カメラ・ギャラリー） ───
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { launchCamera() },
                        modifier = Modifier
                            .weight(1f)
                            .height(72.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.AddAPhoto, null, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.height(4.dp))
                            Text("カメラ追加", fontSize = 12.sp)
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            galleryLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(72.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.PhotoLibrary, null, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.height(4.dp))
                            Text("ギャラリー (複数)", fontSize = 12.sp)
                        }
                    }
                }

                // ─── 選択済み写真のプレビュー ───
                if (capturedPaths.isNotEmpty()) {
                    Text(
                        "選択した写真",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(capturedPaths) { index, path ->
                            Box(modifier = Modifier.size(80.dp)) {
                                AsyncImage(
                                    model = PhotoFileManager.pathToUri(path),
                                    contentDescription = "写真 ${index + 1}",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                // 番号バッジ
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(2.dp)
                                        .size(20.dp)
                                        .background(
                                            MaterialTheme.colorScheme.primary,
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "${index + 1}",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                // 削除ボタン
                                IconButton(
                                    onClick = {
                                        // ファイルを削除してリストから除外
                                        try { File(path).delete() } catch (_: Exception) {}
                                        capturedPaths = capturedPaths.toMutableList().also {
                                            it.removeAt(index)
                                        }
                                    },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(22.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "削除",
                                        modifier = Modifier
                                            .size(16.dp)
                                            .background(
                                                MaterialTheme.colorScheme.errorContainer,
                                                CircleShape
                                            ),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }

                // ─── タグ選択 ───
                if (allTags.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "タグ（任意・全画像に適用）",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(allTags) { tag ->
                                val isSelected = tag.id in selectedTagIds
                                val tagColor = runCatching {
                                    Color(android.graphics.Color.parseColor(tag.color))
                                }.getOrElse { MaterialTheme.colorScheme.primary }

                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        selectedTagIds = if (isSelected) {
                                            selectedTagIds - tag.id
                                        } else {
                                            selectedTagIds + tag.id
                                        }
                                    },
                                    label = { Text(tag.name, fontSize = 12.sp) },
                                    leadingIcon = if (isSelected) {
                                        { Icon(Icons.Default.Check, null, modifier = Modifier.size(14.dp)) }
                                    } else null,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = tagColor.copy(alpha = 0.2f),
                                        selectedLabelColor = tagColor,
                                        selectedLeadingIconColor = tagColor
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        borderColor = tagColor.copy(alpha = 0.4f),
                                        selectedBorderColor = tagColor,
                                        enabled = true,
                                        selected = isSelected
                                    )
                                )
                            }
                        }
                    }
                }

                // ─── 説明テキスト ───
                if (capturedPaths.isEmpty()) {
                    Text(
                        "カメラで複数枚撮影するか、ギャラリーから複数選択してください。\n処理はバックグラウンドで実行され、完了時に通知でお知らせします。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
