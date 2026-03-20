package com.example.taskschedulerv3.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.taskschedulerv3.ui.calendar.CalendarScreen
import com.example.taskschedulerv3.ui.schedulelist.ScheduleListScreen
import com.example.taskschedulerv3.ui.photo.PhotoListScreen
import com.example.taskschedulerv3.ui.photo.PhotoDetailScreen
import com.example.taskschedulerv3.ui.settings.SettingsScreen
import com.example.taskschedulerv3.ui.addtask.AddTaskScreen
import com.example.taskschedulerv3.ui.taskdetail.TaskDetailScreen
import com.example.taskschedulerv3.ui.trash.TrashScreen
import com.example.taskschedulerv3.ui.tag.TagManageScreen

sealed class Screen(val route: String) {
    object Calendar : Screen("calendar")
    object ScheduleList : Screen("schedule_list")
    object Photo : Screen("photo")
    object Settings : Screen("settings")
    object AddTask : Screen("add_task?date={date}") {
        fun createRoute(date: String = "") = "add_task?date=$date"
    }
    object EditTask : Screen("edit_task/{taskId}") {
        fun createRoute(taskId: Int) = "edit_task/$taskId"
    }
    object TaskDetail : Screen("task_detail/{taskId}") {
        fun createRoute(taskId: Int) = "task_detail/$taskId"
    }
    object Trash : Screen("trash")
    object TagManage : Screen("tag_manage")
    object PhotoDetail : Screen("photo_detail/{photoId}") {
        fun createRoute(photoId: Int) = "photo_detail/$photoId"
    }
}

@Composable
fun AppNavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Screen.Calendar.route) {
        composable(Screen.Calendar.route) {
            CalendarScreen(navController = navController)
        }
        composable(Screen.ScheduleList.route) {
            ScheduleListScreen(navController = navController)
        }
        composable(Screen.Photo.route) {
            PhotoListScreen(navController = navController)
        }
        composable(Screen.Settings.route) {
            SettingsScreen(navController = navController)
        }
        composable("add_task?date={date}") { backStack ->
            val date = backStack.arguments?.getString("date") ?: ""
            AddTaskScreen(navController = navController, initialDate = date)
        }
        composable("edit_task/{taskId}") { backStack ->
            val taskId = backStack.arguments?.getString("taskId")?.toIntOrNull() ?: return@composable
            AddTaskScreen(navController = navController, editTaskId = taskId)
        }
        composable("task_detail/{taskId}") { backStack ->
            val taskId = backStack.arguments?.getString("taskId")?.toIntOrNull() ?: return@composable
            TaskDetailScreen(navController = navController, taskId = taskId)
        }
        composable(Screen.Trash.route) {
            TrashScreen(navController = navController)
        }
        composable(Screen.TagManage.route) {
            TagManageScreen(navController = navController)
        }
        composable("photo_detail/{photoId}") { backStack ->
            val photoId = backStack.arguments?.getString("photoId")?.toIntOrNull() ?: return@composable
            PhotoDetailScreen(navController = navController, photoId = photoId)
        }
    }
}
