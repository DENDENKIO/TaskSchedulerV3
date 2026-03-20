package com.example.taskschedulerv3.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getIntExtra("taskId", -1)
        val title = intent.getStringExtra("title") ?: "タスクリマインダー"
        val message = intent.getStringExtra("message") ?: "予定の時間です"
        if (taskId != -1) {
            NotificationHelper.showNotification(context, taskId, title, message)
        }
    }
}
