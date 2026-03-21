package com.example.taskschedulerv3.ui.taskdetail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskschedulerv3.data.db.AppDatabase
import com.example.taskschedulerv3.data.model.PhotoMemo
import com.example.taskschedulerv3.data.model.Task
import com.example.taskschedulerv3.data.repository.TaskRelationRepository
import com.example.taskschedulerv3.data.repository.TaskRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class TaskDetailViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.getInstance(app)
    private val repo = TaskRepository(db.taskDao())
    private val relationRepo = TaskRelationRepository(db.taskRelationDao())

    private val _taskId = MutableStateFlow<Int?>(null)

    val task: StateFlow<Task?> = _taskId
        .filterNotNull()
        .flatMapLatest { id ->
            flow { emit(repo.getById(id)) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Related task ids (reactive)
    val relatedTaskIds: StateFlow<List<Int>> = _taskId
        .filterNotNull()
        .flatMapLatest { id -> relationRepo.getRelatedTaskIds(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // All tasks to resolve related ids → Task objects
    private val allTasks: StateFlow<List<Task>> = repo.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val relatedTasks: StateFlow<List<Task>> = combine(relatedTaskIds, allTasks) { ids, tasks ->
        tasks.filter { it.id in ids }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Photos attached to this task
    val photos: StateFlow<List<PhotoMemo>> = _taskId
        .filterNotNull()
        .flatMapLatest { id -> db.photoMemoDao().getByTaskId(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun loadTask(id: Int) { _taskId.value = id }
}
