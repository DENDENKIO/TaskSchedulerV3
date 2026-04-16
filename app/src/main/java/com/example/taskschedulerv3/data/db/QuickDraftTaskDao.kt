package com.example.taskschedulerv3.data.db

import androidx.room.*
import com.example.taskschedulerv3.data.model.QuickDraftTask
import kotlinx.coroutines.flow.Flow

@Dao
interface QuickDraftTaskDao {
    @Query("SELECT * FROM quick_draft_tasks WHERE status = 'DRAFT' ORDER BY createdAt DESC")
    fun getDrafts(): Flow<List<QuickDraftTask>>

    @Query("SELECT * FROM quick_draft_tasks ORDER BY createdAt DESC")
    fun getAll(): Flow<List<QuickDraftTask>>

    @Query("SELECT * FROM quick_draft_tasks WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): QuickDraftTask?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(draft: QuickDraftTask): Long

    @Update
    suspend fun update(draft: QuickDraftTask)

    @Delete
    suspend fun delete(draft: QuickDraftTask)

    @Query("UPDATE quick_draft_tasks SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: Int, status: String, updatedAt: Long)
}
