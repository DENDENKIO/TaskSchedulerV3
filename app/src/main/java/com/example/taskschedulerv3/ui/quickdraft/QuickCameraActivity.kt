package com.example.taskschedulerv3.ui.quickdraft

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.taskschedulerv3.data.db.AppDatabase
import com.example.taskschedulerv3.data.model.QuickDraftTask
import com.example.taskschedulerv3.notification.NotificationHelper
import com.example.taskschedulerv3.util.AiEngineManager
import com.example.taskschedulerv3.util.AiPreferences
import com.example.taskschedulerv3.util.OcrTextParser
import com.example.taskschedulerv3.util.PhotoFileManager
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * ウィジェットからの直接カメラ起動用Activity。
 * 撮影後はバックグラウンドでOCR/AI処理し、完了時に通知を送信する。
 */
class QuickCameraActivity : ComponentActivity() {

    private var tempCameraFile: File? = null
    private var selectedTagIds: String = ""

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            tempCameraFile?.let { file ->
                val path = PhotoFileManager.saveResizedPhotoFromFile(this, file)
                processDraftInBackground(path)
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

        window.setBackgroundDrawableResource(android.R.color.transparent)

        val intentTagId = intent.getIntExtra("SELECTED_TAG_ID", -1)
        if (intentTagId != -1) {
            selectedTagIds = intentTagId.toString()
        } else {
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

    /**
     * 撮影した写真をバックグラウンドで処理する。
     * AI有効時はOCR→AI解析→DB保存。無効時はフォールバックタイトルで保存。
     * 処理完了時に通知を送信する。
     */
    private fun processDraftInBackground(photoPath: String?) {
        Toast.makeText(this, "バックグラウンドで処理中...", Toast.LENGTH_SHORT).show()

        val appWidgetId = intent.getIntExtra(
            android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID,
            android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID
        )

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.getInstance(this@QuickCameraActivity)
                val aiEnabled = AiPreferences.getAiEnabled(this@QuickCameraActivity).first()

                var finalTitle = generateFallbackTitle()
                var finalOcrText: String? = null
                var aiDate: String? = null
                var aiStartTime: String? = null
                var aiEndTime: String? = null
                var aiSummary: String? = null

                if (aiEnabled && photoPath != null) {
                    // OCR
                    val file = File(photoPath)
                    if (file.exists()) {
                        val bitmap = android.graphics.BitmapFactory.decodeFile(photoPath)
                        if (bitmap != null) {
                            try {
                                val recognizer = TextRecognition.getClient(
                                    JapaneseTextRecognizerOptions.Builder().build()
                                )
                                val image = InputImage.fromBitmap(bitmap, 0)
                                val result = recognizer.process(image).await()
                                finalOcrText = result.text
                                recognizer.close()
                            } catch (_: Exception) {}
                            bitmap.recycle()
                        }
                    }

                    // AI解析
                    if (!finalOcrText.isNullOrBlank()) {
                        if (!AiEngineManager.isLoaded()) {
                            AiEngineManager.loadEngine(this@QuickCameraActivity)
                        }
                        if (AiEngineManager.isLoaded()) {
                            val jsonResult = AiEngineManager.analyze(finalOcrText!!)
                            if (!jsonResult.isNullOrBlank()) {
                                Regex("\"title\"\\s*:\\s*\"(.*?)\"").find(jsonResult)?.let {
                                    val t = it.groupValues[1].trim()
                                    if (t.isNotBlank() && t.lowercase() != "null") finalTitle = t
                                }
                                Regex("\"date\"\\s*:\\s*\"(.*?)\"").find(jsonResult)?.let {
                                    val v = it.groupValues[1].trim()
                                    if (v.isNotBlank() && v.lowercase() != "null") aiDate = v
                                }
                                Regex("\"start_time\"\\s*:\\s*\"(.*?)\"").find(jsonResult)?.let {
                                    val v = it.groupValues[1].trim()
                                    if (v.isNotBlank() && v.lowercase() != "null") aiStartTime = v
                                }
                                Regex("\"end_time\"\\s*:\\s*\"(.*?)\"").find(jsonResult)?.let {
                                    val v = it.groupValues[1].trim()
                                    if (v.isNotBlank() && v.lowercase() != "null") aiEndTime = v
                                }
                                Regex("\"summary\"\\s*:\\s*\"([\\s\\S]*?)\"").find(jsonResult)?.let {
                                    val v = it.groupValues[1].trim()
                                    if (v.isNotBlank() && v.lowercase() != "null") aiSummary = v
                                }
                            } else {
                                // AI失敗 → フォールバック
                                val fb = OcrTextParser.fallbackParseFromOcr(finalOcrText!!)
                                if (fb.title != null) finalTitle = fb.title
                                aiDate = fb.date; aiStartTime = fb.startTime
                                aiEndTime = fb.endTime; aiSummary = fb.summary
                            }
                        }
                    }
                }

                val task = QuickDraftTask(
                    title = finalTitle,
                    description = aiSummary,
                    photoPath = photoPath,
                    ocrText = finalOcrText,
                    createdAt = System.currentTimeMillis(),
                    status = "DRAFT",
                    tagIds = selectedTagIds,
                    startDate = aiDate,
                    startTime = aiStartTime,
                    endTime = aiEndTime
                )
                db.quickDraftTaskDao().insert(task)

                // ウィジェットのタグ選択状態をリセット
                val prefs = getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
                if (appWidgetId != android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID) {
                    prefs.edit().remove("selected_tag_id_$appWidgetId").apply()
                } else {
                    prefs.edit().clear().apply()
                }

                // ウィジェット更新
                val updateIntent = android.content.Intent(
                    this@QuickCameraActivity,
                    com.example.taskschedulerv3.widget.QuickPhotoWidgetProvider::class.java
                )
                updateIntent.action = android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE
                val ids = android.appwidget.AppWidgetManager.getInstance(this@QuickCameraActivity)
                    .getAppWidgetIds(
                        android.content.ComponentName(
                            this@QuickCameraActivity,
                            com.example.taskschedulerv3.widget.QuickPhotoWidgetProvider::class.java
                        )
                    )
                updateIntent.putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                sendBroadcast(updateIntent)

                // 完了通知
                NotificationHelper.showDraftBatchComplete(this@QuickCameraActivity, 1, 0)

            } catch (e: Exception) {
                e.printStackTrace()
                NotificationHelper.showDraftBatchComplete(this@QuickCameraActivity, 0, 1)
            } finally {
                launch(Dispatchers.Main) { finish() }
            }
        }
    }

    private fun generateFallbackTitle(): String {
        val now = LocalDateTime.now()
        return now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) + " 仮登録"
    }
}
