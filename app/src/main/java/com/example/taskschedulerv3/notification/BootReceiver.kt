package com.example.taskschedulerv3.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // TODO: Re-register alarms for all active tasks after reboot
            // AlarmScheduler.reScheduleAll(context)
        }
    }
}
