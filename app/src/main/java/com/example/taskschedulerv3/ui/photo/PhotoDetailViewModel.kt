package com.example.taskschedulerv3.ui.photo

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskschedulerv3.data.db.AppDatabase
import com.example.taskschedulerv3.data.model.PhotoMemo
import com.example.taskschedulerv3.data.repository.PhotoMemoRepository
import com.example.taskschedulerv3.util.PhotoFileManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PhotoDetailViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = PhotoMemoRepository(AppDatabase.getInstance(app).photoMemoDao())

    private val _photo = MutableStateFlow<PhotoMemo?>(null)
    val photo: StateFlow<PhotoMemo?> = _photo.asStateFlow()

    fun loadPhoto(id: Int) = viewModelScope.launch {
        _photo.value = repo.getById(id)
    }

    fun deletePhoto(photo: PhotoMemo) = viewModelScope.launch {
        repo.delete(photo)
        PhotoFileManager.deletePhoto(photo.imagePath)
    }
}
