package com.example.taskschedulerv3.ui.photo

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskschedulerv3.data.db.AppDatabase
import com.example.taskschedulerv3.data.model.PhotoMemo
import com.example.taskschedulerv3.data.repository.PhotoMemoRepository
import com.example.taskschedulerv3.util.DateUtils
import com.example.taskschedulerv3.util.PhotoFileManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class PhotoMonthSection(
    val yearMonth: String,      // "yyyy-MM"
    val photos: List<PhotoMemo>
)

class PhotoListViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = PhotoMemoRepository(AppDatabase.getInstance(app).photoMemoDao())

    // All photos grouped by month
    val photosByMonth: StateFlow<List<PhotoMonthSection>> = repo.getAll()
        .map { photos ->
            photos.groupBy { it.date.substring(0, 7) }  // "yyyy-MM"
                .entries
                .sortedByDescending { it.key }
                .map { (month, list) -> PhotoMonthSection(month, list) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Photos for selected date (for calendar integration)
    private val _selectedDate = MutableStateFlow(DateUtils.today())
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    val photosForDate: StateFlow<List<PhotoMemo>> = _selectedDate
        .flatMapLatest { repo.getByDate(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectDate(date: String) { _selectedDate.value = date }

    /**
     * Save a photo from camera (taken picture as Uri from TakePicture contract).
     * The uri points to the temp file created by PhotoFileManager.createTempPhotoUri.
     */
    fun savePhotoFromCamera(tempFile: java.io.File, date: String, taskId: Int? = null) =
        viewModelScope.launch {
            val path = PhotoFileManager.saveResizedPhotoFromFile(getApplication(), tempFile) ?: return@launch
            repo.insert(PhotoMemo(taskId = taskId, date = date, imagePath = path))
        }

    /**
     * Save a photo selected from gallery.
     */
    fun savePhotoFromGallery(uri: Uri, date: String, taskId: Int? = null) =
        viewModelScope.launch {
            val path = PhotoFileManager.saveResizedPhoto(getApplication(), uri) ?: return@launch
            repo.insert(PhotoMemo(taskId = taskId, date = date, imagePath = path))
        }

    fun deletePhoto(photo: PhotoMemo) = viewModelScope.launch {
        repo.delete(photo)
        PhotoFileManager.deletePhoto(photo.imagePath)
    }
}
