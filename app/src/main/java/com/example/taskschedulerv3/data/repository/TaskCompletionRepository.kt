package com.example.taskschedulerv3.data.repository

import com.example.taskschedulerv3.data.db.TaskCompletionDao
import com.example.taskschedulerv3.data.model.TaskCompletion
import kotlinx.coroutines.flow.Flow

class TaskCompletionRepository(private val dao: TaskCompletionDao) {
    fun getByTaskId(taskId: Int): Flow<List<TaskCompletion>> = dao.getByTaskId(taskId)
    suspend fun isCompleted(taskId: Int, date: String): Boolean = dao.isCompleted(taskId, date)
    suspend fun insert(taskId: Int, date: String) = dao.insert(TaskCompletion(taskId = taskId, completedDate = date))
    suspend fun delete(completion: TaskCompletion) = dao.delete(completion)
}
