package com.example.taskschedulerv3.ui.quickdraft

import android.app.Application
import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskschedulerv3.data.db.AppDatabase
import com.example.taskschedulerv3.data.model.QuickDraftTask
import com.example.taskschedulerv3.data.repository.QuickDraftRepository
import com.example.taskschedulerv3.util.AiEngineManager
import com.example.taskschedulerv3.util.OcrTextParser
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
            // ── 1. ML KitでOCR実行 ──
            val file = File(photoPath)
            if (file.exists()) {
                val bitmap = BitmapFactory.decodeFile(photoPath)
                if (bitmap != null) {
                    val image = InputImage.fromBitmap(bitmap, 0)
                    val result = recognizer.process(image).await()
                    finalOcrText = result.text
                    Log.d(TAG, "OCR Result (${finalOcrText.length} chars): ${finalOcrText.take(100)}...")
                }
            }

            // ── 2. AI推論の実行 ──
            if (finalOcrText.isNotBlank()) {

                // Engineが未ロードなら初期化を試みる
                if (!AiEngineManager.isLoaded()) {
                    Log.d(TAG, "Engine not loaded, loading now...")
                    AiEngineManager.loadEngine(context)
                }

                if (AiEngineManager.isLoaded()) {
                    // LiteRT-LM でJSON抽出
                    val jsonResult = AiEngineManager.analyze(finalOcrText)
                    Log.d(TAG, "AI JSON Result: $jsonResult")

                    // ── 3. JSONからデータを正規表現で抽出（崩れたJSONにも対応） ──
                    if (!jsonResult.isNullOrBlank()) {
                        try {
                            val titleMatch = Regex("\"title\"\\s*:\\s*\"(.*?)\"").find(jsonResult)
                            if (titleMatch != null) {
                                val aiTitle = titleMatch.groupValues[1].trim()
                                if (aiTitle.isNotBlank() && aiTitle.lowercase() != "null") {
                                    finalTitle = aiTitle
                                }
                            }

                            val dateMatch = Regex("\"date\"\\s*:\\s*\"(.*?)\"").find(jsonResult)
                            if (dateMatch != null) {
                                val extracted = dateMatch.groupValues[1].trim()
                                if (extracted.isNotBlank() && extracted.lowercase() != "null") {
                                    aiDate = extracted
                                }
                            }

                            val startMatch = Regex("\"start_time\"\\s*:\\s*\"(.*?)\"").find(jsonResult)
                            if (startMatch != null) {
                                val extracted = startMatch.groupValues[1].trim()
                                if (extracted.isNotBlank() && extracted.lowercase() != "null") {
                                    aiStartTime = extracted
                                }
                            }

                            val endMatch = Regex("\"end_time\"\\s*:\\s*\"(.*?)\"").find(jsonResult)
                            if (endMatch != null) {
                                val extracted = endMatch.groupValues[1].trim()
                                if (extracted.isNotBlank() && extracted.lowercase() != "null") {
                                    aiEndTime = extracted
                                }
                            }

                            val summaryMatch = Regex("\"summary\"\\s*:\\s*\"([\\s\\S]*?)\"").find(jsonResult)
                            if (summaryMatch != null) {
                                val extracted = summaryMatch.groupValues[1].trim()
                                if (extracted.isNotBlank() && extracted.lowercase() != "null") {
                                    aiSummary = extracted
                                }
                            }

                            Log.d(TAG, "AI Parsed -> title=$finalTitle, date=$aiDate, start=$aiStartTime")
                        } catch (e: Exception) {
                            Log.e(TAG, "Regex Parse Error", e)
                        }
                    } else {
                        // AI が null/空を返した → OcrTextParser でフォールバック
                        Log.w(TAG, "AI returned null/blank, using OCR fallback parser")
                        applyFallback(finalOcrText).let { fb ->
                            if (fb.title != null) finalTitle = fb.title
                            aiDate = fb.date
                            aiStartTime = fb.startTime
                            aiEndTime = fb.endTime
                            aiSummary = fb.summary
                        }
                    }
                } else {
                    // Engine 初期化失敗 → OcrTextParser でフォールバック
                    Log.e(TAG, "Engine failed to load: ${AiEngineManager.getInitError()}")
                    applyFallback(finalOcrText).let { fb ->
                        if (fb.title != null) finalTitle = fb.title
                        aiDate = fb.date
                        aiStartTime = fb.startTime
                        aiEndTime = fb.endTime
                        aiSummary = fb.summary
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "AI Processing Error", e)
        } finally {
            // ── 4. DB保存 ──
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
            _navigateToDraftId.value = newId
        }
    }

    /** OcrTextParser によるフォールバック抽出 */
    private fun applyFallback(ocrText: String): OcrTextParser.ParsedInfo {
        return OcrTextParser.fallbackParseFromOcr(ocrText)
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

    companion object {
        private const val TAG = "QuickDraftVM"
    }
}
