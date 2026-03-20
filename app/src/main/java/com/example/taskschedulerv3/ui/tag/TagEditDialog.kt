package com.example.taskschedulerv3.ui.tag

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val TAG_PRESET_COLORS = listOf(
    "#E53935", // Red
    "#FB8C00", // Orange
    "#FDD835", // Yellow
    "#43A047", // Green
    "#00ACC1", // Cyan
    "#1E88E5", // Blue
    "#8E24AA", // Purple
    "#F06292", // Pink
    "#6D4C41", // Brown
    "#546E7A", // Blue-grey
    "#78909C", // Grey
    "#212121"  // Dark
)

@Composable
fun TagEditDialog(
    title: String,
    initialName: String = "",
    initialColor: String = TAG_PRESET_COLORS.first(),
    onConfirm: (name: String, color: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var selectedColor by remember { mutableStateOf(initialColor) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("タグ名") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Text("カラー", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(TAG_PRESET_COLORS) { colorHex ->
                        val color = parseColor(colorHex)
                        val isSelected = colorHex == selectedColor
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(color)
                                .then(
                                    if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    else Modifier
                                )
                                .clickable { selectedColor = colorHex }
                        )
                    }
                }
                // Preview
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(parseColor(selectedColor)))
                    Text(name.ifBlank { "プレビュー" }, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim(), selectedColor) },
                enabled = name.isNotBlank()
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        }
    )
}

fun parseColor(hex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (e: Exception) {
        Color.Gray
    }
}
