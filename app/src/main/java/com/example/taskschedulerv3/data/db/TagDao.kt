package com.example.taskschedulerv3.data.db

import androidx.room.*
import com.example.taskschedulerv3.data.model.Tag
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tag: Tag): Long

    @Update
    suspend fun update(tag: Tag)

    @Delete
    suspend fun delete(tag: Tag)

    @Query("SELECT * FROM tags ORDER BY sortOrder ASC")
    fun getAll(): Flow<List<Tag>>

    @Query("SELECT * FROM tags WHERE level = :level ORDER BY sortOrder ASC")
    fun getByLevel(level: Int): Flow<List<Tag>>

    @Query("SELECT * FROM tags WHERE parentId = :parentId ORDER BY sortOrder ASC")
    fun getByParentId(parentId: Int): Flow<List<Tag>>

    @Query("SELECT * FROM tags WHERE id = :id")
    suspend fun getById(id: Int): Tag?

    // フィルタ用 ソート済み全件
    @Query("SELECT * FROM tags ORDER BY sortOrder ASC, name ASC")
    fun getAllForFilter(): Flow<List<Tag>>

    // AIチャット用: タグ名でキーワード検索（同期）
    @Query("SELECT * FROM tags WHERE name LIKE '%' || :keyword || '%'")
    suspend fun searchByNameSync(keyword: String): List<Tag>

    // AIチャット用: 全タグ取得（同期）
    @Query("SELECT * FROM tags ORDER BY sortOrder ASC")
    suspend fun getAllSync(): List<Tag>
}
