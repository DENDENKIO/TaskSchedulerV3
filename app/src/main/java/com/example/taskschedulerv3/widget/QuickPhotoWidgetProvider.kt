package com.example.taskschedulerv3.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.example.taskschedulerv3.R
import com.example.taskschedulerv3.ui.quickdraft.QuickCameraActivity

class QuickPhotoWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == "com.example.taskschedulerv3.ACTION_WIDGET_TAG_CLICK") {
            val tagId = intent.getStringExtra("TAG_ID") ?: return
            
            val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
            val selectedTags = prefs.getStringSet("selected_tag_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
            
            if (selectedTags.contains(tagId)) {
                selectedTags.remove(tagId)
            } else {
                selectedTags.add(tagId)
            }
            
            prefs.edit().putStringSet("selected_tag_ids", selectedTags).apply()
            
            // GridViewのデータを更新
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, QuickPhotoWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widget_tag_grid)
        }
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_quick_photo)

        // タグ一覧を表示するための RemoteAdapter の設定
        val serviceIntent = Intent(context, WidgetTagService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
        }
        views.setRemoteAdapter(R.id.widget_tag_grid, serviceIntent)

        // タップしたタグのIDを受け取って onReceive で処理するためのテンプレート
        val tagClickIntent = Intent(context, QuickPhotoWidgetProvider::class.java).apply {
            action = "com.example.taskschedulerv3.ACTION_WIDGET_TAG_CLICK"
        }
        val tagClickPendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            tagClickIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        views.setPendingIntentTemplate(R.id.widget_tag_grid, tagClickPendingIntent)

        // カメラボタン: 透明な QuickCameraActivity を起動
        val cameraIntent = Intent(context, QuickCameraActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val cameraPendingIntent = PendingIntent.getActivity(
            context,
            appWidgetId, // ユニークなリクエストコード
            cameraIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_camera_btn, cameraPendingIntent)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
