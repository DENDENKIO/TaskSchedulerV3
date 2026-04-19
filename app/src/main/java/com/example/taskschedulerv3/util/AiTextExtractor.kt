package com.example.taskschedulerv3.util

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * MediaPipe GenAI Tasks を使用してオンデバイスLLM推論を行うユーティリティ。
 */
object AiTextExtractor {
    private const val TAG = "AiTextExtractor"
    private var llmInference: LlmInference? = null

    /**
     * AIエンジンが既に初期化済みか確認します。
     */
    fun isInitialized(): Boolean = llmInference != null

    /**
     * AIエンジンを初期化します。
     * AI推論を行う画面を開く際に呼び出します。
     */
    fun initialize(context: Context) {
        if (llmInference != null) return
        
        try {
            val modelPath = AiModelManager.getModelPath(context)
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(512) // 出力の最大長
                .build()
            
            llmInference = LlmInference.createFromOptions(context, options)
            Log.d(TAG, "MediaPipe LLM Inference initialized successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "MediaPipe LLM initialization failed", e)
        }
    }

    /**
     * AIエンジンを解放します。
     * 画面を閉じる際に呼び出し、RAMを解放します。
     */
    fun close() {
        llmInference?.close()
        llmInference = null
        Log.d(TAG, "MediaPipe LLM Inference closed.")
    }

    /**
     * OCRテキストから予定情報をJSON形式で抽出します。
     */
    suspend fun extractScheduleInfo(ocrText: String): String? = withContext(Dispatchers.IO) {
        val llm = llmInference ?: run {
            Log.e(TAG, "LLM is not initialized. Call initialize() first.")
            return@withContext null
        }

        // JSONのみを出力させるための厳密なプロンプト
        val prompt = """
            以下のテキストは、書類をOCRで読み取ったものです。この内容から、予定の「タイトル」「日付」「開始時間」「終了時間」「要約」を抽出し、以下のJSONフォーマットのみで出力してください。Markdownの装飾(```jsonなど)や説明は一切含めないでください。

            【出力フォーマット】
            { "title": "...", "date": "YYYY-MM-DD", "start_time": "HH:mm", "end_time": "HH:mm", "summary": "..." }
            ※不明な項目は "" (空文字) にしてください。

            【入力テキスト】
            $ocrText
        """.trimIndent()

        try {
            // generateResponse() は同期ブロッキング処理のため IOスレッドで実行
            val response = llm.generateResponse(prompt)
            Log.d(TAG, "LLM Raw Response: $response")
            
            // LLMが余計な言葉（「はい、出力します」等）を付けた場合のためのJSON抽出
            extractJsonString(response)
        } catch (e: Exception) {
            Log.e(TAG, "Error generating LLM response", e)
            null
        }
    }

    /**
     * 文字列から { } で囲まれたJSON部分だけを抜き出す安全策
     */
    private fun extractJsonString(response: String): String {
        val startIndex = response.indexOf("{")
        val endIndex = response.lastIndexOf("}")
        if (startIndex != -1 && endIndex != -1 && startIndex < endIndex) {
            return response.substring(startIndex, endIndex + 1)
        }
        return response
    }
}
