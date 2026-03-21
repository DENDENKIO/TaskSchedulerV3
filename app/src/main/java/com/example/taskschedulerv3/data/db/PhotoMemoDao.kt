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

    @Query("SELECT * FROM photo_memos ORDER BY createdAt DESC")
    fun getAll(): Flow<List<PhotoMemo>>

    @Query("SELECT * FROM photo_memos WHERE date = :date ORDER BY createdAt DESC")
    fun getByDate(date: String): Flow<List<PhotoMemo>>

    @Query("SELECT * FROM photo_memos WHERE taskId = :taskId ORDER BY createdAt DESC")
    fun getByTaskId(taskId: Int): Flow<List<PhotoMemo>>

    @Query("SELECT * FROM photo_memos WHERE strftime('%Y-%m', date) = :yearMonth ORDER BY date ASC, createdAt ASC")
    fun getByYearMonth(yearMonth: String): Flow<List<PhotoMemo>>

    @Query("SELECT DISTINCT strftime('%Y-%m', date) as month FROM photo_memos ORDER BY month DESC")
    fun getDistinctMonths(): Flow<List<String>>

    @Query("SELECT * FROM photo_memos WHERE id = :id")
    suspend fun getById(id: Int): PhotoMemo?

    @Update
    suspend fun update(photoMemo: PhotoMemo)

    /** Search by title or memo text */
    @Query("SELECT * FROM photo_memos WHERE title LIKE '%' || :query || '%' OR memo LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    fun search(query: String): Flow<List<PhotoMemo>>

    /** Get photos that have any of the given tag ids (via photo_tag_cross_ref) */
    @Query("""
        SELECT DISTINCT photo_memos.* FROM photo_memos
        INNER JOIN photo_tag_cross_ref ON photo_memos.id = photo_tag_cross_ref.photoId
        WHERE photo_tag_cross_ref.tagId IN (:tagIds)
        ORDER BY photo_memos.createdAt DESC
    """)
    fun getByTagIds(tagIds: List<Int>): Flow<List<PhotoMemo>>
}
