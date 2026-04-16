package com.example.taskschedulerv3.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.taskschedulerv3.data.model.PhotoMemo
import java.io.File

@Composable
fun PhotoThumbnailRow(
    photos: List<PhotoMemo>,
    maxShow: Int = 3,
    modifier: Modifier = Modifier,
    onPhotoClick: ((Int) -> Unit)? = null
) {
    val displayPhotos = photos.take(maxShow)
    val remainingCount = if (photos.size > maxShow) photos.size - maxShow else 0

    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        itemsIndexed(displayPhotos) { index, photo ->
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .then(if (onPhotoClick != null) Modifier.clickable { onPhotoClick(photo.id) } else Modifier)
            ) {
                if (File(photo.imagePath).exists()) {
                    AsyncImage(
                        model = photo.imagePath,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.BrokenImage,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // 「+N」オーバーレイ表示
                if (index == maxShow - 1 && remainingCount > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "+$remainingCount",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
