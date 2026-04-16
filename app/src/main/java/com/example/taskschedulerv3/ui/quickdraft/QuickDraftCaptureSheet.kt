package com.example.taskschedulerv3.ui.quickdraft

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.taskschedulerv3.data.model.Tag
import com.example.taskschedulerv3.util.PhotoFileManager
import java.io.File

/**
 * 仮登録フロー BottomSheet
 *
 * フロー:
 * 1. 写真撮影またはギャラリー選択
 * 2. タグ選択（省略可）
 * 3. 保存ボタン → onSave(photoPath, selectedTagIds) を呼び出し
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickDraftCaptureSheet(
    allTags: List<Tag>,
    onSave: (photoPath: String?, tagIds: List<Int>) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    // 取得した写真の状態管理
    var capturedPhotoPath by remember { mutableStateOf<String?>(null) }
    var capturedPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var tempCameraFile by remember { mutableStateOf<File?>(null) }

    // 選択中のタグID集合
    var selectedTagIds by remember { mutableStateOf(setOf<Int>()) }

    // ─── カメラ起動 ───
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            tempCameraFile?.let { file ->
                val path = PhotoFileManager.saveResizedPhotoFromFile(context, file)
                capturedPhotoPath = path
                capturedPhotoUri = path?.let { PhotoFileManager.pathToUri(it) }
                tempCameraFile = null
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

    // ─── ギャラリー選択 ───
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let {
            val path = PhotoFileManager.saveResizedPhoto(context, it)
            capturedPhotoPath = path
            capturedPhotoUri = path?.let { p -> PhotoFileManager.pathToUri(p) }
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
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
                    "仮登録",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Button(
                    onClick = {
                        onSave(capturedPhotoPath, selectedTagIds.toList())
                        onDismiss()
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("保存する", fontWeight = FontWeight.SemiBold)
                }
            }

            // ─── 写真エリア ───
            if (capturedPhotoUri != null) {
                // サムネイル表示＋撮り直しボタン
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
                    // 撮り直しラベル
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
                // 写真未選択 → 撮影/ギャラリーボタン
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { launchCamera() },
                        modifier = Modifier
                            .weight(1f)
                            .height(80.dp),
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
                        modifier = Modifier
                            .weight(1f)
                            .height(80.dp),
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

            // ─── タグ選択（タグがある場合のみ表示）───
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
    }
}
