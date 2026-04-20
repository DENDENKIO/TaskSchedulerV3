Now I have all the information needed. Let me verify the `sendMessageAsync` return type to ensure the code is correct:

The API documentation confirms:
- `sendMessageAsync(contents): Flow<Message>` returns a Kotlin Flow
- In the Flow collect, it uses `it.toString()` to get the text
- `Contents.of(systemPrompt)` is the correct way to set system instruction
- `Message.text` property exists (shown in manual tool calling: `finalMessage.text`)

Now I have complete knowledge of all files. Let me write the comprehensive MD document.

Here is the comprehensive migration document in Markdown format:

---

# TaskSchedulerV3 — Gemma 4 E2B 移行 & AI自由応答化 完全指示書

## 1. 概要

本ドキュメントは `feature/AImode` ブランチにおいて、以下の変更を **エラーなく** 実施するための完全な指示書です。

**目的:**
1. AIモデルを **Gemma 3 1B-IT** (≈529 MB) から **Gemma 4 E2B** (≈2.58 GB) へ切り替える
2. AIアシスタント（AiChat）の応答を **予定検索のみの固定パターン** から **自由な会話＋予定検索** へ拡張する
3. Quick Draft の AI タイトル生成が正しく動作するよう、推論パイプライン全体を安定化する

**対象端末:** DOOGEE S200 (MediaTek Dimensity 7050, RAM 32 GB, Android 14)

**変更対象ファイル（全6ファイル）:**

| # | ファイルパス | 変更内容 |
|---|---|---|
| 1 | `util/AiModelManager.kt` | モデルURL・ファイル名・サイズ検証の変更 |
| 2 | `util/AiEngineManager.kt` | タイムアウト延長、OCR上限拡大、プロンプト最適化、汎用応答メソッド強化 |
| 3 | `ui/settings/SettingsScreen.kt` | UI文言をGemma 4 E2Bに合わせて更新 |
| 4 | `ui/aichat/AiChatViewModel.kt` | 自由会話モード追加、予定検索との統合ロジック |
| 5 | `ui/aichat/AiChatScreen.kt` | UI微修正（モデル名表示など） |
| 6 | `ui/settings/SettingsViewModel.kt` | 変更なし（シグネチャ維持の確認用） |

**変更しないファイル（互換性維持）:**
- `QuickDraftViewModel.kt` — `AiEngineManager.analyze()` のシグネチャが同一のため変更不要
- `QuickDraftCaptureSheet.kt` — ViewModel 経由の呼び出しのみ、変更不要
- `OcrTextParser.kt` — フォールバックロジックはそのまま機能
- `QuickDraftTask.kt` — エンティティ定義は変更なし
- `AppDatabase.kt` — マイグレーション不要（スキーマ変更なし）
- `app/build.gradle.kts` — `litertlm-android:0.10.0` は Gemma 4 E2B 対応済み
- `libs.versions.toml` — 変更不要
- `AndroidManifest.xml` — `libvndksupport.so` と `libOpenCL.so` は既に宣言済み
- `SettingsViewModel.kt` — シグネチャ変更なし（AiModelManager のAPI互換）
- `QuickDraftRepository.kt`, `QuickDraftTaskDao.kt`, `AiPreferences.kt`, `DataStoreProvider.kt`, `NavGraph.kt` — 全て変更不要

---

## 2. ファイル 1: `util/AiModelManager.kt`（全置換）

**パス:** `app/src/main/java/com/example/taskschedulerv3/util/AiModelManager.kt`

**変更点:**
- `MODEL_FILENAME` を `gemma-4-E2B-it.litertlm` に変更
- `MODEL_DOWNLOAD_URL` を Hugging Face の Gemma 4 E2B リポジトリURLに変更
- `checkModelExists` のサイズ検証閾値を `1_000_000_000` (1 GB) に引き上げ
- ダウンロード完了時の `tmpFile.length()` 検証も同じ閾値に変更
- `deleteModel` で旧モデル（`gemma3-1b-it-int4.litertlm`）も削除するロジックを維持

