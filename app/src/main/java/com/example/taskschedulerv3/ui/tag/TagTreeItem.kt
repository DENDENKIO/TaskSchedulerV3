package com.example.taskschedulerv3.ui.tag

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.taskschedulerv3.data.model.Tag

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TagTreeItem(
    tag: Tag,
    isExpanded: Boolean,
    hasChildren: Boolean,
    onToggleExpand: () -> Unit,
    onAddChild: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val indentDp = ((tag.level - 1) * 20).dp
    var showContextMenu by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { if (hasChildren || tag.level < 3) onToggleExpand() },
                    onLongClick = { showContextMenu = true }
                )
                .padding(start = 16.dp + indentDp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Color dot
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(parseColor(tag.color))
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = tag.name,
                style = when (tag.level) {
                    1 -> MaterialTheme.typography.bodyLarge
                    2 -> MaterialTheme.typography.bodyMedium
                    else -> MaterialTheme.typography.bodySmall
                },
                modifier = Modifier.weight(1f)
            )
            // Expand icon
            if (hasChildren) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Context menu
        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("編集") },
                onClick = { showContextMenu = false; onEdit() }
            )
            DropdownMenuItem(
                text = { Text("削除", color = MaterialTheme.colorScheme.error) },
                onClick = { showContextMenu = false; onDelete() }
            )
        }
    }
}

@Composable
fun AddTagButton(label: String, indentLevel: Int, onClick: () -> Unit) {
    val indentDp = ((indentLevel - 1) * 20).dp
    TextButton(
        onClick = onClick,
        modifier = Modifier.padding(start = 12.dp + indentDp)
    ) {
        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}
