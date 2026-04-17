package com.example.taskschedulerv3.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.taskschedulerv3.R
import com.example.taskschedulerv3.data.db.AppDatabase
import com.example.taskschedulerv3.ui.quickdraft.QuickCameraActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class QuickPhotoWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_CYCLE_TAG = "com.example.taskschedulerv3.ACTION_CYCLE_TAG"
        private const val PREFS_NAME = "widget_prefs"
        private const val KEY_SELECTED_TAG_ID = "selected_tag_id_"
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        for (appWidgetId in appWidgetIds) {
            editor.remove(KEY_SELECTED_TAG_ID + appWidgetId)
        }
        editor.apply()
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            CoroutineScope(Dispatchers.IO).launch {
                updateAppWidget(context, appWidgetManager, appWidgetId)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_CYCLE_TAG) {
            val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return

            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getInstance(context)
                    val allTags = db.tagDao().getAll().first()
                    
                    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    val currentId = if (prefs.contains(KEY_SELECTED_TAG_ID + appWidgetId)) {
                        prefs.getInt(KEY_SELECTED_TAG_ID + appWidgetId, -1)
                    } else null

                    val nextId = when {
                        allTags.isEmpty() -> null
                        currentId == null || currentId == -1 -> allTags[0].id
                        else -> {
                            val currentIndex = allTags.indexOfFirst { it.id == currentId }
                            if (currentIndex == -1 || currentIndex == allTags.size - 1) null
                            else allTags[currentIndex + 1].id
                        }
                    }

                    if (nextId == null) {
                        prefs.edit().remove(KEY_SELECTED_TAG_ID + appWidgetId).apply()
                    } else {
                        prefs.edit().putInt(KEY_SELECTED_TAG_ID + appWidgetId, nextId).apply()
                    }

                    withContext(Dispatchers.Main) {
                        updateAppWidget(context, AppWidgetManager.getInstance(context), appWidgetId)
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    private suspend fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_quick_photo)
        
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val selectedId = if (prefs.contains(KEY_SELECTED_TAG_ID + appWidgetId)) {
            prefs.getInt(KEY_SELECTED_TAG_ID + appWidgetId, -1)
        } else null

        val tagName = if (selectedId != null && selectedId != -1) {
            AppDatabase.getInstance(context).tagDao().getById(selectedId)?.name ?: "未選択"
        } else "未選択"

        // UI更新
        views.setTextViewText(R.id.widget_selected_tag, tagName)

        // 順送りボタン (タグ表示エリア全体をクリッカブルに)
        val cycleIntent = Intent(context, QuickPhotoWidgetProvider::class.java).apply {
            action = ACTION_CYCLE_TAG
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        val cyclePendingIntent = PendingIntent.getBroadcast(
            context,
            appWidgetId,
            cycleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_tag_cycle_btn, cyclePendingIntent)

        // カメラボタン
        val cameraIntent = Intent(context, QuickCameraActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            putExtra("SELECTED_TAG_ID", selectedId ?: -1)
        }
        val cameraPendingIntent = PendingIntent.getActivity(
            context,
            appWidgetId + 1000, 
            cameraIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_camera_btn, cameraPendingIntent)

        withContext(Dispatchers.Main) {
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
