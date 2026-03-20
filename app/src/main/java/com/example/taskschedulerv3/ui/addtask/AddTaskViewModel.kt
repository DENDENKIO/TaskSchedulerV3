package com.example.taskschedulerv3.ui.addtask

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskschedulerv3.data.db.AppDatabase
import com.example.taskschedulerv3.data.model.*
import com.example.taskschedulerv3.data.repository.TagRepository
import com.example.taskschedulerv3.data.repository.TaskRepository
import com.example.taskschedulerv3.notification.AlarmScheduler
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AddTaskViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.getInstance(app)
    private val repo = TaskRepository(db.taskDao())
    private val tagRepo = TagRepository(db.tagDao())
    private val crossRefDao = db.taskTagCrossRefDao()

    val title = MutableStateFlow("")
    val description = MutableStateFlow("")
    val startDate = MutableStateFlow("")
    val endDate = MutableStateFlow("")
    val startTime = MutableStateFlow("")
    val endTime = MutableStateFlow("")
    val scheduleType = MutableStateFlow(ScheduleType.NORMAL)
    val recurrencePattern = MutableStateFlow<RecurrencePattern?>(null)
    val recurrenceDays = MutableStateFlow("")
    val recurrenceEndDate = MutableStateFlow("")
    val priority = MutableStateFlow(1)
    val notifyEnabled = MutableStateFlow(true)
    val notifyMinutesBefore = MutableStateFlow(10)

    // Tag selection: set of deepest-level tag ids
    val selectedTagIds = MutableStateFlow<Set<Int>>(emptySet())

    // All tags for TagSelector
    val allTags: StateFlow<List<Tag>> = tagRepo.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
            recurrencePattern.value = t.recurrencePattern
            recurrenceDays.value = t.recurrenceDays ?: ""
            recurrenceEndDate.value = t.recurrenceEndDate ?: ""
            priority.value = t.priority
            notifyEnabled.value = t.notifyEnabled
            notifyMinutesBefore.value = t.notifyMinutesBefore
        }
        // Load existing tag associations
        crossRefDao.getTagsByTaskId(taskId).first().let { tags ->
            selectedTagIds.value = tags.map { it.id }.toSet()
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
            recurrencePattern = recurrencePattern.value,
            recurrenceDays = recurrenceDays.value.ifEmpty { null },
            recurrenceEndDate = recurrenceEndDate.value.ifEmpty { null },
            priority = priority.value,
            notifyEnabled = notifyEnabled.value,
            notifyMinutesBefore = notifyMinutesBefore.value,
            updatedAt = System.currentTimeMillis()
        )
        val taskId = repo.insert(task).toInt()
        val finalId = if (editId != null) editId else taskId

        // Re-save tag associations
        crossRefDao.deleteByTaskId(finalId)
        selectedTagIds.value.forEach { tagId ->
            crossRefDao.insert(TaskTagCrossRef(taskId = finalId, tagId = tagId))
        }

        // Schedule (or cancel) notification alarm
        val savedTask = task.copy(id = finalId)
        AlarmScheduler.scheduleForTask(getApplication(), savedTask)

        _saveSuccess.value = true
    }
}
