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
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

data class PhotoWeekSection(
    val weekLabel: String,   // 例: "2026/03/16 〜 03/22"
    val photos: List<PhotoMemo>
)

// 未登録フィルタ種別
enum class PhotoMissingFilter { NONE, NO_TITLE, NO_MEMO, NO_TAG }

@OptIn(ExperimentalCoroutinesApi::class)
class PhotoListViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.getInstance(app)
    private val repo = PhotoMemoRepository(db.photoMemoDao())
    private val photoTagDao = db.photoTagCrossRefDao()

    // ── 検索 ──
    val searchQuery = MutableStateFlow("")

    // ── タグフィルタ ──
    val filterTagId = MutableStateFlow<Int?>(null)

    // ── 未登録フィルタ ──
    val missingFilter = MutableStateFlow(PhotoMissingFilter.NONE)

    // ── 日付範囲 ──
    val filterDateFrom = MutableStateFlow<String>("")  // yyyy-MM-dd
    val filterDateTo   = MutableStateFlow<String>("")  // yyyy-MM-dd

    // ── 複数選択 ──
    val selectedPhotoIds = MutableStateFlow<Set<Int>>(emptySet())
    val isSelectionMode: StateFlow<Boolean> = selectedPhotoIds
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // ── 全タグ ──
    val allTags: StateFlow<List<Tag>> = db.tagDao().getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // タグIDセット per photo (photoId -> Set<tagId>)
    private val photoTagMapFlow: StateFlow<Map<Int, Set<Int>>> = run {
        repo.getAll().flatMapLatest { photos ->
            if (photos.isEmpty()) flowOf(emptyMap())
            else {
                val flows = photos.map { p ->
                    photoTagDao.getTagsByPhotoId(p.id).map { tags -> p.id to tags.map { it.id }.toSet() }
                }
                combine(flows) { pairs -> pairs.toMap() }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
    }

    // ── 絞り込み後フラットリスト ──
    private val basePhotos: Flow<List<PhotoMemo>> =
        searchQuery.debounce(300).flatMapLatest { q ->
            if (q.isBlank()) repo.getAll() else db.photoMemoDao().search(q)
        }

    private val tagFilteredPhotoIds: StateFlow<Set<Int>?> = filterTagId
        .flatMapLatest { tagId ->
            if (tagId == null) flowOf(null)
            else allTags.flatMapLatest { tags ->
                val ids = collectInclusiveTagIds(tagId, tags).toList()
                if (ids.isEmpty()) flowOf(emptySet())
                else photoTagDao.getPhotoIdsByTagIds(ids).map { it.toSet() }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val displayPhotos: StateFlow<List<PhotoMemo>> = combine(
        basePhotos, tagFilteredPhotoIds, missingFilter,
        filterDateFrom, filterDateTo, photoTagMapFlow
    ) { photos, tagIds, missing, dateFrom, dateTo, tagMap ->
        var result = photos
        // Tag filter
        if (tagIds != null) result = result.filter { it.id in tagIds }
        // Missing filter
        result = when (missing) {
            PhotoMissingFilter.NO_TITLE -> result.filter { it.title.isNullOrBlank() }
            PhotoMissingFilter.NO_MEMO  -> result.filter { it.memo.isNullOrBlank() }
            PhotoMissingFilter.NO_TAG   -> result.filter { (tagMap[it.id] ?: emptySet()).isEmpty() }
            PhotoMissingFilter.NONE     -> result
        }
        // Date range filter
        if (dateFrom.isNotEmpty()) {
            result = result.filter { it.date >= dateFrom }
        }
        if (dateTo.isNotEmpty()) {
            result = result.filter { it.date <= dateTo }
        }
        result
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── 1週間ごとグループ ──
    val photosByWeek: StateFlow<List<PhotoWeekSection>> = displayPhotos
        .map { photos ->
            photos.groupBy { photo ->
                // Monday of the photo's week
                val date = LocalDate.parse(photo.date, DateUtils.formatter)
                val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                monday
            }
                .entries
                .sortedByDescending { it.key }
                .map { (monday, list) ->
                    val sunday = monday.plusDays(6)
                    val label = "${monday.year}/${"%02d".format(monday.monthValue)}/${"%02d".format(monday.dayOfMonth)}" +
                        " 〜 ${"%02d".format(sunday.monthValue)}/${"%02d".format(sunday.dayOfMonth)}"
                    PhotoWeekSection(label, list.sortedByDescending { it.date })
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── 選択操作 ──
    fun toggleSelection(photoId: Int) {
        val s = selectedPhotoIds.value.toMutableSet()
        if (photoId in s) s.remove(photoId) else s.add(photoId)
        selectedPhotoIds.value = s
    }
    fun clearSelection() { selectedPhotoIds.value = emptySet() }
    fun selectAll() { selectedPhotoIds.value = displayPhotos.value.map { it.id }.toSet() }

    // ── 一括タグ付け ──
    fun bulkSetTags(photoIds: Set<Int>, tagIds: List<Int>) = viewModelScope.launch {
        photoIds.forEach { photoId ->
            photoTagDao.deleteByPhotoId(photoId)
            tagIds.forEach { tagId -> photoTagDao.insert(PhotoTagCrossRef(photoId = photoId, tagId = tagId)) }
        }
        clearSelection()
    }

    // ── 単体タグ更新 ──
    fun setTagsForPhoto(photoId: Int, tagIds: List<Int>) = viewModelScope.launch {
        photoTagDao.deleteByPhotoId(photoId)
        tagIds.forEach { tagId -> photoTagDao.insert(PhotoTagCrossRef(photoId = photoId, tagId = tagId)) }
    }

    // ── メモ・タイトル更新 ──
    fun updatePhotoMemo(photo: PhotoMemo, title: String?, memo: String?) = viewModelScope.launch {
        db.photoMemoDao().update(photo.copy(title = title?.ifBlank { null }, memo = memo?.ifBlank { null }))
    }

    fun getTagsForPhoto(photoId: Int) = photoTagDao.getTagsByPhotoId(photoId)

    // ── フィルタ設定 ──
    fun setSearchQuery(q: String) { searchQuery.value = q }
    fun setTagFilter(tagId: Int?) { filterTagId.value = tagId }
    fun setMissingFilter(f: PhotoMissingFilter) {
        missingFilter.value = if (missingFilter.value == f) PhotoMissingFilter.NONE else f
    }
    fun setDateRange(from: String, to: String) {
        filterDateFrom.value = from
        filterDateTo.value = to
    }
    fun clearDateRange() { filterDateFrom.value = ""; filterDateTo.value = "" }

    // ── Calendar integration ──
    private val _selectedDate = MutableStateFlow(DateUtils.today())
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()
    val photosForDate: StateFlow<List<PhotoMemo>> = _selectedDate
        .flatMapLatest { repo.getByDate(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun selectDate(date: String) { _selectedDate.value = date }

    fun savePhotoFromCamera(tempFile: java.io.File, date: String, taskId: Int? = null,
                            title: String? = null, memo: String? = null) =
        viewModelScope.launch {
            val path = PhotoFileManager.saveResizedPhotoFromFile(getApplication(), tempFile) ?: return@launch
            repo.insert(PhotoMemo(taskId = taskId, date = date, imagePath = path,
                title = title?.ifBlank { null }, memo = memo?.ifBlank { null }))
        }

    fun savePhotoFromGallery(uri: Uri, date: String, taskId: Int? = null,
                             title: String? = null, memo: String? = null) =
        viewModelScope.launch {
            val path = PhotoFileManager.saveResizedPhoto(getApplication(), uri) ?: return@launch
            repo.insert(PhotoMemo(taskId = taskId, date = date, imagePath = path,
                title = title?.ifBlank { null }, memo = memo?.ifBlank { null }))
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
