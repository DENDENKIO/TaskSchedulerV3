package com.example.taskschedulerv3.ui.tag

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.taskschedulerv3.data.model.Tag

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagManageScreen(navController: NavController, vm: TagManageViewModel = viewModel()) {
    val allTags by vm.allTags.collectAsState()
    val expandedIds by vm.expandedIds.collectAsState()

    // Dialog state
    var showCreateDialog by remember { mutableStateOf(false) }
    var createLevel by remember { mutableStateOf(1) }
    var createParentId by remember { mutableStateOf<Int?>(null) }
    var editingTag by remember { mutableStateOf<Tag?>(null) }
    var deletingTag by remember { mutableStateOf<Tag?>(null) }

    // Create dialog
    if (showCreateDialog) {
        val levelName = when (createLevel) { 1 -> "大カテゴリ"; 2 -> "中カテゴリ"; else -> "小カテゴリ" }
        TagEditDialog(
            title = "${levelName}を追加",
            onConfirm = { name, color ->
                vm.createTag(name, color, createLevel, createParentId)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false }
        )
    }

    // Edit dialog
    editingTag?.let { tag ->
        TagEditDialog(
            title = "タグを編集",
            initialName = tag.name,
            initialColor = tag.color,
            onConfirm = { name, color ->
                vm.updateTag(tag, name, color)
                editingTag = null
            },
            onDismiss = { editingTag = null }
        )
    }

    // Delete confirmation dialog
    deletingTag?.let { tag ->
        val hasChildren = vm.hasChildren(tag.id)
        AlertDialog(
            onDismissRequest = { deletingTag = null },
            title = { Text("タグを削除") },
            text = {
                Text(
                    if (hasChildren)
                        "「${tag.name}」と配下のタグをすべて削除します。この操作は取り消せません。"
                    else
                        "「${tag.name}」を削除します。この操作は取り消せません。"
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.deleteTagRecursive(tag); deletingTag = null }) {
                    Text("削除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingTag = null }) { Text("キャンセル") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("タグ管理") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        createLevel = 1; createParentId = null; showCreateDialog = true
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "大カテゴリを追加")
                    }
                }
            )
        }
    ) { padding ->
        if (allTags.isEmpty()) {
            Box(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("タグがありません", style = MaterialTheme.typography.bodyLarge)
                    TextButton(onClick = { createLevel = 1; createParentId = null; showCreateDialog = true }) {
                        Text("最初のタグを作成する")
                    }
                }
            }
        } else {
            val largeTags = allTags.filter { it.level == 1 }.sortedBy { it.sortOrder }

            LazyColumn(modifier = Modifier.padding(padding)) {
                largeTags.forEach { large ->
                    item(key = "tag_${large.id}") {
                        TagTreeItem(
                            tag = large,
                            isExpanded = large.id in expandedIds,
                            hasChildren = vm.hasChildren(large.id),
                            onToggleExpand = { vm.toggleExpand(large.id) },
                            onAddChild = {
                                createLevel = 2; createParentId = large.id; showCreateDialog = true
                            },
                            onEdit = { editingTag = large },
                            onDelete = { deletingTag = large }
                        )
                        HorizontalDivider()
                    }

                    if (large.id in expandedIds) {
                        val mediumTags = allTags.filter { it.parentId == large.id }.sortedBy { it.sortOrder }

                        mediumTags.forEach { medium ->
                            item(key = "tag_${medium.id}") {
                                TagTreeItem(
                                    tag = medium,
                                    isExpanded = medium.id in expandedIds,
                                    hasChildren = vm.hasChildren(medium.id),
                                    onToggleExpand = { vm.toggleExpand(medium.id) },
                                    onAddChild = {
                                        createLevel = 3; createParentId = medium.id; showCreateDialog = true
                                    },
                                    onEdit = { editingTag = medium },
                                    onDelete = { deletingTag = medium }
                                )
                                HorizontalDivider()
                            }

                            if (medium.id in expandedIds) {
                                val smallTags = allTags.filter { it.parentId == medium.id }.sortedBy { it.sortOrder }
                                smallTags.forEach { small ->
                                    item(key = "tag_${small.id}") {
                                        TagTreeItem(
                                            tag = small,
                                            isExpanded = false,
                                            hasChildren = false,
                                            onToggleExpand = {},
                                            onAddChild = {},
                                            onEdit = { editingTag = small },
                                            onDelete = { deletingTag = small }
                                        )
                                        HorizontalDivider()
                                    }
                                }
                                // Add small tag button
                                item(key = "add_small_${medium.id}") {
                                    AddTagButton(label = "+ 小カテゴリを追加", indentLevel = 3) {
                                        createLevel = 3; createParentId = medium.id; showCreateDialog = true
                                    }
                                }
                            }
                        }
                        // Add medium tag button
                        item(key = "add_medium_${large.id}") {
                            AddTagButton(label = "+ 中カテゴリを追加", indentLevel = 2) {
                                createLevel = 2; createParentId = large.id; showCreateDialog = true
                            }
                        }
                    }
                }
                // Add large tag button at bottom
                item(key = "add_large") {
                    AddTagButton(label = "+ 大カテゴリを追加", indentLevel = 1) {
                        createLevel = 1; createParentId = null; showCreateDialog = true
                    }
                }
            }
        }
    }
}
