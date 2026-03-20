package com.example.taskschedulerv3.ui.photo

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.taskschedulerv3.data.model.PhotoMemo
import com.example.taskschedulerv3.navigation.Screen
import com.example.taskschedulerv3.util.DateUtils
import com.example.taskschedulerv3.util.PhotoFileManager
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoListScreen(navController: NavController, vm: PhotoListViewModel = viewModel()) {
    val context = LocalContext.current
    val photosByMonth by vm.photosByMonth.collectAsState()
    val today = remember { DateUtils.today() }

    var showAddMenu by remember { mutableStateOf(false) }
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

    // Gallery launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { vm.savePhotoFromGallery(it, today) }
    }

    // Camera permission launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val (uri, file) = PhotoFileManager.createTempPhotoUri(context)
            tempPhotoFile = file
            cameraLauncher.launch(uri)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("写真メモ") }) },
        floatingActionButton = {
            Box {
                FloatingActionButton(onClick = { showAddMenu = true }) {
                    Icon(Icons.Default.Add, contentDescription = "写真追加")
                }
                DropdownMenu(
                    expanded = showAddMenu,
                    onDismissRequest = { showAddMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("カメラで撮影") },
                        onClick = {
                            showAddMenu = false
                            val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                            if (hasPerm) {
                                val (uri, file) = PhotoFileManager.createTempPhotoUri(context)
                                tempPhotoFile = file
                                cameraLauncher.launch(uri)
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        leadingIcon = { Icon(Icons.Default.CameraAlt, null) }
                    )
                    DropdownMenuItem(
                        text = { Text("ギャラリーから選択") },
                        onClick = {
                            showAddMenu = false
                            galleryLauncher.launch(
                                androidx.activity.result.PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        },
                        leadingIcon = { Icon(Icons.Default.Photo, null) }
                    )
                }
            }
        }
    ) { padding ->
        if (photosByMonth.isEmpty()) {
            Box(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Photo, null, modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                    Spacer(Modifier.height(12.dp))
                    Text("写真メモがありません", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    Text("右下の＋ボタンから追加してください",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                }
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                photosByMonth.forEach { section ->
                    item {
                        Text(
                            text = section.yearMonth.replace("-", "年") + "月",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                    item {
                        PhotoGrid(
                            photos = section.photos,
                            onPhotoClick = { photo ->
                                navController.navigate(Screen.PhotoDetail.createRoute(photo.id))
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PhotoGrid(photos: List<PhotoMemo>, onPhotoClick: (PhotoMemo) -> Unit) {
    val columns = 3
    val rows = (photos.size + columns - 1) / columns
    Column(modifier = Modifier.padding(4.dp)) {
        for (row in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (col in 0 until columns) {
                    val idx = row * columns + col
                    if (idx < photos.size) {
                        val photo = photos[idx]
                        AsyncImage(
                            model = PhotoFileManager.pathToUri(photo.imagePath),
                            contentDescription = photo.title ?: photo.date,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .padding(2.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { onPhotoClick(photo) }
                        )
                    } else {
                        Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                    }
                }
            }
        }
    }
}
