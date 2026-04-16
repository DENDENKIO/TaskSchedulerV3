package com.example.taskschedulerv3.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

class TaskFlowUiViewModel : ViewModel() {
    var showAddTaskSheet by mutableStateOf(false)
        private set
    
    var editingTaskId by mutableStateOf<Int?>(null)
        private set

    var showQuickDraftSheet by mutableStateOf(false)
        private set

    fun openAddTask() {
        editingTaskId = null
        showAddTaskSheet = true
    }

    fun openEditTask(taskId: Int) {
        editingTaskId = taskId
        showAddTaskSheet = true
    }

    fun closeAddTaskSheet() {
        showAddTaskSheet = false
        editingTaskId = null
    }

    fun openQuickDraftSheet() { showQuickDraftSheet = true }
    fun closeQuickDraftSheet() { showQuickDraftSheet = false }
}