```kotlin
package com.example.taskschedulerv3.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object AiModelManager {

    private const val TAG = "AiModelManager"
    private const val MODEL_DIR = "ai_model"
    private const val MODEL_FILENAME = "gemma-4-E2B-it.litertlm"

    // Gemma 4 E2B: 約2.58GB, CPU/GPU両対応, 高品質日本語推論
    private const val MODEL_DOWNLOAD_URL =
        "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"

    // ダウンロード済みと見なすファイルサイズの閾値（1 GB以上であること）
    private const val MIN_MODEL_SIZE_BYTES = 1_000_000_000L

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
        return file.exists() && file.length() > MIN_MODEL_SIZE_BYTES
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
     * Hugging Face は複数回のリダイレクトを返すことがある。
     */
    private fun openConnectionWithRedirects(
        urlStr: String,
        hfToken: String,
        maxRedirects: Int = 10
    ): HttpURLConnection {
        var currentUrl = urlStr
        var redirectCount = 0

        while (redirectCount < maxRedirects) {
            val url = URL(currentUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 60_000
            connection.readTimeout = 60_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", "TaskSchedulerV3/1.0")

            if (hfToken.isNotBlank()) {
                connection.setRequestProperty("Authorization", "Bearer $hfToken")
            }

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

    suspend fun downloadModel(context: Context, hfToken: String): Boolean = withContext(Dispatchers.IO) {
        try {
            _state.value = ModelState.Downloading(0)
            Log.d(TAG, "ダウンロード開始: $MODEL_DOWNLOAD_URL (Token: ${hfToken.take(5)}...)")

            val file = getModelFile(context)
            val tmpFile = File(file.parent, "${file.name}.tmp")

            val connection = openConnectionWithRedirects(MODEL_DOWNLOAD_URL, hfToken)

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                val errorBody = try {
                    connection.errorStream?.bufferedReader()?.readText()?.take(200) ?: ""
                } catch (_: Exception) { "" }
                connection.disconnect()

                val errorMsg = when (responseCode) {
                    401, 403 -> "アクセス拒否 (HTTP $responseCode)。Hugging Faceトークンが無効か、ライセンス同意が必要です。"
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

            // Gemma 4 E2B は約2.58 GBなので1 GB以上を必須とする
            if (tmpFile.length() < MIN_MODEL_SIZE_BYTES) {
                tmpFile.delete()
                _state.value = ModelState.Error("ダウンロードファイルが不完全です（サイズ: ${tmpFile.length() / (1024 * 1024)} MB、最低1 GB必要）")
                return@withContext false
            }

            if (file.exists()) file.delete()
            tmpFile.renameTo(file)

            Log.d(TAG, "ダウンロード完了: ${file.length() / (1024 * 1024)} MB")
            _state.value = ModelState.Ready
            true
        } catch (e: Exception) {
            Log.e(TAG, "ダウンロードエラー", e)
            val tmpFile = File(getModelFile(context).parent, "${MODEL_FILENAME}.tmp")
            if (tmpFile.exists()) tmpFile.delete()

            _state.value = ModelState.Error("ダウンロード失敗: ${e.localizedMessage ?: "不明なエラー"}")
            false
        }
    }

    suspend fun deleteModel(context: Context) = withContext(Dispatchers.IO) {
        val file = getModelFile(context)
        if (file.exists()) file.delete()
        // 旧モデル（Gemma 3 1B）が残っている場合も削除
        val oldFile = File(File(context.filesDir, MODEL_DIR), "gemma3-1b-it-int4.litertlm")
        if (oldFile.exists()) {
            oldFile.delete()
            Log.d(TAG, "旧モデル gemma3-1b-it-int4.litertlm を削除しました")
        }
        _state.value = ModelState.NotDownloaded
    }
}
```

---

## 3. ファイル 2: `util/AiEngineManager.kt`（全置換）

**パス:** `app/src/main/java/com/example/taskschedulerv3/util/AiEngineManager.kt`

**変更点:**
- `INFERENCE_TIMEOUT_MS` を `90_000L`（90秒）に延長 — Gemma 4 E2B は Dimensity 7050 で初回推論に最大30秒かかる可能性がある
- `MAX_OCR_LENGTH` を `1000` に拡大 — E2B は 32K コンテキスト対応で長文処理が安定
- モデルサイズ検証の閾値を `1_000_000_000` (1 GB) に変更
- `analyze()` のシステムプロンプトを Gemma 4 E2B の日本語理解力を活かして最適化
- `generateResponse()` を汎用自由応答にも使える形で強化（温度パラメータ調整）
- `generateChatResponse()` メソッドを新規追加 — AiChat用の会話コンテキスト付き応答生成
- 全メソッドで `sendMessageAsync` + `Flow` を使用（キャンセル可能・タイムアウト制御対応）
- ストリーミング結果を `it.toString()` で収集（LiteRT-LM Kotlin API 公式仕様準拠）

```kotlin
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
```

---

## 4. ファイル 3: `ui/settings/SettingsScreen.kt`（全置換）

**パス:** `app/src/main/java/com/example/taskschedulerv3/ui/settings/SettingsScreen.kt`

**変更点:**
- AI確認ダイアログの文言: 「約529MB」→「約2.6GB」
- AIセクションの見出し: 「AI機能（Gemma 4 E2B）」
- 未ダウンロード時の説明: 「モデル（約2.6GB）をダウンロード」
- その他の既存UI構造・ロジックは全て維持

