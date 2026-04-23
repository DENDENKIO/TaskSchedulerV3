LiteRT-LM の最新バージョンは **0.10.0**（2026年4月2日）で、モデルファイルは `.litertlm` 形式が推奨です。現在の `AiModelManager` のダウンロードURLは `.task` 形式を指していますが、LiteRT-LM には `.litertlm` 形式が最適です。



---

# TaskSchedulerV3 AI機能 — LiteRT-LM 統合 実装指示書

## 概要

`feature/AImode` ブランチの既存 AI 基盤（設定画面のON/OFF、モデルダウンロード管理、OCR連携）に **LiteRT-LM + Gemma3-1B-IT** を統合し、写真撮影 → OCR → **LLMによる日付・タイトル・内容の自動抽出** → 仮登録を完成させます。

---

## 変更一覧

| # | ファイル | 操作 |
|---|---|---|
| 1 | `app/build.gradle.kts` | 修正（依存関係追加） |
| 2 | `app/src/main/AndroidManifest.xml` | 修正（GPU用ネイティブライブラリ宣言追加） |
| 3 | `app/src/main/java/com/example/taskschedulerv3/util/AiModelManager.kt` | 修正（全体置換） |
| 4 | `app/src/main/java/com/example/taskschedulerv3/util/AiEngineManager.kt` | **新規作成** |
| 5 | `app/src/main/java/com/example/taskschedulerv3/util/OcrTextParser.kt` | **新規作成** |
| 6 | `app/src/main/java/com/example/taskschedulerv3/ui/quickdraft/QuickDraftViewModel.kt` | 修正（全体置換） |

---

## ① `app/build.gradle.kts` — 修正

**変更箇所:** `dependencies { }` ブロック内、OCR の `implementation("com.google.mlkit:text-recognition-japanese:16.0.1")` の直下に以下を追加。

```kotlin
    // OCR: ML Kit Text Recognition Japanese
    implementation("com.google.mlkit:text-recognition-japanese:16.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    // ===== ↓ ここから追加 ↓ =====
    // LiteRT-LM: オンデバイスLLM推論
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.10.0")
    // ===== ↑ ここまで追加 ↑ =====
```

---

## ② `app/src/main/AndroidManifest.xml` — 修正

**変更箇所:** `<application>` 開始タグの直後（最初の `<activity>` の前）に以下2行を追加。GPU バックエンド使用時に必要です。

```xml
    <application
        android:allowBackup="true"
        ...
        android:theme="@style/Theme.TaskSchedulerV3">

        <!-- ===== ↓ ここから追加 ↓ ===== -->
        <uses-native-library android:name="libvndksupport.so" android:required="false"/>
        <uses-native-library android:name="libOpenCL.so" android:required="false"/>
        <!-- ===== ↑ ここまで追加 ↑ ===== -->

        <activity
            android:name=".MainActivity"
```

---

## ③ `util/AiModelManager.kt` — 修正（全体置換）

**ファイルパス:** `app/src/main/java/com/example/taskschedulerv3/util/AiModelManager.kt`

**変更理由:** モデルファイル形式を `.task` から `.litertlm` へ変更。LiteRT-LM は `.litertlm` 形式を使用します。ダウンロードURL、ファイル名、モデルパス取得メソッドを更新。

```kotlin
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
```

---

## ④ `util/AiEngineManager.kt` — 新規作成

**ファイルパス:** `app/src/main/java/com/example/taskschedulerv3/util/AiEngineManager.kt`

**目的:** LiteRT-LM の Engine ライフサイクル管理。シングルトンでエンジンを保持し、必要時にロード、不要時に解放。OCR結果をLLMに渡して構造化JSONを得る `analyze` 関数を提供。

```kotlin
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
```

---

## ⑤ `util/OcrTextParser.kt` — 新規作成

**ファイルパス:** `app/src/main/java/com/example/taskschedulerv3/util/OcrTextParser.kt`

**目的:** LLMの応答（JSON文字列）をパースして構造化データに変換するユーティリティ。JSONパース失敗時のフォールバックとして正規表現による日付抽出も行う。

