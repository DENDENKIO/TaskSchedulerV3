package com.example.taskschedulerv3.ui.calendar

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskschedulerv3.data.db.AppDatabase
import com.example.taskschedulerv3.data.model.Task
import com.example.taskschedulerv3.data.repository.TaskRepository
import com.example.taskschedulerv3.util.DateUtils
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class CalendarViewMode { YEAR, MONTH, WEEK, DAY }

class CalendarViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = TaskRepository(AppDatabase.getInstance(app).taskDao())

    // --- View mode ---
    private val _viewMode = MutableStateFlow(CalendarViewMode.MONTH)
    val viewMode: StateFlow<CalendarViewMode> = _viewMode.asStateFlow()

    // --- Selected date ---
    private val _selectedDate = MutableStateFlow(DateUtils.today())
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    // --- Current month/year for month & year views ---
    private val _currentYear = MutableStateFlow(LocalDate.now().year)
    val currentYear: StateFlow<Int> = _currentYear.asStateFlow()

    private val _currentMonth = MutableStateFlow(LocalDate.now().monthValue)
    val currentMonth: StateFlow<Int> = _currentMonth.asStateFlow()

    // --- Tasks for selected date ---
    val tasksForSelectedDate: StateFlow<List<Task>> = _selectedDate
        .flatMapLatest { date -> repo.getByDate(date) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- All tasks (for dot display) ---
    val allTasks: StateFlow<List<Task>> = repo.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Today summary ---
    val todaySummary: StateFlow<TodaySummary> = repo.getByDate(DateUtils.today())
        .map { tasks ->
            val incomplete = tasks.filter { !it.isCompleted }
            val next = incomplete.minByOrNull { it.startTime ?: "99:99" }
            TodaySummary(
                date = DateUtils.today(),
                incompleteCount = incomplete.size,
                nextTaskTitle = next?.title,
                nextTaskTime = next?.startTime
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TodaySummary(DateUtils.today(), 0, null, null))

    // --- Navigation ---
    fun setViewMode(mode: CalendarViewMode) { _viewMode.value = mode }
    fun selectDate(date: String) {
        _selectedDate.value = date
        val d = DateUtils.parse(date)
        _currentYear.value = d.year
        _currentMonth.value = d.monthValue
    }

    fun previousMonth() {
        val date = LocalDate.of(_currentYear.value, _currentMonth.value, 1).minusMonths(1)
        _currentYear.value = date.year
        _currentMonth.value = date.monthValue
    }

    fun nextMonth() {
        val date = LocalDate.of(_currentYear.value, _currentMonth.value, 1).plusMonths(1)
        _currentYear.value = date.year
        _currentMonth.value = date.monthValue
    }

    fun previousYear() { _currentYear.value-- }
    fun nextYear() { _currentYear.value++ }

    fun previousWeek() {
        val d = DateUtils.parse(_selectedDate.value).minusWeeks(1)
        _selectedDate.value = DateUtils.format(d)
    }

    fun nextWeek() {
        val d = DateUtils.parse(_selectedDate.value).plusWeeks(1)
        _selectedDate.value = DateUtils.format(d)
    }

    fun previousDay() {
        val d = DateUtils.parse(_selectedDate.value).minusDays(1)
        _selectedDate.value = DateUtils.format(d)
    }

    fun nextDay() {
        val d = DateUtils.parse(_selectedDate.value).plusDays(1)
        _selectedDate.value = DateUtils.format(d)
    }

    // --- Actions ---
    fun deleteTask(task: Task) = viewModelScope.launch { repo.softDelete(task.id) }
    fun toggleComplete(task: Task) = viewModelScope.launch { repo.setCompleted(task.id, !task.isCompleted) }
}

data class TodaySummary(
    val date: String,
    val incompleteCount: Int,
    val nextTaskTitle: String?,
    val nextTaskTime: String?
)
