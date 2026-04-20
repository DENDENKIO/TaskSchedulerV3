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

/**
 * LiteRT-LM Engine のシングルトン管理。
 * - loadEngine: モデルをRAMへロード（初回約3-10秒）
 * - analyze: OCRテキストからJSON形式で日付・タイトル・要約を抽出
 * - releaseEngine: RAM解放
 */
object AiEngineManager {

    private const val TAG = "AiEngineManager"
    private var engine: Engine? = null
    private val mutex = Mutex()
    private var initError: String? = null

    /** Engine がロード済みか */
    fun isLoaded(): Boolean = engine != null

    /** 初期化エラーメッセージ（デバッグ用） */
    fun getInitError(): String? = initError

    /**
     * Engine をロードする。既にロード済みなら何もしない。
     * 必ずバックグラウンドスレッドから呼ぶこと（約3-10秒かかる）。
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
     *
     * @param ocrText ML Kit OCRで取得した生テキスト
     * @return LLMからのレスポンス文字列（JSON形式を期待）、失敗時はnull
     */
    suspend fun analyze(ocrText: String): String? = withContext(Dispatchers.IO) {
        val currentEngine = engine ?: run {
            Log.e(TAG, "Engine not loaded. Call loadEngine() first.")
            return@withContext null
        }

        val systemPrompt = """あなたは優秀な秘書です。以下の【OCRテキスト】を読み取り、JSONフォーマットで情報を抽出してください。

【絶対のルール】
1. "title": テキストの一番上の行をそのまま使うのではなく、内容全体を読んで15文字以内の「分かりやすいタイトル」をあなたが新しく考えてください。
2. "summary": OCRテキストの誤字や改行のおかしな部分を修正し、人間が読みやすいように要約した文章を必ず書いてください。
3. "date", "start_time", "end_time" は見つからなければ "" (空文字) にしてください。
4. 必ずJSON形式のみを返してください。説明文やMarkdown装飾は不要です。

【出力の例】
{"title":"保護者会のお知らせ","date":"2026-04-25","start_time":"13:00","end_time":"15:00","summary":"来週土曜日に体育館で保護者会が開催されます。スリッパを持参してください。"}"""

        val userMessage = "以下のOCR読み取りテキストから予定情報をJSON形式で抽出してください:\n\n$ocrText"

        try {
            val conversationConfig = ConversationConfig(
                systemInstruction = Contents.of(systemPrompt),
                samplerConfig = SamplerConfig(
                    topK = 5,
                    topP = 0.9,
                    temperature = 0.3
                )
            )
            currentEngine.createConversation(conversationConfig).use { conversation ->
                val response = conversation.sendMessage(userMessage)
                val result = response.toString()
                Log.d(TAG, "LLM Response: $result")
                result
            }
        } catch (e: Exception) {
            Log.e(TAG, "LLM inference error", e)
            null
        }
    }

    /**
     * 汎用のLLM推論。プロンプトをそのまま渡してレスポンスを得る。
     * チャット意図解析など、OCR以外の用途に使用。
     */
    suspend fun generateResponse(prompt: String): String? = withContext(Dispatchers.IO) {
        val currentEngine = engine ?: run {
            Log.e(TAG, "Engine not loaded. Call loadEngine() first.")
            return@withContext null
        }

        try {
            val conversationConfig = ConversationConfig(
                samplerConfig = SamplerConfig(
                    topK = 10,
                    topP = 0.95,
                    temperature = 0.5
                )
            )
            currentEngine.createConversation(conversationConfig).use { conversation ->
                val response = conversation.sendMessage(prompt)
                val result = response.toString()
                Log.d(TAG, "LLM GenerateResponse: ${result.take(200)}")
                result
            }
        } catch (e: Exception) {
            Log.e(TAG, "LLM generateResponse error", e)
            null
        }
    }
}
