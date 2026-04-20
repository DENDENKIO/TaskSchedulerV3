# TaskSchedulerV3 — AIモデル切り替え指示書
## Gemma 3 1B → Gemma 4 E2B 移行 + 推論ハング修正

---

## 1. 概要

### 1.1 目的
現在使用中の **Gemma3-1B-IT**（529MB, 1Bパラメータ）を **Gemma 4 E2B**（2.58GB, 2Bパラメータ）に切り替える。同時に、推論が無限にハングする問題を修正する。

### 1.2 背景・問題点
- 現在のGemma3-1B-ITでは推論が非常に遅く、`sendMessage()`（同期呼び出し）がタイムアウトなく無限に待機し、UIが「AI解析中...」のまま止まる
- 1Bモデルは日本語JSON出力が不安定で、AIタイトル生成が機能しない
- Gemma 4 E2Bは日本語理解力・JSON出力精度が大幅に向上し、上記の問題を解決できる

### 1.3 ターゲット端末
- **DOOGEE S200**（MediaTek Dimensity 7050, 32GB RAM, Android 14）
- RAM 32GBのためGemma 4 E2B（使用RAM約1.7GB）は余裕で動作する

### 1.4 ブランチ
- `feature/AImode`（現在の作業ブランチ）で作業を継続

### 1.5 変更対象ファイル一覧

| # | ファイルパス | 操作 |
|---|---|---|
| 1 | `app/src/main/java/com/example/taskschedulerv3/util/AiModelManager.kt` | **全体置き換え** |
| 2 | `app/src/main/java/com/example/taskschedulerv3/util/AiEngineManager.kt` | **全体置き換え** |
| 3 | `app/src/main/java/com/example/taskschedulerv3/ui/settings/SettingsScreen.kt` | **全体置き換え** |

### 1.6 変更しないファイル（確認済み・変更不要）

以下のファイルは現在のコードのまま正しく動作する。**一切変更しないこと。**

- `app/build.gradle.kts` — LiteRT-LM 0.10.0依存は既に存在、変更不要
- `gradle/libs.versions.toml` — Kotlin 2.3.0, KSP 2.3.6, Room 2.7.0 は既に正しい
- `app/src/main/AndroidManifest.xml` — INTERNET権限、GPU native library宣言は既に存在
- `app/src/main/java/.../util/AiPreferences.kt` — AI ON/OFF管理、変更不要
- `app/src/main/java/.../util/DataStoreProvider.kt` — DataStore定義、変更不要
- `app/src/main/java/.../util/ThemePreferences.kt` — テーマ管理、変更不要
- `app/src/main/java/.../util/OcrTextParser.kt` — フォールバックパーサー、変更不要
- `app/src/main/java/.../ui/settings/SettingsViewModel.kt` — 変更不要（downloadModelのシグネチャは変わらない）
- `app/src/main/java/.../ui/quickdraft/QuickDraftViewModel.kt` — 変更不要（AiEngineManagerのAPIシグネチャは変わらない）
- `app/src/main/java/.../ui/quickdraft/QuickDraftCaptureSheet.kt` — 変更不要
- `app/src/main/java/.../ui/quickdraft/QuickDraftListScreen.kt` — 変更不要
- `app/src/main/java/.../ui/quickdraft/QuickDraftEditScreen.kt` — 変更不要
- `app/src/main/java/.../data/model/QuickDraftTask.kt` — 変更不要
- `app/src/main/java/.../data/db/AppDatabase.kt` — version 9のまま変更不要
- `app/src/main/java/.../data/db/QuickDraftTaskDao.kt` — 変更不要
- `app/src/main/java/.../data/repository/QuickDraftRepository.kt` — 変更不要
- `app/src/main/java/.../navigation/NavGraph.kt` — 変更不要
- `app/src/main/java/.../MainActivity.kt` — 変更不要

---

## 2. ファイル別変更詳細

---

### 2.1 `app/src/main/java/com/example/taskschedulerv3/util/AiModelManager.kt`

**操作: ファイル全体を以下のコードで置き換える**

#### 変更点の説明
- `MODEL_FILENAME`: `gemma3-1b-it-int4.litertlm` → `gemma-4-E2B-it.litertlm`
- `MODEL_DOWNLOAD_URL`: Gemma3のURLからGemma4 E2BのURLに変更
- `checkModelExists()`: 最小サイズチェックを `100_000`(100KB) → `1_000_000_000`(1GB) に引き上げ（2.58GBファイルの不完全ダウンロード検知）
- `downloadModel()`: tmpファイルの最小サイズチェックも同様に `1_000_000_000` に変更
- その他のメソッド（`initState`, `getModelFile`, `getModelPath`, `getModelSizeMB`, `openConnectionWithRedirects`, `deleteModel`）はロジック変更なし

