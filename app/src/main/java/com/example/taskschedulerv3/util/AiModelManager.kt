package com.example.taskschedulerv3.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

object AiModelManager {

    private const val TAG = "AiModelManager"
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

    fun getModelPath(context: Context): String {
        return getModelFile(context).absolutePath
    }

    fun getModelSizeMB(context: Context): Long {
        val file = getModelFile(context)
        return if (file.exists()) file.length() / (1024 * 1024) else 0
    }

    /**
     * リダイレクトを手動で追跡する接続ヘルパー。
     * Hugging Face は複数回のリダイレクト（302/303/307）を返すことがある。
     */
    private fun openConnectionWithRedirects(urlStr: String, maxRedirects: Int = 10): HttpURLConnection {
        var currentUrl = urlStr
        var redirectCount = 0

        while (redirectCount < maxRedirects) {
            val url = URL(currentUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 60_000
            connection.readTimeout = 60_000
            connection.instanceFollowRedirects = false // 手動でリダイレクト追跡
            connection.setRequestProperty("User-Agent", "TaskSchedulerV3/1.0")
            connection.connect()

            val responseCode = connection.responseCode
            Log.d(TAG, "URL: $currentUrl -> HTTP $responseCode")

            if (responseCode in 300..399) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                if (location.isNullOrBlank()) {
                    throw Exception("リダイレクト先が見つかりません (HTTP $responseCode)")
                }
                currentUrl = if (location.startsWith("http")) location
                             else URL(URL(currentUrl), location).toString()
                redirectCount++
                Log.d(TAG, "リダイレクト ($redirectCount): $currentUrl")
            } else {
                return connection
            }
        }
        throw Exception("リダイレクト回数が上限($maxRedirects)を超えました")
    }

    suspend fun downloadModel(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            _state.value = ModelState.Downloading(0)
            Log.d(TAG, "ダウンロード開始: $MODEL_DOWNLOAD_URL")

            val file = getModelFile(context)
            val tmpFile = File(file.parent, "${file.name}.tmp")

            val connection = openConnectionWithRedirects(MODEL_DOWNLOAD_URL)

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                val errorBody = try {
                    connection.errorStream?.bufferedReader()?.readText()?.take(200) ?: ""
                } catch (_: Exception) { "" }
                connection.disconnect()

                val errorMsg = when (responseCode) {
                    401, 403 -> "アクセス拒否 (HTTP $responseCode)。Hugging Faceのライセンス同意が必要な可能性があります。"
                    404 -> "モデルが見つかりません (HTTP 404)"
                    else -> "ダウンロード失敗 (HTTP $responseCode): $errorBody"
                }
                Log.e(TAG, errorMsg)
                _state.value = ModelState.Error(errorMsg)
                return@withContext false
            }

            val totalSize = connection.contentLengthLong
            Log.d(TAG, "ファイルサイズ: ${totalSize / (1024 * 1024)} MB")

            connection.inputStream.use { input ->
                tmpFile.outputStream().use { output ->
                    val buffer = ByteArray(16384)
                    var downloaded = 0L
                    var bytesRead: Int
                    var lastProgressUpdate = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloaded += bytesRead

                        // 進捗更新を500msごとに制限（UIスレッド負荷軽減）
                        val now = System.currentTimeMillis()
                        if (totalSize > 0 && now - lastProgressUpdate > 500) {
                            val progress = (downloaded * 100 / totalSize).toInt()
                            _state.value = ModelState.Downloading(progress)
                            lastProgressUpdate = now
                        }
                    }
                }
            }
            connection.disconnect()

            // ダウンロード完了: サイズ検証
            if (tmpFile.length() < 100_000) {
                tmpFile.delete()
                _state.value = ModelState.Error("ダウンロードファイルが小さすぎます")
                return@withContext false
            }

            if (file.exists()) file.delete()
            tmpFile.renameTo(file)

            Log.d(TAG, "ダウンロード完了: ${file.length() / (1024 * 1024)} MB")
            _state.value = ModelState.Ready
            true
        } catch (e: Exception) {
            Log.e(TAG, "ダウンロードエラー", e)
            try {
                val tmpFile = File(getModelFile(context).parent, "${MODEL_FILENAME}.tmp")
                if (tmpFile.exists()) tmpFile.delete()
            } catch (_: Exception) {}

            _state.value = ModelState.Error("ダウンロード失敗: ${e.localizedMessage ?: "不明なエラー"}")
            false
        }
    }

    suspend fun deleteModel(context: Context) = withContext(Dispatchers.IO) {
        val file = getModelFile(context)
        if (file.exists()) file.delete()
        _state.value = ModelState.NotDownloaded
    }
}
