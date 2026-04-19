package com.example.taskschedulerv3.util

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object AiModelManager {

    private const val MODEL_DIR = "ai_model"
    private const val MODEL_FILENAME = "gemma3-1b-it-int4.litertlm"

    // Gemma3-1B-IT int4 QAT版: 約529MB, CPU/GPU両対応, 高速推論
    private const val MODEL_DOWNLOAD_URL =
        "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.litertlm"

    sealed class ModelState {
        object NotDownloaded : ModelState()
        data class Downloading(val progress: Int) : ModelState()
        object Ready : ModelState()
        data class Error(val message: String) : ModelState()
    }

    private val _state = MutableStateFlow<ModelState>(ModelState.NotDownloaded)
    val state = _state.asStateFlow()

    fun initState(context: Context) {
        _state.value = if (checkModelExists(context)) ModelState.Ready
                       else ModelState.NotDownloaded
    }

    fun checkModelExists(context: Context): Boolean {
        val file = getModelFile(context)
        return file.exists() && file.length() > 100_000
    }

    fun getModelFile(context: Context): File {
        val dir = File(context.filesDir, MODEL_DIR)
        if (!dir.exists()) dir.mkdirs()
        return File(dir, MODEL_FILENAME)
    }

    /** LiteRT-LM Engine に渡すモデルの絶対パス */
    fun getModelPath(context: Context): String {
        return getModelFile(context).absolutePath
    }

    fun getModelSizeMB(context: Context): Long {
        val file = getModelFile(context)
        return if (file.exists()) file.length() / (1024 * 1024) else 0
    }

    suspend fun downloadModel(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            _state.value = ModelState.Downloading(0)

            val file = getModelFile(context)
            val tmpFile = File(file.parent, "${file.name}.tmp")

            val url = URL(MODEL_DOWNLOAD_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 30_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = true
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                _state.value = ModelState.Error(
                    "ダウンロード失敗 (HTTP ${connection.responseCode})"
                )
                return@withContext false
            }

            val totalSize = connection.contentLengthLong

            connection.inputStream.use { input ->
                tmpFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var downloaded = 0L
                    var bytesRead: Int

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloaded += bytesRead
                        if (totalSize > 0) {
                            _state.value = ModelState.Downloading(
                                (downloaded * 100 / totalSize).toInt()
                            )
                        }
                    }
                }
            }

            // ダウンロード完了: 一時ファイルをリネーム
            if (file.exists()) file.delete()
            tmpFile.renameTo(file)

            _state.value = ModelState.Ready
            true
        } catch (e: Exception) {
            e.printStackTrace()
            val tmpFile = File(getModelFile(context).parent, "${MODEL_FILENAME}.tmp")
            if (tmpFile.exists()) tmpFile.delete()

            _state.value = ModelState.Error("ダウンロード失敗: ${e.localizedMessage}")
            false
        }
    }

    suspend fun deleteModel(context: Context) = withContext(Dispatchers.IO) {
        val file = getModelFile(context)
        if (file.exists()) file.delete()
        _state.value = ModelState.NotDownloaded
    }
}
