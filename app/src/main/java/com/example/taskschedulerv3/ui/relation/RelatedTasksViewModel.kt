package com.example.taskschedulerv3.ui.relation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskschedulerv3.data.db.AppDatabase
import com.example.taskschedulerv3.data.model.Task
import com.example.taskschedulerv3.data.repository.TaskRelationRepository
import com.example.taskschedulerv3.data.repository.TaskRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class RelatedTasksViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.getInstance(app)
    private val repo = TaskRepository(db.taskDao())
    private val relationRepo = TaskRelationRepository(db.taskRelationDao())

    private val _originTaskId = MutableStateFlow<Int?>(null)

    val originTask: StateFlow<Task?> = _originTaskId
        .filterNotNull()
        .flatMapLatest { id -> flow { emit(repo.getById(id)) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val relatedIds: StateFlow<List<Int>> = _originTaskId
        .filterNotNull()
        .flatMapLatest { id -> relationRepo.getRelatedTaskIds(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val allTasks: StateFlow<List<Task>> = repo.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val relatedTasks: StateFlow<List<Task>> = combine(relatedIds, allTasks) { ids, tasks ->
        tasks.filter { it.id in ids }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun loadForTask(taskId: Int) { _originTaskId.value = taskId }
}
