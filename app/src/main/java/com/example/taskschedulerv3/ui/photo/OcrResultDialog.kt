package com.example.taskschedulerv3.ui.photo

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import android.widget.Toast

@Composable
fun OcrResultDialog(
    text: String,
    onApplyToTitle: (String) -> Unit,
    onApplyToDescription: (String, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var textFieldValue by remember { mutableStateOf(TextFieldValue(text)) }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    // 現在のターゲットテキスト（1行または選択範囲）を計算
    val safeMin = maxOf(0, minOf(textFieldValue.selection.min, textFieldValue.text.length))
    val safeMax = maxOf(0, minOf(textFieldValue.selection.max, textFieldValue.text.length))
    val isCollapsed = safeMin == safeMax

    val targetText = remember(textFieldValue.text, safeMin, safeMax, isCollapsed) {
        if (isCollapsed) {
            val textStr = textFieldValue.text
            var lineStart = textStr.lastIndexOf('\n', safeMin - 1)
            if (lineStart == -1) lineStart = 0 else lineStart += 1
            var lineEnd = textStr.indexOf('\n', safeMin)
            if (lineEnd == -1) lineEnd = textStr.length
            textStr.substring(lineStart, lineEnd).trim()
        } else {
            textFieldValue.text.substring(safeMin, safeMax).trim()
        }
    }

    // 自動コピー（ワンタップで即コピー、ドラッグは0.5秒停止後にコピー）
    LaunchedEffect(textFieldValue.selection) {
        if (targetText.isNotBlank() && targetText != text) {
            if (!isCollapsed) {
                kotlinx.coroutines.delay(500)
            }
            clipboardManager.setText(AnnotatedString(targetText))
            Toast.makeText(context, if (isCollapsed) "1行コピーしました" else "選択範囲をコピーしました", Toast.LENGTH_SHORT).show()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("OCR 読み取り結果") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "タップで1行自動コピー、ドラッグで選択範囲をコピーします。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = textFieldValue,
                    onValueChange = { textFieldValue = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 250.dp),
                    textStyle = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { onApplyToTitle(targetText); onDismiss() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = targetText.isNotBlank()
                ) {
                    Text(if (isCollapsed) "1行をタスク名に適用" else "選択範囲をタスク名に適用")
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onApplyToDescription(targetText, true); onDismiss() },
                        modifier = Modifier.weight(1f),
                        enabled = targetText.isNotBlank(),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Text(if (isCollapsed) "1行をメモに追記" else "選択範囲を空き行に追記", style = MaterialTheme.typography.labelMedium)
                    }
                    OutlinedButton(
                        onClick = { onApplyToDescription(targetText, false); onDismiss() },
                        modifier = Modifier.weight(1f),
                        enabled = targetText.isNotBlank(),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Text("メモを置き換え", style = MaterialTheme.typography.labelMedium)
                    }
                }
                // 全文適用も残しておく
                TextButton(
                    onClick = { onApplyToDescription(textFieldValue.text, true); onDismiss() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("全文をメモに適用")
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("キャンセル")
                }
            }
        }
    )
}

