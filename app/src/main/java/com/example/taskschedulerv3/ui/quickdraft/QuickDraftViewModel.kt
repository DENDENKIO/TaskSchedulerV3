package com.example.taskschedulerv3.ui.quickdraft

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskschedulerv3.data.db.AppDatabase
import com.example.taskschedulerv3.data.model.QuickDraftTask
import com.example.taskschedulerv3.data.repository.QuickDraftRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class QuickDraftViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.getInstance(app)
    val repo = QuickDraftRepository(db.quickDraftTaskDao(), db.taskDao(), db.taskTagCrossRefDao())

    val drafts: StateFlow<List<QuickDraftTask>> = repo.getDrafts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val convertSuccess = MutableStateFlow(false)

    /**
     * カメラ撮影後に仮登録を自動作成する。
     * タイトルは "yyyy-MM-dd HH:mm 仮登録" 形式。
     * @param photoPath 保存済み画像のパス（なければnull）
     * @param ocrText OCRで読み取ったテキスト（なければnull）
     */
    fun createFromCamera(photoPath: String? = null, ocrText: String? = null, tagIds: List<Int> = emptyList()) = viewModelScope.launch {
        val now = LocalDateTime.now()
        val autoTitle = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) + " 仮登録"
        val tagIdsStr = if (tagIds.isEmpty()) null else tagIds.joinToString(",")
        val draft = QuickDraftTask(
            title = autoTitle,
            photoPath = photoPath,
            ocrText = ocrText,
            status = "DRAFT",
            tagIds = tagIdsStr
        )
        repo.insert(draft)
    }

    /** 仮登録を更新 */
    fun updateDraft(draft: QuickDraftTask) = viewModelScope.launch {
        repo.update(draft)
    }

    /** 仮登録を削除 */
    fun deleteDraft(draft: QuickDraftTask) = viewModelScope.launch {
        repo.delete(draft)
    }

    /**
     * 仮登録 → 通常タスクへ変換
     * 完了時に convertSuccess = true を発火
     */
    fun convertToTask(
        draft: QuickDraftTask,
        startDate: String = LocalDate.now().toString(),
        priority: Int = 1
    ) = viewModelScope.launch {
        repo.convertToTask(draft, startDate, priority)
        convertSuccess.value = true
    }

    fun clearConvertSuccess() { convertSuccess.value = false }
}
