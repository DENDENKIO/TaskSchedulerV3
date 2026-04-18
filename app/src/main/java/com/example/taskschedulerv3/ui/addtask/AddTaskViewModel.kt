package com.example.taskschedulerv3.ui.addtask

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskschedulerv3.data.db.AppDatabase
import com.example.taskschedulerv3.data.model.*
import com.example.taskschedulerv3.data.repository.PhotoMemoRepository
import com.example.taskschedulerv3.data.repository.TagRepository
import com.example.taskschedulerv3.data.repository.TaskRelationRepository
import com.example.taskschedulerv3.data.repository.TaskRepository
import com.example.taskschedulerv3.notification.AlarmScheduler
import com.example.taskschedulerv3.util.PhotoFileManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

class AddTaskViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.getInstance(app)
    private val repo = TaskRepository(db.taskDao(), db.roadmapStepDao())
    private val tagRepo = TagRepository(db.tagDao())
    private val photoRepo = PhotoMemoRepository(db.photoMemoDao())
    private val relationRepo = TaskRelationRepository(db.taskRelationDao())
    private val crossRefDao = db.taskTagCrossRefDao()

    val title = MutableStateFlow("")
    val description = MutableStateFlow("")
    val startDate = MutableStateFlow("")
    val endDate = MutableStateFlow("")
    val startTime = MutableStateFlow("")
    val endTime = MutableStateFlow("")
    val scheduleType = MutableStateFlow(ScheduleType.NORMAL)
    val recurrencePattern = MutableStateFlow<RecurrencePattern?>(null)
    val recurrenceDays = MutableStateFlow("")
    val recurrenceEndDate = MutableStateFlow("")
    val notifyEnabled = MutableStateFlow(true)
    val notifyMinutesBefore = MutableStateFlow(60)
    val isIndefinite = MutableStateFlow(false)  // 無期限登録フラグ
    val parentTaskId = MutableStateFlow<Int?>(null) // 親タスクID (ステップ5)
    val roadmapEnabled = MutableStateFlow(false) // ロードマップ有効化 (ステップ6)

    // Tag selection
    val selectedTagIds = MutableStateFlow<Set<Int>>(emptySet())
    val allTags: StateFlow<List<Tag>> = tagRepo.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // All tasks for relation picker (excluding self)
    val allTasks: StateFlow<List<Task>> = repo.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Related task ids: existing (saved) + newly added - newly removed
    private val _existingRelatedIds = MutableStateFlow<Set<Int>>(emptySet())
    private val _addedRelatedIds = MutableStateFlow<Set<Int>>(emptySet())
    private val _removedRelatedIds = MutableStateFlow<Set<Int>>(emptySet())

    val relatedTaskIds: StateFlow<Set<Int>> = combine(
        _existingRelatedIds, _addedRelatedIds, _removedRelatedIds
    ) { existing, added, removed ->
        (existing + added) - removed
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val relatedTasks: StateFlow<List<Task>> = combine(relatedTaskIds, allTasks) { ids, tasks ->
        tasks.filter { it.id in ids }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 親タスクオブジェクト (ステップ5)
    val parentTask: StateFlow<Task?> = combine(parentTaskId, allTasks) { id, tasks ->
        tasks.find { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Photo attachments
    private val _pendingPhotoPaths = MutableStateFlow<List<String>>(emptyList())
    val pendingPhotoPaths: StateFlow<List<String>> = _pendingPhotoPaths.asStateFlow()
    private val _existingPhotos = MutableStateFlow<List<PhotoMemo>>(emptyList())
    val existingPhotos: StateFlow<List<PhotoMemo>> = _existingPhotos.asStateFlow()

    private val _saveSuccess = MutableStateFlow(false)
    val saveSuccess: StateFlow<Boolean> = _saveSuccess.asStateFlow()

    private var _editId: Int? = null

    fun loadTask(taskId: Int) = viewModelScope.launch {
        _editId = taskId
        repo.getById(taskId)?.let { t ->
            title.value = t.title
            description.value = t.description ?: ""
            startDate.value = t.startDate
            endDate.value = t.endDate ?: ""
            startTime.value = t.startTime ?: ""
            endTime.value = t.endTime ?: ""
            scheduleType.value = t.scheduleType
            recurrencePattern.value = t.recurrencePattern
            recurrenceDays.value = t.recurrenceDays ?: ""
            recurrenceEndDate.value = t.recurrenceEndDate ?: ""
            notifyEnabled.value = t.notifyEnabled
            notifyMinutesBefore.value = t.notifyMinutesBefore
            isIndefinite.value = t.isIndefinite
            parentTaskId.value = t.parentTaskId // ステップ5
            roadmapEnabled.value = t.roadmapEnabled // ステップ6
        }
        crossRefDao.getTagsByTaskId(taskId).first().let { tags ->
            selectedTagIds.value = tags.map { it.id }.toSet()
        }
        photoRepo.getByTaskId(taskId).first().let { photos ->
            _existingPhotos.value = photos
        }
        // Load existing relation ids
        val existingIds = relationRepo.getRelationsForTask(taskId).map { rel ->
            if (rel.taskId1 == taskId) rel.taskId2 else rel.taskId1
        }.toSet()
        _existingRelatedIds.value = existingIds
        _saveSuccess.value = false
    }

    fun resetState() {
        title.value = ""
        description.value = ""
        startDate.value = ""
        endDate.value = ""
        startTime.value = ""
        endTime.value = ""
        scheduleType.value = ScheduleType.NORMAL
        recurrencePattern.value = null
        recurrenceDays.value = ""
        recurrenceEndDate.value = ""
        notifyEnabled.value = true
        notifyMinutesBefore.value = 60
        isIndefinite.value = false
        parentTaskId.value = null // ステップ5
        roadmapEnabled.value = false // ステップ6
        selectedTagIds.value = emptySet()
        _pendingPhotoPaths.value = emptyList()
        _existingPhotos.value = emptyList()
        _addedRelatedIds.value = emptySet()
        _removedRelatedIds.value = emptySet()
        _existingRelatedIds.value = emptySet()
        _editId = null
        _saveSuccess.value = false
    }

    fun addRelatedTask(taskId: Int) {
        _addedRelatedIds.value = _addedRelatedIds.value + taskId
        _removedRelatedIds.value = _removedRelatedIds.value - taskId
    }

    fun removeRelatedTask(taskId: Int) {
        if (taskId in _existingRelatedIds.value) {
            _removedRelatedIds.value = _removedRelatedIds.value + taskId
        }
        _addedRelatedIds.value = _addedRelatedIds.value - taskId
    }

    fun addPhotoFromCamera(tempFile: File) = viewModelScope.launch {
        val path = PhotoFileManager.saveResizedPhotoFromFile(getApplication(), tempFile) ?: return@launch
        _pendingPhotoPaths.value = _pendingPhotoPaths.value + path
    }

    fun addPhotoFromGallery(uri: Uri) = viewModelScope.launch {
        val path = PhotoFileManager.saveResizedPhoto(getApplication(), uri) ?: return@launch
        _pendingPhotoPaths.value = _pendingPhotoPaths.value + path
    }

    fun removePendingPhoto(path: String) {
        PhotoFileManager.deletePhoto(path)
        _pendingPhotoPaths.value = _pendingPhotoPaths.value - path
    }

    fun removeExistingPhoto(photo: PhotoMemo) = viewModelScope.launch {
        photoRepo.delete(photo)
        PhotoFileManager.deletePhoto(photo.imagePath)
        _existingPhotos.value = _existingPhotos.value - photo
    }

    fun toggleWeeklyDay(day: Int) {
        val current = recurrenceDays.value.split(",").filter { it.isNotBlank() }.toMutableSet()
        val s = day.toString()
        if (s in current) current.remove(s) else current.add(s)
        recurrenceDays.value = current.sorted().joinToString(",")
    }

    fun toggleMonthlyDate(date: Int) {
        val current = recurrenceDays.value.split(",").filter { it.isNotBlank() }.toMutableSet()
        val s = date.toString()
        if (s in current) current.remove(s) else current.add(s)
        recurrenceDays.value = current.sortedBy { it.toInt() }.joinToString(",")
    }

    fun applyOcrToTitle(text: String) {
        title.value = text.trim()
    }

    fun applyOcrToDescription(text: String, isAppend: Boolean) {
        val current = description.value
        description.value = if (isAppend && current.isNotEmpty()) "$current\n${text.trim()}" else text.trim()
    }

    fun save(editId: Int? = null) = viewModelScope.launch {
        val task = Task(
            id = editId ?: 0,
            title = title.value.trim(),
            description = description.value.trim().ifEmpty { null },
            startDate = if (isIndefinite.value) "" else startDate.value,
            endDate = if (isIndefinite.value) null else endDate.value.ifEmpty { null },
            startTime = startTime.value.ifEmpty { null },
            endTime = endTime.value.ifEmpty { null },
            scheduleType = scheduleType.value,
            recurrencePattern = recurrencePattern.value,
            recurrenceDays = recurrenceDays.value.ifEmpty { null },
            recurrenceEndDate = recurrenceEndDate.value.ifEmpty { null },
            priority = 1,
            notifyEnabled = notifyEnabled.value,
            notifyMinutesBefore = notifyMinutesBefore.value,
            isIndefinite = isIndefinite.value,
            parentTaskId = parentTaskId.value, // ステップ5
            roadmapEnabled = roadmapEnabled.value, // ステップ6
            updatedAt = System.currentTimeMillis()
        )
        val finalId = if (editId != null) {
            repo.update(task)
            editId
        } else {
            repo.insert(task).toInt()
        }

        // Tags
        crossRefDao.deleteByTaskId(finalId)
        selectedTagIds.value.forEach { tagId ->
            crossRefDao.insert(TaskTagCrossRef(taskId = finalId, tagId = tagId))
        }

        // Photos
        val dateStr = startDate.value
        val taskTitle = title.value.trim().ifEmpty { null }
        val taskDesc = description.value.trim().ifEmpty { null }
        val photoTagDao = db.photoTagCrossRefDao()
        _pendingPhotoPaths.value.forEach { path ->
            val photoId = photoRepo.insert(
                PhotoMemo(taskId = finalId, date = dateStr, imagePath = path, title = taskTitle, memo = taskDesc)
            ).toInt()
            selectedTagIds.value.forEach { tagId ->
                photoTagDao.insert(PhotoTagCrossRef(photoId = photoId, tagId = tagId))
            }
        }
        _pendingPhotoPaths.value = emptyList()

        // Relations
        _addedRelatedIds.value.forEach { relId -> relationRepo.insert(finalId, relId) }
        _removedRelatedIds.value.forEach { relId -> relationRepo.deleteRelation(finalId, relId) }

        // Alarm (無期限の場合は通知なし)
        if (!isIndefinite.value) {
            AlarmScheduler.scheduleForTask(getApplication(), task.copy(id = finalId))
        }
        _saveSuccess.value = true
    }
}
