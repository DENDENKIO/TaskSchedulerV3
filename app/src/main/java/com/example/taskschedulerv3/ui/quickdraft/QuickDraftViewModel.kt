package com.example.taskschedulerv3.ui.quickdraft

import android.app.Application
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskschedulerv3.data.db.AppDatabase
import com.example.taskschedulerv3.data.model.QuickDraftTask
import com.example.taskschedulerv3.data.repository.QuickDraftRepository
import com.example.taskschedulerv3.util.AiTextExtractor
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class QuickDraftViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.getInstance(app)
    val repo = QuickDraftRepository(
        db.quickDraftTaskDao(), db.taskDao(), db.taskTagCrossRefDao(), db.photoMemoDao()
    )

    private val recognizer = TextRecognition.getClient(
        JapaneseTextRecognizerOptions.Builder().build()
    )

    val drafts: StateFlow<List<QuickDraftTask>> = repo.getDrafts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val convertSuccess = MutableStateFlow(false)

    private val _isAiProcessing = MutableStateFlow(false)
    val isAiProcessing = _isAiProcessing.asStateFlow()

    private val _navigateToDraftId = MutableStateFlow<Int?>(null)
    val navigateToDraftId = _navigateToDraftId.asStateFlow()

    /**
     * 通常の仮登録保存（AIオフ時）
     */
    fun createFromCamera(
        photoPath: String? = null,
        ocrText: String? = null,
        tagIds: List<Int> = emptyList()
    ) = viewModelScope.launch {
        val newId = insertDraft(
            title = generateFallbackTitle(),
            description = null,
            photoPath = photoPath,
            ocrText = ocrText,
            tagIds = tagIds
        )
        _navigateToDraftId.value = newId
    }

    /**
     * AIを使用して写真から予定を自動抽出し、仮登録を作成する
     */
    fun createFromCameraWithAi(
        context: Context,
        photoPath: String,
        tagIds: List<Int>
    ) = viewModelScope.launch(Dispatchers.IO) {
        _isAiProcessing.value = true
        
        var finalTitle = generateFallbackTitle()
        var finalOcrText = ""
        var aiDate: String? = null
        var aiStartTime: String? = null
        var aiEndTime: String? = null
        var aiSummary: String? = null
        
        try {
            // 1. ML KitでOCR実行
            val file = File(photoPath)
            if (file.exists()) {
                val bitmap = BitmapFactory.decodeFile(photoPath)
                if (bitmap != null) {
                    val image = InputImage.fromBitmap(bitmap, 0)
                    val result = recognizer.process(image).await()
                    finalOcrText = result.text
                    Log.d("QuickDraftVM", "OCR Result: ${finalOcrText.take(100)}...")
                }
            }

            // 2. AI推論の実行
            if (finalOcrText.isNotBlank()) {
                val jsonResult = AiTextExtractor.extractScheduleInfo(finalOcrText)
                
                // 3. JSONパースとデータ抽出
                if (!jsonResult.isNullOrBlank()) {
                    try {
                        val json = JSONObject(jsonResult)
                        
                        // --- タイトルの取得をより確実にする ---
                        val aiTitle = json.optString("title", "").trim()
                        // AIがタイトルを生成できた場合のみ上書き（"null"という文字列が返ってきた場合も弾く）
                        if (aiTitle.isNotBlank() && aiTitle.lowercase() != "null") {
                            finalTitle = aiTitle
                        }
                        // ------------------------------------

                        aiDate = json.optString("date", "").takeIf { it.isNotBlank() && it.lowercase() != "null" }
                        aiStartTime = json.optString("start_time", "").takeIf { it.isNotBlank() && it.lowercase() != "null" }
                        aiEndTime = json.optString("end_time", "").takeIf { it.isNotBlank() && it.lowercase() != "null" }
                        aiSummary = json.optString("summary", "").takeIf { it.isNotBlank() && it.lowercase() != "null" }
                        
                        Log.d("QuickDraftViewModel", "AI Parsed Data: $json")
                        Log.d("QuickDraftViewModel", "Final Title set to: $finalTitle") // デバッグ用ログ
                    } catch (e: Exception) {
                        Log.e("QuickDraftViewModel", "JSON Parse Error", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("QuickDraftVM", "AI Processing Error", e)
        } finally {
            // 4. DB保存
            val newId = insertDraft(
                title = finalTitle,
                description = aiSummary,
                photoPath = photoPath,
                ocrText = finalOcrText,
                tagIds = tagIds,
                startDate = aiDate,
                startTime = aiStartTime,
                endTime = aiEndTime
            )
            _isAiProcessing.value = false
            // 編集画面へ遷移させるためにIDを発行
            _navigateToDraftId.value = newId
        }
    }

    private suspend fun insertDraft(
        title: String,
        description: String?,
        photoPath: String?,
        ocrText: String?,
        tagIds: List<Int>,
        startDate: String? = null,
        startTime: String? = null,
        endTime: String? = null
    ): Int {
        val tagIdsStr = if (tagIds.isEmpty()) null else tagIds.joinToString(",")
        val draft = QuickDraftTask(
            title = title,
            description = description,
            photoPath = photoPath,
            ocrText = ocrText,
            status = "DRAFT",
            tagIds = tagIdsStr,
            startDate = startDate,
            startTime = startTime,
            endTime = endTime
        )
        return repo.insert(draft).toInt()
    }

    private fun generateFallbackTitle(): String {
        val now = LocalDateTime.now()
        return now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) + " 仮登録"
    }

    fun updateDraft(draft: QuickDraftTask) = viewModelScope.launch {
        repo.update(draft)
    }

    fun deleteDraft(draft: QuickDraftTask) = viewModelScope.launch {
        repo.delete(draft)
    }

    fun convertToTask(
        draft: QuickDraftTask,
        startDate: String = LocalDate.now().toString(),
        priority: Int = 1
    ) = viewModelScope.launch {
        repo.convertToTask(draft, startDate, priority)
        convertSuccess.value = true
    }

    fun clearConvertSuccess() { convertSuccess.value = false }

    fun clearNavigation() {
        _navigateToDraftId.value = null
    }

    override fun onCleared() {
        super.onCleared()
        recognizer.close()
    }
}
