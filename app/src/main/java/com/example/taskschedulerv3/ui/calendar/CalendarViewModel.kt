package com.example.taskschedulerv3.ui.calendar

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskschedulerv3.data.db.AppDatabase
import com.example.taskschedulerv3.data.model.ScheduleType
import com.example.taskschedulerv3.data.model.Tag
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

data class TagTaskEntry(val tag: Tag, val taskIds: Set<Int>)

/** 1日分の月リスト行データ。taskLines は常に空リスト（縦書き表示廃止） */
data class MonthDayRow(
    val dateStr: String,
    val dayOfMonth: Int,
    val dayOfWeekLabel: String,
    val isToday: Boolean,
    val isHoliday: Boolean,
    val isSaturday: Boolean,
    val taskLines: List<String> = emptyList(),
    val hasRangeTask: Boolean = false
)

/**
 * ガントチャート1行分。NORMAL(単日) ・ PERIOD(期間) 両対応。
 * [activeDatesInMonth]: 当月内で表示対象となる日付セット(yyyy-MM-dd)
 * [startDateInMonth]:   バー開始日（当月内最初の対象日）
 * [endDateInMonth]:     バー終了日（当月内最後の対象日）
 */
data class GanttRow(
    val task: Task,
    val activeDatesInMonth: Set<String>,
    val startDateInMonth: String,
    val endDateInMonth: String
)

class CalendarViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.getInstance(app)
    private val repo = TaskRepository(db.taskDao())
    private val completionDao = db.taskCompletionDao()

    private val _viewMode = MutableStateFlow(CalendarViewMode.MONTH)
    val viewMode: StateFlow<CalendarViewMode> = _viewMode.asStateFlow()

    private val _selectedDate = MutableStateFlow(DateUtils.today())
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    private val _currentYear = MutableStateFlow(LocalDate.now().year)
    val currentYear: StateFlow<Int> = _currentYear.asStateFlow()

    private val _currentMonth = MutableStateFlow(LocalDate.now().monthValue)
    val currentMonth: StateFlow<Int> = _currentMonth.asStateFlow()

    val allTasks: StateFlow<List<Task>> = repo.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val allTags: StateFlow<List<Tag>> = db.tagDao().getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _taskTagMap: StateFlow<Map<Int, Set<Int>>> = run {
        allTasks.flatMapLatest { tasks ->
            if (tasks.isEmpty()) flowOf(emptyMap())
            else {
                val flows = tasks.map { task ->
                    db.taskTagCrossRefDao().getTagsByTaskId(task.id)
                        .map { tags -> task.id to tags.map { it.id }.toSet() }
                }
                combine(flows) { pairs -> pairs.toMap() }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
    }

    // 日付セルには予定を表示しない。hasRangeTask はガント行ありのインジケーターのみ
    val monthDayRows: StateFlow<List<MonthDayRow>> = combine(
        allTasks, _currentYear, _currentMonth
    ) { tasks, year, month ->
        buildMonthDayRows(tasks, year, month)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // NORMAL + PERIOD 全てをガントチャート行として生成。RECURRING は除外。
    val ganttRows: StateFlow<List<GanttRow>> = combine(
        allTasks, _currentYear, _currentMonth
    ) { tasks, year, month ->
        buildGanttRows(tasks, year, month)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun buildGanttRows(tasks: List<Task>, year: Int, month: Int): List<GanttRow> {
        val monthStart = LocalDate.of(year, month, 1)
        val monthEnd = monthStart.with(TemporalAdjusters.lastDayOfMonth())

        return tasks
            .filter { it.scheduleType != ScheduleType.RECURRING }   // RECURRING 完全除外
            .mapNotNull { task ->
                val dates: List<LocalDate> = when (task.scheduleType) {
                    ScheduleType.NORMAL -> {
                        val d = DateUtils.parse(task.startDate)
                        if (d in monthStart..monthEnd) listOf(d) else emptyList()
                    }
                    ScheduleType.PERIOD ->
                        RecurrenceCalculator.getPeriodDatesInRange(task, monthStart, monthEnd)
                    else -> emptyList()
                }
                if (dates.isEmpty()) return@mapNotNull null
                val dateStrs = dates.map { DateUtils.format(it) }.toSortedSet()
                GanttRow(
                    task = task,
                    activeDatesInMonth = dateStrs,
                    startDateInMonth = dateStrs.first(),
                    endDateInMonth = dateStrs.last()
                )
            }
            .sortedWith(compareBy({ it.startDateInMonth }, { it.task.title }))
    }

    private fun buildMonthDayRows(tasks: List<Task>, year: Int, month: Int): List<MonthDayRow> {
        val daysInMonth = DateUtils.daysInMonth(year, month)
        val todayStr = DateUtils.today()
        val monthStart = LocalDate.of(year, month, 1)
        val monthEnd = monthStart.with(TemporalAdjusters.lastDayOfMonth())

        // ガント行がある日（インジケータードット用）
        val ganttDateSet = mutableSetOf<String>()
        tasks.filter { it.scheduleType != ScheduleType.RECURRING }.forEach { task ->
            when (task.scheduleType) {
                ScheduleType.NORMAL -> {
                    val d = DateUtils.parse(task.startDate)
                    if (d in monthStart..monthEnd) ganttDateSet.add(task.startDate)
                }
                ScheduleType.PERIOD -> {
                    RecurrenceCalculator.getPeriodDatesInRange(task, monthStart, monthEnd)
                        .forEach { ganttDateSet.add(DateUtils.format(it)) }
                }
                else -> {}
            }
        }

        return (1..daysInMonth).map { day ->
            val dateStr = "%04d-%02d-%02d".format(year, month, day)
            val date = DateUtils.parse(dateStr)
            val dow = date.dayOfWeek
            val dowLabel = when (dow) {
                java.time.DayOfWeek.MONDAY    -> "月"
                java.time.DayOfWeek.TUESDAY   -> "火"
                java.time.DayOfWeek.WEDNESDAY -> "水"
                java.time.DayOfWeek.THURSDAY  -> "木"
                java.time.DayOfWeek.FRIDAY    -> "金"
                java.time.DayOfWeek.SATURDAY  -> "土"
                else                          -> "日"
            }
            MonthDayRow(
                dateStr = dateStr,
                dayOfMonth = day,
                dayOfWeekLabel = dowLabel,
                isToday = dateStr == todayStr,
                isHoliday = dow == java.time.DayOfWeek.SUNDAY,
                isSaturday = dow == java.time.DayOfWeek.SATURDAY,
                taskLines = emptyList(),            // 日付セル内に予定は表示しない
                hasRangeTask = dateStr in ganttDateSet
            )
        }
    }

    val allTaskDates: StateFlow<Set<String>> = combine(
        allTasks, _currentYear, _currentMonth, _viewMode
    ) { tasks, year, month, mode ->
        val dates = mutableSetOf<String>()
        val (rangeStart, rangeEnd) = when (mode) {
            CalendarViewMode.YEAR  -> LocalDate.of(year, 1, 1) to LocalDate.of(year, 12, 31)
            CalendarViewMode.MONTH -> {
                LocalDate.of(year, month, 1) to
                    LocalDate.of(year, month, 1).with(TemporalAdjusters.lastDayOfMonth())
            }
            CalendarViewMode.WEEK  -> {
                val sel = DateUtils.parse(_selectedDate.value)
                val start = sel.with(java.time.DayOfWeek.MONDAY)
                start to start.plusDays(6)
            }
            CalendarViewMode.DAY   -> { val d = DateUtils.parse(_selectedDate.value); d to d }
        }
        tasks.filter { it.scheduleType != ScheduleType.RECURRING }.forEach { task ->
            when (task.scheduleType) {
                ScheduleType.NORMAL -> { if (task.startDate.isNotBlank()) dates.add(task.startDate) }
                ScheduleType.PERIOD -> {
                    RecurrenceCalculator.getPeriodDatesInRange(task, rangeStart, rangeEnd)
                        .forEach { dates.add(DateUtils.format(it)) }
                }
                else -> {}
            }
        }
        dates
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    fun getPeriodTasksForRange(rangeStart: LocalDate, rangeEnd: LocalDate): List<Pair<Task, List<LocalDate>>> {
        return allTasks.value
            .filter { it.scheduleType == ScheduleType.PERIOD }
            .mapNotNull { task ->
                val dates = RecurrenceCalculator.getPeriodDatesInRange(task, rangeStart, rangeEnd)
                if (dates.isNotEmpty()) task to dates else null
            }
    }

    val tasksForSelectedDate: StateFlow<List<Task>> = combine(
        allTasks, _selectedDate
    ) { tasks, dateStr ->
        if (dateStr.isBlank()) return@combine emptyList()
        val date = DateUtils.parse(dateStr)
        tasks.filter { task ->
            if (task.scheduleType == ScheduleType.RECURRING) return@filter false
            when (task.scheduleType) {
                ScheduleType.NORMAL -> task.startDate == dateStr
                ScheduleType.PERIOD -> RecurrenceCalculator.getPeriodDatesInRange(task, date, date).isNotEmpty()
                else -> false
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todaySummary: StateFlow<TodaySummary> = allTasks
        .map { tasks ->
            val todayStr = DateUtils.today()
            val today = DateUtils.parse(todayStr)
            val todayTasks = tasks.filter { task ->
                when (task.scheduleType) {
                    ScheduleType.NORMAL    -> task.startDate == todayStr
                    ScheduleType.PERIOD    -> RecurrenceCalculator.getPeriodDatesInRange(task, today, today).isNotEmpty()
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

    fun isRecurringCompleted(taskId: Int, date: String): Flow<Boolean> =
        completionDao.getByTaskId(taskId).map { list -> list.any { it.completedDate == date } }

    fun toggleRecurringComplete(taskId: Int, date: String, currentlyCompleted: Boolean) =
        viewModelScope.launch {
            if (currentlyCompleted) {
                val list = completionDao.getByTaskId(taskId).first()
                list.find { it.completedDate == date }?.let { completionDao.delete(it) }
            } else {
                completionDao.insert(TaskCompletion(taskId = taskId, completedDate = date))
            }
        }

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
    fun nextYear()     { _currentYear.value++ }

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

    fun deleteTask(task: Task) = viewModelScope.launch { repo.softDelete(task.id) }
    fun toggleComplete(task: Task) = viewModelScope.launch { repo.setCompleted(task.id, !task.isCompleted) }
}

data class TodaySummary(
    val date: String,
    val incompleteCount: Int,
    val nextTaskTitle: String?,
    val nextTaskTime: String?
)
