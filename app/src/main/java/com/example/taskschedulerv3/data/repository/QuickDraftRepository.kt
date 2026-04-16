package com.example.taskschedulerv3.data.repository

import com.example.taskschedulerv3.data.db.QuickDraftTaskDao
import com.example.taskschedulerv3.data.db.TaskDao
import com.example.taskschedulerv3.data.db.TaskTagCrossRefDao
import com.example.taskschedulerv3.data.model.QuickDraftTask
import com.example.taskschedulerv3.data.model.RecurrencePattern
import com.example.taskschedulerv3.data.model.ScheduleType
import com.example.taskschedulerv3.data.model.Task
import com.example.taskschedulerv3.data.model.TaskTagCrossRef
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class QuickDraftRepository(
    private val draftDao: QuickDraftTaskDao,
    private val taskDao: TaskDao,
    private val crossRefDao: TaskTagCrossRefDao
) {
    /** アクティブな仮登録一覧 (status=DRAFT) */
    fun getDrafts(): Flow<List<QuickDraftTask>> = draftDao.getDrafts()

    /** 全仮登録 (フィルタなし) */
    fun getAll(): Flow<List<QuickDraftTask>> = draftDao.getAll()

    /** ID取得 */
    suspend fun getById(id: Int): QuickDraftTask? = draftDao.getById(id)

    /** 仮登録新規作成 */
    suspend fun insert(draft: QuickDraftTask): Long = draftDao.insert(draft)

    /** 仮登録更新 */
    suspend fun update(draft: QuickDraftTask) = draftDao.update(draft.copy(updatedAt = System.currentTimeMillis()))

    /** 仮登録削除 */
    suspend fun delete(draft: QuickDraftTask) = draftDao.delete(draft)

    /**
     * 仮登録を正式なTaskに変換して保存する。
     * 変換後は draft.status = CONVERTED に更新する。
     * @return 新規作成した Task の ID
     */
    suspend fun convertToTask(
        draft: QuickDraftTask,
        startDate: String = LocalDate.now().toString(),
        priority: Int = 1,
        tagIds: List<Int> = emptyList()
    ): Long {
        val now = System.currentTimeMillis()

        // QuickDraftTask → Task 生成
        val newTask = Task(
            title = draft.title,
            description = buildString {
                if (!draft.description.isNullOrBlank()) append(draft.description)
                if (!draft.ocrText.isNullOrBlank()) {
                    if (isNotEmpty()) append("\n\n---\n")
                    append("[OCR]\n")
                    append(draft.ocrText)
                }
            }.ifBlank { null },
            startDate = startDate,
            priority = priority,
            scheduleType = ScheduleType.NORMAL,
            recurrencePattern = null,
            createdAt = now,
            updatedAt = now
        )

        // Task を挿入
        val taskId = taskDao.insert(newTask)

        // draftのtagIds(文字列)をリストに変換
        val draftTagIds = draft.tagIds?.split(",")?.mapNotNull { it.toIntOrNull() } ?: emptyList()
        // draftに登録されていたタグと引数で渡されたタグをすべて結合して重複排除
        val allTagIds = (draftTagIds + tagIds).toSet()

        // 中間テーブル(TaskTagCrossRef)に保存
        allTagIds.forEach { tagId ->
            crossRefDao.insert(TaskTagCrossRef(taskId.toInt(), tagId))
        }

        // draft のステータスを CONVERTED に更新
        draftDao.updateStatus(draft.id, "CONVERTED", now)

        return taskId
    }

    /** 仮登録を破棄する (DISCARDED) */
    suspend fun discard(draft: QuickDraftTask) {
        draftDao.updateStatus(draft.id, "DISCARDED", System.currentTimeMillis())
    }
}