#### 完全なコード

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
        return file.exists() && file.length() > 1_000_000_000
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

            if (tmpFile.length() < 1_000_000_000) {
                tmpFile.delete()
                _state.value = ModelState.Error("ダウンロードファイルが不完全です（サイズ不足）")
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
        // 旧モデル（Gemma3）が残っている場合も削除
        val oldFile = File(File(context.filesDir, MODEL_DIR), "gemma3-1b-it-int4.litertlm")
        if (oldFile.exists()) oldFile.delete()
        _state.value = ModelState.NotDownloaded
    }
}
```

---

### 2.2 `app/src/main/java/com/example/taskschedulerv3/util/AiEngineManager.kt`

**操作: ファイル全体を以下のコードで置き換える**

#### 変更点の説明
- **タイムアウト追加**: `withTimeoutOrNull(90_000L)` で90秒のタイムアウトを設定。推論が完了しない場合は`null`を返し、`QuickDraftViewModel`のフォールバック（`OcrTextParser`）で処理される
- **`sendMessage`（同期）→ `sendMessageAsync`（非同期Flow）に変更**: コルーチンのキャンセルが効くため、`withTimeoutOrNull`が確実に機能する
- **OCR文字数上限**: `500` → `1000`（Gemma 4 E2Bは2Bパラメータで長文処理が可能）
- **プロンプト最適化**: Gemma 4 E2Bは日本語が得意なので、日本語プロンプトに変更。ただしシンプルに保つ
- **モデルファイルサイズチェック**: `100_000` → `1_000_000_000`（2.58GBモデルに合わせる）
- **import変更なし**: 使用するLiteRT-LMのクラスは同一（`Engine`, `EngineConfig`, `Backend`, `Contents`, `ConversationConfig`, `SamplerConfig`）
- **公開API変更なし**: `isLoaded()`, `getInitError()`, `loadEngine()`, `releaseEngine()`, `analyze()`, `generateResponse()` のシグネチャはすべて同一のため、呼び出し元（`QuickDraftViewModel.kt`, `QuickDraftCaptureSheet.kt`）の変更は不要

#### 完全なコード

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
```

---

### 2.3 `app/src/main/java/com/example/taskschedulerv3/ui/settings/SettingsScreen.kt`

**操作: ファイル全体を以下のコードで置き換える**

#### 変更点の説明
- AI ON確認ダイアログ: `約529MB` → `約2.6GB`
- NotDownloaded時の説明テキスト: `約529MB` → `約2.6GB`
- その他のUI（テーマ設定、エクスポート/インポート、タグ管理、ゴミ箱、HFトークン入力、プログレスバー、モデル削除ダイアログ等）はすべて変更なし

#### 完全なコード

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
                Text("AI機能を使用するには、約2.6GBのモデルデータをダウンロードする必要があります。\n\nWi-Fi環境でのダウンロードを推奨します。")
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
                        "写真から予定の日付・タイトル・内容を自動認識します",
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
                                "モデルダウンロード済み（${vm.getModelSizeMB()}MB）",
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

## 3. 変更しないファイルの根拠

### 3.1 `SettingsViewModel.kt` — 変更不要
`downloadModel(app, _hfToken.value)` の呼び出しシグネチャは `AiModelManager.downloadModel(context: Context, hfToken: String): Boolean` のまま変更なし。`checkModelExists` も引き続き同じシグネチャ。

### 3.2 `QuickDraftViewModel.kt` — 変更不要
`AiEngineManager.isLoaded()`, `AiEngineManager.loadEngine(context)`, `AiEngineManager.analyze(ocrText)` のシグネチャはすべて変更なし。戻り値の型も `String?` のまま。正規表現でのJSONパースもそのまま機能する。

### 3.3 `QuickDraftCaptureSheet.kt` — 変更不要
`AiEngineManager.isLoaded()` と `AiEngineManager.loadEngine(context)` の呼び出しのみで、シグネチャ変更なし。

### 3.4 `OcrTextParser.kt` — 変更不要
フォールバックパーサーとして使用。AI推論がタイムアウトした場合にこちらが使われる設計は変わらない。

### 3.5 `app/build.gradle.kts` — 変更不要
`implementation("com.google.ai.edge.litertlm:litertlm-android:0.10.0")` は既に存在。Gemma 4 E2BもGemma 3 1Bも同じLiteRT-LM 0.10.0ランタイムで動作する。

### 3.6 `AndroidManifest.xml` — 変更不要
`INTERNET` 権限、GPU用 `uses-native-library` 宣言はすべて既に存在。

### 3.7 `AppDatabase.kt` — 変更不要
DBスキーマ version 9 のまま。AI モデル切り替えはDB構造に影響しない。

---

## 4. ビルド前の準備

### 4.1 旧モデルの削除
アプリの設定画面で「AIモデルを削除」を実行し、旧Gemma 3 1Bファイル（約529MB）をストレージから削除する。
（新コードの `deleteModel()` には旧ファイル名の削除ロジックも含めてあるが、念のため手動でも確認する）

