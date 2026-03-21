package com.example.taskschedulerv3.ui.photo

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskschedulerv3.data.db.AppDatabase
import com.example.taskschedulerv3.data.model.PhotoMemo
import com.example.taskschedulerv3.data.model.PhotoTagCrossRef
import com.example.taskschedulerv3.data.model.Tag
import com.example.taskschedulerv3.data.repository.PhotoMemoRepository
import com.example.taskschedulerv3.util.DateUtils
import com.example.taskschedulerv3.util.PhotoFileManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class PhotoMonthSection(
    val yearMonth: String,
    val photos: List<PhotoMemo>
)

@OptIn(ExperimentalCoroutinesApi::class)
class PhotoListViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.getInstance(app)
    private val repo = PhotoMemoRepository(db.photoMemoDao())
    private val photoTagDao = db.photoTagCrossRefDao()

    // ── 検索 ──
    val searchQuery = MutableStateFlow("")

    // ── タグフィルタ ──
    val filterTagId = MutableStateFlow<Int?>(null)

    // ── 複数選択モード ──
    val selectedPhotoIds = MutableStateFlow<Set<Int>>(emptySet())
    val isSelectionMode: StateFlow<Boolean> = selectedPhotoIds
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // ── 全タグリスト ──
    val allTags: StateFlow<List<Tag>> = db.tagDao().getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── 絞り込み後の全写真 (flat) ──
    private val filteredPhotos: StateFlow<List<PhotoMemo>> = combine(
        searchQuery.debounce(300).flatMapLatest { q ->
            if (q.isBlank()) repo.getAll() else db.photoMemoDao().search(q)
        },
        filterTagId,
        allTags
    ) { photos, tagId, tags ->
        if (tagId == null) photos
        else {
            // Collect inclusive tag ids (tag + descendants)
            val ids = collectInclusiveTagIds(tagId, tags)
            // We need photos that have at least one of those tag ids
            // Filter synchronously from the pre-loaded photo list using a subquery approach
            photos  // further filtered reactively below
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Tag-filtered photo ids flow
    private val tagFilteredPhotoIds: StateFlow<Set<Int>?> = filterTagId
        .flatMapLatest { tagId ->
            if (tagId == null) flowOf(null)
            else {
                allTags.flatMapLatest { tags ->
                    val ids = collectInclusiveTagIds(tagId, tags)
                    if (ids.isEmpty()) flowOf(emptySet())
                    else photoTagDao.getPhotoIdsByTagIds(ids.toList())
                        .map { it.toSet() }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Final filtered photos
    private val displayPhotos: StateFlow<List<PhotoMemo>> = combine(
        filteredPhotos, tagFilteredPhotoIds
    ) { photos, tagPhotoIds ->
        if (tagPhotoIds == null) photos
        else photos.filter { it.id in tagPhotoIds }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── 月別グループ ──
    val photosByMonth: StateFlow<List<PhotoMonthSection>> = displayPhotos
        .map { photos ->
            photos.groupBy { it.date.substring(0, 7) }
                .entries
                .sortedByDescending { it.key }
                .map { (month, list) -> PhotoMonthSection(month, list) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── 選択操作 ──
    fun toggleSelection(photoId: Int) {
        val current = selectedPhotoIds.value.toMutableSet()
        if (photoId in current) current.remove(photoId) else current.add(photoId)
        selectedPhotoIds.value = current
    }

    fun clearSelection() { selectedPhotoIds.value = emptySet() }

    fun selectAll() {
        selectedPhotoIds.value = displayPhotos.value.map { it.id }.toSet()
    }

    // ── 一括タグ付け ──
    fun bulkSetTags(photoIds: Set<Int>, tagIds: List<Int>) = viewModelScope.launch {
        photoIds.forEach { photoId ->
            photoTagDao.deleteByPhotoId(photoId)
            tagIds.forEach { tagId ->
                photoTagDao.insert(PhotoTagCrossRef(photoId = photoId, tagId = tagId))
            }
        }
        clearSelection()
    }

    // ── 単体タグ更新 ──
    fun setTagsForPhoto(photoId: Int, tagIds: List<Int>) = viewModelScope.launch {
        photoTagDao.deleteByPhotoId(photoId)
        tagIds.forEach { tagId ->
            photoTagDao.insert(PhotoTagCrossRef(photoId = photoId, tagId = tagId))
        }
    }

    // ── メモ・タイトル更新 ──
    fun updatePhotoMemo(photo: PhotoMemo, title: String?, memo: String?) = viewModelScope.launch {
        db.photoMemoDao().update(photo.copy(title = title?.ifBlank { null }, memo = memo?.ifBlank { null }))
    }

    // ── 写真タグ取得 ──
    fun getTagsForPhoto(photoId: Int) = photoTagDao.getTagsByPhotoId(photoId)

    // ── フィルタ ──
    fun setSearchQuery(q: String) { searchQuery.value = q }
    fun setTagFilter(tagId: Int?) { filterTagId.value = tagId }

    // ── Calendar integration ──
    private val _selectedDate = MutableStateFlow(DateUtils.today())
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    val photosForDate: StateFlow<List<PhotoMemo>> = _selectedDate
        .flatMapLatest { repo.getByDate(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectDate(date: String) { _selectedDate.value = date }

    fun savePhotoFromCamera(tempFile: java.io.File, date: String, taskId: Int? = null) =
        viewModelScope.launch {
            val path = PhotoFileManager.saveResizedPhotoFromFile(getApplication(), tempFile) ?: return@launch
            repo.insert(PhotoMemo(taskId = taskId, date = date, imagePath = path))
        }

    fun savePhotoFromGallery(uri: Uri, date: String, taskId: Int? = null) =
        viewModelScope.launch {
            val path = PhotoFileManager.saveResizedPhoto(getApplication(), uri) ?: return@launch
            repo.insert(PhotoMemo(taskId = taskId, date = date, imagePath = path))
        }

    fun deletePhoto(photo: PhotoMemo) = viewModelScope.launch {
        repo.delete(photo)
        PhotoFileManager.deletePhoto(photo.imagePath)
    }

    private fun collectInclusiveTagIds(tagId: Int, tags: List<Tag>): Set<Int> {
        val result = mutableSetOf<Int>()
        fun collect(id: Int) {
            result.add(id)
            tags.filter { it.parentId == id }.forEach { collect(it.id) }
        }
        collect(tagId)
        return result
    }
}
