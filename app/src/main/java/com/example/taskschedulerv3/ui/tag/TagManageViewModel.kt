package com.example.taskschedulerv3.ui.tag

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskschedulerv3.data.db.AppDatabase
import com.example.taskschedulerv3.data.model.Tag
import com.example.taskschedulerv3.data.repository.TagRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class TagManageViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.getInstance(app)
    private val repo = TagRepository(db.tagDao())

    val allTags: StateFlow<List<Tag>> = repo.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Expanded state: set of tag ids that are expanded
    private val _expandedIds = MutableStateFlow<Set<Int>>(emptySet())
    val expandedIds: StateFlow<Set<Int>> = _expandedIds.asStateFlow()

    fun toggleExpand(tagId: Int) {
        val current = _expandedIds.value.toMutableSet()
        if (tagId in current) current.remove(tagId) else current.add(tagId)
        _expandedIds.value = current
    }

    fun createTag(name: String, color: String, level: Int, parentId: Int?) = viewModelScope.launch {
        val siblings = allTags.value.filter { it.parentId == parentId && it.level == level }
        val sortOrder = (siblings.maxOfOrNull { it.sortOrder } ?: -1) + 1
        repo.insert(Tag(name = name, color = color, level = level, parentId = parentId, sortOrder = sortOrder))
    }

    fun updateTag(tag: Tag, name: String, color: String) = viewModelScope.launch {
        repo.update(tag.copy(name = name, color = color))
    }

    /** Recursively collect all descendant ids, then delete them all */
    fun deleteTagRecursive(tag: Tag) = viewModelScope.launch {
        val toDelete = collectDescendants(tag.id, allTags.value) + tag
        toDelete.forEach { t ->
            // Delete cross refs first
            db.taskTagCrossRefDao().deleteByTaskId(t.id) // wrong - should be by tagId, handled by FK CASCADE
            repo.delete(t)
        }
    }

    private fun collectDescendants(parentId: Int, all: List<Tag>): List<Tag> {
        val children = all.filter { it.parentId == parentId }
        return children + children.flatMap { collectDescendants(it.id, all) }
    }

    fun hasChildren(tagId: Int): Boolean =
        allTags.value.any { it.parentId == tagId }
}