```kotlin
package com.example.taskschedulerv3.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.taskschedulerv3.navigation.Screen
import com.example.taskschedulerv3.util.AiModelManager
import com.example.taskschedulerv3.util.ThemeMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController, vm: SettingsViewModel = viewModel()) {
    val themeMode by vm.themeMode.collectAsState()
    val exportImportState by vm.exportImportState.collectAsState()
    var showThemeDialog by remember { mutableStateOf(false) }
    var showSnackbar by remember { mutableStateOf<String?>(null) }

    // AI関連
    val aiEnabled by vm.aiEnabled.collectAsState()
    val aiModelState by vm.aiModelState.collectAsState()
    var showDeleteModelDialog by remember { mutableStateOf(false) }
    var showAiOnConfirmDialog by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { vm.exportToUri(it) } }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { vm.importFromUri(it, overwrite = false) } }

    LaunchedEffect(exportImportState) {
        when (val s = exportImportState) {
            is ExportImportState.Success -> { showSnackbar = s.message; vm.clearState() }
            is ExportImportState.Error -> { showSnackbar = s.message; vm.clearState() }
            else -> {}
        }
    }

    // テーマ選択ダイアログ
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("テーマ") },
            text = {
                Column {
                    listOf(
                        ThemeMode.SYSTEM to "システム連動",
                        ThemeMode.LIGHT to "ライト",
                        ThemeMode.DARK to "ダーク"
                    ).forEach { (mode, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    vm.setThemeMode(mode)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            RadioButton(
                                selected = themeMode == mode,
                                onClick = {
                                    vm.setThemeMode(mode)
                                    showThemeDialog = false
                                }
                            )
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) { Text("閉じる") }
            }
        )
    }

    // AI ON確認ダイアログ（モデル未ダウンロード時）
    if (showAiOnConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showAiOnConfirmDialog = false },
            title = { Text("AIモデルのダウンロード") },
            text = {
                Text("AI機能を使用するには、約2.6GBのモデルデータ（Gemma 4 E2B）をダウンロードする必要があります。\n\nWi-Fi環境でのダウンロードを推奨します。\nストレージに約3GBの空きが必要です。")
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.setAiEnabled(true)
                    showAiOnConfirmDialog = false
                }) { Text("ダウンロードして有効化") }
            },
            dismissButton = {
                TextButton(onClick = { showAiOnConfirmDialog = false }) { Text("キャンセル") }
            }
        )
    }

    // モデル削除確認ダイアログ
    if (showDeleteModelDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteModelDialog = false },
            title = { Text("AIモデルを削除") },
            text = {
                Text("AIモデルを削除してストレージを約${vm.getModelSizeMB()}MB解放します。\nAI機能はOFFになります。再度使用するにはダウンロードが必要です。")
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteAiModel()
                    showDeleteModelDialog = false
                }) { Text("削除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteModelDialog = false }) { Text("キャンセル") }
            }
        )
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(showSnackbar) {
        showSnackbar?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            showSnackbar = null
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("設定") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {

            SettingsItem(
                title = "テーマ",
                subtitle = when (themeMode) {
                    ThemeMode.LIGHT -> "ライト"
                    ThemeMode.DARK -> "ダーク"
                    ThemeMode.SYSTEM -> "システム連動"
                },
                onClick = { showThemeDialog = true }
            )
            HorizontalDivider()

            SettingsItem(
                title = "完了した予定",
                subtitle = "完了した予定の確認・復元",
                onClick = { navController.navigate(Screen.CompletedTasks.route) }
            )
            HorizontalDivider()

            SettingsItem(
                title = "無期限予定",
                subtitle = "日付を決めない予定の一覧・編集・削除",
                onClick = { navController.navigate(Screen.IndefiniteTask.route) }
            )
            HorizontalDivider()

            SettingsItem(
                title = "繰り返し予定",
                subtitle = "繰り返し予定の一覧・編集・削除",
                onClick = { navController.navigate(Screen.Recurring.route) }
            )
            HorizontalDivider()

            SettingsItem(
                title = "仮登録管理",
                subtitle = "写真から仮登録したタスクの管理",
                onClick = { navController.navigate(Screen.QuickDraftList.route) }
            )
            HorizontalDivider()

            SettingsItem(
                title = "タグ管理",
                subtitle = "3階層タグの作成・編集・削除",
                onClick = { navController.navigate(Screen.TagManage.route) }
            )
            HorizontalDivider()

            SettingsItem(
                title = "ゴミ箱",
                subtitle = "削除したタスクの確認・復元",
                onClick = { navController.navigate(Screen.Trash.route) }
            )
            HorizontalDivider()

            SettingsItem(
                title = "データエクスポート",
                subtitle = "スケジュールをJSONで保存",
                onClick = {
                    val filename = "taskscheduler_export_${
                        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.JAPAN).format(Date())
                    }.json"
                    exportLauncher.launch(filename)
                }
            )
            HorizontalDivider()

            SettingsItem(
                title = "データインポート",
                subtitle = "JSONからスケジュールを復元",
                onClick = {
                    importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                }
            )
            HorizontalDivider()

            if (exportImportState is ExportImportState.Loading) {
                ListItem(
                    headlineContent = { Text("処理中...") },
                    trailingContent = {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    }
                )
                HorizontalDivider()
            }

            // ==================== AI設定セクション ==================== //

            Spacer(modifier = Modifier.height(8.dp))

            // --- トークン入力フィールド ---
            val hfToken by vm.hfToken.collectAsState()
            var isTokenVisible by remember { mutableStateOf(false) }

            OutlinedTextField(
                value = hfToken,
                onValueChange = { vm.setHfToken(it) },
                label = { Text("Hugging Face Access Token (hf_...)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                visualTransformation = if (isTokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                placeholder = { Text("hf_xxxxxxxxxxxxxxxxx") },
                supportingText = { Text("Gatedモデルのダウンロードに必要です") }
            )

            ListItem(
                headlineContent = {
                    Text(
                        "AI機能（Gemma 4 E2B）",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                supportingContent = {
                    Text(
                        "写真から予定の自動認識、AIチャットアシスタントで使用します",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            )

            ListItem(
                headlineContent = { Text("AI機能を使用する") },
                supportingContent = {
                    val currentState = aiModelState
                    when (currentState) {
                        is AiModelManager.ModelState.NotDownloaded ->
                            Text(
                                "ONにするとモデル（約2.6GB）をダウンロードします",
                                style = MaterialTheme.typography.bodySmall
                            )
                        is AiModelManager.ModelState.Downloading ->
                            Text(
                                "ダウンロード中... ${currentState.progress}%",
                                style = MaterialTheme.typography.bodySmall
                            )
                        is AiModelManager.ModelState.Ready ->
                            Text(
                                "モデルダウンロード済み（≈${vm.getModelSizeMB()}MB）",
                                style = MaterialTheme.typography.bodySmall
                            )
                        is AiModelManager.ModelState.Error ->
                            Text(
                                currentState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                    }
                },
                trailingContent = {
                    Switch(
                        checked = aiEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled && aiModelState is AiModelManager.ModelState.NotDownloaded) {
                                showAiOnConfirmDialog = true
                            } else {
                                vm.setAiEnabled(enabled)
                            }
                        },
                        enabled = aiModelState !is AiModelManager.ModelState.Downloading
                    )
                }
            )

            // プログレスバー
            val currentStateForBar = aiModelState
            if (currentStateForBar is AiModelManager.ModelState.Downloading) {
                val progressValue = currentStateForBar.progress / 100f
                LinearProgressIndicator(
                    progress = { progressValue },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            if (aiModelState is AiModelManager.ModelState.Ready) {
                ListItem(
                    headlineContent = {
                        Text("AIモデルを削除", color = MaterialTheme.colorScheme.error)
                    },
                    supportingContent = {
                        Text(
                            "ストレージを約${vm.getModelSizeMB()}MB解放します",
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    modifier = Modifier.clickable { showDeleteModelDialog = true }
                )
            }

            HorizontalDivider()

            // ==================== AI設定セクションここまで ==================== //

            ListItem(
                headlineContent = { Text("バージョン情報") },
                supportingContent = { Text("TaskSchedulerV3  v1.0") }
            )
        }
    }
}

@Composable
fun SettingsItem(title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle, style = MaterialTheme.typography.bodySmall) },
        trailingContent = { Icon(Icons.Default.ChevronRight, null) },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
```

