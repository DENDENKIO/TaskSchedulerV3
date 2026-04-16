package com.example.taskschedulerv3.data.repository

import com.example.taskschedulerv3.data.db.TaskDao
import com.example.taskschedulerv3.data.model.Task
import kotlinx.coroutines.flow.Flow

class TaskRepository(private val dao: TaskDao) {
    fun getAll(): Flow<List<Task>> = dao.getAll()
    fun getByDate(date: String): Flow<List<Task>> = dao.getByDate(date)
    suspend fun getById(id: Int): Task? = dao.getById(id)
    fun search(query: String): Flow<List<Task>> = dao.searchByTitle(query)
    fun getDeleted(): Flow<List<Task>> = dao.getDeleted()
    fun getIndefinite(): Flow<List<Task>> = dao.getIndefiniteTasks()
    fun getRecurring(): Flow<List<Task>> = dao.getRecurringTasks()
    fun getCompleted(): Flow<List<Task>> = dao.getCompletedTasks()

    suspend fun insert(task: Task): Long = dao.insert(task)
    suspend fun update(task: Task) = dao.update(task)

    suspend fun softDelete(id: Int) {
        val now = System.currentTimeMillis()
        dao.softDelete(id, now, now)
    }

    suspend fun restore(id: Int) = dao.restore(id, System.currentTimeMillis())

    suspend fun purgeOldDeleted() {
        val threshold = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        dao.purgeOldDeleted(threshold)
    }

    suspend fun setCompleted(id: Int, completed: Boolean) =
        dao.setCompleted(id, completed, System.currentTimeMillis())
}
