package com.example.taskschedulerv3.data.db

import androidx.room.*
import com.example.taskschedulerv3.data.model.TaskCompletion
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskCompletionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(completion: TaskCompletion)

    @Delete
    suspend fun delete(completion: TaskCompletion)

    @Query("SELECT * FROM task_completions WHERE taskId = :taskId")
    fun getByTaskId(taskId: Int): Flow<List<TaskCompletion>>

    @Query("SELECT COUNT(*) > 0 FROM task_completions WHERE taskId = :taskId AND completedDate = :date")
    suspend fun isCompleted(taskId: Int, date: String): Boolean

    @Query("SELECT * FROM task_completions")
    fun getAll(): kotlinx.coroutines.flow.Flow<List<TaskCompletion>>
}
