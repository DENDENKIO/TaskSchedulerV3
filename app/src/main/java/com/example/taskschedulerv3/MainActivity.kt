package com.example.taskschedulerv3

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.taskschedulerv3.navigation.AppNavGraph
import com.example.taskschedulerv3.navigation.Screen
import com.example.taskschedulerv3.notification.NotificationHelper
import com.example.taskschedulerv3.ui.theme.TaskSchedulerV3Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationHelper.createChannel(this)
        enableEdgeToEdge()
        setContent {
            TaskSchedulerV3Theme {
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
    val bottomItems = listOf(
        BottomNavItem("カレンダー", Icons.Default.CalendarMonth, Screen.Calendar.route),
        BottomNavItem("一覧", Icons.Default.List, Screen.ScheduleList.route),
        BottomNavItem("写真", Icons.Default.Photo, Screen.Photo.route),
        BottomNavItem("設定", Icons.Default.Settings, Screen.Settings.route),
    )
    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStack?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                bottomItems.forEach { item ->
                    NavigationBarItem(
                        selected = currentRoute == item.route,
                        onClick = {
                            if (currentRoute != item.route) {
                                navController.navigate(item.route) {
                                    popUpTo(Screen.Calendar.route) { saveState = true }
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
    ) { padding ->
        AppNavGraph(navController = navController)
    }
}
