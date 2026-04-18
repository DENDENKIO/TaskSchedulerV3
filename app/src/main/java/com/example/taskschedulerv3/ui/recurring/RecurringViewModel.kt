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
    private val repo = TaskRepository(db.taskDao(), db.roadmapStepDao())

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

    /**
     * 繰り返し予定を保存する（新規 or 上書き）
     * @param editTask null なら新規作成、non-null なら既存タスクを更新
     */
    fun saveRecurring(
        editTask: Task?,
        title: String,
        startDate: String,
        pattern: String,  // EVERY_N_DAYS / WEEKLY_MULTI / MONTHLY_DATES
        days: String      // CSV
    ) = viewModelScope.launch {
        val now = System.currentTimeMillis()
        val recurrence = when (pattern) {
            "EVERY_N_DAYS" -> com.example.taskschedulerv3.data.model.RecurrencePattern.EVERY_N_DAYS
            "WEEKLY_MULTI" -> com.example.taskschedulerv3.data.model.RecurrencePattern.WEEKLY_MULTI
            "MONTHLY_DATES" -> com.example.taskschedulerv3.data.model.RecurrencePattern.MONTHLY_DATES
            else -> com.example.taskschedulerv3.data.model.RecurrencePattern.EVERY_N_DAYS
        }
        if (editTask == null) {
            repo.insert(
                Task(
                    title = title,
                    startDate = startDate,
                    scheduleType = ScheduleType.RECURRING,
                    recurrencePattern = recurrence,
                    recurrenceDays = days,
                    createdAt = now,
                    updatedAt = now
                )
            )
        } else {
            repo.update(
                editTask.copy(
                    title = title,
                    startDate = startDate,
                    scheduleType = ScheduleType.RECURRING,
                    recurrencePattern = recurrence,
                    recurrenceDays = days,
                    updatedAt = now
                )
            )
        }
    }
}