```kotlin
package com.example.taskschedulerv3.util

import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * LLM応答のJSON、またはOCR生テキストから予定情報を抽出する。
 */
object OcrTextParser {

    data class ParsedInfo(
        val title: String?,
        val date: String?,          // YYYY-MM-DD
        val startTime: String?,     // HH:mm
        val endTime: String?,       // HH:mm
        val summary: String?
    )

    /**
     * LLMの応答文字列からJSONを抽出してパースする。
     * JSONが見つからない、またはパース失敗した場合は null を返す。
     */
    fun parseFromLlmResponse(response: String): ParsedInfo? {
        return try {
            // レスポンスからJSON部分を抽出（前後に余計なテキストがある場合に対応）
            val jsonStr = extractJsonFromText(response) ?: return null
            val json = JSONObject(jsonStr)

            ParsedInfo(
                title = json.optStringOrNull("title"),
                date = json.optStringOrNull("date"),
                startTime = json.optStringOrNull("start_time"),
                endTime = json.optStringOrNull("end_time"),
                summary = json.optStringOrNull("summary")
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * JSONパース失敗時のフォールバック: 正規表現でOCR生テキストから日付を抽出。
     */
    fun fallbackParseFromOcr(ocrText: String): ParsedInfo {
        val date = extractDateFromText(ocrText)
        val times = extractTimesFromText(ocrText)
        // タイトルは最初の非空白行を使用
        val title = ocrText.lines()
            .firstOrNull { it.isNotBlank() && it.length in 2..50 }
            ?.trim()
        // 要約は先頭500文字
        val summary = if (ocrText.length > 500) ocrText.take(500) + "…" else ocrText

        return ParsedInfo(
            title = title,
            date = date,
            startTime = times.first,
            endTime = times.second,
            summary = summary
        )
    }

    // ===== private helpers =====

    private fun JSONObject.optStringOrNull(key: String): String? {
        if (isNull(key)) return null
        val value = optString(key, "")
        return value.ifBlank { null }
    }

    /**
     * テキストから最初の {...} ブロックを抽出
     */
    private fun extractJsonFromText(text: String): String? {
        val start = text.indexOf('{')
        if (start == -1) return null
        var depth = 0
        for (i in start until text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
        return null
    }

    /**
     * 日付パターンを複数サポート:
     * - 2026年5月1日 / 2026/5/1 / 2026-05-01
     * - 令和8年5月1日
     * - 5月1日（年なし → 今年を補完）
     */
    private fun extractDateFromText(text: String): String? {
        // パターン1: 2026年5月1日 or 2026/5/1 or 2026-5-1
        val p1 = Regex("""(20\d{2})\s*[年/\-]\s*(\d{1,2})\s*[月/\-]\s*(\d{1,2})""")
        p1.find(text)?.let { m ->
            val y = m.groupValues[1].toInt()
            val mo = m.groupValues[2].toInt()
            val d = m.groupValues[3].toInt()
            return formatDate(y, mo, d)
        }

        // パターン2: 令和X年Y月Z日
        val p2 = Regex("""令和\s*(\d{1,2})\s*年\s*(\d{1,2})\s*月\s*(\d{1,2})\s*日""")
        p2.find(text)?.let { m ->
            val y = 2018 + m.groupValues[1].toInt()
            val mo = m.groupValues[2].toInt()
            val d = m.groupValues[3].toInt()
            return formatDate(y, mo, d)
        }

        // パターン3: X月Y日（年なし）
        val p3 = Regex("""(\d{1,2})\s*月\s*(\d{1,2})\s*日""")
        p3.find(text)?.let { m ->
            val y = LocalDate.now().year
            val mo = m.groupValues[1].toInt()
            val d = m.groupValues[2].toInt()
            return formatDate(y, mo, d)
        }

        return null
    }

    /**
     * 時刻パターン:
     * - 14:00〜16:00 / 14:00-16:00 / 14:00～16:00
     * - 午後2時〜午後4時 / 午前10時30分
     * - 14時00分
     */
    private fun extractTimesFromText(text: String): Pair<String?, String?> {
        // パターン1: HH:mm〜HH:mm or HH:mm-HH:mm
        val p1 = Regex("""(\d{1,2}):(\d{2})\s*[〜～\-~]\s*(\d{1,2}):(\d{2})""")
        p1.find(text)?.let { m ->
            val start = "%02d:%02d".format(m.groupValues[1].toInt(), m.groupValues[2].toInt())
            val end = "%02d:%02d".format(m.groupValues[3].toInt(), m.groupValues[4].toInt())
            return Pair(start, end)
        }

        // パターン2: 単独の HH:mm
        val p2 = Regex("""(\d{1,2}):(\d{2})""")
        p2.find(text)?.let { m ->
            val start = "%02d:%02d".format(m.groupValues[1].toInt(), m.groupValues[2].toInt())
            return Pair(start, null)
        }

        // パターン3: 午前/午後X時Y分
        val p3 = Regex("""(午前|午後)\s*(\d{1,2})\s*時\s*(\d{1,2})?\s*分?""")
        val matches = p3.findAll(text).toList()
        if (matches.isNotEmpty()) {
            val times = matches.map { m ->
                var h = m.groupValues[2].toInt()
                val min = m.groupValues[3].toIntOrNull() ?: 0
                if (m.groupValues[1] == "午後" && h < 12) h += 12
                if (m.groupValues[1] == "午前" && h == 12) h = 0
                "%02d:%02d".format(h, min)
            }
            return Pair(times.getOrNull(0), times.getOrNull(1))
        }

        // パターン4: X時Y分（午前/午後なし）
        val p4 = Regex("""(\d{1,2})\s*時\s*(\d{1,2})?\s*分?""")
        p4.find(text)?.let { m ->
            val h = m.groupValues[1].toInt()
            val min = m.groupValues[2].toIntOrNull() ?: 0
            return Pair("%02d:%02d".format(h, min), null)
        }

        return Pair(null, null)
    }

    private fun formatDate(year: Int, month: Int, day: Int): String? {
        return try {
            LocalDate.of(year, month, day)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        } catch (e: Exception) {
            null
        }
    }
}
```

