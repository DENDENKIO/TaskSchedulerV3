package com.example.taskschedulerv3.data.repository

import com.example.taskschedulerv3.data.db.RoadmapStepDao
import com.example.taskschedulerv3.data.db.TaskDao
import com.example.taskschedulerv3.data.model.Task
import kotlinx.coroutines.flow.Flow

class TaskRepository(
    private val dao: TaskDao,
    private val roadmapDao: RoadmapStepDao
) {
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

    suspend fun revertRoadmapStep(taskId: Int) {
        val task = dao.getById(taskId) ?: return
        if (!task.roadmapEnabled) return
        
        val steps = roadmapDao.getStepsForTaskSync(taskId)
        if (steps.isEmpty()) {
            if (task.isCompleted) dao.setCompleted(taskId, false, System.currentTimeMillis())
            return
        }

        val now = System.currentTimeMillis()
        if (task.isCompleted) {
            // 全完了状態から戻す -> 最後のステップを実行中に戻す
            val lastStep = steps.last()
            roadmapDao.setStepCompleted(lastStep.id, false, null)
            
            val updatedTask = task.copy(
                isCompleted = false,
                activeRoadmapStepId = lastStep.id,
                startDate = lastStep.date ?: task.startDate,
                updatedAt = now
            )
            dao.update(updatedTask)
        } else {
            val currentStepId = task.activeRoadmapStepId
            if (currentStepId == null) {
                // 開始前(START)なら何もしない
                return
            }
            
            // 現在のステップを未完了にし、一つ前に戻る
            roadmapDao.setStepCompleted(currentStepId, false, null)
            val currentIndex = steps.indexOfFirst { it.id == currentStepId }
            val prevStep = if (currentIndex > 0) steps[currentIndex - 1] else null
            val prevStepId = prevStep?.id
            
            val updatedTask = task.copy(
                activeRoadmapStepId = prevStepId,
                startDate = prevStep?.date ?: task.startDate,
                updatedAt = now
            )
            dao.update(updatedTask)
        }
    }

    // ロードマップ・親子関係用
    suspend fun countChildren(parentId: Int): Int = dao.countChildren(parentId)
    
    suspend fun getNextIncompleteStep(taskId: Int) = 
        roadmapDao.getNextIncompleteStep(taskId)

    suspend fun calculateRoadmapProgress(taskId: Int): Int {
        val task = dao.getById(taskId) ?: return 0
        val total = roadmapDao.countAllSteps(taskId) + 1 // +1 はタスク本体(START)
        if (total <= 1) return 0
        val completedSteps = roadmapDao.countCompletedSteps(taskId)
        val completedTotal = completedSteps + (if (task.activeRoadmapStepId != null || task.isCompleted) 1 else 0)
        return (completedTotal * 100) / total
    }
}
