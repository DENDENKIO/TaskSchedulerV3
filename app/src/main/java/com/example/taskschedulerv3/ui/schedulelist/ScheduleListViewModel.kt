package com.example.taskschedulerv3.ui.schedulelist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskschedulerv3.data.db.AppDatabase
import com.example.taskschedulerv3.data.model.*
import com.example.taskschedulerv3.data.repository.QuickDraftRepository
import com.example.taskschedulerv3.data.repository.TagRepository
import com.example.taskschedulerv3.data.repository.TaskRepository
import com.example.taskschedulerv3.ui.components.DisplayMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleListViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.getInstance(app)
    private val repo = TaskRepository(db.taskDao())
    private val tagRepo = TagRepository(db.tagDao())
    private val crossRefDao = db.taskTagCrossRefDao()
    val draftRepo = QuickDraftRepository(db.quickDraftTaskDao(), db.taskDao(), crossRefDao)

    val searchQuery = MutableStateFlow("")
    val sortOption = MutableStateFlow(SortOption.DATE_ASC)
    val filterOption = MutableStateFlow(FilterOption())
    val filterDate = MutableStateFlow("")
    val filterDateFrom = MutableStateFlow("")
    val filterDateTo = MutableStateFlow("")

    // 表示モード (第3弾)
    val displayMode = MutableStateFlow(DisplayMode.TODAY)

    // タグフィルタ (第3弾) — 単一選択
    val selectedTagId = MutableStateFlow<Int?>(null)

    // All tags for filter chips
    val allTags: StateFlow<List<Tag>> = db.tagDao().getAllForFilter()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 後方互換のため維持 (旧コードとの互換)
    val filterTagId: MutableStateFlow<Int?> get() = selectedTagId

    // 仮登録一覧
    val drafts = draftRepo.getDrafts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** displayMode に応じた基本タスクFlow */
    private val baseTaskFlow: Flow<List<Task>> = displayMode.flatMapLatest { mode ->
        when (mode) {
            DisplayMode.RECURRING -> repo.getRecurring()
            DisplayMode.DONE -> repo.getCompleted()
            else -> repo.getAll()
        }
    }

    /** タグで絞り込まれたタスクID集合 */
    val tagFilteredTaskIds: StateFlow<Set<Int>?> = selectedTagId
        .combine(allTags) { tagId, tags -> tagId to tags }
        .flatMapLatest { (tagId, tags) ->
            if (tagId == null) flowOf(null)
            else {
                val ids = collectInclusiveTagIds(tagId, tags)
                db.taskDao().getTasksByTagIds(ids.toList()).map { list ->
                    list.map { it.id }.toSet()
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val tasks: StateFlow<List<Task>> = combine(
        baseTaskFlow,
        searchQuery.debounce(300),
        sortOption,
        selectedTagId,
        tagFilteredTaskIds
    ) { list, query, sort, tagId, taggedIds ->
        var result = list

        // 検索
        if (query.isNotBlank()) {
            result = result.filter { it.title.contains(query, ignoreCase = true) }
        }

        // タグフィルタ
        if (tagId != null && taggedIds != null) {
            result = result.filter { it.id in taggedIds }
        }

        // ソート
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

    private fun collectInclusiveTagIds(tagId: Int, allTags: List<Tag>): Set<Int> {
        val result = mutableSetOf(tagId)
        allTags.filter { it.parentId == tagId }.forEach {
            result.addAll(collectInclusiveTagIds(it.id, allTags))
        }
        return result
    }

    fun setDisplayMode(mode: DisplayMode) { displayMode.value = mode }
    fun setTagFilter(tagId: Int?) { selectedTagId.value = tagId }
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
    fun clearFilters() { filterOption.value = FilterOption(); selectedTagId.value = null }

    fun softDelete(task: Task) = viewModelScope.launch { repo.softDelete(task.id) }
    fun toggleComplete(task: Task) = viewModelScope.launch { repo.setCompleted(task.id, !task.isCompleted) }
    fun deleteDraft(draft: com.example.taskschedulerv3.data.model.QuickDraftTask) = viewModelScope.launch { draftRepo.delete(draft) }
}
