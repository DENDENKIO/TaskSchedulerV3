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
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class ScheduleListViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.getInstance(app)
    private val repo = TaskRepository(db.taskDao(), db.roadmapStepDao())
    private val tagRepo = TagRepository(db.tagDao())
    private val crossRefDao = db.taskTagCrossRefDao()
    val draftRepo = QuickDraftRepository(db.quickDraftTaskDao(), db.taskDao(), crossRefDao, db.photoMemoDao())

    val searchQuery = MutableStateFlow("")
    val sortOption = MutableStateFlow(SortOption.DATE_ASC)
    val filterOption = MutableStateFlow(FilterOption())
    val filterDate = MutableStateFlow("")
    val filterDateFrom = MutableStateFlow("")
    val filterDateTo = MutableStateFlow("")

    val displayMode = MutableStateFlow(DisplayMode.ALL)

    val selectedTagId = MutableStateFlow<Int?>(null)

    val allTags: StateFlow<List<Tag>> = db.tagDao().getAllForFilter()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filterTagId: MutableStateFlow<Int?> get() = selectedTagId

    val drafts = draftRepo.getDrafts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** displayMode に応じた基本タスクFlow (DRAFT以外) */
    private val baseTaskFlow: Flow<List<Task>> = displayMode.flatMapLatest { mode ->
        val today = LocalDate.now()
        when (mode) {
            DisplayMode.RECURRING -> repo.getRecurring()
            DisplayMode.DONE -> repo.getCompleted()
            DisplayMode.TODAY -> repo.getAll().map { list ->
                list.filter { task ->
                    if (task.isCompleted || task.isIndefinite) return@filter false
                    if (task.scheduleType == ScheduleType.RECURRING) {
                        com.example.taskschedulerv3.util.RecurrenceCalculator.occursOn(task, today)
                    } else {
                        try { java.time.LocalDate.parse(task.startDate) == today } catch (_: Exception) { false }
                    }
                }
            }
            DisplayMode.WEEK -> {
                val weekEnd = today.plusDays(6)
                repo.getAll().map { list ->
                    list.filter { task ->
                        if (task.isCompleted || task.isIndefinite) return@filter false
                        val d = try { java.time.LocalDate.parse(task.startDate) } catch (_: Exception) { null }
                        d != null && !d.isBefore(today) && !d.isAfter(weekEnd)
                    }
                }
            }
            DisplayMode.INDEFINITE -> repo.getAll().map { list -> list.filter { it.isIndefinite && !it.isCompleted } }
            DisplayMode.DRAFT -> flowOf(emptyList())
            else -> repo.getAll().map { list -> list.filter { !it.isCompleted } }
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

    /** 各タスクに対応するタグリストのマップ */
    val taskTagMap: StateFlow<Map<Int, List<Tag>>> = combine(
        crossRefDao.getAll(),
        allTags
    ) { refs, tags ->
        refs.groupBy { it.taskId }.mapValues { entry ->
            val tagIds = entry.value.map { it.tagId }
            tags.filter { it.id in tagIds }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // ★改善: searchQuery の debounce を検索にのみ適用するため分離
    private val debouncedSearch = searchQuery.debounce(300)

    /** タグフィルタ関連情報をまとめる Flow */
    private val tagFilterInfo = combine(
        selectedTagId,
        tagFilteredTaskIds,
        taskTagMap
    ) { tagId, taggedIds, tagMap ->
        Triple(tagId, taggedIds, tagMap)
    }

    val tasks: StateFlow<List<Task>> = combine(
        baseTaskFlow,
        debouncedSearch,
        sortOption,
        tagFilterInfo
    ) { list, query, sort, tagInfo ->
        val (tagId, taggedIds, tagMap) = tagInfo
        var result = list

        if (query.isNotBlank()) {
            result = result.filter { it.title.contains(query, ignoreCase = true) }
        }

        if (tagId != null && taggedIds != null) {
            result = result.filter { it.id in taggedIds || tagMap[it.id].isNullOrEmpty() }
        }

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

    // ★改善: ロードマップとchild情報をキャッシュとして保持し、タスクリストと結合
    // DB変更時のみ自動更新される（毎回クエリしない）
    private val stepMapFlow: StateFlow<Map<Int, String>> = db.roadmapStepDao().getAllStepsFlow()
        .map { steps -> steps.associate { it.id to it.title } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val stepsByTaskFlow: StateFlow<Map<Int, List<RoadmapStep>>> = db.roadmapStepDao().getAllStepsFlow()
        .map { steps -> steps.groupBy { it.taskId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val childCountMapFlow: StateFlow<Map<Int?, Int>> = db.taskDao().getAllChildrenFlow()
        .map { children -> children.groupBy { it.parentTaskId }.mapValues { it.value.size } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val uiTasks: StateFlow<List<TaskListItemUiModel>> = combine(
        tasks,
        stepMapFlow,
        stepsByTaskFlow,
        childCountMapFlow
    ) { currentTasks, stepMap, stepsByTask, childCountMap ->
        currentTasks.map { task ->
            val emoji = when {
                task.roadmapEnabled -> "🛣️"
                task.scheduleType == ScheduleType.RECURRING -> "🔁"
                task.isIndefinite -> "📝"
                else -> "📅"
            }

            var activeLabel: String? = null
            val displayTitle = if (task.roadmapEnabled && task.activeRoadmapStepId != null) {
                val stepName = stepMap[task.activeRoadmapStepId] ?: "進行中"
                activeLabel = stepName
                "$emoji 【$stepName】${task.title}"
            } else {
                "$emoji ${task.title}"
            }

            val taskSteps = stepsByTask[task.id] ?: emptyList()
            val totalSteps = taskSteps.size + 1
            val completedCount = taskSteps.count { it.isCompleted } + (if (task.activeRoadmapStepId != null || task.isCompleted) 1 else 0)

            val progress = if (task.roadmapEnabled) {
                if (totalSteps <= 1) 0 else (completedCount * 100) / totalSteps
            } else {
                task.progress
            }

            TaskListItemUiModel(
                task = task,
                displayTitle = displayTitle,
                displayDate = task.startDate,
                progressPercent = progress,
                emoji = emoji,
                isRoadmapTask = task.roadmapEnabled,
                activeStageLabel = activeLabel,
                relatedCount = childCountMap[task.id] ?: 0,
                completedSteps = completedCount,
                totalSteps = totalSteps
            )
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
    fun clearFilters() { filterOption.value = FilterOption(); selectedTagId.value = null }

    fun softDelete(task: Task) = viewModelScope.launch { repo.softDelete(task.id) }

    fun toggleComplete(task: Task) = viewModelScope.launch {
        if (task.roadmapEnabled) {
            processRoadmapCompletion(task)
        } else {
            repo.setCompleted(task.id, !task.isCompleted)
        }
    }

    private suspend fun processRoadmapCompletion(task: Task) {
        val roadmapStepDao = db.roadmapStepDao()
        val steps = roadmapStepDao.getStepsForTaskSync(task.id)

        if (steps.isEmpty()) {
            repo.setCompleted(task.id, true)
            return
        }

        val currentStepId = task.activeRoadmapStepId
        if (currentStepId == null) {
            val firstStep = steps.firstOrNull()
            if (firstStep != null) {
                db.taskDao().update(task.copy(
                    activeRoadmapStepId = firstStep.id,
                    startDate = firstStep.date ?: task.startDate,
                    updatedAt = System.currentTimeMillis()
                ))
                repo.syncTaskProgress(task.id)
            } else {
                repo.setCompleted(task.id, true)
                repo.syncTaskProgress(task.id)
            }
        } else {
            roadmapStepDao.setStepCompleted(currentStepId, true, System.currentTimeMillis())

            val currentIndex = steps.indexOfFirst { it.id == currentStepId }
            val nextStep = if (currentIndex != -1 && currentIndex < steps.size - 1) {
                steps[currentIndex + 1]
            } else {
                null
            }

            if (nextStep != null) {
                db.taskDao().update(task.copy(
                    activeRoadmapStepId = nextStep.id,
                    startDate = nextStep.date ?: task.startDate,
                    updatedAt = System.currentTimeMillis()
                ))
                repo.syncTaskProgress(task.id)
            } else {
                repo.setCompleted(task.id, true)
                repo.syncTaskProgress(task.id)
            }
        }
    }

    fun deleteDraft(draft: com.example.taskschedulerv3.data.model.QuickDraftTask) = viewModelScope.launch { draftRepo.delete(draft) }
}
