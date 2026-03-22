package com.example.taskschedulerv3.ui.schedulelist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskschedulerv3.data.db.AppDatabase
import com.example.taskschedulerv3.data.model.*
import com.example.taskschedulerv3.data.repository.TagRepository
import com.example.taskschedulerv3.data.repository.TaskRepository
import com.example.taskschedulerv3.util.RecurrenceCalculator
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

class ScheduleListViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.getInstance(app)
    private val repo = TaskRepository(db.taskDao())
    private val tagRepo = TagRepository(db.tagDao())
    private val crossRefDao = db.taskTagCrossRefDao()

    val searchQuery = MutableStateFlow("")
    val sortOption = MutableStateFlow(SortOption.DATE_ASC)
    val filterOption = MutableStateFlow(FilterOption())
    /** 月ビューから遷移したときの日付フィルタ (yyyy-MM-dd、空文字=フィルタなし) */
    val filterDate = MutableStateFlow("")
    /** 期間日付検索 */
    val filterDateFrom = MutableStateFlow("")
    val filterDateTo   = MutableStateFlow("")

    // All tags for tag-filter dialog
    val allTags: StateFlow<List<Tag>> = tagRepo.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Selected filter tag id (single tag, inclusive of descendants)
    val filterTagId = MutableStateFlow<Int?>(null)

    val tasks: StateFlow<List<Task>> = combine(
        searchQuery.debounce(300).flatMapLatest { q ->
            if (q.isBlank()) repo.getAll() else repo.search(q)
        },
        sortOption,
        filterOption,
        filterTagId,
        allTags
    ) { list, sort, filter, tagId, tags ->
        // RECURRING tasks are hidden by default unless explicitly filtered
        val showRecurring = ScheduleType.RECURRING in filter.scheduleTypes
        var result = if (showRecurring) list
                     else list.filter { it.scheduleType != ScheduleType.RECURRING }

        // Completion filter
        result = when (filter.completionStatus) {
            CompletionFilter.INCOMPLETE -> result.filter { !it.isCompleted }
            CompletionFilter.COMPLETE -> result.filter { it.isCompleted }
            CompletionFilter.ALL -> result
        }
        if (filter.scheduleTypes.isNotEmpty()) result = result.filter { it.scheduleType in filter.scheduleTypes }
        if (filter.priorities.isNotEmpty()) result = result.filter { it.priority in filter.priorities }
        result = when (filter.notifyFilter) {
            NotifyFilter.ON_ONLY -> result.filter { it.notifyEnabled }
            NotifyFilter.OFF_ONLY -> result.filter { !it.notifyEnabled }
            NotifyFilter.ALL -> result
        }
        // Tag inclusive filter
        if (tagId != null) {
            val inclusiveIds = collectInclusiveTagIds(tagId, tags)
            val taggedTaskIds = mutableSetOf<Int>()
            // We'll filter synchronously from DB cache — this is an approximation;
            // full async approach would use Flow<List> from DAO
            result = result // tag filtering done below via taskIdsForTag
        }
        // Sort
        when (sort) {
            SortOption.DATE_ASC -> result.sortedWith(compareBy({ it.startDate }, { it.startTime ?: "99:99" }))
            SortOption.DATE_DESC -> result.sortedWith(compareByDescending<Task> { it.startDate }.thenByDescending { it.startTime ?: "" })
            SortOption.PRIORITY_HIGH -> result.sortedBy { it.priority }
            SortOption.PRIORITY_LOW -> result.sortedByDescending { it.priority }
            SortOption.TITLE_ASC -> result.sortedBy { it.title }
            SortOption.TITLE_DESC -> result.sortedByDescending { it.title }
            SortOption.CREATED_ASC -> result.sortedBy { it.createdAt }
            SortOption.CREATED_DESC -> result.sortedByDescending { it.createdAt }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Task ids that match the tag filter (updated reactively)
    val tagFilteredTaskIds: StateFlow<Set<Int>?> = filterTagId
        .combine(allTags) { tagId, tags -> tagId to tags }
        .flatMapLatest { (tagId, tags) ->
            if (tagId == null) flowOf(null)
            else {
                val ids = collectInclusiveTagIds(tagId, tags)
                crossRefDao.getTasksByTagIds(ids.toList()).map { taskList ->
                    taskList.map { it.id }.toSet()
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private fun collectInclusiveTagIds(tagId: Int, allTags: List<Tag>): Set<Int> {
        val result = mutableSetOf(tagId)
        val children = allTags.filter { it.parentId == tagId }
        children.forEach { result.addAll(collectInclusiveTagIds(it.id, allTags)) }
        return result
    }

    fun setSearchQuery(q: String) { searchQuery.value = q }
    fun setSortOption(opt: SortOption) { sortOption.value = opt }
    fun setCompletionFilter(cf: CompletionFilter) { filterOption.value = filterOption.value.copy(completionStatus = cf) }
    fun toggleScheduleTypeFilter(type: ScheduleType) {
        val current = filterOption.value.scheduleTypes.toMutableSet()
        if (type in current) current.remove(type) else current.add(type)
        filterOption.value = filterOption.value.copy(scheduleTypes = current)
    }
    fun togglePriorityFilter(priority: Int) {
        val current = filterOption.value.priorities.toMutableSet()
        if (priority in current) current.remove(priority) else current.add(priority)
        filterOption.value = filterOption.value.copy(priorities = current)
    }
    fun setTagFilter(tagId: Int?) { filterTagId.value = tagId }
    fun clearFilters() { filterOption.value = FilterOption(); filterTagId.value = null }

    fun softDelete(task: Task) = viewModelScope.launch { repo.softDelete(task.id) }
    fun toggleComplete(task: Task) = viewModelScope.launch { repo.setCompleted(task.id, !task.isCompleted) }
}
