package com.example.taskschedulerv3.ui.roadmap

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskschedulerv3.data.db.AppDatabase
import com.example.taskschedulerv3.data.model.RoadmapStep
import com.example.taskschedulerv3.data.model.Task
import com.example.taskschedulerv3.data.repository.TaskRepository
import com.example.taskschedulerv3.notification.AlarmScheduler
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID
import androidx.room.withTransaction

/**
 * ロードマップ編集用ViewModel (仕様書 5.4, 21 章準拠)
 */
class RoadmapEditViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.getInstance(app)
    private val roadmapDao = db.roadmapStepDao()
    private val taskDao = db.taskDao()
    private val appContext = app.applicationContext
    private val repo = TaskRepository(taskDao, roadmapDao)

    private val _taskId = MutableStateFlow<Int?>(null)
    val task: StateFlow<Task?> = _taskId
        .filterNotNull()
        .map { taskDao.getById(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // 仕様書 140: 編集中のアイテムリスト
    private val _editorItems = MutableStateFlow<List<RoadmapStepEditorItem>>(emptyList())
    val editorItems: StateFlow<List<RoadmapStepEditorItem>> = _editorItems.asStateFlow()

    // 仕様書 141: 未保存の変更有無
    val hasUnsavedChanges = combine(_editorItems, _taskId) { items, id ->
        items.any { it.isDirty || it.isDeleted || it.isNew }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // 仕様書 142: フォーカス管理
    private val _focusedItemLocalId = MutableStateFlow<String?>(null)
    val focusedItemLocalId: StateFlow<String?> = _focusedItemLocalId.asStateFlow()

    // 仕様書 143: Undo用一時保持
    private val _pendingDeleteUndoItem = MutableStateFlow<RoadmapStepEditorItem?>(null)

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _saveSuccess = MutableStateFlow(false)
    val saveSuccess: StateFlow<Boolean> = _saveSuccess.asStateFlow()

    fun loadTask(id: Int) {
        if (_taskId.value == id) return
        _taskId.value = id
        viewModelScope.launch {
            val steps = roadmapDao.getStepsForTaskSync(id)
            _editorItems.value = steps.map { step ->
                RoadmapStepEditorItem(
                    localId = UUID.randomUUID().toString(),
                    dbId = step.id,
                    taskId = step.taskId,
                    title = step.title,
                    targetDate = step.date?.let { LocalDate.parse(it) },
                    isCompleted = step.isCompleted,
                    sortOrder = step.sortOrder,
                    isNew = false,
                    isDirty = false
                )
            }
        }
    }

    // 仕様書 572: ステップ追加
    fun addStep() {
        val currentTaskId = _taskId.value ?: return
        val currentItems = _editorItems.value
        val nextOrder = (currentItems.filter { !it.isDeleted }.maxOfOrNull { it.sortOrder } ?: 0) + 1
        val newLocalId = UUID.randomUUID().toString()
        
        val newItem = RoadmapStepEditorItem(
            localId = newLocalId,
            taskId = currentTaskId,
            title = "",
            sortOrder = nextOrder,
            isNew = true,
            isDirty = true
        )
        
        _editorItems.value = currentItems + newItem
        _focusedItemLocalId.value = newLocalId
    }

    // 仕様書 573: タイトル更新
    fun updateTitle(localId: String, title: String) {
        _editorItems.value = _editorItems.value.map {
            if (it.localId == localId) it.copy(title = title, isDirty = true) else it
        }
    }

    // 仕様書 574: 日付更新
    fun updateDate(localId: String, date: LocalDate?) {
        _editorItems.value = _editorItems.value.map {
            if (it.localId == localId) it.copy(targetDate = date, isDirty = true) else it
        }
    }

    // 仕様書 576: 削除 (Undo対応)
    fun deleteStep(localId: String) {
        val item = _editorItems.value.find { it.localId == localId } ?: return
        if (item.isNew) {
            // まだDBにないものは即除去
            _editorItems.value = _editorItems.value.filter { it.localId != localId }
        } else {
            // DBにあるものは削除フラグを立てる
            _editorItems.value = _editorItems.value.map {
                if (it.localId == localId) it.copy(isDeleted = true, isDirty = true) else it
            }
            _pendingDeleteUndoItem.value = item
        }
    }

    fun undoDelete() {
        val item = _pendingDeleteUndoItem.value ?: return
        _editorItems.value = _editorItems.value.map {
            if (it.localId == item.localId) it.copy(isDeleted = false, isDirty = true) else it
        }
        _pendingDeleteUndoItem.value = null
    }

    // 仕様書 578: 並び替え (削除済みアイテムを考慮したインデックスマッピング)
    fun moveStep(fromIndexInVisible: Int, toIndexInVisible: Int) {
        val currentItems = _editorItems.value
        val visibleItems = currentItems.filter { !it.isDeleted }
        
        // 境界チェック
        if (fromIndexInVisible !in visibleItems.indices || toIndexInVisible !in visibleItems.indices) return
        
        val fromItem = visibleItems[fromIndexInVisible]
        val toItem = visibleItems[toIndexInVisible]
        
        val fullList = currentItems.toMutableList()
        val actualFrom = fullList.indexOfFirst { it.localId == fromItem.localId }
        val actualTo = fullList.indexOfFirst { it.localId == toItem.localId }
        
        if (actualFrom == -1 || actualTo == -1) return
        
        val item = fullList.removeAt(actualFrom)
        fullList.add(actualTo, item)
        
        // 全アイテムの sortOrder を現在のリスト順に基づいて再設定
        _editorItems.value = fullList.mapIndexed { index, roadmapEditItem ->
            if (roadmapEditItem.sortOrder != index + 1) {
                roadmapEditItem.copy(sortOrder = index + 1, isDirty = true)
            } else roadmapEditItem
        }
    }

    // 仕様書 581: 保存 (トランザクション一括保存)
    fun save() = viewModelScope.launch {
        val t = task.value ?: return@launch
        _isSaving.value = true
        
        try {
            db.withTransaction {
                val currentItems = _editorItems.value
                
                // 1. 有効なアイテムの抽出と再採番 (仕様書 358)
                val visibleItems = currentItems.filter { !it.isDeleted && it.title.isNotBlank() }
                
                // 2. 削除対象をDELETE (仕様書 359)
                val idsToDelete = currentItems.filter { it.isDeleted || (it.title.isBlank() && !it.isNew) }
                    .mapNotNull { it.dbId }
                if (idsToDelete.isNotEmpty()) {
                    roadmapDao.deleteStepsByIds(idsToDelete)
                    idsToDelete.forEach { AlarmScheduler.cancelForStep(appContext, it) }
                }

                // 3. 既存・新規の保存
                val stepsToUpsert = visibleItems.mapIndexed { index, item ->
                    RoadmapStep(
                        id = item.dbId ?: 0,
                        taskId = item.taskId,
                        title = item.title,
                        date = item.targetDate?.toString(),
                        sortOrder = index + 1,
                        isCompleted = item.isCompleted
                    )
                }
                
                // 順序が変わるので一旦一括保存
                roadmapDao.insertAll(stepsToUpsert)
            }
            
            // 4. 保存後に通知を同期 (仕様書 402)
            // 再読込して確定したIDを取得
            val savedSteps = roadmapDao.getStepsForTaskSync(t.id)
            savedSteps.forEach { step ->
                if (!step.isCompleted && step.date != null) {
                    AlarmScheduler.scheduleForStep(appContext, t, step)
                } else {
                    AlarmScheduler.cancelForStep(appContext, step.id)
                }
            }
            
            repo.syncTaskProgress(t.id)

            _saveSuccess.value = true
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            _isSaving.value = false
        }
    }

    fun clearFocusedItem() {
        _focusedItemLocalId.value = null
    }
}
