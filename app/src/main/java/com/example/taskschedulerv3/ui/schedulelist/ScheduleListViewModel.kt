package com.example.taskschedulerv3.ui.schedulelist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskschedulerv3.data.db.AppDatabase
import com.example.taskschedulerv3.data.model.*
import com.example.taskschedulerv3.data.repository.TaskRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ScheduleListViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = TaskRepository(AppDatabase.getInstance(app).taskDao())

    val searchQuery = MutableStateFlow("")
    val sortOption = MutableStateFlow(SortOption.DATE_ASC)
    val filterOption = MutableStateFlow(FilterOption())

    val tasks: StateFlow<List<Task>> = combine(
        searchQuery.debounce(300).flatMapLatest { q ->
            if (q.isBlank()) repo.getAll() else repo.search(q)
        },
        sortOption,
        filterOption
    ) { list, sort, filter ->
        var result = list
        // Filter
        result = when (filter.completionStatus) {
            CompletionFilter.INCOMPLETE -> result.filter { !it.isCompleted }
            CompletionFilter.COMPLETE -> result.filter { it.isCompleted }
            CompletionFilter.ALL -> result
        }
        if (filter.scheduleTypes.isNotEmpty()) {
            result = result.filter { it.scheduleType in filter.scheduleTypes }
        }
        if (filter.priorities.isNotEmpty()) {
            result = result.filter { it.priority in filter.priorities }
        }
        result = when (filter.notifyFilter) {
            NotifyFilter.ON_ONLY -> result.filter { it.notifyEnabled }
            NotifyFilter.OFF_ONLY -> result.filter { !it.notifyEnabled }
            NotifyFilter.ALL -> result
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

    fun setSearchQuery(q: String) { searchQuery.value = q }
    fun setSortOption(opt: SortOption) { sortOption.value = opt }
    fun setCompletionFilter(cf: CompletionFilter) {
        filterOption.value = filterOption.value.copy(completionStatus = cf)
    }
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
    fun clearFilters() { filterOption.value = FilterOption() }

    fun softDelete(task: Task) = viewModelScope.launch { repo.softDelete(task.id) }
    fun toggleComplete(task: Task) = viewModelScope.launch { repo.setCompleted(task.id, !task.isCompleted) }
}
