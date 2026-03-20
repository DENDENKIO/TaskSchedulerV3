package com.example.taskschedulerv3.data.repository

import com.example.taskschedulerv3.data.db.PhotoMemoDao
import com.example.taskschedulerv3.data.model.PhotoMemo
import kotlinx.coroutines.flow.Flow

class PhotoMemoRepository(private val dao: PhotoMemoDao) {
    fun getByDate(date: String): Flow<List<PhotoMemo>> = dao.getByDate(date)
    fun getByTaskId(taskId: Int): Flow<List<PhotoMemo>> = dao.getByTaskId(taskId)
    suspend fun insert(photoMemo: PhotoMemo): Long = dao.insert(photoMemo)
    suspend fun delete(photoMemo: PhotoMemo) = dao.delete(photoMemo)
}
