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
import com.example.taskschedulerv3.notification.NotificationHelper
import com.example.taskschedulerv3.util.AiEngineManager
import com.example.taskschedulerv3.util.AiPreferences
import com.example.taskschedulerv3.util.OcrTextParser
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
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

    // ── バッチ処理状態 ──

    /** バッチ処理の状態を公開する */
    data class BatchState(
        val isProcessing: Boolean = false,
        val totalCount: Int = 0,
        val completedCount: Int = 0,
        val currentFileName: String = ""
    )

    private val _batchState = MutableStateFlow(BatchState())
    val batchState: StateFlow<BatchState> = _batchState.asStateFlow()

    // 互換性のため旧フラグも維持（QuickCameraActivity等が参照）
    private val _isAiProcessing = MutableStateFlow(false)
    val isAiProcessing = _isAiProcessing.asStateFlow()

    // ── バッチキューで順番に処理 ──

    /**
     * 複数の写真パスをバックグラウンドキューに投入する。
     * AI有効時はOCR→AI解析→DB保存、無効時はフォールバックタイトルで保存。
     * 処理はバックグラウンドで順番に実行され、完了時に通知を送信する。
     *
     * @param photoPaths 処理対象の写真ファイルパスリスト
     * @param tagIds 全ドラフトに適用するタグIDリスト
     * @param useAi AI解析を使用するかどうか
     */
    fun enqueueBatch(
        context: Context,
        photoPaths: List<String>,
        tagIds: List<Int>,
        useAi: Boolean
    ) {
        if (photoPaths.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            val total = photoPaths.size
            _batchState.value = BatchState(
                isProcessing = true,
                totalCount = total,
                completedCount = 0
            )
            _isAiProcessing.value = true

            // 進捗通知を表示
            NotificationHelper.showDraftBatchProgress(context, 0, total)

            // AI有効時はEngineを先にロードしておく
            if (useAi && !AiEngineManager.isLoaded()) {
                Log.d(TAG, "Pre-loading AI engine for batch...")
                AiEngineManager.loadEngine(context)
            }

            var successCount = 0
            var failCount = 0

            for ((index, photoPath) in photoPaths.withIndex()) {
                val fileName = File(photoPath).name
                _batchState.value = _batchState.value.copy(
                    completedCount = index,
                    currentFileName = fileName
                )

                try {
                    if (useAi) {
                        processSinglePhotoWithAi(context, photoPath, tagIds)
                    } else {
                        processSinglePhotoSimple(photoPath, tagIds)
                    }
                    successCount++
                } catch (e: Exception) {
                    Log.e(TAG, "Batch item failed: $fileName", e)
                    // 失敗してもフォールバックタイトルで保存する
                    try {
                        insertDraft(
                            title = generateFallbackTitle(),
                            description = null,
                            photoPath = photoPath,
                            ocrText = null,
                            tagIds = tagIds
                        )
                        failCount++
                    } catch (e2: Exception) {
                        Log.e(TAG, "Even fallback insert failed", e2)
                        failCount++
                    }
                }

                // 進捗通知を更新
                NotificationHelper.showDraftBatchProgress(context, index + 1, total)
            }

            // 完了
            _batchState.value = BatchState(
                isProcessing = false,
                totalCount = total,
                completedCount = total
            )
            _isAiProcessing.value = false

            // 完了通知
            NotificationHelper.showDraftBatchComplete(context, successCount, failCount)

            Log.d(TAG, "Batch complete: $successCount success, $failCount fail (total $total)")
        }
    }

    /**
     * AI解析付きで1枚の写真を処理してDBに保存する。
     */
    private suspend fun processSinglePhotoWithAi(
        context: Context,
        photoPath: String,
        tagIds: List<Int>
    ) {
        var finalTitle = generateFallbackTitle()
        var finalOcrText = ""
        var aiDate: String? = null
        var aiStartTime: String? = null
        var aiEndTime: String? = null
        var aiSummary: String? = null

        // 1. ML Kit OCR
        val file = File(photoPath)
        if (file.exists()) {
            val bitmap = BitmapFactory.decodeFile(photoPath)
            if (bitmap != null) {
                try {
                    val image = InputImage.fromBitmap(bitmap, 0)
                    val result = recognizer.process(image).await()
                    finalOcrText = result.text
                    Log.d(TAG, "OCR Result (${finalOcrText.length} chars): ${finalOcrText.take(80)}...")
                } finally {
                    bitmap.recycle()
                }
            }
        }

        // 2. AI推論
        if (finalOcrText.isNotBlank() && AiEngineManager.isLoaded()) {
            val jsonResult = AiEngineManager.analyze(finalOcrText)
            Log.d(TAG, "AI JSON Result: $jsonResult")

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
                // AI が null/空 → OcrTextParser フォールバック
                Log.w(TAG, "AI returned null/blank, using OCR fallback")
                applyFallback(finalOcrText).let { fb ->
                    if (fb.title != null) finalTitle = fb.title
                    aiDate = fb.date
                    aiStartTime = fb.startTime
                    aiEndTime = fb.endTime
                    aiSummary = fb.summary
                }
            }
        } else if (finalOcrText.isNotBlank()) {
            // Engine未ロード → OcrTextParser フォールバック
            applyFallback(finalOcrText).let { fb ->
                if (fb.title != null) finalTitle = fb.title
                aiDate = fb.date
                aiStartTime = fb.startTime
                aiEndTime = fb.endTime
                aiSummary = fb.summary
            }
        }
        // finalOcrText がブランクの場合はフォールバックタイトルのまま

        // 3. DB保存
        insertDraft(
            title = finalTitle,
            description = aiSummary,
            photoPath = photoPath,
            ocrText = finalOcrText.ifBlank { null },
            tagIds = tagIds,
            startDate = aiDate,
            startTime = aiStartTime,
            endTime = aiEndTime
        )
    }

    /**
     * AIなしで1枚の写真を処理してDBに保存する（フォールバックタイトル）。
     */
    private suspend fun processSinglePhotoSimple(
        photoPath: String,
        tagIds: List<Int>
    ) {
        insertDraft(
            title = generateFallbackTitle(),
            description = null,
            photoPath = photoPath,
            ocrText = null,
            tagIds = tagIds
        )
    }

    // ── 互換性のため旧メソッドを維持 ──

    /**
     * 通常の仮登録保存（AIオフ時）。単一写真用。
     * QuickCameraActivity から呼ばれる可能性があるため維持。
     */
    fun createFromCamera(
        photoPath: String? = null,
        ocrText: String? = null,
        tagIds: List<Int> = emptyList()
    ) = viewModelScope.launch {
        insertDraft(
            title = generateFallbackTitle(),
            description = null,
            photoPath = photoPath,
            ocrText = ocrText,
            tagIds = tagIds
        )
    }

    /**
     * AI使用の単一写真仮登録。互換性維持用。
     * 内部的にバッチキュー（1件）として処理する。
     */
    fun createFromCameraWithAi(
        context: Context,
        photoPath: String,
        tagIds: List<Int>
    ) {
        enqueueBatch(context, listOf(photoPath), tagIds, useAi = true)
    }

    // ── 共通ヘルパー ──

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

    override fun onCleared() {
        super.onCleared()
        recognizer.close()
    }

    companion object {
        private const val TAG = "QuickDraftVM"
    }
}
