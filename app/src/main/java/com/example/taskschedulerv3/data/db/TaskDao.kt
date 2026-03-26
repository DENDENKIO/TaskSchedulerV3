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

    @Query("SELECT * FROM tasks WHERE isDeleted = 0 AND isIndefinite = 0 ORDER BY startDate ASC")
    fun getAll(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE startDate = :date AND isDeleted = 0 AND isIndefinite = 0")
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
}
