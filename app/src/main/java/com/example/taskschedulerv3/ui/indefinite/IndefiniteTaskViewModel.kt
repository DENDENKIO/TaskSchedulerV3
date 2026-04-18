package com.example.taskschedulerv3.ui.indefinite

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
import java.time.LocalDate

class IndefiniteTaskViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.getInstance(app)
    private val repo = TaskRepository(db.taskDao(), db.roadmapStepDao())

    val indefiniteTasks: StateFlow<List<Task>> = repo.getIndefinite()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 期限付きに変換（startDate を今日にセット、isIndefinite = false）
    fun convertToScheduled(task: Task, newStartDate: String) = viewModelScope.launch {
        repo.insert(
            task.copy(
                isIndefinite = false,
                startDate = newStartDate,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    fun deleteTask(task: Task) = viewModelScope.launch {
        repo.softDelete(task.id)
    }
}
