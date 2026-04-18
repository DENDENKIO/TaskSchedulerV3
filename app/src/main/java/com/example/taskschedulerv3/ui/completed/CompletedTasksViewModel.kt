package com.example.taskschedulerv3.ui.completed

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

class CompletedTasksViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.getInstance(app)
    private val repo = TaskRepository(db.taskDao(), db.roadmapStepDao())

    val completedTasks: StateFlow<List<Task>> = repo.getCompleted()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun restore(task: Task) = viewModelScope.launch {
        repo.setCompleted(task.id, false)
    }

    fun deletePermanently(task: Task) = viewModelScope.launch {
        // 完了済み画面からの削除は、論理削除(isDeleted=1) ではなく
        // ここでは物理削除にしてしまうか、さらにゴミ箱へ送る。
        // 要望としては「管理する画面」なので、ここで消すと完全に消えるのが自然か。
        db.taskDao().delete(task)
    }
}
