package com.example.taskschedulerv3.ui.trash

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskschedulerv3.data.db.AppDatabase
import com.example.taskschedulerv3.data.model.Task
import com.example.taskschedulerv3.data.repository.TaskRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TrashViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = TaskRepository(AppDatabase.getInstance(app).taskDao())

    val deletedTasks: StateFlow<List<Task>> = repo.getDeleted()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun restore(task: Task) = viewModelScope.launch { repo.restore(task.id) }

    fun permanentDelete(task: Task) = viewModelScope.launch {
        // Hard delete by updating isDeleted and then purging
        repo.purgeOldDeleted()
    }

    fun purgeAll() = viewModelScope.launch {
        deletedTasks.value.forEach { task ->
            // Force deletedAt to past time so purge removes them
            repo.softDelete(task.id) // re-softDelete to refresh deletedAt if needed
        }
        // Purge all older than 0ms (immediate purge)
        AppDatabase.getInstance(getApplication()).taskDao().purgeOldDeleted(System.currentTimeMillis() + 1000)
    }
}
