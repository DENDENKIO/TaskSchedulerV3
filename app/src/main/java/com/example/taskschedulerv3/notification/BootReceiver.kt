package com.example.taskschedulerv3.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.taskschedulerv3.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                // Get all non-deleted tasks with notification enabled
                val tasks = db.taskDao().getAll().first()
                tasks.filter { it.notifyEnabled && !it.isDeleted }.forEach { task ->
                    AlarmScheduler.scheduleForTask(context, task)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
