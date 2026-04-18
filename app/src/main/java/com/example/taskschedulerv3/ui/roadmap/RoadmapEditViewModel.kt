package com.example.taskschedulerv3.ui.roadmap

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskschedulerv3.data.db.AppDatabase
import com.example.taskschedulerv3.data.model.RoadmapStep
import com.example.taskschedulerv3.data.model.Task
import com.example.taskschedulerv3.data.repository.TaskRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
class RoadmapEditViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.getInstance(app)
    private val repo = TaskRepository(db.taskDao(), db.roadmapStepDao())
    private val RoadmapStepDao = db.roadmapStepDao()

    private val _taskId = MutableStateFlow<Int?>(null)
    val taskId: StateFlow<Int?> = _taskId.asStateFlow()

    val task: StateFlow<Task?> = _taskId
        .filterNotNull()
        .flatMapLatest { id -> flow { emit(repo.getById(id)) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val steps: StateFlow<List<RoadmapStep>> = _taskId
        .filterNotNull()
        .flatMapLatest { id -> RoadmapStepDao.getStepsForTask(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun loadTask(id: Int) { _taskId.value = id }

    fun addStep(title: String, date: String? = null) = viewModelScope.launch {
        val currentTaskId = _taskId.value ?: return@launch
        val currentSteps = steps.value
        val nextOrder = (currentSteps.maxOfOrNull { it.sortOrder } ?: 0) + 1
        val newStep = RoadmapStep(
            taskId = currentTaskId,
            title = title,
            date = date,
            sortOrder = nextOrder
        )
        RoadmapStepDao.insert(newStep)
    }

    fun updateStep(step: RoadmapStep) = viewModelScope.launch {
        RoadmapStepDao.update(step)
    }

    fun deleteStep(step: RoadmapStep) = viewModelScope.launch {
        RoadmapStepDao.delete(step)
    }

    fun toggleStepCompletion(step: RoadmapStep) = viewModelScope.launch {
        val completed = !step.isCompleted
        val now = if (completed) System.currentTimeMillis() else null
        RoadmapStepDao.setStepCompleted(step.id, completed, now)
    }

    fun updateStepOrders(orderedSteps: List<RoadmapStep>) = viewModelScope.launch {
        val updated = orderedSteps.mapIndexed { index, step ->
            step.copy(sortOrder = index + 1)
        }
        RoadmapStepDao.updateSortOrders(updated)
    }
}
