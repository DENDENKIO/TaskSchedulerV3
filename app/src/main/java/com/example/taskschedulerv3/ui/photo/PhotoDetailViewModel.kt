package com.example.taskschedulerv3.ui.photo

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskschedulerv3.data.db.AppDatabase
import com.example.taskschedulerv3.data.model.PhotoMemo
import com.example.taskschedulerv3.data.model.PhotoTagCrossRef
import com.example.taskschedulerv3.data.model.Tag
import com.example.taskschedulerv3.data.repository.PhotoMemoRepository
import com.example.taskschedulerv3.util.PhotoFileManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class PhotoDetailViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.getInstance(app)
    private val repo = PhotoMemoRepository(db.photoMemoDao())
    private val taskRepo = com.example.taskschedulerv3.data.repository.TaskRepository(db.taskDao())
    private val photoTagDao = db.photoTagCrossRefDao()

    private val _photoId = MutableStateFlow<Int?>(null)

    val photo: StateFlow<PhotoMemo?> = _photoId
        .filterNotNull()
        .flatMapLatest { id -> flow { emit(repo.getById(id)) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val photoTags: StateFlow<List<Tag>> = _photoId
        .filterNotNull()
        .flatMapLatest { id -> photoTagDao.getTagsByPhotoId(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTags: StateFlow<List<Tag>> = db.tagDao().getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun loadPhoto(id: Int) { _photoId.value = id }

    fun updateMemo(title: String?, memo: String?) = viewModelScope.launch {
        val p = photo.value ?: return@launch
        db.photoMemoDao().update(p.copy(
            title = title?.ifBlank { null },
            memo = memo?.ifBlank { null }
        ))
        // Reload
        _photoId.value = p.id
    }

    fun updateParentTaskTitle(newTitle: String) = viewModelScope.launch {
        val p = photo.value ?: return@launch
        val tId = p.taskId ?: return@launch
        val t = taskRepo.getById(tId) ?: return@launch
        taskRepo.update(t.copy(title = newTitle))
    }

    fun setTags(tagIds: List<Int>) = viewModelScope.launch {
        val photoId = _photoId.value ?: return@launch
        photoTagDao.deleteByPhotoId(photoId)
        tagIds.forEach { tagId ->
            photoTagDao.insert(PhotoTagCrossRef(photoId = photoId, tagId = tagId))
        }
    }

    fun deletePhoto(photo: PhotoMemo) = viewModelScope.launch {
        repo.delete(photo)
        PhotoFileManager.deletePhoto(photo.imagePath)
    }
}