### 4.2 ビルド手順
1. 上記3ファイルを置き換え
2. Android Studio → `File → Sync Project with Gradle Files`
3. `Build → Clean Project`
4. `Build → Rebuild Project`
5. エラーがないことを確認

---

## 5. ビルド後の動作確認チェックリスト

### 5.1 設定画面
- [ ] AI設定セクションが表示される（スクロールで到達可能）
- [ ] 「AI機能（Gemma 4 E2B）」というタイトルが表示される
- [ ] 「ONにするとモデル（約2.6GB）をダウンロードします」と表示される
- [ ] Hugging Faceトークン入力欄がある

### 5.2 モデルダウンロード
- [ ] AI ONスイッチ → 確認ダイアログに「約2.6GB」と表示される
- [ ] ダウンロード開始でプログレスバーが表示される
- [ ] ダウンロード完了後「モデルダウンロード済み（約2583MB）」と表示される
- [ ] AI ONスイッチがON状態になる

### 5.3 AI解析（仮登録）
- [ ] クイック仮登録で写真撮影 → 「AIが書類を解析して予定を作成中...」表示
- [ ] 90秒以内にAI解析が完了し、仮登録一覧に遷移する
- [ ] 仮登録のタスク名がAI生成のタイトル（例:「保護者会のお知らせ」等）になっている
- [ ] descriptionにAI生成の要約が入っている
- [ ] Logcatで `AiEngineManager` タグに `LLM Response: {"title":...}` が出力されている

### 5.4 タイムアウト動作
- [ ] もし90秒でタイムアウトした場合、Logcatに `LLM inference timed out` が出力される
- [ ] タイムアウト時は `OcrTextParser` のフォールバックでタイトル・日付が抽出されて保存される
- [ ] UIが「解析中」のまま止まることがない

### 5.5 AI OFF時
- [ ] AI OFFで写真撮影 → 従来通り「yyyy-MM-dd HH:mm 仮登録」形式で保存される

### 5.6 既存機能（回帰テスト）
- [ ] テーマ切り替え（ライト/ダーク/システム連動）が正常動作
- [ ] 予定のCRUD（作成/読取/更新/削除）が正常動作
- [ ] データエクスポート/インポートが正常動作
- [ ] 通知（アラーム）が正常動作
- [ ] タグ管理が正常動作

### 5.7 モデル削除
- [ ] 設定画面「AIモデルを削除」→ 確認ダイアログ → 削除でストレージ解放
- [ ] AI機能がOFFになる

---

## 6. Logcatデバッグガイド

### フィルタ設定
```
tag:AiModelManager OR tag:AiEngineManager OR tag:QuickDraftVM
```

### 正常時のログ例
```
D/AiModelManager: ダウンロード開始: https://huggingface.co/litert-community/...
D/AiModelManager: ファイルサイズ: 2583 MB
D/AiModelManager: ダウンロード完了: 2583 MB
D/AiEngineManager: Loading Gemma 4 E2B: /data/.../ai_model/gemma-4-E2B-it.litertlm (2583 MB)
D/AiEngineManager: Gemma 4 E2B Engine initialized successfully
D/QuickDraftVM: OCR Result (342 chars): 保護者会のご案内...
D/AiEngineManager: Analyze input (342 chars)
D/AiEngineManager: LLM Response: {"title":"保護者会のお知らせ","date":"2026-05-01","start_time":"13:00","end_time":"15:00","summary":"..."}
D/QuickDraftVM: AI JSON Result: {"title":"保護者会のお知らせ",...}
D/QuickDraftVM: AI Parsed -> title=保護者会のお知らせ, date=2026-05-01, start=13:00
```

### タイムアウト時のログ例
```
D/AiEngineManager: Analyze input (1000 chars)
W/AiEngineManager: LLM inference timed out after 90s
W/QuickDraftVM: AI returned null/blank, using OCR fallback parser
```

---

## 7. トラブルシューティング

| 症状 | 原因 | 対策 |
|---|---|---|
| ダウンロードがHTTP 401/403 | HFトークンが無効またはライセンス未同意 | huggingface.co で Gemma 4 のライセンスに同意し、新しいトークンを生成 |
| ダウンロードが途中で止まる | ネットワーク切断 | Wi-Fi接続を確認し、旧tmpファイルを削除して再試行 |
| Engine初期化でクラッシュ | メモリ不足（通常は発生しない） | 他のアプリを終了してから再試行 |
| タイムアウトが頻発 | OCRテキストが長すぎる or CPU負荷 | `MAX_OCR_LENGTH` を500に下げる |
| JSON出力が崩れる | モデルの不安定出力 | `temperature` を0.1に下げる |
| ビルドエラー: Unresolved reference | importの不整合 | 上記の完全なコードをそのままコピー&ペースト |
