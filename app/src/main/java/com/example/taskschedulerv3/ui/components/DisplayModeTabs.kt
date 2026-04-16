package com.example.taskschedulerv3.ui.components

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.sp

enum class DisplayMode(val label: String) {
    TODAY("今日"),
    WEEK("今週"),
    INDEFINITE("無期限"),
    ALL("すべて"),
    DONE("完了"),
    RECURRING("繰り返し"),
    DRAFT("仮登録")
}

@Composable
fun DisplayModeTabs(
    selectedMode: DisplayMode,
    onModeSelected: (DisplayMode) -> Unit
) {
    ScrollableTabRow(
        selectedTabIndex = DisplayMode.entries.indexOf(selectedMode),
        edgePadding = 0.dp,
        containerColor = MaterialTheme.colorScheme.surface,
        divider = {}
    ) {
        DisplayMode.entries.forEach { mode ->
            Tab(
                selected = selectedMode == mode,
                onClick = { onModeSelected(mode) },
                text = {
                    Text(
                        mode.label,
                        fontSize = 12.sp
                    )
                }
            )
        }
    }
}

// extension for zero dp (workaround for kotlin import)
private val Int.dp get() = androidx.compose.ui.unit.Dp(this.toFloat())