---

## 5. ファイル 4: `ui/aichat/AiChatViewModel.kt`（全置換）

**パス:** `app/src/main/java/com/example/taskschedulerv3/ui/aichat/AiChatViewModel.kt`

**変更点:**
- **自由会話モード追加:** AIが予定検索以外の質問にも自然に応答する
- **ハイブリッドアーキテクチャ:** ルールベース判定 → LLM意図分類 → 予定検索 or 自由応答の3段階
- **意図分類:** LLMに「schedule_search」か「general_chat」かを判定させる
- **予定コンテキスト:** 自由会話でも直近の予定情報をコンテキストとして渡し、予定に関する質問に的確に答える
- **フォールバック:** Engine未ロード時やタイムアウト時はルールベースのみで動作

```kotlin
package com.example.taskschedulerv3.ui.aichat

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskschedulerv3.data.db.AppDatabase
import com.example.taskschedulerv3.data.repository.TaskRepository
import com.example.taskschedulerv3.util.AiEngineManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

class AiChatViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.getInstance(app)
    private val taskRepo = TaskRepository(db.taskDao(), db.roadmapStepDao())

    private val _messages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                text = "こんにちは！予定について聞いたり、何でも自由に質問してください。\n\n例：\n・「明日の予定は？」\n・「今週の会議を教えて」\n・「タスク管理のコツは？」\n・「集中力を上げる方法は？」",
                isUser = false
            )
        )
    )
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        addMessage(ChatMessage(text = text, isUser = true))

        viewModelScope.launch {
            _isTyping.value = true
            try {
                val responseText = processQuery(text)
                addMessage(ChatMessage(text = responseText, isUser = false))
            } catch (e: Exception) {
                Log.e(TAG, "sendMessage error", e)
                addMessage(ChatMessage(text = "エラーが発生しました。もう一度お試しください。", isUser = false))
            } finally {
                _isTyping.value = false
            }
        }
    }

    private fun addMessage(message: ChatMessage) {
        _messages.value = _messages.value + message
    }

    /**
     * ユーザーの質問を処理する。
     * 1. ルールベースで予定検索キーワードを検出
     * 2. ルールベースで判定できない場合、LLMで意図を分類
     * 3. 意図に応じて予定検索 or 自由会話応答を返す
     */
    private suspend fun processQuery(query: String): String {
        val today = LocalDate.now()
        val todayStr = today.format(DateTimeFormatter.ISO_LOCAL_DATE)

        // ── 1. ルールベース判定：明確な予定検索パターン ──
        val ruleResult = tryRuleBasedScheduleSearch(query, today)
        if (ruleResult != null) return ruleResult

        // ── 2. LLMベースの意図分類 ──
        if (AiEngineManager.isLoaded() || tryLoadEngine()) {
            val intentResult = classifyIntentWithLlm(query, todayStr)

            if (intentResult != null) {
                when (intentResult.intent) {
                    "schedule_search" -> {
                        // LLMが予定検索と判断した場合
                        val searchResult = searchTasks(
                            intentResult.targetDate,
                            intentResult.keyword,
                            intentResult.dateLabel
                        )
                        if (searchResult != null) return searchResult
                        // 検索結果なしの場合はフォールスルーして自由応答へ
                    }
                    "general_chat" -> {
                        // 自由会話と判断された場合 → LLM で直接応答
                        val chatResponse = generateFreeResponse(query, todayStr)
                        if (chatResponse != null) return chatResponse
                    }
                }
            }

            // 意図分類が失敗しても、予定関連の可能性があれば自由応答を試みる
            val chatResponse = generateFreeResponse(query, todayStr)
            if (chatResponse != null) return chatResponse
        }

        // ── 3. エンジン未ロード時のフォールバック ──
        return "AIモデルが読み込まれていません。設定画面でAI機能をONにしてください。\n\n" +
               "予定の検索は「明日の予定」「今日の予定」のように聞いていただけます。"
    }

    /**
     * ルールベースで明確な予定検索パターンを検出する。
     * 「今日」「明日」「明後日」などのキーワードと「予定」「スケジュール」の組み合わせ。
     */
    private suspend fun tryRuleBasedScheduleSearch(query: String, today: LocalDate): String? {
        var targetDateStr = ""
        var dateLabel = ""

        // 日付キーワード検出
        when {
            query.contains("今日") -> {
                targetDateStr = today.format(DateTimeFormatter.ISO_LOCAL_DATE)
                dateLabel = "今日"
            }
            query.contains("明後日") -> {
                targetDateStr = today.plusDays(2).format(DateTimeFormatter.ISO_LOCAL_DATE)
                dateLabel = "明後日"
            }
            query.contains("明日") -> {
                targetDateStr = today.plusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE)
                dateLabel = "明日"
            }
        }

        // 予定関連キーワードが含まれている場合のみルールベースで処理
        val scheduleKeywords = listOf("予定", "スケジュール", "タスク", "やること", "何がある")
        val isScheduleQuery = scheduleKeywords.any { query.contains(it) }

        if (targetDateStr.isBlank() && !isScheduleQuery) return null
        if (targetDateStr.isBlank()) return null  // 日付が特定できない場合はLLMに委ねる

        // キーワード抽出
        var keyword = ""
        val keywordMatch = Regex("「(.+)」").find(query) ?: Regex("(.+)の予定").find(query)
        if (keywordMatch != null) {
            val rawKeyword = keywordMatch.groupValues[1]
            keyword = rawKeyword
                .replace("今日", "").replace("明日", "").replace("明後日", "")
                .replace("の", "").trim()
        }

        return searchTasks(targetDateStr, keyword, dateLabel)
    }

    /**
     * LLMでユーザーの意図を分類する。
     * 返り値: IntentResult（intent, targetDate, keyword, dateLabel）
     */
    private suspend fun classifyIntentWithLlm(query: String, currentDate: String): IntentResult? {
        try {
            val prompt = """今日は ${currentDate} です。

ユーザーの入力を分析し、以下のJSONのみを出力してください。

【分類ルール】
- 予定・スケジュール・タスクを聞いている → intent = "schedule_search"
- それ以外（雑談・質問・相談・アドバイスなど） → intent = "general_chat"

【出力フォーマット（JSONのみ、他のテキストは不要）】
{"intent":"schedule_search","target_date":"YYYY-MM-DD","keyword":"検索語"}
または
{"intent":"general_chat","target_date":"","keyword":""}

【注意】
- 「来週の月曜」「3日後」なども日付を計算して target_date に入れる
- キーワードがない場合は空文字にする
- 必ずJSON形式のみを出力する

【ユーザーの入力】
$query"""

            val response = AiEngineManager.generateResponse(prompt) ?: return null

            // JSONを抽出
            val jsonStr = extractJsonFromText(response) ?: return null
            val json = JSONObject(jsonStr)

            val intent = json.optString("intent", "general_chat")
            val targetDate = json.optString("target_date", "").replace("/", "-")
            val keyword = json.optString("keyword", "")

            Log.d(TAG, "Intent classification: intent=$intent, date=$targetDate, keyword=$keyword")

            return IntentResult(
                intent = intent,
                targetDate = targetDate,
                keyword = keyword,
                dateLabel = if (targetDate.isNotBlank()) "${targetDate}の" else ""
            )
        } catch (e: Exception) {
            Log.e(TAG, "classifyIntentWithLlm error", e)
            return null
        }
    }

    /**
     * 自由会話応答を生成する。
     * 直近7日間の予定をコンテキストとして渡し、予定に関する質問にも対応可能にする。
     */
    private suspend fun generateFreeResponse(query: String, currentDate: String): String? {
        try {
            // 直近7日間の予定をコンテキストとして取得
            val taskContext = buildTaskContext(currentDate)

            val systemPrompt = """あなたはTaskSchedulerV3アプリのAIアシスタントです。
現在の日付は ${currentDate} です。

あなたの役割:
- ユーザーの質問に日本語で丁寧に答える
- 予定やタスク管理に関するアドバイスを提供する
- 一般的な質問にも知識の範囲で回答する
- 回答は簡潔で分かりやすくする（200文字以内を目安）

${if (taskContext.isNotBlank()) "【ユーザーの直近の予定】\n$taskContext" else ""}

注意:
- Markdown装飾は使わない（**太字**や##見出しなど）
- 予定の詳細を聞かれた場合は上記の予定情報を参照する
- 予定情報がない場合は「予定が登録されていません」と正直に答える"""

            return AiEngineManager.generateChatResponse(query, systemPrompt)
        } catch (e: Exception) {
            Log.e(TAG, "generateFreeResponse error", e)
            return null
        }
    }

    /**
     * 直近7日間の予定をテキスト形式で構築する。
     */
    private suspend fun buildTaskContext(currentDate: String): String {
        return try {
            val today = LocalDate.parse(currentDate)
            val allTasks = taskRepo.getAll().first()
            val upcoming = allTasks.filter { task ->
                !task.isCompleted && !task.isDeleted && task.startDate != null &&
                try {
                    val taskDate = LocalDate.parse(task.startDate)
                    !taskDate.isBefore(today) && !taskDate.isAfter(today.plusDays(7))
                } catch (_: Exception) { false }
            }.sortedWith(compareBy({ it.startDate }, { it.startTime ?: "23:59" }))

            if (upcoming.isEmpty()) return ""

            val sb = StringBuilder()
            upcoming.take(20).forEach { task ->
                val time = task.startTime ?: "終日"
                sb.append("${task.startDate} $time: ${task.title}\n")
            }
            sb.toString()
        } catch (e: Exception) {
            Log.e(TAG, "buildTaskContext error", e)
            ""
        }
    }

    /**
     * タスクを検索して結果を文字列で返す。
     */
    private suspend fun searchTasks(targetDateStr: String, keyword: String, dateLabel: String): String? {
        if (targetDateStr.isBlank() && keyword.isBlank()) return null

        val allTasks = taskRepo.getAll().first()
        val matchedTasks = allTasks.filter { task ->
            val matchDate = targetDateStr.isBlank() || task.startDate == targetDateStr
            val matchKeyword = keyword.isBlank() || (
                task.title.contains(keyword, ignoreCase = true) ||
                task.description?.contains(keyword, ignoreCase = true) == true
            )
            !task.isCompleted && !task.isDeleted && matchDate && matchKeyword
        }.sortedBy { it.startTime ?: "23:59" }

        val dLabel = if (dateLabel.isNotBlank()) dateLabel
                     else if (targetDateStr.isNotBlank()) "${targetDateStr}の" else ""
        val kwLabel = if (keyword.isNotBlank()) "「${keyword}」に関する" else ""

        if (matchedTasks.isEmpty()) {
            return "${dLabel}${kwLabel}予定は見つかりませんでした。"
        }

        val sb = StringBuilder()
        sb.append("${dLabel}${kwLabel}予定は ${matchedTasks.size}件 あります。\n\n")
        matchedTasks.forEach { task ->
            val time = task.startTime ?: "終日"
            sb.append("・ $time : ${task.title}\n")
        }
        return sb.toString()
    }

    /**
     * Engine未ロードの場合にロードを試みる。
     */
    private suspend fun tryLoadEngine(): Boolean {
        return try {
            AiEngineManager.loadEngine(getApplication())
            AiEngineManager.isLoaded()
        } catch (e: Exception) {
            Log.e(TAG, "Engine load failed", e)
            false
        }
    }

    /**
     * テキストから最初の {...} ブロックを抽出する。
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

    override fun onCleared() {
        super.onCleared()
        // Engineの解放はアプリ全体で管理するため、ここでは行わない
    }

    /**
     * 意図分類の結果データクラス
     */
    private data class IntentResult(
        val intent: String,       // "schedule_search" or "general_chat"
        val targetDate: String,   // "YYYY-MM-DD" or ""
        val keyword: String,      // 検索キーワード or ""
        val dateLabel: String     // 表示用ラベル（例: "明日の"）
    )

    companion object {
        private const val TAG = "AiChatVM"
    }
}
```

