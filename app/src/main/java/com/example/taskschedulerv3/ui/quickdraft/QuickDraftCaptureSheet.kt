package com.example.taskschedulerv3.ui.quickdraft

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import coil.compose.AsyncImage
import com.example.taskschedulerv3.data.model.Tag
import com.example.taskschedulerv3.ui.settings.SettingsViewModel
import com.example.taskschedulerv3.util.AiTextExtractor
import com.example.taskschedulerv3.util.PhotoFileManager
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickDraftCaptureSheet(
    viewModel: QuickDraftViewModel = viewModel(),
    settingsVm: SettingsViewModel = viewModel(),
    allTags: List<Tag>,
    autoMode: Boolean = false,
    onNavigateToEdit: (Int) -> Unit = {}, // 既存のナビゲーション用コールバックを保持
    onSaveFallback: (photoPath: String?, tagIds: List<Int>) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isAiProcessing by viewModel.isAiProcessing.collectAsState()
    val useAi by settingsVm.aiEnabled.collectAsState()
    val navigateToDraftId by viewModel.navigateToDraftId.collectAsState()

    var showCloseConfirmation by remember { mutableStateOf(false) }
    var sheetHeight by remember { mutableFloatStateOf(0f) }

    var capturedPhotoPath by remember { mutableStateOf<String?>(null) }
    var capturedPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var tempCameraFile by remember { mutableStateOf<File?>(null) }
    var selectedTagIds by remember { mutableStateOf(setOf<Int>()) }

    // 古い写真を削除して新しい写真をセットする関数
    fun setAndCleanUpPhoto(newPath: String?) {
        if (capturedPhotoPath != null && capturedPhotoPath != newPath) {
            try {
                File(capturedPhotoPath!!).delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        capturedPhotoPath = newPath
        capturedPhotoUri = newPath?.let { PhotoFileManager.pathToUri(it) }
    }

    // AIモデルのライフサイクル管理
    LaunchedEffect(useAi) {
        if (useAi) {
            AiTextExtractor.initialize(context)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            AiTextExtractor.close()
        }
    }

    // ─── カメラ起動 ───
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            tempCameraFile?.let { file ->
                val path = PhotoFileManager.saveResizedPhotoFromFile(context, file)
                setAndCleanUpPhoto(path)
                tempCameraFile = null

                if (autoMode) {
                    if (useAi && path != null) {
                        viewModel.createFromCameraWithAi(context, path, emptyList())
                    } else {
                        onSaveFallback(path, emptyList())
                    }
                }
            }
        } else {
            if (autoMode) onDismiss()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val (uri, file) = PhotoFileManager.createTempPhotoUri(context)
            tempCameraFile = file
            cameraLauncher.launch(uri)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let {
            val path = PhotoFileManager.saveResizedPhoto(context, it)
            setAndCleanUpPhoto(path)
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

    LaunchedEffect(autoMode) {
        if (autoMode && capturedPhotoUri == null) launchCamera()
    }
    
    // ==========================================
    // 修正：シートを安全に閉じるための仕組み（アニメーションを待ってから消す）
    // ==========================================
    val scope = rememberCoroutineScope()
    var forceClose by remember { mutableStateOf(false) }

    val draftSheetStateRef = remember { mutableStateOf<SheetState?>(null) }
    val draftSheetState = rememberModalBottomSheetState(
        confirmValueChange = { newValue ->
            if (newValue == SheetValue.Hidden && !isAiProcessing && !forceClose) {
                val currentOffset = draftSheetStateRef.value?.requireOffset() ?: 0f
                if (currentOffset > sheetHeight * 0.8f) {
                    showCloseConfirmation = true
                }
                false
            } else {
                !isAiProcessing
            }
        }
    )
    SideEffect { draftSheetStateRef.value = draftSheetState }

    // アニメーションを完了させてから安全に破棄する関数
    fun closeSheetSafely() {
        scope.launch {
            forceClose = true
            try {
                draftSheetState.hide()
            } catch (e: Exception) {
                // ignore
            } finally {
                onDismiss()
            }
        }
    }

    // 保存完了を検知してシートを閉じる
    LaunchedEffect(isAiProcessing) {
        if (!isAiProcessing && capturedPhotoPath != null && autoMode) {
            closeSheetSafely()
        }
    }

    // 編集画面へのナビゲーション監視
    LaunchedEffect(navigateToDraftId) {
        navigateToDraftId?.let { draftId ->
            onNavigateToEdit(draftId) // 編集画面へ自動遷移
            viewModel.clearNavigation()
            closeSheetSafely()
        }
    }

    if (showCloseConfirmation) {
        AlertDialog(
            onDismissRequest = { showCloseConfirmation = false },
            title = { Text("入力内容の破棄") },
            text = { Text("仮登録を中断して閉じますか？") },
            confirmButton = {
                TextButton(onClick = { showCloseConfirmation = false; closeSheetSafely() }) { 
                    Text("破棄", color = MaterialTheme.colorScheme.error) 
                }
            },
            dismissButton = {
                TextButton(onClick = { showCloseConfirmation = false }) { Text("キャンセル") }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = { 
            if (!isAiProcessing) showCloseConfirmation = true 
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
                // ─── タイトルと保存ボタン ───
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (useAi) "仮登録 (AI解析ON ✨)" else "仮登録",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (useAi) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    Button(
                        enabled = !isAiProcessing,
                        onClick = {
                            if (useAi && capturedPhotoPath != null) {
                                viewModel.createFromCameraWithAi(context, capturedPhotoPath!!, selectedTagIds.toList())
                            } else {
                                onSaveFallback(capturedPhotoPath, selectedTagIds.toList())
                                closeSheetSafely()
                            }
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("保存する", fontWeight = FontWeight.SemiBold)
                    }
                }

                if (capturedPhotoUri != null) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        AsyncImage(
                            model = capturedPhotoUri,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = { launchCamera() },
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp)
                            ) {
                                Icon(Icons.Default.AddAPhoto, null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("撮り直す", fontSize = 11.sp)
                            }
                            Spacer(Modifier.width(6.dp))
                            FilledTonalButton(
                                onClick = {
                                    capturedPhotoPath?.let { path ->
                                        PhotoFileManager.rotateImage(context, path)?.let { newPath ->
                                            setAndCleanUpPhoto(newPath)
                                        }
                                    }
                                },
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp)
                            ) {
                                Icon(Icons.Default.RotateRight, null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("回転", fontSize = 11.sp)
                            }
                            Spacer(Modifier.width(6.dp))
                            FilledTonalButton(
                                onClick = {
                                    galleryLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp)
                            ) {
                                Icon(Icons.Default.PhotoLibrary, null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("選択", fontSize = 11.sp)
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { launchCamera() },
                            modifier = Modifier.weight(1f).height(80.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.AddAPhoto, null, modifier = Modifier.size(24.dp))
                                Spacer(Modifier.height(4.dp))
                                Text("カメラで撮影", fontSize = 12.sp)
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                galleryLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.weight(1f).height(80.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.PhotoLibrary, null, modifier = Modifier.size(24.dp))
                                Spacer(Modifier.height(4.dp))
                                Text("ギャラリー", fontSize = 12.sp)
                            }
                        }
                    }
                }

                if (allTags.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "タグ（任意）",
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
            }

            if (isAiProcessing) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                        .clickable(enabled = false) {},
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "AIが書類を解析して予定を作成中...",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
