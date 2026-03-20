package com.example.taskschedulerv3.ui.relation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.taskschedulerv3.data.model.ScheduleType
import com.example.taskschedulerv3.navigation.Screen
import com.example.taskschedulerv3.ui.taskdetail.RelatedTaskRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelatedTasksScreen(
    navController: NavController,
    taskId: Int,
    vm: RelatedTasksViewModel = viewModel()
) {
    val originTask by vm.originTask.collectAsState()
    val relatedTasks by vm.relatedTasks.collectAsState()

    LaunchedEffect(taskId) { vm.loadForTask(taskId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("関連予定") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Origin task card
            originTask?.let { origin ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "起点タスク",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(origin.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "${origin.startDate}${origin.startTime?.let { " $it" } ?: ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                            val typeLabel = when (origin.scheduleType) {
                                ScheduleType.PERIOD -> "期間"
                                ScheduleType.RECURRING -> "繰り返し"
                                else -> "通常"
                            }
                            Text(
                                typeLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                item {
                    Text(
                        "関連する予定 (${relatedTasks.size}件)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            if (relatedTasks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "関連予定がありません",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            } else {
                items(relatedTasks) { task ->
                    RelatedTaskRow(
                        task = task,
                        onClick = { navController.navigate(Screen.TaskDetail.createRoute(task.id)) }
                    )
                }
            }
        }
    }
}