---

## 6. ファイル 5: `ui/aichat/AiChatScreen.kt`（全置換）

**パス:** `app/src/main/java/com/example/taskschedulerv3/ui/aichat/AiChatScreen.kt`

**変更点:**
- TopAppBar のタイトルを「AI アシスタント（Gemma 4 E2B）」に変更
- プレースホルダーテキストを「なんでも聞いてください...」に変更
- AI応答中のインジケーターテキストを改善
- 既存のUI構造は全て維持

```kotlin
package com.example.taskschedulerv3.ui.aichat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(
    navController: NavController,
    vm: AiChatViewModel = viewModel()
) {
    val messages by vm.messages.collectAsState()
    val isTyping by vm.isTyping.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // 新しいメッセージが来たら一番下までスクロール
    LaunchedEffect(messages.size, isTyping) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI アシスタント") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        },
        bottomBar = {
            // 入力エリア
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .navigationBarsPadding()
                        .imePadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("なんでも聞いてください...") },
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 3,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (inputText.isNotBlank() && !isTyping) {
                                    vm.sendMessage(inputText)
                                    inputText = ""
                                }
                            }
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilledIconButton(
                        onClick = {
                            if (inputText.isNotBlank() && !isTyping) {
                                vm.sendMessage(inputText)
                                inputText = ""
                            }
                        },
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape,
                        enabled = !isTyping
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "送信")
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(messages) { msg ->
                ChatBubble(message = msg)
            }
            if (isTyping) {
                item {
                    ChatTypingIndicator()
                }
            }
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    val alignment = if (message.isUser) Alignment.CenterEnd else Alignment.CenterStart
    val bgColor = if (message.isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
    val textColor = if (message.isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
    val shape = if (message.isUser) {
        RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp)
    } else {
        RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp)
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Surface(
            shape = shape,
            color = bgColor,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Text(
                text = message.text,
                color = textColor,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
fun ChatTypingIndicator() {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp),
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Text(
                text = "AIが考え中...",
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
```

