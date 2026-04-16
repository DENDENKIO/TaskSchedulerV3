package com.example.taskschedulerv3.ui.photo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

@Composable
fun OcrCropOverlay(
    onCropChanged: (Rect) -> Unit,
    modifier: Modifier = Modifier
) {
    var topLeft by remember { mutableStateOf(Offset(100f, 100f)) }
    var size by remember { mutableStateOf(Size(600f, 400f)) }

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        topLeft += dragAmount
                        onCropChanged(Rect(topLeft, size))
                    }
                }
        ) {
            // 背景の半透明レイヤー
            val fullRectPath = Path().apply {
                addRect(Rect(Offset.Zero, this@Canvas.size))
            }
            val cropRectPath = Path().apply {
                addRect(Rect(topLeft, size))
            }
            
            // 実際は Path.combine などを使って抜き取り描画を行うが、簡易的に枠線のみ描画
            drawRect(
                color = Color.Black.copy(alpha = 0.5f)
            )
            
            // 切り抜き範囲をクリア（DrawScopeでは難しいので、枠線とハイライトで代用）
            drawRect(
                color = Color.White,
                topLeft = topLeft,
                size = size,
                style = Stroke(width = 2.dp.toPx())
            )
            
            // 四隅のハンドル（簡易版）
            drawCircle(Color.White, radius = 8.dp.toPx(), center = topLeft)
            drawCircle(Color.White, radius = 8.dp.toPx(), center = topLeft + Offset(size.width, 0f))
            drawCircle(Color.White, radius = 8.dp.toPx(), center = topLeft + Offset(0f, size.height))
            drawCircle(Color.White, radius = 8.dp.toPx(), center = topLeft + Offset(size.width, size.height))
        }
    }
}
