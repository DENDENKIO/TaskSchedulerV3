package com.example.taskschedulerv3.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.taskschedulerv3.data.model.Tag
import com.example.taskschedulerv3.ui.tag.parseColor

/**
 * Represents a selected tag path: the final deepest tag chosen.
 * E.g., selectedTag = "A社案件" (level=3), path = ["仕事", "営業部", "A社案件"]
 */
data class TagSelection(val tag: Tag, val pathLabels: List<String>)

@Composable
fun TagSelector(
    allTags: List<Tag>,
    selectedTagIds: Set<Int>,
    onTagsChanged: (Set<Int>) -> Unit,
    onNavigateToTagManage: () -> Unit
) {
    // Currently selected tag ids (deepest level chosen)
    val mutableSelected = remember(selectedTagIds) { selectedTagIds.toMutableSet() }

    // UI selection state for cascading
    var pickedLarge by remember { mutableStateOf<Tag?>(null) }
    var pickedMedium by remember { mutableStateOf<Tag?>(null) }
    var isAdding by remember { mutableStateOf(false) }

    val largeTags = allTags.filter { it.level == 1 }.sortedBy { it.sortOrder }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Header row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("タグ", style = MaterialTheme.typography.labelLarge)
            TextButton(onClick = onNavigateToTagManage) { Text("タグを管理", style = MaterialTheme.typography.labelSmall) }
        }

        // Selected badges
        val selectedTags = allTags.filter { it.id in selectedTagIds }
        if (selectedTags.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(selectedTags) { tag ->
                    val path = buildPath(tag, allTags)
                    InputChip(
                        selected = true,
                        onClick = {},
                        label = { Text(path, style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = {
                            Box(
                                modifier = Modifier.size(10.dp).clip(CircleShape)
                                    .background(parseColor(tag.color))
                            )
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    val newSet = selectedTagIds.toMutableSet().also { it.remove(tag.id) }
                                    onTagsChanged(newSet)
                                },
                                modifier = Modifier.size(18.dp)
                            ) {
                                Icon(Icons.Default.Close, null, modifier = Modifier.size(12.dp))
                            }
                        }
                    )
                }
            }
        }

        if (largeTags.isEmpty()) {
            Text(
                "タグがありません。「タグを管理」から作成してください。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            return@Column
        }

        // Cascade selector (shown when adding)
        if (!isAdding) {
            TextButton(onClick = { isAdding = true; pickedLarge = null; pickedMedium = null }) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("タグを追加")
            }
        } else {
            // Level 1: large categories
            Text("大カテゴリ", style = MaterialTheme.typography.labelSmall)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(largeTags) { tag ->
                    FilterChip(
                        selected = pickedLarge?.id == tag.id,
                        onClick = {
                            if (pickedLarge?.id == tag.id) {
                                pickedLarge = null; pickedMedium = null
                            } else {
                                pickedLarge = tag; pickedMedium = null
                                // If no children → confirm selection
                                val medChildren = allTags.filter { it.parentId == tag.id }
                                if (medChildren.isEmpty()) {
                                    val newSet = selectedTagIds.toMutableSet().also { it.add(tag.id) }
                                    onTagsChanged(newSet)
                                    isAdding = false; pickedLarge = null
                                }
                            }
                        },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(parseColor(tag.color)))
                                Spacer(Modifier.width(4.dp))
                                Text(tag.name)
                            }
                        }
                    )
                }
            }

            // Level 2: medium categories
            pickedLarge?.let { large ->
                val mediumTags = allTags.filter { it.parentId == large.id }.sortedBy { it.sortOrder }
                if (mediumTags.isNotEmpty()) {
                    Text("中カテゴリ", style = MaterialTheme.typography.labelSmall)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(mediumTags) { tag ->
                            FilterChip(
                                selected = pickedMedium?.id == tag.id,
                                onClick = {
                                    if (pickedMedium?.id == tag.id) {
                                        pickedMedium = null
                                    } else {
                                        pickedMedium = tag
                                        // If no children → confirm
                                        val smallChildren = allTags.filter { it.parentId == tag.id }
                                        if (smallChildren.isEmpty()) {
                                            val newSet = selectedTagIds.toMutableSet().also { it.add(tag.id) }
                                            onTagsChanged(newSet)
                                            isAdding = false; pickedLarge = null; pickedMedium = null
                                        }
                                    }
                                },
                                label = { Text(tag.name) }
                            )
                        }
                    }
                }

                // Level 3: small categories
                pickedMedium?.let { medium ->
                    val smallTags = allTags.filter { it.parentId == medium.id }.sortedBy { it.sortOrder }
                    if (smallTags.isNotEmpty()) {
                        Text("小カテゴリ", style = MaterialTheme.typography.labelSmall)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(smallTags) { tag ->
                                FilterChip(
                                    selected = false,
                                    onClick = {
                                        val newSet = selectedTagIds.toMutableSet().also { it.add(tag.id) }
                                        onTagsChanged(newSet)
                                        isAdding = false; pickedLarge = null; pickedMedium = null
                                    },
                                    label = { Text(tag.name) }
                                )
                            }
                        }
                    }
                }
            }

            // Cancel button
            TextButton(onClick = { isAdding = false; pickedLarge = null; pickedMedium = null }) {
                Text("キャンセル")
            }
        }
    }
}

/** Build readable path string: "大 > 中 > 小" */
fun buildPath(tag: Tag, allTags: List<Tag>): String {
    val path = mutableListOf(tag.name)
    var current = tag
    while (current.parentId != null) {
        val parent = allTags.find { it.id == current.parentId } ?: break
        path.add(0, parent.name)
        current = parent
    }
    return path.joinToString(" > ")
}
