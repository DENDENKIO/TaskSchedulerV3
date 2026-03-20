package com.example.taskschedulerv3.data.db

import androidx.room.*
import com.example.taskschedulerv3.data.model.Tag
import com.example.taskschedulerv3.data.model.Task
import com.example.taskschedulerv3.data.model.TaskTagCrossRef
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskTagCrossRefDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(crossRef: TaskTagCrossRef)

    @Query("DELETE FROM task_tag_cross_ref WHERE taskId = :taskId")
    suspend fun deleteByTaskId(taskId: Int)

    @Query("""
        SELECT tags.* FROM tags
        INNER JOIN task_tag_cross_ref ON tags.id = task_tag_cross_ref.tagId
        WHERE task_tag_cross_ref.taskId = :taskId
    """)
    fun getTagsByTaskId(taskId: Int): Flow<List<Tag>>

    @Query("""
        SELECT tasks.* FROM tasks
        INNER JOIN task_tag_cross_ref ON tasks.id = task_tag_cross_ref.taskId
        WHERE task_tag_cross_ref.tagId IN (:tagIds) AND tasks.isDeleted = 0
    """)
    fun getTasksByTagIds(tagIds: List<Int>): Flow<List<Task>>
}
