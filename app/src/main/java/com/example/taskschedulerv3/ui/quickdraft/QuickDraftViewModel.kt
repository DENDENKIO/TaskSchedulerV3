package com.example.taskschedulerv3.ui.quickdraft

import android.app.Application
import android.graphics.BitmapFactory
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskschedulerv3.data.db.AppDatabase
import com.example.taskschedulerv3.data.model.QuickDraftTask
import com.example.taskschedulerv3.data.repository.QuickDraftRepository
import com.example.taskschedulerv3.util.AiModelManager
import com.example.taskschedulerv3.util.AiPreferences
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
     * AI ON時: OCR → （将来的にLLM解析）→ 仮登録
     */
    private suspend fun createFromCameraWithAi(
        photoPath: String?,
        tagIds: List<Int>
    ) {
        _isProcessing.value = true
        try {
            var ocrText: String? = null

            if (photoPath != null) {
                val bitmap = BitmapFactory.decodeFile(photoPath)
                if (bitmap != null) {
                    val image = InputImage.fromBitmap(bitmap, 0)
                    val result = recognizer.process(image).await()
                    ocrText = result.text.ifEmpty { null }
                }
            }

            // TODO: LiteRT-LM統合後、ここでLLMにocrTextを渡して解析
            // val parsed = aiAnalyze(ocrText)

            val now = LocalDateTime.now()
            val fallbackTitle = now.format(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            ) + " 仮登録"
            val tagIdsStr = if (tagIds.isEmpty()) null else tagIds.joinToString(",")

            val draft = QuickDraftTask(
                title = fallbackTitle,
                description = null,
                photoPath = photoPath,
                ocrText = ocrText,
                status = "DRAFT",
                tagIds = tagIdsStr
            )
            repo.insert(draft)
        } catch (e: Exception) {
            e.printStackTrace()
            createFromCamera(photoPath = photoPath, tagIds = tagIds)
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
        val now = LocalDateTime.now()
        val autoTitle = now.format(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        ) + " 仮登録"
        val tagIdsStr = if (tagIds.isEmpty()) null else tagIds.joinToString(",")
        val draft = QuickDraftTask(
            title = autoTitle,
            photoPath = photoPath,
            ocrText = ocrText,
            status = "DRAFT",
            tagIds = tagIdsStr
        )
        repo.insert(draft)
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
}
