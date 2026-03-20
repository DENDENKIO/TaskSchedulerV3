package com.example.taskschedulerv3.ui.trash

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(navController: NavController, vm: TrashViewModel = viewModel()) {
    val tasks by vm.deletedTasks.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ゴミ箱") },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, null) } }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            items(tasks) { task ->
                ListItem(
                    headlineContent = { Text(task.title) },
                    supportingContent = { Text("削除日: ${task.deletedAt?.let { java.util.Date(it) } ?: ""}") },
                    trailingContent = {
                        Row {
                            TextButton(onClick = { vm.restore(task) }) { Text("復元") }
                            TextButton(onClick = { vm.permanentDelete(task) }) { Text("削除") }
                        }
                    }
                )
                HorizontalDivider()
            }
        }
    }
}
