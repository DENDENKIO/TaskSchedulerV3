package com.example.taskschedulerv3.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskschedulerv3.data.db.AppDatabase
import com.example.taskschedulerv3.data.model.*
import com.example.taskschedulerv3.data.repository.PhotoMemoRepository
import com.example.taskschedulerv3.data.repository.TaskRepository
import com.example.taskschedulerv3.util.RecurrenceCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.getInstance(app)
    private val taskRepository = TaskRepository(db.taskDao())
    private val photoRepository = PhotoMemoRepository(db.photoMemoDao())
    private val crossRefDao = db.taskTagCrossRefDao()

    private val _filterType = MutableStateFlow("今日")
    val filterType: StateFlow<String> = _filterType

    private val _tasks = _filterType.flatMapLatest { type ->
        val today = LocalDate.now()
        val sevenDaysLater = today.plusDays(7)
        taskRepository.getAll().map { list ->
            list.filter { task ->
                if (task.isDeleted) return@filter false
                
                // Recurring task handling
                if (task.scheduleType == ScheduleType.RECURRING) {
                    return@filter when (type) {
                        "今日" -> RecurrenceCalculator.occursOn(task, today)
                        "直近7日" -> {
                            (0..7).any { i -> RecurrenceCalculator.occursOn(task, today.plusDays(i.toLong())) }
                        }
                        "全て" -> true
                        else -> false
                    }
                }

                // Normal / Period task handling
                val start = try { LocalDate.parse(task.startDate) } catch (_: Exception) { null }
                val end = task.endDate?.let { try { LocalDate.parse(it) } catch (_: Exception) { null } }
                
                when (type) {
                    "今日" -> {
                        start == today || (end != null && !start!!.isAfter(today) && !end.isBefore(today))
                    }
                    "直近7日" -> {
                        (start != null && !start.isBefore(today) && !start.isAfter(sevenDaysLater)) || 
                        (end != null && !start!!.isAfter(sevenDaysLater) && !end.isBefore(today))
                    }
                    "無期限" -> task.isIndefinite
                    "全て" -> true
                    else -> start == today
                }
            }
        }.distinctUntilChanged()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val incompleteTasks: StateFlow<List<Task>> = _tasks
        .map { list -> list.filter { !it.isCompleted } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val completedTasks: StateFlow<List<Task>> = _tasks
        .map { list -> list.filter { it.isCompleted } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalCount: StateFlow<Int> = _tasks
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val completedCount: StateFlow<Int> = _tasks
        .map { it.count { it.isCompleted } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun setFilterType(type: String) {
        _filterType.value = type
    }

    fun toggleComplete(task: Task) {
        viewModelScope.launch(Dispatchers.IO) {
            taskRepository.setCompleted(task.id, !task.isCompleted)
        }
    }

    fun getTagsForTask(taskId: Int): Flow<List<Tag>> = 
        crossRefDao.getTagsByTaskId(taskId).catch { emit(emptyList()) }

    fun getPhotosForTask(taskId: Int): Flow<List<PhotoMemo>> = 
        photoRepository.getPhotosForTask(taskId).catch { emit(emptyList()) }
}
