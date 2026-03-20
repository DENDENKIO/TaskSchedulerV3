package com.example.taskschedulerv3.ui.schedulelist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskschedulerv3.data.db.AppDatabase
import com.example.taskschedulerv3.data.model.Task
import com.example.taskschedulerv3.data.repository.TaskRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ScheduleListViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = TaskRepository(AppDatabase.getInstance(app).taskDao())
    val searchQuery = MutableStateFlow("")

    val tasks: StateFlow<List<Task>> = searchQuery
        .debounce(300)
        .flatMapLatest { q ->
            if (q.isBlank()) repo.getAll() else repo.search(q)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearchQuery(q: String) { searchQuery.value = q }
}
