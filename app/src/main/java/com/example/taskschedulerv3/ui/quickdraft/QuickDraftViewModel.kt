package com.example.taskschedulerv3.ui.quickdraft

import android.app.Application
import android.graphics.BitmapFactory
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskschedulerv3.data.db.AppDatabase
import com.example.taskschedulerv3.data.model.QuickDraftTask
import com.example.taskschedulerv3.data.repository.QuickDraftRepository
import com.example.taskschedulerv3.util.AiEngineManager
import com.example.taskschedulerv3.util.AiModelManager
import com.example.taskschedulerv3.util.AiPreferences
import com.example.taskschedulerv3.util.OcrTextParser
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class QuickDraftViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        private const val TAG = "QuickDraftVM"
    }

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

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing = _isProcessing.asStateFlow()

    /**
     * AI ON/OFFに応じた仮登録メイン関数。
     * 外部（MainActivity、QuickDraftListScreen）からはこのメソッドを呼ぶ。
     */
    fun createSmartDraft(
        photoPath: String?,
        tagIds: List<Int> = emptyList()
    ) = viewModelScope.launch(Dispatchers.IO) {
        val context = getApplication<Application>()
        val aiEnabled = AiPreferences.getAiEnabled(context).first()
        val modelReady = AiModelManager.checkModelExists(context)

        if (aiEnabled && modelReady) {
            createFromCameraWithAi(photoPath, tagIds)
        } else {
            createFromCamera(photoPath = photoPath, tagIds = tagIds)
        }
    }

    /**
     * AI ON時: OCR → LLM解析 → 自動タイトル/日付/要約で仮登録
     */
    private suspend fun createFromCameraWithAi(
        photoPath: String?,
        tagIds: List<Int>
    ) {
        _isProcessing.value = true
        try {
            val context = getApplication<Application>()
            var ocrText: String? = null

            // ① ML Kit OCR で文字認識
            if (photoPath != null) {
                val bitmap = BitmapFactory.decodeFile(photoPath)
                if (bitmap != null) {
                    val image = InputImage.fromBitmap(bitmap, 0)
                    val result = recognizer.process(image).await()
                    ocrText = result.text.ifEmpty { null }
                }
            }

            // OCRテキストが取れなかった場合 → 通常登録へフォールバック
            if (ocrText.isNullOrBlank()) {
                Log.d(TAG, "OCRテキストが空のため通常登録にフォールバック")
                insertDraft(
                    title = generateFallbackTitle(),
                    description = null,
                    photoPath = photoPath,
                    ocrText = null,
                    tagIds = tagIds
                )
                return
            }

            // ② LLM Engine をロード（未ロードの場合のみ）
            if (!AiEngineManager.isLoaded()) {
                Log.d(TAG, "AI Engine ロード開始...")
                AiEngineManager.loadEngine(context)
                Log.d(TAG, "AI Engine ロード完了")
            }

            // ③ LLM に OCR テキストを渡して解析
            Log.d(TAG, "LLM解析開始: ${ocrText.take(100)}...")
            val llmResponse = AiEngineManager.analyze(ocrText)
            Log.d(TAG, "LLM応答: ${llmResponse.take(200)}")

            // ④ LLM応答をパース
            var parsed = OcrTextParser.parseFromLlmResponse(llmResponse)

            // LLMパース失敗時 → 正規表現フォールバック
            if (parsed == null || (parsed.title == null && parsed.date == null)) {
                Log.d(TAG, "LLMパース失敗。正規表現フォールバックを使用")
                parsed = OcrTextParser.fallbackParseFromOcr(ocrText)
            }

            // ⑤ パース結果でドラフト作成
            val title = parsed.title ?: generateFallbackTitle()
            val description = buildDescription(parsed, ocrText)

            insertDraft(
                title = title,
                description = description,
                photoPath = photoPath,
                ocrText = ocrText,
                tagIds = tagIds
            )

            Log.d(TAG, "AI仮登録完了: title=$title, date=${parsed.date}")

        } catch (e: Exception) {
            Log.e(TAG, "AI処理中にエラー発生。通常登録にフォールバック", e)
            // 全体失敗 → 従来の仮登録
            insertDraft(
                title = generateFallbackTitle(),
                description = null,
                photoPath = photoPath,
                ocrText = null,
                tagIds = tagIds
            )
        } finally {
            _isProcessing.value = false
        }
    }

    /**
     * 既存: 通常の仮登録（AI OFF時 / フォールバック）
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

    // ===== ヘルパー関数 =====

    private suspend fun insertDraft(
        title: String,
        description: String?,
        photoPath: String?,
        ocrText: String?,
        tagIds: List<Int>
    ) {
        val tagIdsStr = if (tagIds.isEmpty()) null else tagIds.joinToString(",")
        val draft = QuickDraftTask(
            title = title,
            description = description,
            photoPath = photoPath,
            ocrText = ocrText,
            status = "DRAFT",
            tagIds = tagIdsStr
        )
        repo.insert(draft)
    }

    private fun generateFallbackTitle(): String {
        val now = LocalDateTime.now()
        return now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) + " 仮登録"
    }

    /**
     * パース結果から description を組み立てる。
     * 日付・時刻情報 + 要約をまとめてメモとして保存。
     */
    private fun buildDescription(parsed: OcrTextParser.ParsedInfo, ocrText: String): String? {
        val parts = mutableListOf<String>()

        if (parsed.date != null) {
            var dateInfo = "📅 日付: ${parsed.date}"
            if (parsed.startTime != null) {
                dateInfo += "  ${parsed.startTime}"
                if (parsed.endTime != null) {
                    dateInfo += "〜${parsed.endTime}"
                }
            }
            parts.add(dateInfo)
        }

        if (parsed.summary != null) {
            parts.add("📝 内容:\n${parsed.summary}")
        }

        return if (parts.isEmpty()) null else parts.joinToString("\n\n")
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
        // 注意: AiEngineManager はシングルトンなのでここでは解放しない。
        // 設定画面のAI OFF時やアプリ終了時に解放する。
    }
}
