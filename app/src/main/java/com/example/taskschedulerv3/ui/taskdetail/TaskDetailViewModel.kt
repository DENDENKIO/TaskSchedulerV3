package com.example.taskschedulerv3.ui.taskdetail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskschedulerv3.data.db.AppDatabase
import com.example.taskschedulerv3.data.model.PhotoMemo
import com.example.taskschedulerv3.data.model.RoadmapStep
import com.example.taskschedulerv3.data.model.Task
import com.example.taskschedulerv3.data.repository.TaskRelationRepository
import com.example.taskschedulerv3.data.repository.TaskRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class TaskDetailViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.getInstance(app)
    private val repo = TaskRepository(db.taskDao(), db.roadmapStepDao())
    private val relationRepo = TaskRelationRepository(db.taskRelationDao())

    private val _taskId = MutableStateFlow<Int?>(null)

    val task: StateFlow<Task?> = _taskId
        .filterNotNull()
        .flatMapLatest { id ->
            repo.getByIdFlow(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // 全タスク Flow
    private val allTasks: StateFlow<List<Task>> = repo.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 関連タスク (TaskRelation + Legacy Parent/Child) の基盤データ
    private val relatedTaskIdsFromTable: Flow<List<Int>> = _taskId
        .filterNotNull()
        .flatMapLatest { id -> relationRepo.getRelatedTaskIds(id) }

    val relatedTasks: StateFlow<List<Task>> = combine(
        task,
        allTasks,
        relatedTaskIdsFromTable
    ) { currentTask, all, tableIds ->
        if (currentTask == null) return@combine emptyList<Task>()
        
        val id = currentTask.id
        val legacyParentId = currentTask.parentTaskId
        val legacyChildrenIds = all.filter { it.parentTaskId == id }.map { it.id }
        
        val combinedIds = (tableIds + listOfNotNull(legacyParentId) + legacyChildrenIds).toSet()
        all.filter { it.id in combinedIds && it.id != id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ロードマップステップ一覧 (ステップ6)
    val roadmapSteps: StateFlow<List<RoadmapStep>> = _taskId
        .filterNotNull()
        .flatMapLatest { id -> db.roadmapStepDao().getStepsForTask(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Photos attached to this task
    val photos: StateFlow<List<PhotoMemo>> = _taskId
        .filterNotNull()
        .flatMapLatest { id -> db.photoMemoDao().getByTaskId(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun loadTask(id: Int) { _taskId.value = id }

    fun toggleComplete(task: Task) = viewModelScope.launch {
        if (task.roadmapEnabled) {
            processRoadmapCompletion(task)
        } else {
            repo.setCompleted(task.id, !task.isCompleted)
        }
    }

    fun revertRoadmapStep(task: Task) = viewModelScope.launch {
        repo.revertRoadmapStep(task.id)
        repo.syncTaskProgress(task.id)
    }

    private suspend fun processRoadmapCompletion(task: Task) {
        val roadmapStepDao = db.roadmapStepDao()
        val steps = roadmapStepDao.getStepsForTaskSync(task.id)
        
        if (steps.isEmpty()) {
            repo.setCompleted(task.id, true)
            return
        }

        val currentStepId = task.activeRoadmapStepId
        if (currentStepId == null) {
            val firstStep = steps.firstOrNull()
            if (firstStep != null) {
                db.taskDao().update(task.copy(
                    activeRoadmapStepId = firstStep.id,
                    startDate = firstStep.date ?: task.startDate,
                    updatedAt = System.currentTimeMillis()
                ))
                repo.syncTaskProgress(task.id)
            } else {
                repo.setCompleted(task.id, true)
                repo.syncTaskProgress(task.id)
            }
        } else {
            roadmapStepDao.setStepCompleted(currentStepId, true, System.currentTimeMillis())
            val currentIndex = steps.indexOfFirst { it.id == currentStepId }
            val nextStep = if (currentIndex != -1 && currentIndex < steps.size - 1) {
                steps[currentIndex + 1]
            } else {
                null
            }

            if (nextStep != null) {
                db.taskDao().update(task.copy(
                    activeRoadmapStepId = nextStep.id,
                    startDate = nextStep.date ?: task.startDate,
                    updatedAt = System.currentTimeMillis()
                ))
                repo.syncTaskProgress(task.id)
            } else {
                repo.setCompleted(task.id, true)
                repo.syncTaskProgress(task.id)
            }
        }
    }
}