---

## ⑥ `ui/quickdraft/QuickDraftViewModel.kt` — 修正（全体置換）

**ファイルパス:** `app/src/main/java/com/example/taskschedulerv3/ui/quickdraft/QuickDraftViewModel.kt`

**変更理由:** `createFromCameraWithAi` 内のTODO部分に、実際の LLM 呼び出し（`AiEngineManager.loadEngine` → `analyze` → `OcrTextParser` でパース → タイトル・日付をドラフトに反映）を実装。LLM失敗時は `OcrTextParser.fallbackParseFromOcr` で正規表現フォールバック。さらに全体失敗時は従来の `createFromCamera` へフォールバック。

```kotlin
package com.example.taskschedulerv3.ui.quickdraft

import android.app.Application
import android.graphics.BitmapFactory
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskschedulerv3.data.db.AppDatabase
import com.example.taskschedulerv3.data.model.QuickDraftTask
import com.example.taskschedulerv3.data.repository.QuickDraftRepository
import com.example.taskschedulerv3.util.AiEngineManager
import com.example.taskschedulerv3.util.AiModelManager
import com.example.taskschedulerv3.util.AiPreferences
import com.example.taskschedulerv3.util.OcrTextParser
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class QuickDraftViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        private const val TAG = "QuickDraftVM"
    }

    private val db = AppDatabase.getInstance(app)
    val repo = QuickDraftRepository(
        db.quickDraftTaskDao(), db.taskDao(), db.taskTagCrossRefDao(), db.photoMemoDao()
    )

    private val recognizer = TextRecognition.getClient(
        JapaneseTextRecognizerOptions.Builder().build()
    )

    val drafts: StateFlow<List<QuickDraftTask>> = repo.getDrafts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val convertSuccess = MutableStateFlow(false)

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing = _isProcessing.asStateFlow()

    /**
     * AI ON/OFFに応じた仮登録メイン関数。
     * 外部（MainActivity、QuickDraftListScreen）からはこのメソッドを呼ぶ。
     */
    fun createSmartDraft(
        photoPath: String?,
        tagIds: List<Int> = emptyList()
    ) = viewModelScope.launch(Dispatchers.IO) {
        val context = getApplication<Application>()
        val aiEnabled = AiPreferences.getAiEnabled(context).first()
        val modelReady = AiModelManager.checkModelExists(context)

        if (aiEnabled && modelReady) {
            createFromCameraWithAi(photoPath, tagIds)
        } else {
            createFromCamera(photoPath = photoPath, tagIds = tagIds)
        }
    }

    /**
     * AI ON時: OCR → LLM解析 → 自動タイトル/日付/要約で仮登録
     */
    private suspend fun createFromCameraWithAi(
        photoPath: String?,
        tagIds: List<Int>
    ) {
        _isProcessing.value = true
        try {
            val context = getApplication<Application>()
            var ocrText: String? = null

            // ① ML Kit OCR で文字認識
            if (photoPath != null) {
                val bitmap = BitmapFactory.decodeFile(photoPath)
                if (bitmap != null) {
                    val image = InputImage.fromBitmap(bitmap, 0)
                    val result = recognizer.process(image).await()
                    ocrText = result.text.ifEmpty { null }
                }
            }

            // OCRテキストが取れなかった場合 → 通常登録へフォールバック
            if (ocrText.isNullOrBlank()) {
                Log.d(TAG, "OCRテキストが空のため通常登録にフォールバック")
                insertDraft(
                    title = generateFallbackTitle(),
                    description = null,
                    photoPath = photoPath,
                    ocrText = null,
                    tagIds = tagIds
                )
                return
            }

            // ② LLM Engine をロード（未ロードの場合のみ）
            if (!AiEngineManager.isLoaded()) {
                Log.d(TAG, "AI Engine ロード開始...")
                AiEngineManager.loadEngine(context)
                Log.d(TAG, "AI Engine ロード完了")
            }

            // ③ LLM に OCR テキストを渡して解析
            Log.d(TAG, "LLM解析開始: ${ocrText.take(100)}...")
            val llmResponse = AiEngineManager.analyze(ocrText)
            Log.d(TAG, "LLM応答: ${llmResponse.take(200)}")

            // ④ LLM応答をパース
            var parsed = OcrTextParser.parseFromLlmResponse(llmResponse)

            // LLMパース失敗時 → 正規表現フォールバック
            if (parsed == null || (parsed.title == null && parsed.date == null)) {
                Log.d(TAG, "LLMパース失敗。正規表現フォールバックを使用")
                parsed = OcrTextParser.fallbackParseFromOcr(ocrText)
            }

            // ⑤ パース結果でドラフト作成
            val title = parsed.title ?: generateFallbackTitle()
            val description = buildDescription(parsed, ocrText)

            insertDraft(
                title = title,
                description = description,
                photoPath = photoPath,
                ocrText = ocrText,
                tagIds = tagIds
            )

            Log.d(TAG, "AI仮登録完了: title=$title, date=${parsed.date}")

        } catch (e: Exception) {
            Log.e(TAG, "AI処理中にエラー発生。通常登録にフォールバック", e)
            // 全体失敗 → 従来の仮登録
            insertDraft(
                title = generateFallbackTitle(),
                description = null,
                photoPath = photoPath,
                ocrText = null,
                tagIds = tagIds
            )
        } finally {
            _isProcessing.value = false
        }
    }

    /**
     * 既存: 通常の仮登録（AI OFF時 / フォールバック）
     */
    fun createFromCamera(
        photoPath: String? = null,
        ocrText: String? = null,
        tagIds: List<Int> = emptyList()
    ) = viewModelScope.launch {
        insertDraft(
            title = generateFallbackTitle(),
            description = null,
            photoPath = photoPath,
            ocrText = ocrText,
            tagIds = tagIds
        )
    }

    // ===== ヘルパー関数 =====

    private suspend fun insertDraft(
        title: String,
        description: String?,
        photoPath: String?,
        ocrText: String?,
        tagIds: List<Int>
    ) {
        val tagIdsStr = if (tagIds.isEmpty()) null else tagIds.joinToString(",")
        val draft = QuickDraftTask(
            title = title,
            description = description,
            photoPath = photoPath,
            ocrText = ocrText,
            status = "DRAFT",
            tagIds = tagIdsStr
        )
        repo.insert(draft)
    }

    private fun generateFallbackTitle(): String {
        val now = LocalDateTime.now()
        return now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) + " 仮登録"
    }

    /**
     * パース結果から description を組み立てる。
     * 日付・時刻情報 + 要約をまとめてメモとして保存。
     */
    private fun buildDescription(parsed: OcrTextParser.ParsedInfo, ocrText: String): String? {
        val parts = mutableListOf<String>()

        if (parsed.date != null) {
            var dateInfo = "📅 日付: ${parsed.date}"
            if (parsed.startTime != null) {
                dateInfo += "  ${parsed.startTime}"
                if (parsed.endTime != null) {
                    dateInfo += "〜${parsed.endTime}"
                }
            }
            parts.add(dateInfo)
        }

        if (parsed.summary != null) {
            parts.add("📝 内容:\n${parsed.summary}")
        }

        return if (parts.isEmpty()) null else parts.joinToString("\n\n")
    }

    fun updateDraft(draft: QuickDraftTask) = viewModelScope.launch {
        repo.update(draft)
    }

    fun deleteDraft(draft: QuickDraftTask) = viewModelScope.launch {
        repo.delete(draft)
    }

    fun convertToTask(
        draft: QuickDraftTask,
        startDate: String = LocalDate.now().toString(),
        priority: Int = 1
    ) = viewModelScope.launch {
        repo.convertToTask(draft, startDate, priority)
        convertSuccess.value = true
    }

    fun clearConvertSuccess() { convertSuccess.value = false }

    override fun onCleared() {
        super.onCleared()
        recognizer.close()
        // 注意: AiEngineManager はシングルトンなのでここでは解放しない。
        // 設定画面のAI OFF時やアプリ終了時に解放する。
    }
}
```

