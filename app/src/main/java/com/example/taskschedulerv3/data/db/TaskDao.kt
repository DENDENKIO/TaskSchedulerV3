package com.example.taskschedulerv3.data.db

import androidx.room.*
import com.example.taskschedulerv3.data.model.Task
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: Task): Long

    @Update
    suspend fun update(task: Task)

    @Delete
    suspend fun delete(task: Task)

    @Query("SELECT * FROM tasks WHERE isDeleted = 0 ORDER BY startDate ASC")
    fun getAll(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE startDate = :date AND isDeleted = 0")
    fun getByDate(date: String): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: Int): Task?

    @Query("SELECT * FROM tasks WHERE id = :id")
    fun getByIdFlow(id: Int): Flow<Task?>

    @Query("SELECT * FROM tasks WHERE (title LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%') AND isDeleted = 0")
    fun searchByTitle(query: String): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE isDeleted = 1")
    fun getDeleted(): Flow<List<Task>>

    @Query("UPDATE tasks SET isDeleted = 1, deletedAt = :deletedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: Int, deletedAt: Long, updatedAt: Long)

    @Query("UPDATE tasks SET isDeleted = 0, deletedAt = NULL, updatedAt = :updatedAt WHERE id = :id")
    suspend fun restore(id: Int, updatedAt: Long)

    @Query("DELETE FROM tasks WHERE isDeleted = 1 AND deletedAt < :threshold")
    suspend fun purgeOldDeleted(threshold: Long)

    @Query("UPDATE tasks SET isCompleted = :completed, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setCompleted(id: Int, completed: Boolean, updatedAt: Long)

    // 無期限予定取得
    @Query("SELECT * FROM tasks WHERE isIndefinite = 1 AND isDeleted = 0 ORDER BY createdAt DESC")
    fun getIndefiniteTasks(): Flow<List<Task>>

    // 繰り返し予定取得
    @Query("SELECT * FROM tasks WHERE recurrencePattern IS NOT NULL AND recurrencePattern != 'NONE' AND isDeleted = 0 ORDER BY startDate ASC")
    fun getRecurringTasks(): Flow<List<Task>>

    // 完了タスク取得
    @Query("SELECT * FROM tasks WHERE isCompleted = 1 AND isDeleted = 0 ORDER BY updatedAt DESC")
    fun getCompletedTasks(): Flow<List<Task>>

    @Query("SELECT t.* FROM tasks t INNER JOIN task_tag_cross_ref c ON t.id = c.taskId WHERE c.tagId IN (:tagIds) AND t.isDeleted = 0 ORDER BY t.startDate ASC")
    fun getTasksByTagIds(tagIds: List<Int>): Flow<List<Task>>

    // 親子関係用
    @Query("SELECT * FROM tasks WHERE parentTaskId = :parentId AND isDeleted = 0 ORDER BY startDate ASC")
    fun getChildrenTasks(parentId: Int): Flow<List<Task>>

    @Query("SELECT COUNT(*) FROM tasks WHERE parentTaskId = :parentId AND isDeleted = 0")
    suspend fun countChildren(parentId: Int): Int

    @Query("UPDATE tasks SET parentTaskId = :parentId, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateParentTaskId(id: Int, parentId: Int?, updatedAt: Long)

    @Query("SELECT * FROM tasks WHERE parentTaskId IS NOT NULL AND isDeleted = 0")
    suspend fun getAllChildrenSync(): List<Task>

    // ★追加: Flow版（リアクティブキャッシュ用）
    @Query("SELECT * FROM tasks WHERE parentTaskId IS NOT NULL AND isDeleted = 0")
    fun getAllChildrenFlow(): Flow<List<Task>>

    @Query("UPDATE tasks SET activeRoadmapStepId = :stepId, updatedAt = :updatedAt WHERE id = :taskId")
    suspend fun updateActiveRoadmapStep(taskId: Int, stepId: Int?, updatedAt: Long)

    @Query("UPDATE tasks SET progress = :progress, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateProgress(id: Int, progress: Int, updatedAt: Long)

    // AIチャット用: 日付範囲で未完了タスクを取得
    @Query("SELECT * FROM tasks WHERE startDate BETWEEN :fromDate AND :toDate AND isDeleted = 0 AND isCompleted = 0 ORDER BY startDate ASC, startTime ASC")
    suspend fun getTasksBetweenDates(fromDate: String, toDate: String): List<Task>

    // AIチャット用: 全未完了タスクを取得（直近順）
    @Query("SELECT * FROM tasks WHERE isDeleted = 0 AND isCompleted = 0 ORDER BY startDate ASC, startTime ASC LIMIT :limit")
    suspend fun getUpcomingTasks(limit: Int = 50): List<Task>

    // AIチャット用: キーワード検索（同期）
    @Query("SELECT * FROM tasks WHERE (title LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%' OR location LIKE '%' || :query || '%') AND isDeleted = 0 AND isCompleted = 0 ORDER BY startDate ASC")
    suspend fun searchTasksSync(query: String): List<Task>

    // AIチャット用: 特定日のタスクを取得（同期）
    @Query("SELECT * FROM tasks WHERE startDate = :date AND isDeleted = 0 ORDER BY startTime ASC")
    suspend fun getTasksByDateSync(date: String): List<Task>

    // ★追加: AI要約追記用
    @Query("UPDATE tasks SET description = CASE WHEN description IS NULL OR description = '' THEN :text ELSE description || '\n\n' || :text END, updatedAt = :updatedAt WHERE id = :id")
    suspend fun appendDescription(id: Int, text: String, updatedAt: Long)

    // AI写真メモ要約用: 完了タスクを同期取得
    @Query("SELECT * FROM tasks WHERE isCompleted = 1 AND isDeleted = 0 ORDER BY updatedAt DESC")
    suspend fun getCompletedTasksSync(): List<Task>
}
