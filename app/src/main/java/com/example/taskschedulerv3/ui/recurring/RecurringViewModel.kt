package com.example.taskschedulerv3.ui.recurring

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskschedulerv3.data.db.AppDatabase
import com.example.taskschedulerv3.data.model.ScheduleType
import com.example.taskschedulerv3.data.model.Task
import com.example.taskschedulerv3.data.repository.TaskRepository
import com.example.taskschedulerv3.util.RecurrenceCalculator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class RecurringViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.getInstance(app)
    private val repo = TaskRepository(db.taskDao())

    val recurringTasks: StateFlow<List<Task>> = repo.getAll()
        .map { tasks -> tasks.filter { it.scheduleType == ScheduleType.RECURRING } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 今日発生する繰り返し予定のみ */
    val todayRecurringTasks: StateFlow<List<Task>> = recurringTasks
        .map { tasks ->
            val today = LocalDate.now()
            tasks.filter { RecurrenceCalculator.occursOn(it, today) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun delete(task: Task) = viewModelScope.launch {
        repo.softDelete(task.id)
    }
}
