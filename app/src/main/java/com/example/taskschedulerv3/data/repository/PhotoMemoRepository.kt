package com.example.taskschedulerv3.data.repository

import com.example.taskschedulerv3.data.db.PhotoMemoDao
import com.example.taskschedulerv3.data.model.PhotoMemo
import kotlinx.coroutines.flow.Flow

class PhotoMemoRepository(private val dao: PhotoMemoDao) {
    fun getAll(): Flow<List<PhotoMemo>> = dao.getAll()
    fun getByDate(date: String): Flow<List<PhotoMemo>> = dao.getByDate(date)
    fun getByTaskId(taskId: Int): Flow<List<PhotoMemo>> = dao.getByTaskId(taskId)
    fun getPhotosForTask(taskId: Int): Flow<List<PhotoMemo>> = dao.getPhotosForTask(taskId)
    fun getByYearMonth(yearMonth: String): Flow<List<PhotoMemo>> = dao.getByYearMonth(yearMonth)
    fun getDistinctMonths(): Flow<List<String>> = dao.getDistinctMonths()
    suspend fun getById(id: Int): PhotoMemo? = dao.getById(id)
    suspend fun insert(photoMemo: PhotoMemo): Long = dao.insert(photoMemo)
    suspend fun delete(photoMemo: PhotoMemo) = dao.delete(photoMemo)
    suspend fun updateOcrText(photoId: Int, ocrText: String) = dao.updateOcrText(photoId, ocrText)
}
