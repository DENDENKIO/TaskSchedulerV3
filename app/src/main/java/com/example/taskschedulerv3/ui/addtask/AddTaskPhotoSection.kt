package com.example.taskschedulerv3.ui.addtask

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.taskschedulerv3.util.PhotoFileManager
import java.io.File

import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.material3.SheetValue
import androidx.compose.material3.ExperimentalMaterial3Api

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskPhotoSection(
    viewModel: AddTaskViewModel,
    onOcrRequested: (Uri) -> Unit
) {
    val context = LocalContext.current
    val pendingPhotos by viewModel.pendingPhotoPaths.collectAsState()
    val existingPhotos by viewModel.existingPhotos.collectAsState()
    
    var tempCameraFile by remember { mutableStateOf<File?>(null) }
    var showSourcePicker by remember { mutableStateOf(false) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            tempCameraFile?.let { file ->
                viewModel.addPhotoFromCamera(file)
                tempCameraFile = null
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { viewModel.addPhotoFromGallery(it) }
    }

    val ocrGalleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { onOcrRequested(it) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val (uri, file) = PhotoFileManager.createTempPhotoUri(context)
            tempCameraFile = file
            cameraLauncher.launch(uri)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("写真メモ", style = MaterialTheme.typography.labelLarge)

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // 追加ボタン
            item {
                Surface(
                    onClick = { showSourcePicker = true },
                    modifier = Modifier.size(72.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 2.dp
                ) {
                    Icon(
                        Icons.Default.AddAPhoto,
                        contentDescription = "追加",
                        modifier = Modifier.padding(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 既存の写真
            items(existingPhotos) { photo ->
                PhotoThumbnail(
                    path = photo.imagePath,
                    onRemove = { viewModel.removeExistingPhoto(photo) }
                )
            }

            // 追加予定の写真
            items(pendingPhotos) { path ->
                PhotoThumbnail(
                    path = path,
                    onRemove = { viewModel.removePendingPhoto(path) }
                )
            }
        }
    }

    if (showSourcePicker) {
        var showCloseConfirmation by remember { mutableStateOf(false) }
        var sheetHeight by remember { mutableFloatStateOf(0f) }
        val pickerSheetStateRef = remember { mutableStateOf<SheetState?>(null) }
        val pickerSheetState = rememberModalBottomSheetState(
            confirmValueChange = { newValue ->
                if (newValue == SheetValue.Hidden) {
                    val currentOffset = pickerSheetStateRef.value?.requireOffset() ?: 0f
                    val threshold = sheetHeight * 0.8f
                    
                    if (currentOffset > threshold) {
                        showCloseConfirmation = true
                    }
                    false
                } else {
                    true
                }
            }
        )
        SideEffect { pickerSheetStateRef.value = pickerSheetState }

        if (showCloseConfirmation) {
            AlertDialog(
                onDismissRequest = { showCloseConfirmation = false },
                title = { Text("閉じる") },
                text = { Text("写真の追加をキャンセルしますか？") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showCloseConfirmation = false
                            showSourcePicker = false
                        }
                    ) { Text("閉じる") }
                },
                dismissButton = {
                    TextButton(onClick = { showCloseConfirmation = false }) { Text("戻る") }
                }
            )
        }

        ModalBottomSheet(
            onDismissRequest = { showCloseConfirmation = true },
            sheetState = pickerSheetState
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coords ->
                        sheetHeight = coords.size.height.toFloat()
                    }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .padding(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                Text("写真の追加", style = MaterialTheme.typography.titleMedium)
                
                ListItem(
                    headlineContent = { Text("カメラで撮影") },
                    leadingContent = { Icon(Icons.Default.AddAPhoto, null) },
                    modifier = Modifier.clickable {
                        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                        if (hasPermission) {
                            val (uri, file) = PhotoFileManager.createTempPhotoUri(context)
                            tempCameraFile = file
                            cameraLauncher.launch(uri)
                        } else {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                        showSourcePicker = false
                    }
                )
                ListItem(
                    headlineContent = { Text("ギャラリーから選択") },
                    leadingContent = { Icon(Icons.Default.DocumentScanner, null) }, // 本来はPhotoアイコンだが、OCRとの対比
                    modifier = Modifier.clickable {
                        galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        showSourcePicker = false
                    }
                )
                ListItem(
                    headlineContent = { Text("OCR 読み取り (解析して入力)") },
                    leadingContent = { Icon(Icons.Default.DocumentScanner, null) },
                    modifier = Modifier.clickable {
                        ocrGalleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        showSourcePicker = false
                    }
                )
            } // Column
        } // Box
    } // ModalBottomSheet
} // if (showSourcePicker)
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun PhotoThumbnail(path: String, onRemove: () -> Unit) {
    var showConfirmDialog by remember { mutableStateOf(false) }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("写真の削除") },
            text = { Text("この写真の関連付けを解除してよろしいですか？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemove()
                        showConfirmDialog = false
                    }
                ) { Text("削除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) { Text("キャンセル") }
            }
        )
    }

    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = {}, // Just consume click
                onLongClick = { showConfirmDialog = true }
            )
    ) {
        AsyncImage(
            model = PhotoFileManager.pathToUri(path),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}
