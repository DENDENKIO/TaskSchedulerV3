package com.example.taskschedulerv3.data.db

import androidx.room.*
import com.example.taskschedulerv3.data.model.PhotoTagCrossRef
import com.example.taskschedulerv3.data.model.Tag
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoTagCrossRefDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(ref: PhotoTagCrossRef)

    @Delete
    suspend fun delete(ref: PhotoTagCrossRef)

    @Query("DELETE FROM photo_tag_cross_ref WHERE photoId = :photoId")
    suspend fun deleteByPhotoId(photoId: Int)

    @Query("""
        SELECT tags.* FROM tags
        INNER JOIN photo_tag_cross_ref ON tags.id = photo_tag_cross_ref.tagId
        WHERE photo_tag_cross_ref.photoId = :photoId
    """)
    fun getTagsByPhotoId(photoId: Int): Flow<List<Tag>>

    @Query("""
        SELECT photo_memos.id FROM photo_memos
        INNER JOIN photo_tag_cross_ref ON photo_memos.id = photo_tag_cross_ref.photoId
        WHERE photo_tag_cross_ref.tagId IN (:tagIds)
    """)
    fun getPhotoIdsByTagIds(tagIds: List<Int>): Flow<List<Int>>

    @Query("SELECT * FROM photo_tag_cross_ref WHERE photoId = :photoId")
    suspend fun getRefsByPhotoId(photoId: Int): List<PhotoTagCrossRef>
}
