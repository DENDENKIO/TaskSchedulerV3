package com.example.taskschedulerv3.data.repository

import com.example.taskschedulerv3.data.db.TagDao
import com.example.taskschedulerv3.data.model.Tag
import kotlinx.coroutines.flow.Flow

class TagRepository(private val dao: TagDao) {
    fun getAll(): Flow<List<Tag>> = dao.getAll()
    fun getByLevel(level: Int): Flow<List<Tag>> = dao.getByLevel(level)
    fun getByParentId(parentId: Int): Flow<List<Tag>> = dao.getByParentId(parentId)
    suspend fun getById(id: Int): Tag? = dao.getById(id)
    suspend fun insert(tag: Tag): Long = dao.insert(tag)
    suspend fun update(tag: Tag) = dao.update(tag)
    suspend fun delete(tag: Tag) = dao.delete(tag)
}
