package com.example.taskschedulerv3.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.taskschedulerv3.data.db.AppDatabase
import com.example.taskschedulerv3.data.model.ScheduleType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getIntExtra("taskId", -1)
        if (taskId == -1) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                val task = db.taskDao().getById(taskId)

                if (task != null && !task.isDeleted) {
                    val minutesBefore = task.notifyMinutesBefore
                    val message = if (minutesBefore > 0) "${task.title}の${minutesBefore}分前です"
                                  else "${task.title}の時間です"
                    NotificationHelper.showNotification(context, taskId, task.title, message)

                    // For RECURRING tasks: schedule the NEXT occurrence after TODAY
                    if (task.scheduleType == ScheduleType.RECURRING && task.notifyEnabled) {
                        // Pass today so nextOccurrenceAfter returns the next date AFTER today
                        val nextMillis = AlarmScheduler.calculateNextTriggerMillisAfter(
                            task, java.time.LocalDate.now()
                        )
                        if (nextMillis != null) {
                            AlarmScheduler.scheduleRaw(context, task, nextMillis)
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
