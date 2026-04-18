package com.example.taskschedulerv3.data.db

import androidx.room.*
import com.example.taskschedulerv3.data.model.RoadmapStep
import kotlinx.coroutines.flow.Flow

@Dao
interface RoadmapStepDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(step: RoadmapStep): Long

    @Update
    suspend fun update(step: RoadmapStep)

    @Delete
    suspend fun delete(step: RoadmapStep)

    @Query("SELECT * FROM roadmap_steps WHERE taskId = :taskId ORDER BY sortOrder ASC")
    fun getStepsForTask(taskId: Int): Flow<List<RoadmapStep>>

    @Query("SELECT * FROM roadmap_steps")
    suspend fun getAllStepsSync(): List<RoadmapStep>

    @Query("SELECT * FROM roadmap_steps WHERE taskId = :taskId ORDER BY sortOrder ASC")
    suspend fun getStepsForTaskSync(taskId: Int): List<RoadmapStep>

    @Query("SELECT * FROM roadmap_steps WHERE id = :id")
    suspend fun getById(id: Int): RoadmapStep?

    @Query("SELECT * FROM roadmap_steps WHERE taskId = :taskId AND isCompleted = 0 ORDER BY sortOrder ASC LIMIT 1")
    suspend fun getNextIncompleteStep(taskId: Int): RoadmapStep?

    @Query("SELECT COUNT(*) FROM roadmap_steps WHERE taskId = :taskId")
    suspend fun countAllSteps(taskId: Int): Int

    @Query("SELECT COUNT(*) FROM roadmap_steps WHERE taskId = :taskId AND isCompleted = 1")
    suspend fun countCompletedSteps(taskId: Int): Int

    @Query("UPDATE roadmap_steps SET isCompleted = :completed, completedAt = :completedAt WHERE id = :id")
    suspend fun setStepCompleted(id: Int, completed: Boolean, completedAt: Long?)

    @Transaction
    suspend fun updateSortOrders(steps: List<RoadmapStep>) {
        steps.forEach { update(it) }
    }
}
