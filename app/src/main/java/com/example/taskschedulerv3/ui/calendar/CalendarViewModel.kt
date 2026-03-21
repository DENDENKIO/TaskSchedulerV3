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

/** タグ1件と、そのタグに紐付くタスクid一覧 */
data class TagTaskEntry(val tag: Tag, val taskIds: Set<Int>)

/** 1日分の月リスト行データ */
data class MonthDayRow(
    val dateStr: String,          // yyyy-MM-dd
    val dayOfMonth: Int,
    val dayOfWeekLabel: String,   // 月/火/水...
    val isToday: Boolean,
    val isHoliday: Boolean,       // 日曜=true
    val isSaturday: Boolean,
    /** 大カテゴリtag → (中カテゴリtag? → (小カテゴリtag? → count)) の集約チップリスト */
    val tagChips: List<TagChip>
)

/** 月ビューの1タグチップ: 短縮ラベル + 件数 + 色 */
data class TagChip(
    val label: String,   // 例: "仕本配" or "仕本" or "仕"
    val count: Int,
    val color: String    // #RRGGBB
)

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

    // --- All tags ---
    private val allTags: StateFlow<List<Tag>> = db.tagDao().getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // taskId → Set<tagId> mapping (crossRef table を監視)
    private val _taskTagMap: StateFlow<Map<Int, Set<Int>>> = run {
        // Observe crossRef table changes by joining with tasks flow
        allTasks.flatMapLatest { tasks ->
            if (tasks.isEmpty()) flowOf(emptyMap())
            else {
                // Collect all crossRefs for all tasks
                val flows = tasks.map { task ->
                    db.taskTagCrossRefDao().getTagsByTaskId(task.id)
                        .map { tags -> task.id to tags.map { it.id }.toSet() }
                }
                combine(flows) { pairs -> pairs.toMap() }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
    }

    // --- Month day rows for MonthView list ---
    val monthDayRows: StateFlow<List<MonthDayRow>> = combine(
        allTasks, allTags, _taskTagMap, _currentYear, _currentMonth
    ) { tasks, tags, tagMap, year, month ->
        buildMonthDayRows(tasks, tags, tagMap, year, month)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun buildMonthDayRows(
        tasks: List<Task>,
        tags: List<Tag>,
        tagMap: Map<Int, Set<Int>>,
        year: Int,
        month: Int
    ): List<MonthDayRow> {
        val tagById = tags.associateBy { it.id }
        val daysInMonth = DateUtils.daysInMonth(year, month)
        val todayStr = DateUtils.today()
        val rows = mutableListOf<MonthDayRow>()

        for (day in 1..daysInMonth) {
            val dateStr = "%04d-%02d-%02d".format(year, month, day)
            val date = DateUtils.parse(dateStr)
            val dow = date.dayOfWeek
            val dowLabel = when (dow) {
                java.time.DayOfWeek.MONDAY -> "月"
                java.time.DayOfWeek.TUESDAY -> "火"
                java.time.DayOfWeek.WEDNESDAY -> "水"
                java.time.DayOfWeek.THURSDAY -> "木"
                java.time.DayOfWeek.FRIDAY -> "金"
                java.time.DayOfWeek.SATURDAY -> "土"
                else -> "日"
            }

            // Tasks active on this date
            val dayTasks = tasks.filter { task ->
                when (task.scheduleType) {
                    ScheduleType.NORMAL -> task.startDate == dateStr
                    ScheduleType.PERIOD -> RecurrenceCalculator.getPeriodDatesInRange(task, date, date).isNotEmpty()
                    ScheduleType.RECURRING -> RecurrenceCalculator.occursOn(task, date)
                }
            }

            // Build tag chips: group tasks by top-level tag (large category)
            // For each task, find its tags and their ancestors
            val chipMap = mutableMapOf<String, Pair<String, Int>>() // label -> (color, count)

            dayTasks.forEach { task ->
                val taskTagIds = tagMap[task.id] ?: emptySet()
                if (taskTagIds.isEmpty()) return@forEach

                // Find small tags (level 3) first, else medium (level 2), else large (level 1)
                val taskTags = taskTagIds.mapNotNull { tagById[it] }
                val smallTags = taskTags.filter { it.level == 3 }
                val midTags = taskTags.filter { it.level == 2 }
                val largeTags = taskTags.filter { it.level == 1 }

                // Build label from tag hierarchy for each leaf tag
                val leafTags = when {
                    smallTags.isNotEmpty() -> smallTags
                    midTags.isNotEmpty() -> midTags
                    else -> largeTags
                }

                leafTags.forEach { leaf ->
                    val small = if (leaf.level == 3) leaf else null
                    val mid = when {
                        leaf.level == 3 -> tagById[leaf.parentId]
                        leaf.level == 2 -> leaf
                        else -> null
                    }
                    val large = when {
                        leaf.level == 3 -> tagById[leaf.parentId]?.let { tagById[it.parentId] }
                        leaf.level == 2 -> tagById[leaf.parentId]
                        else -> leaf
                    }

                    // Build short label: first char of large + mid + small
                    val label = buildString {
                        large?.name?.firstOrNull()?.let { append(it) }
                        mid?.name?.firstOrNull()?.let { append(it) }
                        small?.name?.firstOrNull()?.let { append(it) }
                    }.ifEmpty { leaf.name.take(3) }

                    // Color: use large tag color if available, else leaf color
                    val color = large?.color ?: leaf.color

                    val key = label + color
                    val current = chipMap[key]
                    chipMap[key] = color to ((current?.second ?: 0) + 1)
                }
            }

            // If no tags, still show total count as gray chip
            val chips = if (chipMap.isEmpty() && dayTasks.isNotEmpty()) {
                listOf(TagChip("予定", dayTasks.size, "#9E9E9E"))
            } else {
                chipMap.entries.map { (key, pair) ->
                    val label = key.dropLast(7) // remove color suffix
                    TagChip(label, pair.second, pair.first)
                }
            }

            rows.add(MonthDayRow(
                dateStr = dateStr,
                dayOfMonth = day,
                dayOfWeekLabel = dowLabel,
                isToday = dateStr == todayStr,
                isHoliday = dow == java.time.DayOfWeek.SUNDAY,
                isSaturday = dow == java.time.DayOfWeek.SATURDAY,
                tagChips = chips
            ))
        }
        return rows
    }

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
