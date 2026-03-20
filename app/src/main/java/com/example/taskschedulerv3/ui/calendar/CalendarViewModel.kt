package com.example.taskschedulerv3.ui.calendar

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskschedulerv3.data.db.AppDatabase
import com.example.taskschedulerv3.data.model.ScheduleType
import com.example.taskschedulerv3.data.model.Task
import com.example.taskschedulerv3.data.model.TaskCompletion
import com.example.taskschedulerv3.data.repository.TaskRepository
import com.example.taskschedulerv3.util.DateUtils
import com.example.taskschedulerv3.util.RecurrenceCalculator
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

enum class CalendarViewMode { YEAR, MONTH, WEEK, DAY }

class CalendarViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.getInstance(app)
    private val repo = TaskRepository(db.taskDao())
    private val completionDao = db.taskCompletionDao()

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

    // --- All tasks (base list) ---
    val allTasks: StateFlow<List<Task>> = repo.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Expanded task dates (NORMAL + PERIOD + RECURRING) for dot/bar display ---
    // Returns a Set<String> of all yyyy-MM-dd dates that have at least one task
    val allTaskDates: StateFlow<Set<String>> = combine(
        allTasks,
        _currentYear,
        _currentMonth,
        _viewMode
    ) { tasks, year, month, mode ->
        val dates = mutableSetOf<String>()
        val (rangeStart, rangeEnd) = when (mode) {
            CalendarViewMode.YEAR -> {
                LocalDate.of(year, 1, 1) to LocalDate.of(year, 12, 31)
            }
            CalendarViewMode.MONTH -> {
                LocalDate.of(year, month, 1) to
                    LocalDate.of(year, month, 1).with(TemporalAdjusters.lastDayOfMonth())
            }
            CalendarViewMode.WEEK -> {
                val sel = DateUtils.parse(_selectedDate.value)
                val start = sel.with(java.time.DayOfWeek.MONDAY)
                start to start.plusDays(6)
            }
            CalendarViewMode.DAY -> {
                val d = DateUtils.parse(_selectedDate.value)
                d to d
            }
        }
        tasks.forEach { task ->
            when (task.scheduleType) {
                ScheduleType.NORMAL -> {
                    if (task.startDate.isNotBlank()) dates.add(task.startDate)
                }
                ScheduleType.PERIOD -> {
                    RecurrenceCalculator.getPeriodDatesInRange(task, rangeStart, rangeEnd)
                        .forEach { dates.add(DateUtils.format(it)) }
                }
                ScheduleType.RECURRING -> {
                    RecurrenceCalculator.getOccurrencesInRange(task, rangeStart, rangeEnd)
                        .forEach { dates.add(DateUtils.format(it)) }
                }
            }
        }
        dates
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    // --- Period tasks that span a given date range (for WeekView bars) ---
    fun getPeriodTasksForRange(rangeStart: LocalDate, rangeEnd: LocalDate): List<Pair<Task, List<LocalDate>>> {
        return allTasks.value
            .filter { it.scheduleType == ScheduleType.PERIOD }
            .mapNotNull { task ->
                val dates = RecurrenceCalculator.getPeriodDatesInRange(task, rangeStart, rangeEnd)
                if (dates.isNotEmpty()) task to dates else null
            }
    }

    // --- Tasks for selected date (includes NORMAL exact match + PERIOD/RECURRING expansions) ---
    val tasksForSelectedDate: StateFlow<List<Task>> = combine(
        allTasks,
        _selectedDate
    ) { tasks, dateStr ->
        if (dateStr.isBlank()) return@combine emptyList()
        val date = DateUtils.parse(dateStr)
        tasks.filter { task ->
            when (task.scheduleType) {
                ScheduleType.NORMAL -> task.startDate == dateStr
                ScheduleType.PERIOD -> RecurrenceCalculator.getPeriodDatesInRange(task, date, date).isNotEmpty()
                ScheduleType.RECURRING -> RecurrenceCalculator.occursOn(task, date)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Today summary ---
    val todaySummary: StateFlow<TodaySummary> = allTasks
        .map { tasks ->
            val todayStr = DateUtils.today()
            val today = DateUtils.parse(todayStr)
            val todayTasks = tasks.filter { task ->
                when (task.scheduleType) {
                    ScheduleType.NORMAL -> task.startDate == todayStr
                    ScheduleType.PERIOD -> RecurrenceCalculator.getPeriodDatesInRange(task, today, today).isNotEmpty()
                    ScheduleType.RECURRING -> RecurrenceCalculator.occursOn(task, today)
                }
            }
            val incomplete = todayTasks.filter { !it.isCompleted }
            val next = incomplete.minByOrNull { it.startTime ?: "99:99" }
            TodaySummary(
                date = todayStr,
                incompleteCount = incomplete.size,
                nextTaskTitle = next?.title,
                nextTaskTime = next?.startTime
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TodaySummary(DateUtils.today(), 0, null, null))

    // --- TaskCompletion for recurring tasks ---
    fun isRecurringCompleted(taskId: Int, date: String): Flow<Boolean> =
        completionDao.getByTaskId(taskId).map { list -> list.any { it.completedDate == date } }

    fun toggleRecurringComplete(taskId: Int, date: String, currentlyCompleted: Boolean) =
        viewModelScope.launch {
            if (currentlyCompleted) {
                // Find and delete
                val list = completionDao.getByTaskId(taskId).first()
                list.find { it.completedDate == date }?.let { completionDao.delete(it) }
            } else {
                completionDao.insert(TaskCompletion(taskId = taskId, completedDate = date))
            }
        }

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
