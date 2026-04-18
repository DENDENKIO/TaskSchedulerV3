package com.example.taskschedulerv3.ui.photo

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.taskschedulerv3.util.PhotoFileManager
import java.io.File

import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.material3.SheetValue
import androidx.compose.material3.ExperimentalMaterial3Api

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskPhotoPickerSheet(
    onPhotoCaptured: (File) -> Unit,
    onPhotoSelected: (Uri) -> Unit,
    onOcrRequested: (Uri) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var showCloseConfirmation by remember { mutableStateOf(false) }
    var sheetHeight by remember { mutableFloatStateOf(0f) }
    var tempCameraFile by remember { mutableStateOf<File?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            tempCameraFile?.let { onPhotoCaptured(it); tempCameraFile = null }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { onPhotoSelected(it) }
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

    // 破棄確認ダイアログ
    if (showCloseConfirmation) {
        AlertDialog(
            onDismissRequest = { showCloseConfirmation = false },
            title = { Text("閉じる") },
            text = { Text("写真の追加をキャンセルしますか？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCloseConfirmation = false
                        onDismiss()
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
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
            Text("写真の追加・OCR", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))

            ListItem(
                headlineContent = { Text("カメラで撮影") },
                leadingContent = { Icon(Icons.Default.AddAPhoto, null) },
                modifier = Modifier.clickable {
                    val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                    if (hasPerm) {
                        val (uri, file) = PhotoFileManager.createTempPhotoUri(context)
                        tempCameraFile = file
                        cameraLauncher.launch(uri)
                    } else {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                    onDismiss()
                }
            )

            ListItem(
                headlineContent = { Text("ギャラリーから選択") },
                leadingContent = { Icon(Icons.Default.PhotoLibrary, null) },
                modifier = Modifier.clickable {
                    galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    onDismiss()
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            ListItem(
                headlineContent = { Text("画像からテキストを抽出 (OCR)") },
                supportingContent = { Text("メモやタイトルに自動入力します") },
                leadingContent = { Icon(Icons.Default.DocumentScanner, null, tint = MaterialTheme.colorScheme.primary) },
                modifier = Modifier.clickable {
                    ocrGalleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    onDismiss()
                }
            )
        }
    }
}
}
