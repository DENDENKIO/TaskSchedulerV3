package com.example.taskschedulerv3.data.db

import androidx.room.*
import com.example.taskschedulerv3.data.model.PhotoMemo
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoMemoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(photoMemo: PhotoMemo): Long

    @Delete
    suspend fun delete(photoMemo: PhotoMemo)

    @Query("SELECT * FROM photo_memos WHERE date = :date")
    fun getByDate(date: String): Flow<List<PhotoMemo>>

    @Query("SELECT * FROM photo_memos WHERE taskId = :taskId")
    fun getByTaskId(taskId: Int): Flow<List<PhotoMemo>>
}
