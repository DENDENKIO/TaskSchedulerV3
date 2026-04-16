package com.example.taskschedulerv3.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.taskschedulerv3.data.model.Tag

@Composable
fun TagFilterRow(
    tags: List<Tag>,
    selectedTagId: Int?,
    onTagSelected: (Int?) -> Unit
) {
    LazyRow(modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)) {
        item {
            FilterChip(
                selected = selectedTagId == null,
                onClick = { onTagSelected(null) },
                label = { Text("すべて", fontSize = 11.sp) },
                modifier = Modifier.padding(end = 4.dp)
            )
        }
        items(tags, key = { it.id }) { tag ->
            FilterChip(
                selected = selectedTagId == tag.id,
                onClick = { onTagSelected(if (selectedTagId == tag.id) null else tag.id) },
                label = { Text(tag.name, fontSize = 11.sp) },
                modifier = Modifier.padding(end = 4.dp)
            )
        }
    }
}
