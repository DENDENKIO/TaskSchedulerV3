package com.example.taskschedulerv3.ui.tag

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// 固定色（UIでの色入力を廃止し、保存時にこの値を使用）
const val TAG_FIXED_COLOR = "#7A7A8C"

@Composable
fun TagEditDialog(
    title: String,
    initialName: String = "",
    initialColor: String = TAG_FIXED_COLOR,  // 後方互換のため引数は残す
    onConfirm: (name: String, color: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }

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
                // 色入力欄・カラープレビューは廃止 (DBのcolorカラムは維持)
                // 保存時は TAG_FIXED_COLOR を使用する
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim(), TAG_FIXED_COLOR) },
                enabled = name.isNotBlank()
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        }
    )
}

/** @deprecated Use TAG_FIXED_COLOR instead. Kept for compilation compatibility. */
fun parseColor(hex: String): androidx.compose.ui.graphics.Color {
    return try {
        androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(hex))
    } catch (e: Exception) {
        androidx.compose.ui.graphics.Color.Gray
    }
}
