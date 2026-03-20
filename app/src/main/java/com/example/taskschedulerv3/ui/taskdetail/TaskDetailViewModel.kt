package com.example.taskschedulerv3.ui.taskdetail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskschedulerv3.data.db.AppDatabase
import com.example.taskschedulerv3.data.model.Task
import com.example.taskschedulerv3.data.repository.TaskRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class TaskDetailViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = TaskRepository(AppDatabase.getInstance(app).taskDao())
    private val _task = MutableStateFlow<Task?>(null)
    val task: StateFlow<Task?> = _task
    fun loadTask(id: Int) = viewModelScope.launch { _task.value = repo.getById(id) }
}
