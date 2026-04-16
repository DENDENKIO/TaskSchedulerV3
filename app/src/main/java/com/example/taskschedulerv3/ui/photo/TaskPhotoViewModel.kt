package com.example.taskschedulerv3.ui.photo

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskschedulerv3.util.PhotoFileManager
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File

class TaskPhotoViewModel(app: Application) : AndroidViewModel(app) {
    private val recognizer = TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing = _isProcessing.asStateFlow()

    private val _ocrResult = MutableStateFlow<String?>(null)
    val ocrResult = _ocrResult.asStateFlow()

    /**
     * 画像からテキストを抽出 (OCR)
     */
    fun processImageForOcr(uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        _isProcessing.value = true
        try {
            val image = InputImage.fromFilePath(getApplication(), uri)
            val result = recognizer.process(image).await()
            _ocrResult.value = result.text.ifEmpty { "テキストが見つかりませんでした" }
        } catch (e: Exception) {
            e.printStackTrace()
            _ocrResult.value = "エラー: ${e.localizedMessage}"
        } finally {
            _isProcessing.value = false
        }
    }

    /**
     * FileからOCR処理
     */
    fun processFileForOcr(file: File) = viewModelScope.launch(Dispatchers.IO) {
        _isProcessing.value = true
        try {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return@launch
            val image = InputImage.fromBitmap(bitmap, 0)
            val result = recognizer.process(image).await()
            _ocrResult.value = result.text.ifEmpty { "テキストが見つかりませんでした" }
        } catch (e: Exception) {
            e.printStackTrace()
            _ocrResult.value = "エラー: ${e.localizedMessage}"
        } finally {
            _isProcessing.value = false
        }
    }

    fun clearOcrResult() {
        _ocrResult.value = null
    }

    override fun onCleared() {
        super.onCleared()
        recognizer.close()
    }
}
