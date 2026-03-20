package com.example.taskschedulerv3.data.repository

import com.example.taskschedulerv3.data.db.TaskRelationDao
import com.example.taskschedulerv3.data.model.TaskRelation
import kotlinx.coroutines.flow.Flow

class TaskRelationRepository(private val dao: TaskRelationDao) {
    fun getRelatedTaskIds(taskId: Int): Flow<List<Int>> = dao.getRelatedTaskIds(taskId)

    suspend fun getRelationsForTask(taskId: Int): List<TaskRelation> = dao.getRelationsForTask(taskId)

    suspend fun insert(taskId1: Int, taskId2: Int) {
        val t1 = minOf(taskId1, taskId2)
        val t2 = maxOf(taskId1, taskId2)
        dao.insert(TaskRelation(taskId1 = t1, taskId2 = t2))
    }

    suspend fun delete(relation: TaskRelation) = dao.delete(relation)

    suspend fun deleteRelation(taskId: Int, relatedId: Int) {
        val t1 = minOf(taskId, relatedId)
        val t2 = maxOf(taskId, relatedId)
        dao.getRelation(t1, t2)?.let { dao.delete(it) }
    }
}