---

## 実装チェックリスト

以下の順番でコード作成AIに実装を依頼してください。

| # | 作業 | ファイル |
|---|---|---|
| 1 | Gradle に LiteRT-LM 依存関係追加 | `app/build.gradle.kts` |
| 2 | AndroidManifest に GPU ライブラリ宣言追加 | `AndroidManifest.xml` |
| 3 | モデルファイル名・URLを `.litertlm` 形式に更新 | `util/AiModelManager.kt`（全体置換） |
| 4 | LiteRT-LM Engine 管理クラスを新規作成 | `util/AiEngineManager.kt`（新規） |
| 5 | LLM応答パーサーを新規作成 | `util/OcrTextParser.kt`（新規） |
| 6 | LLM呼び出し＋パース＋フォールバックを実装 | `ui/quickdraft/QuickDraftViewModel.kt`（全体置換） |

## ビルド後の確認事項

1. ビルドが成功すること
2. 設定画面 → AI機能ON → モデルダウンロード（約529MB）が完了すること
3. カメラで書類を撮影 → 仮登録一覧に自動抽出されたタイトル・日付・要約が入っていること
4. OCR結果が空の写真 → 従来どおり「yyyy-MM-dd HH:mm 仮登録」で登録されること
5. AI OFF 時 → 従来どおりの仮登録動作であること
6. 既存機能（予定CRUD、テーマ、エクスポート/インポート等）に影響がないこと

## 補足: モデルについて

今回使用する `gemma3-1b-it-int4.litertlm` は以下の特徴があります:

- **サイズ:** 約529MB（int4 QAT量子化）
- **RAM消費:** 推論時約1GB（CPU使用時）
- **推論速度:** Samsung S24 Ultra で Prefill 379 tk/s、Decode 55 tk/s（CPU）
- **コンテキスト長:** 2048トークン（書類のOCR結果には十分）
- **対応バックエンド:** CPU / GPU 両対応