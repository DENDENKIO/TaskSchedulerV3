package com.example.taskschedulerv3.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.taskschedulerv3.MainActivity
import com.example.taskschedulerv3.R

object NotificationHelper {
    const val CHANNEL_ID = "task_reminder"
    const val CHANNEL_NAME = "タスクリマインダー"

    const val DRAFT_CHANNEL_ID = "quick_draft_batch"
    const val DRAFT_CHANNEL_NAME = "仮登録バッチ処理"

    private const val DRAFT_BATCH_NOTIFICATION_ID = 99999

    fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)

        // 既存: タスクリマインダー
        val taskChannel = NotificationChannel(
            CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH
        ).apply { description = "タスクリマインダーの通知チャンネル" }
        manager.createNotificationChannel(taskChannel)

        // 新規: 仮登録バッチ処理
        val draftChannel = NotificationChannel(
            DRAFT_CHANNEL_ID, DRAFT_CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "仮登録の一括処理完了通知" }
        manager.createNotificationChannel(draftChannel)
    }

    fun showNotification(context: Context, taskId: Int, title: String, message: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("taskId", taskId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, taskId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val manager = context.getSystemService(NotificationManager::class.java)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        manager.notify(taskId, notification)
    }

    /**
     * 仮登録バッチ処理の進捗通知（処理中）。
     * ongoing = true でスワイプ削除不可。
     */
    fun showDraftBatchProgress(context: Context, current: Int, total: Int) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val notification = NotificationCompat.Builder(context, DRAFT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("仮登録を処理中...")
            .setContentText("$current / $total 件完了")
            .setProgress(total, current, false)
            .setOngoing(true)
            .setSilent(true)
            .build()
        manager.notify(DRAFT_BATCH_NOTIFICATION_ID, notification)
    }

    /**
     * 仮登録バッチ処理完了通知。
     * タップすると仮登録管理画面を開く。
     */
    fun showDraftBatchComplete(context: Context, successCount: Int, failCount: Int) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            action = "com.example.taskschedulerv3.ACTION_VIEW_QUICK_DRAFTS"
        }
        val pendingIntent = PendingIntent.getActivity(
            context, DRAFT_BATCH_NOTIFICATION_ID, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val message = if (failCount == 0) {
            "${successCount}件の仮登録が完了しました"
        } else {
            "${successCount}件完了、${failCount}件は文字認識できず日時タイトルで保存しました"
        }

        val manager = context.getSystemService(NotificationManager::class.java)
        val notification = NotificationCompat.Builder(context, DRAFT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("仮登録完了")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        manager.notify(DRAFT_BATCH_NOTIFICATION_ID, notification)
    }
}
