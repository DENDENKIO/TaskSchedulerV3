package com.example.taskschedulerv3.ui.trash

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskschedulerv3.data.db.AppDatabase
import com.example.taskschedulerv3.data.model.Task
import com.example.taskschedulerv3.data.repository.TaskRepository
import com.example.taskschedulerv3.notification.AlarmScheduler
import com.example.taskschedulerv3.util.PhotoFileManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TrashViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.getInstance(app)
    private val repo = TaskRepository(db.taskDao())

    val deletedTasks: StateFlow<List<Task>> = repo.getDeleted()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun restore(task: Task) = viewModelScope.launch {
        repo.restore(task.id)
    }

    fun permanentDelete(task: Task) = viewModelScope.launch {
        // Cancel any pending alarm
        AlarmScheduler.cancel(getApplication(), task.id)
        // Delete associated photos from filesystem
        db.photoMemoDao().getByTaskId(task.id).first().forEach { photo ->
            PhotoFileManager.deletePhoto(photo.imagePath)
        }
        // Physical delete (CASCADE handles TaskTagCrossRef, TaskRelation, TaskCompletion, PhotoMemo)
        db.taskDao().delete(task)
    }

    fun purgeAll() = viewModelScope.launch {
        deletedTasks.value.forEach { task ->
            AlarmScheduler.cancel(getApplication(), task.id)
            db.photoMemoDao().getByTaskId(task.id).first().forEach { photo ->
                PhotoFileManager.deletePhoto(photo.imagePath)
            }
            db.taskDao().delete(task)
        }
    }
}
