package com.example.taskschedulerv3

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.taskschedulerv3.data.db.AppDatabase
import com.example.taskschedulerv3.data.repository.TaskRepository
import com.example.taskschedulerv3.navigation.AppNavGraph
import com.example.taskschedulerv3.navigation.Screen
import com.example.taskschedulerv3.notification.NotificationHelper
import com.example.taskschedulerv3.ui.theme.AppTheme
import com.example.taskschedulerv3.util.ThemeMode
import com.example.taskschedulerv3.util.ThemePreferences
import kotlinx.coroutines.launch
import java.time.LocalDate
import com.example.taskschedulerv3.ui.addtask.AddTaskBottomSheet
import com.example.taskschedulerv3.ui.TaskFlowUiViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationHelper.createChannel(this)

        lifecycleScope.launch {
            try {
                val db = AppDatabase.getInstance(this@MainActivity)
                val repo = TaskRepository(db.taskDao())
                repo.purgeOldDeleted()
                val today = LocalDate.now().toString()
                db.taskCompletionDao().deleteOlderThan(today)
            } catch (_: Exception) {}
        }

        enableEdgeToEdge()
        setContent {
            val themeMode by ThemePreferences.getThemeMode(applicationContext)
                .collectAsState(initial = ThemeMode.SYSTEM)

            val isDark = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            AppTheme(darkTheme = isDark) {
                TaskSchedulerApp()
            }
        }
    }
}

data class BottomNavItem(val label: String, val icon: ImageVector, val route: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskSchedulerApp() {
    val navController = rememberNavController()
    val uiVm: TaskFlowUiViewModel = viewModel()

    val bottomItems = listOf(
        BottomNavItem("一覧", Icons.Default.FormatListBulleted, Screen.ScheduleListV2.route),
        BottomNavItem("設定", Icons.Default.Settings, Screen.Settings.route),
    )
    
    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStack?.destination?.route

    fun routeMatches(current: String?, item: String): Boolean {
        if (current == null) return false
        if (item == Screen.ScheduleListV2.route && current.startsWith("schedule_list")) return true
        return current == item
    }

    Scaffold(
        bottomBar = {
            val showBottomBar = bottomItems.any { routeMatches(currentRoute, it.route) }
            if (showBottomBar) {
                NavigationBar {
                    bottomItems.forEach { item ->
                        NavigationBarItem(
                            selected = routeMatches(currentRoute, item.route),
                            onClick = {
                                if (!routeMatches(currentRoute, item.route)) {
                                    navController.navigate(item.route) {
                                        popUpTo(Screen.ScheduleListV2.route) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            val showFab = currentRoute?.startsWith("schedule_list") == true
            if (showFab) {
                FloatingActionButton(
                    onClick = { uiVm.openAddTask() },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Add, "タスク追加")
                }
            }
        }
    ) { padding ->
        AppNavGraph(
            navController = navController,
            uiVm = uiVm,
            modifier = Modifier.padding(padding)
        )

        if (uiVm.showAddTaskSheet) {
            AddTaskBottomSheet(
                taskId = uiVm.editingTaskId,
                onDismiss = { uiVm.closeAddTaskSheet() }
            )
        }
    }
}
