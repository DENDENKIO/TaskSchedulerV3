package com.example.taskschedulerv3.util

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * LiteRT-LM Engine のシングルトン管理。
 * - loadEngine: モデルをRAMへロード
 * - analyze: OCRテキストからJSON形式で日付・タイトル・要約を抽出（タイムアウト付）
 * - releaseEngine: RAM解放
 */
object AiEngineManager {

    private const val TAG = "AiEngineManager"
    private const val INFERENCE_TIMEOUT_MS = 60_000L  // 60秒タイムアウト
    private const val MAX_OCR_LENGTH = 500            // OCR入力の最大文字数

    private var engine: Engine? = null
    private val mutex = Mutex()
    private var initError: String? = null

    /** Engine がロード済みか */
    fun isLoaded(): Boolean = engine != null

    /** 初期化エラーメッセージ（デバッグ用） */
    fun getInitError(): String? = initError

    /**
     * Engine をロードする。
     */
    suspend fun loadEngine(context: Context) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (engine != null) return@withLock
            initError = null
            try {
                val modelPath = AiModelManager.getModelPath(context)
                val modelFile = java.io.File(modelPath)

                if (!modelFile.exists()) {
                    initError = "モデルファイルが見つかりません: $modelPath"
                    Log.e(TAG, initError!!)
                    return@withLock
                }
                if (modelFile.length() < 100_000) {
                    initError = "モデルファイルが破損しています（サイズ: ${modelFile.length()} bytes）"
                    Log.e(TAG, initError!!)
                    return@withLock
                }

                Log.d(TAG, "Loading model: $modelPath (${modelFile.length() / 1024 / 1024} MB)")

                val config = EngineConfig(
                    modelPath = modelPath,
                    backend = Backend.CPU(),
                    cacheDir = context.cacheDir.absolutePath
                )
                val newEngine = Engine(config)
                newEngine.initialize()
                engine = newEngine
                Log.d(TAG, "LiteRT-LM Engine initialized successfully")
            } catch (e: Exception) {
                initError = "Engine初期化失敗: ${e.localizedMessage}"
                Log.e(TAG, "Engine initialization failed", e)
            }
        }
    }

    /**
     * Engine を解放してRAMを開放する。
     */
    suspend fun releaseEngine() = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                engine?.close()
            } catch (e: Exception) {
                Log.e(TAG, "Engine close error", e)
            }
            engine = null
            Log.d(TAG, "Engine released")
        }
    }

    /**
     * OCRテキストをLLMに渡して構造化データを取得する。
     * 60秒タイムアウト付き。OCRテキストは500文字に制限。
     */
    suspend fun analyze(ocrText: String): String? = withContext(Dispatchers.IO) {
        val currentEngine = engine ?: run {
            Log.e(TAG, "Engine not loaded. Call loadEngine() first.")
            return@withContext null
        }

        // OCRテキストを制限（1Bモデルはコンテキスト処理が遅いため）
        val trimmedOcr = if (ocrText.length > MAX_OCR_LENGTH) {
            ocrText.take(MAX_OCR_LENGTH) + "..."
        } else {
            ocrText
        }
        Log.d(TAG, "Analyze input (${trimmedOcr.length} chars): ${trimmedOcr.take(80)}...")

        // シンプルなプロンプト（1Bモデル向けに最適化）
        val systemPrompt = """Extract schedule info from OCR text. Return ONLY a JSON object:
{"title":"短いタイトル","date":"YYYY-MM-DD","start_time":"HH:mm","end_time":"HH:mm","summary":"要約"}
If not found, use empty string "". No explanation, JSON only."""

        val userMessage = trimmedOcr

        try {
            // タイムアウト付きで推論実行
            val result = withTimeoutOrNull(INFERENCE_TIMEOUT_MS) {
                try {
                    val conversationConfig = ConversationConfig(
                        systemInstruction = Contents.of(systemPrompt),
                        samplerConfig = SamplerConfig(
                            topK = 5,
                            topP = 0.9,
                            temperature = 0.2
                        )
                    )
                    currentEngine.createConversation(conversationConfig).use { conversation ->
                        // ストリーミングで結果を収集（キャンセル可能）
                        val sb = StringBuilder()
                        conversation.sendMessageAsync(userMessage)
                            .collect { chunk ->
                                sb.append(chunk.toString())
                            }
                        sb.toString()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "LLM inference error", e)
                    null
                }
            }

            if (result == null) {
                Log.w(TAG, "LLM inference timed out after ${INFERENCE_TIMEOUT_MS / 1000}s")
                return@withContext null
            }

            Log.d(TAG, "LLM Response: $result")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Analyze error", e)
            null
        }
    }

    /**
     * 汎用のLLM推論。タイムアウト付き。
     */
    suspend fun generateResponse(prompt: String): String? = withContext(Dispatchers.IO) {
        val currentEngine = engine ?: run {
            Log.e(TAG, "Engine not loaded. Call loadEngine() first.")
            return@withContext null
        }

        try {
            val result = withTimeoutOrNull(INFERENCE_TIMEOUT_MS) {
                try {
                    val conversationConfig = ConversationConfig(
                        samplerConfig = SamplerConfig(
                            topK = 10,
                            topP = 0.95,
                            temperature = 0.5
                        )
                    )
                    currentEngine.createConversation(conversationConfig).use { conversation ->
                        val sb = StringBuilder()
                        conversation.sendMessageAsync(prompt)
                            .collect { chunk -> sb.append(chunk.toString()) }
                        sb.toString()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "LLM generateResponse error", e)
                    null
                }
            }

            if (result == null) {
                Log.w(TAG, "generateResponse timed out")
                return@withContext null
            }

            Log.d(TAG, "LLM GenerateResponse: ${result.take(200)}")
            result
        } catch (e: Exception) {
            Log.e(TAG, "generateResponse error", e)
            null
        }
    }
}
