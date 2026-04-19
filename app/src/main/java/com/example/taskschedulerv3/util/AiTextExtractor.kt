package com.example.taskschedulerv3.util

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * MediaPipe GenAI Tasks を使用してオンデバイスLLM推論を行うユーティリティ。
 * 【進化版】OCRテキストの自動修正・要約およびチャット意図解析をサポート。
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
     */
    fun initialize(context: Context) {
        if (llmInference != null) return
        try {
            val modelPath = AiModelManager.getModelPath(context)
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(1024) // 要約文が長くなることを考慮して拡張
                .build()
            
            llmInference = LlmInference.createFromOptions(context, options)
            Log.d(TAG, "MediaPipe LLM Inference initialized successfully. (maxTokens=1024)")
        } catch (e: Exception) {
            Log.e(TAG, "MediaPipe LLM initialization failed", e)
        }
    }

    /**
     * AIエンジンを解放します。
     */
    fun close() {
        llmInference?.close()
        llmInference = null
        Log.d(TAG, "MediaPipe LLM Inference closed.")
    }

    /**
     * 【修正】OCRテキストの解析 ＋ 自動修正・要約機能
     */
    suspend fun extractScheduleInfo(ocrText: String): String? = withContext(Dispatchers.IO) {
        val llm = llmInference ?: run {
            Log.e(TAG, "LLM is not initialized. Call initialize() first.")
            return@withContext null
        }

        // 誤字脱字の修正と要約をプロンプトで明示的に指示
        val prompt = """
            以下のテキストは書類をOCRで読み取ったものですが、誤字脱字や不要な記号が含まれている可能性があります。
            このテキストを「正しい日本語に修正・要約」した上で、予定の情報を抽出し、以下のJSONフォーマットのみで出力してください。
            Markdownの装飾(```jsonなど)は不要です。

            【出力フォーマット】
            {
              "title": "予定のタイトル",
              "date": "YYYY-MM-DD",
              "start_time": "HH:mm",
              "end_time": "HH:mm",
              "summary": "OCRテキストをわかりやすく修正・要約した文章"
            }
            ※不明な項目は "" にすること。

            【入力テキスト】
            $ocrText
        """.trimIndent()

        try {
            val response = llm.generateResponse(prompt)
            Log.d(TAG, "LLM Extraction Response: $response")
            extractJsonString(response)
        } catch (e: Exception) {
            Log.e(TAG, "Error generating LLM response", e)
            null
        }
    }

    /**
     * 【新規】チャット（自然文）から「日付」と「キーワード」を抽出する
     */
    suspend fun parseChatIntent(query: String, currentDate: String): String? = withContext(Dispatchers.IO) {
        val llm = llmInference ?: run {
            Log.e(TAG, "LLM is not initialized. Call initialize() first.")
            return@withContext null
        }

        val prompt = """
            今日は $currentDate です。
            ユーザーの質問から、検索したい予定の「日付」と「キーワード」を抽出し、以下のJSONのみを出力してください。Markdown装飾は不要です。

            【出力フォーマット】
            { "target_date": "YYYY-MM-DD", "keyword": "会議" }
            ※日付が特定できない場合は target_date を "" に、特定のキーワードがない場合は keyword を "" にすること。

            【質問】
            $query
        """.trimIndent()

        try {
            val response = llm.generateResponse(prompt)
            Log.d(TAG, "LLM Intent Parse Response: $response")
            extractJsonString(response)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing chat intent", e)
            null
        }
    }

    /**
     * 文字列から { } で囲まれたJSON部分だけを抜き出す
     */
    private fun extractJsonString(response: String): String {
        val startIndex = response.indexOf("{")
        val endIndex = response.lastIndexOf("}")
        if (startIndex != -1 && endIndex != -1 && startIndex < endIndex) {
            return response.substring(startIndex, endIndex + 1)
        }
        return "" // JSONとして成立していない場合は空文字を返す
    }
}
