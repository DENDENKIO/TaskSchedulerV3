package com.example.taskschedulerv3.data.db

import androidx.room.*
import com.example.taskschedulerv3.data.model.TaskRelation
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskRelationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(relation: TaskRelation)

    @Delete
    suspend fun delete(relation: TaskRelation)

    @Query("""
        SELECT CASE WHEN taskId1 = :taskId THEN taskId2 ELSE taskId1 END AS relatedId
        FROM task_relations
        WHERE taskId1 = :taskId OR taskId2 = :taskId
    """)
    fun getRelatedTaskIds(taskId: Int): Flow<List<Int>>
}
