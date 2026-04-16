package com.example.taskschedulerv3.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.taskschedulerv3.ui.schedulelist.ScheduleListScreenHighDensity
import com.example.taskschedulerv3.ui.photo.PhotoListScreen
import com.example.taskschedulerv3.ui.photo.PhotoDetailScreen
import com.example.taskschedulerv3.ui.settings.SettingsScreen
import com.example.taskschedulerv3.ui.taskdetail.TaskDetailScreen
import com.example.taskschedulerv3.ui.trash.TrashScreen
import com.example.taskschedulerv3.ui.tag.TagManageScreen
import com.example.taskschedulerv3.ui.recurring.RecurringScreen
import com.example.taskschedulerv3.ui.relation.RelatedTasksScreen
import com.example.taskschedulerv3.ui.indefinite.IndefiniteTaskScreen
import com.example.taskschedulerv3.ui.quickdraft.QuickDraftListScreen
import com.example.taskschedulerv3.ui.quickdraft.QuickDraftEditScreen
import com.example.taskschedulerv3.ui.TaskFlowUiViewModel


sealed class Screen(val route: String) {
    // ★ 追加: 高密度行表示バージョン
    object ScheduleListV2 : Screen("schedule_list_v2")
    object Photo : Screen("photo")
    object Settings : Screen("settings")
    object TaskDetail : Screen("task_detail/{taskId}") {
        fun createRoute(taskId: Int) = "task_detail/$taskId"
    }
    object Trash : Screen("trash")
    object Recurring : Screen("recurring")
    object TagManage : Screen("tag_manage")
    object PhotoDetail : Screen("photo_detail/{photoId}") {
        fun createRoute(photoId: Int) = "photo_detail/$photoId"
    }
    object RelatedTasks : Screen("related_tasks/{taskId}") {
        fun createRoute(taskId: Int) = "related_tasks/$taskId"
    }
    object IndefiniteTask : Screen("indefinite_task")
    object QuickDraftList : Screen("quick_draft_list")
    object QuickDraftEdit : Screen("quick_draft_edit/{draftId}") {
        fun createRoute(draftId: Int) = "quick_draft_edit/$draftId"
    }

}

@Composable
fun AppNavGraph(
    navController: NavHostController, 
    uiVm: TaskFlowUiViewModel,
    modifier: Modifier = Modifier
) {
    NavHost(navController = navController, startDestination = Screen.ScheduleListV2.route, modifier = modifier) {
        // ★ 一覧画面をスタート画面に変更
        composable(Screen.ScheduleListV2.route) {
            ScheduleListScreenHighDensity(
                navController = navController,
                onAddTask = { uiVm.openAddTask() }
            )
        }
        composable(Screen.Photo.route) {
            PhotoListScreen(navController = navController)
        }
        composable(Screen.Settings.route) {
            SettingsScreen(navController = navController)
        }
        composable("task_detail/{taskId}") { backStack ->
            val taskId = backStack.arguments?.getString("taskId")?.toIntOrNull() ?: return@composable
            TaskDetailScreen(
                navController = navController, 
                taskId = taskId,
                onEditRequest = { uiVm.openEditTask(taskId) }
            )
        }
        composable(Screen.Trash.route) {
            TrashScreen(navController = navController)
        }
        composable(Screen.Recurring.route) {
            RecurringScreen(
                navController = navController,
                onAddTask = { uiVm.openAddTask() },
                onEditRequest = { taskId -> uiVm.openEditTask(taskId) }
            )
        }
        composable(Screen.TagManage.route) {
            TagManageScreen(navController = navController)
        }
        composable("photo_detail/{photoId}") { backStack ->
            val photoId = backStack.arguments?.getString("photoId")?.toIntOrNull() ?: return@composable
            PhotoDetailScreen(navController = navController, photoId = photoId)
        }
        composable("related_tasks/{taskId}") { backStack ->
            val relTaskId = backStack.arguments?.getString("taskId")?.toIntOrNull() ?: return@composable
            RelatedTasksScreen(navController = navController, taskId = relTaskId)
        }
        composable(Screen.IndefiniteTask.route) {
            IndefiniteTaskScreen(
                navController = navController,
                onEditRequest = { taskId -> uiVm.openEditTask(taskId) }
            )
        }
        composable(Screen.QuickDraftList.route) {
            QuickDraftListScreen(navController = navController)
        }
        composable("quick_draft_edit/{draftId}") { backStack ->
            val draftId = backStack.arguments?.getString("draftId")?.toIntOrNull() ?: return@composable
            QuickDraftEditScreen(navController = navController, draftId = draftId)
        }
    }
}
