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
 * LiteRT-LM Engine のシングルトン管理（Gemma 4 E2B 対応版）
 *
 * - loadEngine: モデルをRAMへロード（初回約5-15秒）
 * - analyze: OCRテキストからJSON形式で日付・タイトル・要約を抽出（90秒タイムアウト付）
 * - releaseEngine: RAM解放
 * - generateResponse: 汎用のLLM推論（90秒タイムアウト付）
 */
object AiEngineManager {

    private const val TAG = "AiEngineManager"
    private const val INFERENCE_TIMEOUT_MS = 90_000L  // 90秒タイムアウト
    private const val MAX_OCR_LENGTH = 1000           // OCR入力の最大文字数

    private var engine: Engine? = null
    private val mutex = Mutex()
    private var initError: String? = null

    /** Engine がロード済みか */
    fun isLoaded(): Boolean = engine != null

    /** 初期化エラーメッセージ（デバッグ用） */
    fun getInitError(): String? = initError

    /**
     * Engine をロードする。既にロード済みなら何もしない。
     * 必ずバックグラウンドスレッドから呼ぶこと（初回約5-15秒かかる）。
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
                if (modelFile.length() < 1_000_000_000) {
                    initError = "モデルファイルが破損しています（サイズ: ${modelFile.length() / (1024 * 1024)} MB）"
                    Log.e(TAG, initError!!)
                    return@withLock
                }

                Log.d(TAG, "Loading Gemma 4 E2B: $modelPath (${modelFile.length() / 1024 / 1024} MB)")

                val config = EngineConfig(
                    modelPath = modelPath,
                    backend = Backend.CPU(),
                    cacheDir = context.cacheDir.absolutePath
                )
                val newEngine = Engine(config)
                newEngine.initialize()
                engine = newEngine
                Log.d(TAG, "Gemma 4 E2B Engine initialized successfully")
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
     *
     * - 90秒タイムアウト付き（タイムアウト時はnullを返す）
     * - OCRテキストは1000文字に制限
     * - sendMessageAsync（Flow）を使用しコルーチンキャンセルに対応
     *
     * @param ocrText ML Kit OCRで取得した生テキスト
     * @return LLMからのレスポンス文字列（JSON形式を期待）、失敗時はnull
     */
    suspend fun analyze(ocrText: String): String? = withContext(Dispatchers.IO) {
        val currentEngine = engine ?: run {
            Log.e(TAG, "Engine not loaded. Call loadEngine() first.")
            return@withContext null
        }

        // OCRテキストを制限
        val trimmedOcr = if (ocrText.length > MAX_OCR_LENGTH) {
            ocrText.take(MAX_OCR_LENGTH) + "..."
        } else {
            ocrText
        }
        Log.d(TAG, "Analyze input (${trimmedOcr.length} chars)")

        // Gemma 4 E2B向けプロンプト（日本語対応、シンプル）
        val systemPrompt = """あなたはOCRテキストから予定情報を抽出するAIです。
以下のJSON形式のみを出力してください。説明文は不要です。

{"title":"15文字以内の短いタイトル","date":"YYYY-MM-DD","start_time":"HH:mm","end_time":"HH:mm","summary":"内容の要約"}

ルール:
- titleはOCR内容を要約した短いタイトルをあなたが考えてください
- summaryはOCRテキストの内容を人間が読みやすいように要約してください
- 見つからない項目は空文字""にしてください
- JSON以外は出力しないでください"""

        try {
            val result = withTimeoutOrNull(INFERENCE_TIMEOUT_MS) {
                try {
                    val conversationConfig = ConversationConfig(
                        systemInstruction = Contents.of(systemPrompt),
                        samplerConfig = SamplerConfig(
                            topK = 10,
                            topP = 0.9,
                            temperature = 0.3
                        )
                    )
                    currentEngine.createConversation(conversationConfig).use { conversation ->
                        val sb = StringBuilder()
                        conversation.sendMessageAsync(trimmedOcr)
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
     * 汎用のLLM推論。プロンプトをそのまま渡してレスポンスを得る。
     * 90秒タイムアウト付き。
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
                Log.w(TAG, "generateResponse timed out after ${INFERENCE_TIMEOUT_MS / 1000}s")
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
