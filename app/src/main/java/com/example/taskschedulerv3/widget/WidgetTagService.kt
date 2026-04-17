package com.example.taskschedulerv3.widget

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.example.taskschedulerv3.R
import com.example.taskschedulerv3.data.db.AppDatabase
import com.example.taskschedulerv3.data.model.Tag
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking

class WidgetTagService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return WidgetTagFactory(this.applicationContext)
    }
}

class WidgetTagFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {
    
    private var tags: List<Tag> = emptyList()
    private var selectedTagIds: Set<String> = emptySet()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        // SharedPreferencesから選択状態を取得
        val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        selectedTagIds = prefs.getStringSet("selected_tag_ids", emptySet()) ?: emptySet()

        // Room DBからタグ一覧を同期的に取得（onDataSetChangedはバックグラウンドスレッドで呼ばれるので可）
        val db = AppDatabase.getInstance(context)
        runBlocking {
            // Flowから最初のリストを取得
            tags = db.tagDao().getAll().firstOrNull() ?: emptyList()
        }
    }

    override fun onDestroy() {
        tags = emptyList()
    }

    override fun getCount(): Int = tags.size

    override fun getViewAt(position: Int): RemoteViews {
        val tag = tags.getOrNull(position) ?: return RemoteViews(context.packageName, R.layout.widget_tag_item)
        
        val isSelected = selectedTagIds.contains(tag.id.toString())
        
        val views = RemoteViews(context.packageName, R.layout.widget_tag_item)
        views.setTextViewText(R.id.widget_tag_text, tag.name)

        // 選択状態に応じて色を変更
        if (isSelected) {
            views.setInt(R.id.widget_tag_container, "setBackgroundResource", R.drawable.widget_tag_bg_selected)
            views.setTextColor(R.id.widget_tag_text, Color.WHITE)
        } else {
            views.setInt(R.id.widget_tag_container, "setBackgroundResource", R.drawable.widget_tag_bg_normal)
            views.setTextColor(R.id.widget_tag_text, Color.BLACK)
        }

        // クリックインテントを設定 (Providerで処理される)
        val fillInIntent = Intent().apply {
            putExtra("TAG_ID", tag.id.toString())
        }
        views.setOnClickFillInIntent(R.id.widget_tag_container, fillInIntent)

        return views
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = tags.getOrNull(position)?.id?.toLong() ?: position.toLong()
    override fun hasStableIds(): Boolean = true
}