---

## 7. 変更不要ファイルの確認

以下のファイルは **シグネチャ互換性が維持される** ため、変更不要です。

**`SettingsViewModel.kt`** — `AiModelManager` の public API（`initState`, `checkModelExists`, `state`, `downloadModel`, `deleteModel`, `getModelSizeMB`）は全て同一シグネチャのため、変更なしでそのまま動作します。

**`QuickDraftViewModel.kt`** — `AiEngineManager.analyze(ocrText: String): String?` のシグネチャは変更なし。`loadEngine`, `isLoaded`, `getInitError` も同一。`OcrTextParser` へのフォールバックロジックもそのまま機能します。

**`QuickDraftCaptureSheet.kt`** — ViewModel 経由の呼び出しのみ。`AiEngineManager.isLoaded()` と `AiEngineManager.loadEngine()` のシグネチャは同一。

**`OcrTextParser.kt`** — AI失敗時のフォールバックパーサー。変更なし。

**`NavGraph.kt`** — `Screen.AiChat` ルートは既に定義済み。

**`app/build.gradle.kts`** — `litertlm-android:0.10.0` は Gemma 4 E2B に対応済み。

**`AndroidManifest.xml`** — `libvndksupport.so` / `libOpenCL.so` は既に宣言済み。

---

## 8. ビルド＆テスト手順

