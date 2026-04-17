package com.example.taskschedulerv3.ui.quickdraft

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.taskschedulerv3.data.db.AppDatabase
import com.example.taskschedulerv3.data.model.QuickDraftTask
import com.example.taskschedulerv3.util.PhotoFileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class QuickCameraActivity : ComponentActivity() {

    private var tempCameraFile: File? = null
    private var selectedTagIds: String = ""

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            tempCameraFile?.let { file ->
                val path = PhotoFileManager.saveResizedPhotoFromFile(this, file)
                saveDraft(path)
            } ?: finish()
        } else {
            finish()
        }
    }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            launchCamera()
        } else {
            Toast.makeText(this, "カメラ権限が必要です", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Windowを透明にして背景を見せる
        window.setBackgroundDrawableResource(android.R.color.transparent)

        // インテントから直接渡されたタグIDを取得（優先）
        val intentTagId = intent.getIntExtra("SELECTED_TAG_ID", -1)
        if (intentTagId != -1) {
            selectedTagIds = intentTagId.toString()
        } else {
            // 後方互換/フォールバック: SharedPreferences
            val prefs = getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
            val selectedTagsSet = prefs.getStringSet("selected_tag_ids", emptySet()) ?: emptySet()
            selectedTagIds = selectedTagsSet.joinToString(",")
        }

        val hasPerm = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (hasPerm) {
            launchCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchCamera() {
        val (uri, file) = PhotoFileManager.createTempPhotoUri(this)
        tempCameraFile = file
        cameraLauncher.launch(uri)
    }

    private fun saveDraft(photoPath: String?) {
        val appWidgetId = intent.getIntExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID)
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.getInstance(this@QuickCameraActivity)
                val now = LocalDateTime.now()
                val titleStr = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) + " 仮登録"
                
                val task = QuickDraftTask(
                    title = titleStr,
                    photoPath = photoPath,
                    createdAt = System.currentTimeMillis(),
                    status = "DRAFT",
                    tagIds = selectedTagIds
                )
                db.quickDraftTaskDao().insert(task)
                
                // タグの選択状態をリセットする (撮影に使用したウィジェットのみ)
                val prefs = getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
                if (appWidgetId != android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID) {
                    prefs.edit().remove("selected_tag_id_$appWidgetId").apply()
                } else {
                    prefs.edit().clear().apply() // フォールバック
                }
                
                // 全ウィジェットの表示を即時更新
                val updateIntent = android.content.Intent(this@QuickCameraActivity, com.example.taskschedulerv3.widget.QuickPhotoWidgetProvider::class.java)
                updateIntent.action = android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE
                val ids = android.appwidget.AppWidgetManager.getInstance(this@QuickCameraActivity)
                    .getAppWidgetIds(android.content.ComponentName(this@QuickCameraActivity, com.example.taskschedulerv3.widget.QuickPhotoWidgetProvider::class.java))
                updateIntent.putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                sendBroadcast(updateIntent)

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                launch(Dispatchers.Main) {
                    Toast.makeText(this@QuickCameraActivity, "仮登録しました", Toast.LENGTH_SHORT).show()
                    
                    // 保存後に仮登録管理画面へ遷移
                    val mainIntent = android.content.Intent(this@QuickCameraActivity, com.example.taskschedulerv3.MainActivity::class.java).apply {
                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                        action = "com.example.taskschedulerv3.ACTION_VIEW_QUICK_DRAFTS"
                    }
                    startActivity(mainIntent)
                    finish()
                }
            }
        }
    }
}
