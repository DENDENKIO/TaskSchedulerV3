package com.example.taskschedulerv3.ui.roadmap

import java.time.LocalDate

/**
 * ロードマップ編集画面専用のUIモデル (詳細仕様書 5.2 準拠)
 */
data class RoadmapStepEditorItem(
    val localId: String,          // Composeでの安定キー管理用 (UUID等)
    val dbId: Int? = null,        // 既存DB行の識別子 (新規行はnull)
    val taskId: Int,              // 親タスクID
    val title: String,            // 入力中のタイトル
    val targetDate: LocalDate? = null, // 入力中の日付
    val isCompleted: Boolean = false,  // 完了状態
    val sortOrder: Int,           // 並び順
    val isDeleted: Boolean = false,    // 即削除UIでも保存前に差分管理するためのフラグ
    val isDirty: Boolean = false,      // 変更有無
    val isNew: Boolean = false         // INSERT対象判定用
)