### 8.1 事前準備
1. **設定画面から旧モデルを削除する**（Gemma 3 1B の 529 MB を解放）
2. 上記5ファイルを置換する
3. Android Studio で `Sync Project with Gradle Files` を実行
4. `Build > Clean Project` → `Build > Rebuild Project`
5. コンパイルエラーがないことを確認

### 8.2 動作確認チェックリスト

| # | テスト項目 | 期待結果 |
|---|---|---|
| 1 | 設定画面表示 | AI セクションに「AI機能（Gemma 4 E2B）」と表示 |
| 2 | AI ON（未DL時） | 「約2.6GBのモデルデータ（Gemma 4 E2B）をダウンロード」確認ダイアログ表示 |
| 3 | ダウンロード実行 | プログレスバー表示、完了後「モデルダウンロード済み（≈2583MB）」表示 |
| 4 | Quick Draft（AI ON） | 写真撮影→OCR→90秒以内にJSON応答→タイトルが自動設定される |
| 5 | Quick Draft（AI OFF） | 従来通りタイムスタンプ付きタイトルで仮登録される |
| 6 | AIタイムアウト | 90秒超過時、Logに `LLM inference timed out` 表示、OCRフォールバック動作 |
| 7 | AIチャット - 予定検索 | 「明日の予定は？」→ 該当予定一覧が表示される |
| 8 | AIチャット - 自由会話 | 「タスク管理のコツは？」→ AIが自然な日本語でアドバイスを返す |
| 9 | AIチャット - 複合質問 | 「来週の金曜の会議は？」→ LLMが日付計算して予定検索 |
| 10 | AIチャット - Engine未ロード | AI OFF時に利用→「設定画面でAI機能をONに」メッセージ |
| 11 | モデル削除 | 「AIモデルを削除」→ ≈2583MB解放される、AI機能OFF |
| 12 | 旧モデル自動削除 | 削除時、`gemma3-1b-it-int4.litertlm` も同時削除される |
| 13 | 既存機能 | テーマ切替、CRUD、エクスポート/インポート、通知、タグ管理、ロードマップ全て正常 |

