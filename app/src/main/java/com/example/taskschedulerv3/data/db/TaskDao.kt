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

    @Query("UPDATE tasks SET activeRoadmapStepId = :stepId, updatedAt = :updatedAt WHERE id = :taskId")
    suspend fun updateActiveRoadmapStep(taskId: Int, stepId: Int?, updatedAt: Long)
}
