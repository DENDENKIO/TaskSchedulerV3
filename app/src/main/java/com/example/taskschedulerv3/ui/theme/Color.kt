package com.example.taskschedulerv3.ui.theme

import androidx.compose.ui.graphics.Color

// --- ダークモード用カラー 定数 ---
val BackgroundDark      = Color(0xFF0E0E11)
val SurfaceDark         = Color(0xFF15151A)
val SurfaceVariantDark  = Color(0xFF1E1E26)
val PrimaryDark         = Color(0xFF8B7CF6)
val OnPrimaryDark       = Color(0xFF1A1040)
val PrimaryContainerDark = Color(0xFF2A2250)
val OnSurfaceDark       = Color(0xFFE8E8F0)
val OnSurfaceVariantDark = Color(0xFF9090A8)
val OutlineDark         = Color(0xFF2E2E3A)

// --- ライトモード用カラー 定数 ---
val BackgroundLight     = Color(0xFFF7F7FC)
val SurfaceLight        = Color(0xFFFFFFFF)
val SurfaceVariantLight = Color(0xFFEEEEF8)
val PrimaryLight        = Color(0xFF5B4CF5)
val OnPrimaryLight      = Color(0xFFFFFFFF)
val PrimaryContainerLight = Color(0xFFE8E4FF)
val OnSurfaceLight      = Color(0xFF1A1A2E)
val OnSurfaceVariantLight = Color(0xFF5C5C70)
val OutlineLight        = Color(0xFFDDDDEE)

// --- 補助カラー ---
val SuccessGreen = Color(0xFF81C784)
val WarningOrange = Color(0xFFFFB74D)
val ErrorRed = Color(0xFFE57373)

// --- 独自・カスタムカラー (TaskFlowカード用 - シック・渋め) ---
val TaskCardBg = Color(0xFF1B223B) // 深い紺
val OnTaskCardBg = Color(0xFFE8E8F0) // オフホワイト
val OnTaskCardBgVariant = Color(0xFF9098B0) // くすんだブルーグレー

// --- 渋めのバッジ用背景色 (くすみカラー) ---
val ChicMutedRedBg   = Color(0xFF453030)
val ChicMutedAmberBg = Color(0xFF454030)
val ChicMutedBlueBg  = Color(0xFF303545)
val ChicMutedGreenBg = Color(0xFF304035)

/**
 * 優先度に応じたカラーを返す (ユーザー指定：そのまま維持)
 */
fun priorityColor(priority: Int, isDark: Boolean): Color = when (priority) {
    0 -> if (isDark) Color(0xFFE57373) else Color(0xFFD32F2F)  // 高：赤
    1 -> if (isDark) Color(0xFFFFB74D) else Color(0xFFA67100)  // 中：濃いめのアンバー（黄土色系）
    2 -> if (isDark) Color(0xFF81C784) else Color(0xFF388E3C)  // 低：緑
    else -> Color.Gray
}

/**
 * 残存日数に応じたカラーを返す
 */
fun remainingDaysColor(days: Int, isDark: Boolean): Color = when {
    days <= 0  -> Color(0xFFE57373)  // 期限切れ・今日：赤
    days <= 2  -> if (isDark) Color(0xFFFFB74D) else Color(0xFFA67100)  // 2日以内：濃いめのアンバー
    days <= 7  -> if (isDark) Color(0xFF8B7CF6) else Color(0xFF3949AB)  // 1週間以内：濃いめの青
    else       -> Color(0xFF9090A8)  // 余裕あり：グレー
}