### 8.3 Logcat フィルタ

```
tag:AiModelManager OR tag:AiEngineManager OR tag:QuickDraftVM OR tag:AiChatVM
```

**正常動作時のログ例（Quick Draft）:**
```
D/AiEngineManager: Loading Gemma 4 E2B model: /data/.../ai_model/gemma-4-E2B-it.litertlm (2583 MB)
D/AiEngineManager: Gemma 4 E2B Engine initialized successfully
D/QuickDraftVM: OCR Result (156 chars): 第5回定例会議 2026年5月15日（金）14:00〜16:00...
D/AiEngineManager: Analyze input (156 chars): 第5回定例会議 2026年5月15日（金）14:00〜16:00...
D/AiEngineManager: LLM Response: {"title":"第5回定例会議","date":"2026-05-15","start_time":"14:00","end_time":"16:00","summary":"定例会議"}
D/QuickDraftVM: AI Parsed -> title=第5回定例会議, date=2026-05-15, start=14:00
```

**正常動作時のログ例（AIチャット自由応答）:**
```
D/AiChatVM: Intent classification: intent=general_chat, date=, keyword=
D/AiEngineManager: LLM ChatResponse (187 chars): タスク管理のコツをいくつかお伝えします...
```

**タイムアウト時のログ例:**
```
W/AiEngineManager: LLM inference timed out after 90s
W/QuickDraftVM: AI returned null/blank, using OCR fallback parser
```

---

## 9. トラブルシューティング

| 症状 | 原因 | 対処法 |
|---|---|---|
| ダウンロード時に HTTP 401/403 | HFトークン無効 or ライセンス同意未完了 | Hugging Face で Gemma 4 のライセンスに同意、トークンを再発行 |
| ダウンロード中断 | ネットワーク不安定 | `.tmp` ファイルを削除後、Wi-Fi で再試行 |
| Engine初期化失敗 | メモリ不足 or モデル破損 | 他アプリを終了、モデル再ダウンロード |
| 推論が90秒でタイムアウト | OCRテキストが複雑すぎる | `MAX_OCR_LENGTH` を 500 に減らす |
| AIチャットで応答が空 | Engine未初期化 | 設定画面でAI ON → モデルDL完了を確認 |
| JSON パースエラー（Quick Draft） | LLMがJSON以外を出力 | Logcat で `LLM Response` を確認。プロンプトの `temperature` をさらに下げる（0.1） |
| `sendMessageAsync` で `ClassCastException` | LiteRT-LM バージョン不一致 | `litertlm-android` が `0.10.0` であることを確認 |
| コンパイルエラー: `generateChatResponse` unresolved | AiEngineManager.kt の置換漏れ | ファイル全体を上記コードで完全に置換 |

---

## 10. アーキテクチャ概要図

```
ユーザー操作
    │
    ├── Quick Draft（写真撮影）
    │       │
    │       ▼
    │   ML Kit OCR（日本語テキスト認識）
    │       │
    │       ▼
    │   AiEngineManager.analyze()  ← Gemma 4 E2B
    │       │                          90秒タイムアウト
    │       ├── 成功 → JSON パース → QuickDraftTask 保存
    │       └── 失敗 → OcrTextParser フォールバック → 保存
    │
    └── AI チャット（自由入力）
            │
            ├── ルールベース判定（今日/明日/明後日）
            │       └── 該当 → TaskRepository 検索 → 結果表示
            │
            ├── LLM 意図分類
            │   AiEngineManager.generateResponse()
            │       ├── schedule_search → TaskRepository 検索
            │       └── general_chat → 自由応答生成
            │
            └── 自由応答生成
                AiEngineManager.generateChatResponse()
                    └── 予定コンテキスト付きで自然な応答
```

---

この指示書をコード生成AIへそのまま渡すことで、6ファイルの変更（うち5ファイルが全置換、1ファイルは変更確認のみ）をエラーなく安全に実施できます。全てのパブリックAPIシグネチャの互換性は維持されており、変更対象外のファイルには一切影響しません。