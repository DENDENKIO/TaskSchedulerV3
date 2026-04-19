package com.example.taskschedulerv3.util

import android.content.Context
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

    private var engine: Engine? = null
    private val mutex = Mutex()

    /**
     * Engine が既にロード済みか確認
     */
    fun isLoaded(): Boolean = engine != null

    /**
     * Engine をロードする。既にロード済みなら何もしない。
     * 必ずバックグラウンドスレッドから呼ぶこと。
     */
    suspend fun loadEngine(context: Context) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (engine != null) return@withLock

            val modelPath = AiModelManager.getModelPath(context)
            val config = EngineConfig(
                modelPath = modelPath,
                backend = Backend.CPU(),
                cacheDir = context.cacheDir.absolutePath
            )
            val newEngine = Engine(config)
            newEngine.initialize()
            engine = newEngine
        }
    }

    /**
     * Engine を解放してRAMを開放する。
     */
    suspend fun releaseEngine() = withContext(Dispatchers.IO) {
        mutex.withLock {
            engine?.close()
            engine = null
        }
    }

    /**
     * OCRテキストをLLMに渡して構造化データを取得する。
     *
     * @param ocrText ML Kit OCRで取得した生テキスト
     * @return LLMからのレスポンス文字列（JSON形式を期待）
     * @throws IllegalStateException Engine未ロード時
     */
    suspend fun analyze(ocrText: String): String = withContext(Dispatchers.IO) {
        val currentEngine = engine
            ?: throw IllegalStateException("AI Engine is not loaded. Call loadEngine() first.")

        val systemPrompt = """あなたは書類やチラシの文字起こしテキストから、予定情報を抽出するアシスタントです。
以下のルールに従ってください:
1. 必ずJSON形式のみを返してください。説明文や挨拶は不要です。
2. 日付は "YYYY-MM-DD" 形式にしてください。年が書かれていない場合は今年を使ってください。
3. 時刻は "HH:mm" 形式（24時間制）にしてください。
4. 該当情報がない場合は null としてください。
5. summaryには本文の要点を200文字以内で簡潔にまとめてください。

出力フォーマット:
{"title":"予定のタイトル","date":"YYYY-MM-DD","start_time":"HH:mm","end_time":"HH:mm","summary":"要約テキスト"}"""

        val userMessage = "以下のOCR読み取りテキストから予定情報をJSON形式で抽出してください:\n\n$ocrText"

        val conversationConfig = ConversationConfig(
            systemInstruction = Contents.of(systemPrompt),
            samplerConfig = SamplerConfig(
                topK = 5,
                topP = 0.9f,
                temperature = 0.3f
            )
        )

        currentEngine.createConversation(conversationConfig).use { conversation ->
            val response = conversation.sendMessage(userMessage)
            response.text ?: ""
        }
    }
}
