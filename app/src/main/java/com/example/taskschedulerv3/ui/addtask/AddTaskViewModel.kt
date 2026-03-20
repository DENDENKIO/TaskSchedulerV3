package com.example.taskschedulerv3.ui.addtask

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskschedulerv3.data.db.AppDatabase
import com.example.taskschedulerv3.data.model.*
import com.example.taskschedulerv3.data.repository.TaskRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AddTaskViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = TaskRepository(AppDatabase.getInstance(app).taskDao())

    val title = MutableStateFlow("")
    val description = MutableStateFlow("")
    val startDate = MutableStateFlow("")
    val endDate = MutableStateFlow("")
    val startTime = MutableStateFlow("")
    val endTime = MutableStateFlow("")
    val scheduleType = MutableStateFlow(ScheduleType.NORMAL)
    val priority = MutableStateFlow(1)
    val notifyEnabled = MutableStateFlow(true)
    val notifyMinutesBefore = MutableStateFlow(10)

    private val _saveSuccess = MutableStateFlow(false)
    val saveSuccess: StateFlow<Boolean> = _saveSuccess.asStateFlow()

    fun loadTask(taskId: Int) = viewModelScope.launch {
        repo.getById(taskId)?.let { t ->
            title.value = t.title
            description.value = t.description ?: ""
            startDate.value = t.startDate
            endDate.value = t.endDate ?: ""
            startTime.value = t.startTime ?: ""
            endTime.value = t.endTime ?: ""
            scheduleType.value = t.scheduleType
            priority.value = t.priority
            notifyEnabled.value = t.notifyEnabled
            notifyMinutesBefore.value = t.notifyMinutesBefore
        }
    }

    fun save(editId: Int? = null) = viewModelScope.launch {
        val task = Task(
            id = editId ?: 0,
            title = title.value.trim(),
            description = description.value.trim().ifEmpty { null },
            startDate = startDate.value,
            endDate = endDate.value.ifEmpty { null },
            startTime = startTime.value.ifEmpty { null },
            endTime = endTime.value.ifEmpty { null },
            scheduleType = scheduleType.value,
            priority = priority.value,
            notifyEnabled = notifyEnabled.value,
            notifyMinutesBefore = notifyMinutesBefore.value,
            updatedAt = System.currentTimeMillis()
        )
        repo.insert(task)
        _saveSuccess.value = true
    }
}
