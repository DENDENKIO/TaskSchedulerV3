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
 * Gemma 4 E2B (≈2.58 GB) 対応。
 *
 * - loadEngine: モデルをRAMへロード
 * - analyze: OCRテキストからJSON形式で日付・タイトル・要約を抽出（タイムアウト付）
 * - generateResponse: 汎用LLM推論（JSON抽出等）
 * - generateChatResponse: 自由会話応答生成
 * - releaseEngine: RAM解放
 */
object AiEngineManager {

    private const val TAG = "AiEngineManager"

    // Gemma 4 E2B は Dimensity 7050 で初回推論に最大30秒程度かかるため余裕を持って90秒
    private const val INFERENCE_TIMEOUT_MS = 90_000L

    // Gemma 4 E2B は32Kコンテキスト対応。OCR入力を1000文字まで拡大
    private const val MAX_OCR_LENGTH = 1000

    // モデルファイルの最小サイズ（1 GB以上であること）
    private const val MIN_MODEL_FILE_SIZE = 1_000_000_000L

    private var engine: Engine? = null
    private val mutex = Mutex()
    private var initError: String? = null

    /** Engine がロード済みか */
    fun isLoaded(): Boolean = engine != null

    /** 初期化エラーメッセージ（デバッグ用） */
    fun getInitError(): String? = initError

    /**
     * Engine をロードする。
     * Gemma 4 E2B のロードには DOOGEE S200 で約5〜15秒を要する。
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
                if (modelFile.length() < MIN_MODEL_FILE_SIZE) {
                    initError = "モデルファイルが破損しています（サイズ: ${modelFile.length() / (1024 * 1024)} MB、最低1 GB必要）"
                    Log.e(TAG, initError!!)
                    return@withLock
                }

                Log.d(TAG, "Loading Gemma 4 E2B model: $modelPath (${modelFile.length() / 1024 / 1024} MB)")

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
     * OCRテキストをLLMに渡して構造化データ(JSON)を取得する。
     * 90秒タイムアウト付き。OCRテキストは1000文字に制限。
     *
     * 返値: JSON文字列（例: {"title":"会議","date":"2026-05-01","start_time":"14:00","end_time":"16:00","summary":"..."}）
     * 失敗時は null を返す（呼び出し元で OcrTextParser へフォールバック）
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
        Log.d(TAG, "Analyze input (${trimmedOcr.length} chars): ${trimmedOcr.take(100)}...")

        // Gemma 4 E2B は日本語理解力が高いため、日本語プロンプトで精度向上
        val systemPrompt = """あなたは写真のOCRテキストから予定情報を抽出するアシスタントです。
以下のJSON形式のみを出力してください。他のテキストは一切含めないでください。

{"title":"短いタイトル（15文字以内）","date":"YYYY-MM-DD","start_time":"HH:mm","end_time":"HH:mm","summary":"内容の要約（50文字以内）"}

ルール:
- titleは予定の内容を端的に表す短いフレーズにする
- 該当する情報がない場合は空文字""にする
- 日付や時刻のフォーマットは必ず上記形式に従う
- 出力はJSONのみ。説明文やMarkdown装飾は不要"""

        try {
            val result = withTimeoutOrNull(INFERENCE_TIMEOUT_MS) {
                try {
                    val conversationConfig = ConversationConfig(
                        systemInstruction = Contents.of(systemPrompt),
                        samplerConfig = SamplerConfig(
                            topK = 5,
                            topP = 0.9,
                            temperature = 0.2  // JSON出力の安定性のため低めに設定
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
                    Log.e(TAG, "LLM inference error in analyze()", e)
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
     * 汎用のLLM推論（JSON抽出・意図解析など構造化出力向け）。
     * タイムアウト付き。
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
                            temperature = 0.3  // 構造化出力のため低温
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

    /**
     * 自由会話応答生成（AIチャット画面用）。
     * systemPrompt でアシスタントのペルソナ・コンテキストを設定し、
     * ユーザーの自由な質問に対して自然な日本語で応答する。
     *
     * @param userMessage ユーザーの入力テキスト
     * @param systemPrompt アシスタントの役割定義（コンテキスト）
     * @return 応答テキスト。失敗時は null
     */
    suspend fun generateChatResponse(
        userMessage: String,
        systemPrompt: String
    ): String? = withContext(Dispatchers.IO) {
        val currentEngine = engine ?: run {
            Log.e(TAG, "Engine not loaded. Call loadEngine() first.")
            return@withContext null
        }

        try {
            val result = withTimeoutOrNull(INFERENCE_TIMEOUT_MS) {
                try {
                    val conversationConfig = ConversationConfig(
                        systemInstruction = Contents.of(systemPrompt),
                        samplerConfig = SamplerConfig(
                            topK = 40,
                            topP = 0.95,
                            temperature = 0.7  // 自由会話のため高めに設定
                        )
                    )
                    currentEngine.createConversation(conversationConfig).use { conversation ->
                        val sb = StringBuilder()
                        conversation.sendMessageAsync(userMessage)
                            .collect { chunk -> sb.append(chunk.toString()) }
                        sb.toString()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "LLM generateChatResponse error", e)
                    null
                }
            }

            if (result == null) {
                Log.w(TAG, "generateChatResponse timed out after ${INFERENCE_TIMEOUT_MS / 1000}s")
                return@withContext null
            }

            Log.d(TAG, "LLM ChatResponse (${result.length} chars): ${result.take(200)}")
            result
        } catch (e: Exception) {
            Log.e(TAG, "generateChatResponse error", e)
            null
        }
    }
}
