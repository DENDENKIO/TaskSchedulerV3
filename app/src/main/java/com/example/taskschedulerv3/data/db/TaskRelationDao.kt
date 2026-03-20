package com.example.taskschedulerv3.data.db

import androidx.room.*
import com.example.taskschedulerv3.data.model.TaskRelation
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskRelationDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(relation: TaskRelation)

    @Delete
    suspend fun delete(relation: TaskRelation)

    @Query("""
        SELECT CASE WHEN taskId1 = :taskId THEN taskId2 ELSE taskId1 END AS relatedId
        FROM task_relations
        WHERE taskId1 = :taskId OR taskId2 = :taskId
    """)
    fun getRelatedTaskIds(taskId: Int): Flow<List<Int>>

    @Query("SELECT * FROM task_relations WHERE taskId1 = :taskId OR taskId2 = :taskId")
    suspend fun getRelationsForTask(taskId: Int): List<TaskRelation>

    @Query("""
        SELECT * FROM task_relations
        WHERE (taskId1 = :taskId1 AND taskId2 = :taskId2)
           OR (taskId1 = :taskId2 AND taskId2 = :taskId1)
        LIMIT 1
    """)
    suspend fun getRelation(taskId1: Int, taskId2: Int): TaskRelation?

    @Query("DELETE FROM task_relations WHERE taskId1 = :taskId OR taskId2 = :taskId")
    suspend fun deleteAllForTask(taskId: Int)
}
