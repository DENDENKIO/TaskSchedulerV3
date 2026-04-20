Now I have a complete picture of the entire data model, DAOs, repository, alarm scheduling, and all models. Let me write the comprehensive specification document.

以下が「AIアシスタント経由でのタスク登録」機能の完全な指示書です。

---

# TaskSchedulerV3 — AIチャットからのタスク登録 完全指示書

## 1. 概要

### 1.1 機能フロー

```
ユーザー: 「明日14時から歯医者、通知30分前」
           ↓
AI: テキストから情報を一括抽出
           ↓
AI: 確認カードを表示
    ┌─────────────────────────────┐
    │ 📋 登録内容の確認            │
    │ ━━━━━━━━━━━━━━━━━━━━━━━━━ │
    │ 予定の種類: 通常              │
    │ タスク名: 歯医者              │
    │ 日付: 2026-04-21             │
    │ 開始時刻: 14:00              │
    │ 通知: 30分前                  │
    │ ロードマップ: なし            │
    │ ━━━━━━━━━━━━━━━━━━━━━━━━━ │
    │ [タグ追加] [メモ追加]         │
    │ [写真追加] [関連予定追加]     │
    │                              │
    │ [✏️ 修正する] [✅ 登録する]  │
    └─────────────────────────────┘
           ↓
ユーザー: 「登録する」をタップ or 「時間は15時に変えて」
           ↓
AI: 修正があれば反映して再確認 → 登録完了
```

### 1.2 必須項目が不足している場合

```
ユーザー: 「会議を登録して」
           ↓
AI: 「タスク名は『会議』ですね。日付はいつですか？」
           ↓
ユーザー: 「来週の月曜」
           ↓
AI: 確認カードを表示（上記と同じ形式）
```

### 1.3 対応する予定の種類

| 種類 | ScheduleType | 検出パターン例 |
|---|---|---|
| 通常 | NORMAL | 「明日の会議」「5月1日に歯医者」 |
| 期間 | PERIOD | 「5月1日から3日まで出張」 |
| 無期限 | NORMAL(isIndefinite=true) | 「いつか本を読む」「そのうちやりたい」 |
| 繰り返し | RECURRING | 「毎週月曜に定例会」「毎月1日に家賃」 |

### 1.4 変更対象ファイル

| # | ファイルパス | 変更種別 |
|---|---|---|
| 1 | `ui/aichat/AiChatViewModel.kt` | **全置換** — タスク構築・確認・登録ロジック追加 |
| 2 | `ui/aichat/AiChatScreen.kt` | **全置換** — 確認カードUI・アクションボタン追加 |
| 3 | `ui/aichat/ChatTaskModels.kt` | **新規作成** — チャット用のタスクデータモデル |

### 1.5 変更しないファイル

`Task.kt`, `Tag.kt`, `TaskTagCrossRef.kt`, `TaskRelation.kt`, `RoadmapStep.kt`, `ScheduleType.kt`, `RecurrencePattern.kt`, `TaskDao.kt`, `TagDao.kt`, `TaskTagCrossRefDao.kt`, `TaskRelationDao.kt`, `RoadmapStepDao.kt`, `TaskRepository.kt`, `AppDatabase.kt`, `AlarmScheduler.kt`, `NotificationHelper.kt`, `NavGraph.kt`, `AiEngineManager.kt`, `OcrTextParser.kt`, `AddTaskBottomSheet.kt`, `AddTaskViewModel.kt` — 全て変更不要

---

## 2. ファイル 1: `ui/aichat/ChatTaskModels.kt`（新規作成）

**パス:** `app/src/main/java/com/example/taskschedulerv3/ui/aichat/ChatTaskModels.kt`

```kotlin
package com.example.taskschedulerv3.ui.aichat

import com.example.taskschedulerv3.data.model.RecurrencePattern
import com.example.taskschedulerv3.data.model.ScheduleType

/**
 * AIチャットでのタスク登録用データモデル。
 * ユーザーとの会話で段階的に組み立てていく。
 */
data class DraftTaskData(
    val title: String = "",
    val description: String? = null,
    val scheduleType: ScheduleType = ScheduleType.NORMAL,
    val startDate: String = "",           // yyyy-MM-dd
    val endDate: String? = null,          // PERIOD用
    val startTime: String? = null,        // HH:mm
    val endTime: String? = null,
    val isIndefinite: Boolean = false,
    val notifyEnabled: Boolean = true,
    val notifyMinutesBefore: Int = 60,
    val recurrencePattern: RecurrencePattern? = null,
    val recurrenceDays: String? = null,
    val recurrenceEndDate: String? = null,
    val roadmapEnabled: Boolean = false,
    val roadmapSteps: List<DraftRoadmapStep> = emptyList(),
    val tagIds: List<Int> = emptyList(),
    val relatedTaskIds: List<Int> = emptyList(),
    val photoPaths: List<String> = emptyList(),
    val priority: Int = 1                 // 0=high, 1=medium, 2=low
) {
    /** 必須項目が揃っているか */
    fun isReadyToRegister(): Boolean {
        if (title.isBlank()) return false
        if (!isIndefinite && startDate.isBlank()) return false
        return true
    }

    /** 次に聞くべき不足項目 */
    fun nextMissingField(): String? {
        if (title.isBlank()) return "title"
        if (!isIndefinite && startDate.isBlank()) return "date"
        return null
    }
}

data class DraftRoadmapStep(
    val title: String,
    val date: String?,       // yyyy-MM-dd
    val sortOrder: Int
)

/**
 * チャットメッセージの種類を拡張。
 * 通常テキストに加えて、確認カードやアクションボタンを表示するための型。
 */
sealed class ChatContent {
    data class Text(val text: String) : ChatContent()
    data class TaskConfirmation(val draft: DraftTaskData) : ChatContent()
    data class TaskRegistered(val taskId: Int, val title: String) : ChatContent()
}
```

---

## 3. ファイル 2: `ui/aichat/AiChatViewModel.kt`（全置換）

**パス:** `app/src/main/java/com/example/taskschedulerv3/ui/aichat/AiChatViewModel.kt`

**変更点:**
- `ChatMessage` に `content: ChatContent` フィールドを追加
- タスク登録フロー: LLMでテキストからタスク情報を一括抽出 → 確認カード表示 → 修正/登録
- `pendingDraft: MutableStateFlow<DraftTaskData?>` で現在構築中のタスクを管理
- `registerTask()` メソッドで実際のDB保存・タグ紐付け・通知設定を実行
- 既存の予定検索・自由会話機能はそのまま維持

```kotlin
package com.example.taskschedulerv3.ui.aichat

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskschedulerv3.data.db.AppDatabase
import com.example.taskschedulerv3.data.model.RecurrencePattern
import com.example.taskschedulerv3.data.model.RoadmapStep
import com.example.taskschedulerv3.data.model.ScheduleType
import com.example.taskschedulerv3.data.model.Task
import com.example.taskschedulerv3.data.model.TaskRelation
import com.example.taskschedulerv3.data.model.TaskTagCrossRef
import com.example.taskschedulerv3.data.repository.TaskRepository
import com.example.taskschedulerv3.notification.AlarmScheduler
import com.example.taskschedulerv3.util.AiEngineManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val content: ChatContent = ChatContent.Text(text),
    val timestamp: Long = System.currentTimeMillis()
)

class AiChatViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.getInstance(app)
    private val taskRepo = TaskRepository(db.taskDao(), db.roadmapStepDao())
    private val tagDao = db.tagDao()
    private val crossRefDao = db.taskTagCrossRefDao()
    private val relationDao = db.taskRelationDao()
    private val roadmapStepDao = db.roadmapStepDao()

    private val _messages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                text = "こんにちは！予定について聞いたり、自由に質問してください。\n\n予定を登録したい場合は「予定を登録」や、直接「明日14時に会議」のように入力できます。",
                isUser = false
            )
        )
    )
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()

    /** 現在構築中のタスクドラフト */
    private val _pendingDraft = MutableStateFlow<DraftTaskData?>(null)
    val pendingDraft: StateFlow<DraftTaskData?> = _pendingDraft.asStateFlow()

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        addMessage(ChatMessage(text = text, isUser = true))

        viewModelScope.launch {
            _isTyping.value = true
            try {
                val response = processInput(text)
                addMessage(response)
            } catch (e: Exception) {
                Log.e(TAG, "sendMessage error", e)
                addMessage(ChatMessage(
                    text = "エラーが発生しました。もう一度お試しください。",
                    isUser = false
                ))
            } finally {
                _isTyping.value = false
            }
        }
    }

    /**
     * 確認カードから「登録する」が押された場合
     */
    fun confirmRegistration() {
        val draft = _pendingDraft.value ?: return
        viewModelScope.launch {
            _isTyping.value = true
            try {
                val taskId = registerTask(draft)
                _pendingDraft.value = null
                val msg = ChatMessage(
                    text = "「${draft.title}」を登録しました！",
                    isUser = false,
                    content = ChatContent.TaskRegistered(taskId, draft.title)
                )
                addMessage(msg)
            } catch (e: Exception) {
                Log.e(TAG, "Registration error", e)
                addMessage(ChatMessage(text = "登録に失敗しました: ${e.localizedMessage}", isUser = false))
            } finally {
                _isTyping.value = false
            }
        }
    }

    /**
     * 確認カードの各フィールドを更新する
     */
    fun updateDraft(updatedDraft: DraftTaskData) {
        _pendingDraft.value = updatedDraft
    }

    /**
     * ドラフトを破棄する
     */
    fun cancelDraft() {
        _pendingDraft.value = null
        addMessage(ChatMessage(text = "登録をキャンセルしました。", isUser = false))
    }

    // ── 入力処理の振り分け ──

    private suspend fun processInput(input: String): ChatMessage {
        val today = LocalDate.now()
        val todayStr = today.format(DateTimeFormatter.ISO_LOCAL_DATE)

        // 1. 構築中のドラフトがある場合 → 修正・補完として処理
        val currentDraft = _pendingDraft.value
        if (currentDraft != null) {
            return handleDraftModification(input, currentDraft, todayStr)
        }

        // 2. 登録意図の検出（ルールベース）
        val registerKeywords = listOf("登録", "追加", "作成", "予定を入れ", "スケジュールを入れ", "リマインダー")
        val hasRegisterIntent = registerKeywords.any { input.contains(it) }

        // 自然文にタスク情報が含まれているか（日付・時刻の存在チェック）
        val hasDateInfo = Regex("(明日|明後日|今日|来週|\\d{1,2}月\\d{1,2}日|\\d{4}[/\\-]\\d{1,2}[/\\-]\\d{1,2})").containsMatchIn(input)
        val hasTimeInfo = Regex("(\\d{1,2}時|\\d{1,2}:\\d{2})").containsMatchIn(input)

        if (hasRegisterIntent || (hasDateInfo && hasTimeInfo)) {
            return handleTaskCreation(input, todayStr)
        }

        // 3. 予定検索（ルールベース）
        val searchResult = tryRuleBasedScheduleSearch(input, today)
        if (searchResult != null) return ChatMessage(text = searchResult, isUser = false)

        // 4. LLM意図分類
        if (AiEngineManager.isLoaded() || tryLoadEngine()) {
            val intentResult = classifyIntentWithLlm(input, todayStr)
            if (intentResult != null) {
                when (intentResult.intent) {
                    "task_register" -> return handleTaskCreation(input, todayStr)
                    "schedule_search" -> {
                        val result = searchTasks(intentResult.targetDate, intentResult.keyword, intentResult.dateLabel)
                        if (result != null) return ChatMessage(text = result, isUser = false)
                    }
                    "general_chat" -> {
                        val chatResponse = generateFreeResponse(input, todayStr)
                        if (chatResponse != null) return ChatMessage(text = chatResponse, isUser = false)
                    }
                }
            }

            // フォールスルー: 自由応答
            val chatResponse = generateFreeResponse(input, todayStr)
            if (chatResponse != null) return ChatMessage(text = chatResponse, isUser = false)
        }

        return ChatMessage(
            text = "AIモデルが読み込まれていません。設定画面でAI機能をONにしてください。\n\n予定の検索は「明日の予定」のように聞いていただけます。",
            isUser = false
        )
    }

    // ── タスク登録フロー ──

    /**
     * ユーザーの自然文からタスク情報を一括抽出し、確認カードを返す。
     */
    private suspend fun handleTaskCreation(input: String, todayStr: String): ChatMessage {
        // LLMでタスク情報を一括抽出
        val draft = extractTaskFromText(input, todayStr)

        // 必須項目の不足チェック
        val missing = draft.nextMissingField()
        if (missing != null) {
            _pendingDraft.value = draft
            val question = when (missing) {
                "title" -> "タスク名を教えてください。"
                "date" -> "日付はいつですか？（例: 明日、5月1日、2026-05-01）"
                else -> "情報が不足しています。詳しく教えてください。"
            }
            return ChatMessage(text = question, isUser = false)
        }

        // 確認カードを表示
        _pendingDraft.value = draft
        val confirmText = buildConfirmationText(draft)
        return ChatMessage(
            text = confirmText,
            isUser = false,
            content = ChatContent.TaskConfirmation(draft)
        )
    }

    /**
     * 構築中ドラフトに対する修正や補完を処理する。
     */
    private suspend fun handleDraftModification(
        input: String,
        currentDraft: DraftTaskData,
        todayStr: String
    ): ChatMessage {
        // LLMに修正内容を解析させる
        val updatedDraft = applyModification(input, currentDraft, todayStr)
        _pendingDraft.value = updatedDraft

        val missing = updatedDraft.nextMissingField()
        if (missing != null) {
            val question = when (missing) {
                "title" -> "タスク名を教えてください。"
                "date" -> "日付はいつですか？"
                else -> "もう少し情報を教えてください。"
            }
            return ChatMessage(text = question, isUser = false)
        }

        val confirmText = buildConfirmationText(updatedDraft)
        return ChatMessage(
            text = confirmText,
            isUser = false,
            content = ChatContent.TaskConfirmation(updatedDraft)
        )
    }

    /**
     * LLMを使ってテキストからタスク情報を一括抽出する。
     */
    private suspend fun extractTaskFromText(input: String, todayStr: String): DraftTaskData {
        if (!AiEngineManager.isLoaded() && !tryLoadEngine()) {
            return parseTaskRuleBased(input, todayStr)
        }

        try {
            val allTags = tagDao.getAll().first()
            val tagListStr = if (allTags.isNotEmpty()) {
                allTags.joinToString(", ") { "${it.id}:${it.name}" }
            } else "なし"

            val prompt = """今日は $todayStr です。
ユーザーが予定を登録したいと言っています。以下のテキストからタスク情報を抽出し、JSONのみを出力してください。

【出力フォーマット（JSONのみ、他のテキスト不要）】
{"title":"タスク名","schedule_type":"NORMAL","start_date":"YYYY-MM-DD","end_date":"","start_time":"HH:mm","end_time":"","is_indefinite":false,"notify_enabled":true,"notify_minutes_before":60,"recurrence_pattern":"","recurrence_days":"","description":"","priority":1,"roadmap_enabled":false}

【schedule_typeの判定ルール】
- 通常の1回の予定 → "NORMAL"
- 開始日〜終了日がある期間予定 → "PERIOD"（end_dateも設定）
- 毎日/毎週/毎月など繰り返し → "RECURRING"（recurrence_patternも設定）
- 「いつか」「そのうち」など日付未定 → "NORMAL" + is_indefinite=true

【recurrence_patternの値】
DAILY, WEEKLY, BIWEEKLY, MONTHLY_DATE, YEARLY, WEEKLY_MULTI
WEEKLY_MULTIの場合はrecurrence_daysに曜日番号（月=1〜日=7）をカンマ区切りで設定

【priorityの値】0=高, 1=中, 2=低

【通知】
「通知あり」「30分前に通知」→ notify_enabled=true, notify_minutes_before=数値
「通知なし」「通知不要」→ notify_enabled=false
指定なし → notify_enabled=true, notify_minutes_before=60

【利用可能なタグ】
$tagListStr

タグが指定されている場合は "tag_ids":[数値の配列] を追加

【ユーザーの入力】
$input"""

            val response = AiEngineManager.generateResponse(prompt)
            if (!response.isNullOrBlank()) {
                val jsonStr = extractJsonFromText(response)
                if (jsonStr != null) {
                    return parseDraftFromJson(jsonStr)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "extractTaskFromText error", e)
        }

        return parseTaskRuleBased(input, todayStr)
    }

    /**
     * 修正テキストを既存ドラフトに適用する。
     */
    private suspend fun applyModification(
        input: String,
        current: DraftTaskData,
        todayStr: String
    ): DraftTaskData {
        if (!AiEngineManager.isLoaded()) return applyModificationRuleBased(input, current, todayStr)

        try {
            val currentJson = draftToJsonString(current)
            val prompt = """今日は $todayStr です。
現在の登録内容:
$currentJson

ユーザーが以下の修正を求めています。修正後のJSONのみを出力してください。フォーマットは同じです。

【ユーザーの修正】
$input"""

            val response = AiEngineManager.generateResponse(prompt)
            if (!response.isNullOrBlank()) {
                val jsonStr = extractJsonFromText(response)
                if (jsonStr != null) {
                    return parseDraftFromJson(jsonStr)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "applyModification error", e)
        }

        return applyModificationRuleBased(input, current, todayStr)
    }

    // ── ルールベースフォールバック ──

    private fun parseTaskRuleBased(input: String, todayStr: String): DraftTaskData {
        val today = LocalDate.parse(todayStr)
        var title = ""
        var startDate = ""
        var startTime: String? = null
        var isIndefinite = false
        var scheduleType = ScheduleType.NORMAL
        var notifyEnabled = true
        var notifyMinutes = 60

        // 日付検出
        when {
            input.contains("今日") -> startDate = todayStr
            input.contains("明後日") -> startDate = today.plusDays(2).toString()
            input.contains("明日") -> startDate = today.plusDays(1).toString()
            input.contains("いつか") || input.contains("そのうち") -> {
                isIndefinite = true
                startDate = todayStr
            }
        }

        // 日付パターン
        Regex("""(\d{1,2})月(\d{1,2})日""").find(input)?.let { m ->
            val mo = m.groupValues[1].toInt()
            val d = m.groupValues[2].toInt()
            startDate = LocalDate.of(today.year, mo, d).toString()
        }

        // 時刻検出
        Regex("""(\d{1,2})[時:](\d{2})?""").find(input)?.let { m ->
            val h = m.groupValues[1].toInt()
            val min = m.groupValues[2].toIntOrNull() ?: 0
            startTime = "%02d:%02d".format(h, min)
        }

        // 通知検出
        Regex("""(\d+)分前""").find(input)?.let { m ->
            notifyMinutes = m.groupValues[1].toInt()
        }
        if (input.contains("通知なし") || input.contains("通知不要")) {
            notifyEnabled = false
        }

        // 繰り返し検出
        if (input.contains("毎日")) {
            scheduleType = ScheduleType.RECURRING
        } else if (input.contains("毎週")) {
            scheduleType = ScheduleType.RECURRING
        } else if (input.contains("毎月")) {
            scheduleType = ScheduleType.RECURRING
        }

        // タイトル: 日付・時刻・通知の文言を除去した残りをタイトルとする
        title = input
            .replace(Regex("(今日|明日|明後日|来週|いつか|そのうち)の?"), "")
            .replace(Regex("\\d{1,2}月\\d{1,2}日の?"), "")
            .replace(Regex("\\d{1,2}[時:]\\d{0,2}分?(から|に|〜)?"), "")
            .replace(Regex("通知\\d*分前(に)?"), "")
            .replace(Regex("(通知あり|通知なし|通知不要)"), "")
            .replace(Regex("(予定を?|を?)(登録|追加|作成|入れ)(して|する|したい)?"), "")
            .replace(Regex("[、。を]"), "")
            .trim()

        return DraftTaskData(
            title = title,
            startDate = startDate,
            startTime = startTime,
            isIndefinite = isIndefinite,
            scheduleType = scheduleType,
            notifyEnabled = notifyEnabled,
            notifyMinutesBefore = notifyMinutes
        )
    }

    private fun applyModificationRuleBased(
        input: String,
        current: DraftTaskData,
        todayStr: String
    ): DraftTaskData {
        var draft = current
        val today = LocalDate.parse(todayStr)

        // 時刻変更
        Regex("""(\d{1,2})[時:](\d{2})?""").find(input)?.let { m ->
            val h = m.groupValues[1].toInt()
            val min = m.groupValues[2].toIntOrNull() ?: 0
            draft = draft.copy(startTime = "%02d:%02d".format(h, min))
        }

        // 日付変更
        when {
            input.contains("今日") -> draft = draft.copy(startDate = todayStr)
            input.contains("明後日") -> draft = draft.copy(startDate = today.plusDays(2).toString())
            input.contains("明日") -> draft = draft.copy(startDate = today.plusDays(1).toString())
        }
        Regex("""(\d{1,2})月(\d{1,2})日""").find(input)?.let { m ->
            val mo = m.groupValues[1].toInt()
            val d = m.groupValues[2].toInt()
            draft = draft.copy(startDate = LocalDate.of(today.year, mo, d).toString())
        }

        // タイトル変更（「タイトルをXXに」パターン）
        Regex("""(タスク名|タイトル|名前)を?「?(.+?)」?に""").find(input)?.let { m ->
            draft = draft.copy(title = m.groupValues[2].trim())
        }

        // 通知変更
        Regex("""(\d+)分前""").find(input)?.let { m ->
            draft = draft.copy(notifyMinutesBefore = m.groupValues[1].toInt(), notifyEnabled = true)
        }
        if (input.contains("通知なし") || input.contains("通知不要")) {
            draft = draft.copy(notifyEnabled = false)
        }

        return draft
    }

    // ── JSON解析 ──

    private fun parseDraftFromJson(jsonStr: String): DraftTaskData {
        return try {
            val json = JSONObject(jsonStr)
            DraftTaskData(
                title = json.optString("title", ""),
                description = json.optString("description", "").ifBlank { null },
                scheduleType = try {
                    ScheduleType.valueOf(json.optString("schedule_type", "NORMAL"))
                } catch (_: Exception) { ScheduleType.NORMAL },
                startDate = json.optString("start_date", ""),
                endDate = json.optString("end_date", "").ifBlank { null },
                startTime = json.optString("start_time", "").ifBlank { null },
                endTime = json.optString("end_time", "").ifBlank { null },
                isIndefinite = json.optBoolean("is_indefinite", false),
                notifyEnabled = json.optBoolean("notify_enabled", true),
                notifyMinutesBefore = json.optInt("notify_minutes_before", 60),
                recurrencePattern = try {
                    val p = json.optString("recurrence_pattern", "")
                    if (p.isNotBlank()) RecurrencePattern.valueOf(p) else null
                } catch (_: Exception) { null },
                recurrenceDays = json.optString("recurrence_days", "").ifBlank { null },
                priority = json.optInt("priority", 1),
                roadmapEnabled = json.optBoolean("roadmap_enabled", false),
                tagIds = try {
                    val arr = json.optJSONArray("tag_ids")
                    if (arr != null) (0 until arr.length()).map { arr.getInt(it) } else emptyList()
                } catch (_: Exception) { emptyList() }
            )
        } catch (e: Exception) {
            Log.e(TAG, "parseDraftFromJson error", e)
            DraftTaskData()
        }
    }

    private fun draftToJsonString(draft: DraftTaskData): String {
        return """{"title":"${draft.title}","schedule_type":"${draft.scheduleType}","start_date":"${draft.startDate}","end_date":"${draft.endDate ?: ""}","start_time":"${draft.startTime ?: ""}","end_time":"${draft.endTime ?: ""}","is_indefinite":${draft.isIndefinite},"notify_enabled":${draft.notifyEnabled},"notify_minutes_before":${draft.notifyMinutesBefore},"priority":${draft.priority},"roadmap_enabled":${draft.roadmapEnabled}}"""
    }

    // ── 確認テキスト生成 ──

    private suspend fun buildConfirmationText(draft: DraftTaskData): String {
        val sb = StringBuilder()
        sb.append("この内容で登録してよろしいですか？\n\n")

        val typeLabel = when {
            draft.isIndefinite -> "無期限"
            draft.scheduleType == ScheduleType.PERIOD -> "期間"
            draft.scheduleType == ScheduleType.RECURRING -> "繰り返し"
            else -> "通常"
        }
        sb.append("予定の種類: $typeLabel\n")
        sb.append("タスク名: ${draft.title}\n")

        if (!draft.isIndefinite) {
            sb.append("日付: ${draft.startDate}\n")
            if (draft.endDate != null) sb.append("終了日: ${draft.endDate}\n")
        }

        if (draft.startTime != null) sb.append("開始時刻: ${draft.startTime}\n")
        if (draft.endTime != null) sb.append("終了時刻: ${draft.endTime}\n")

        if (draft.description != null) sb.append("メモ: ${draft.description}\n")

        val notifyStr = if (draft.notifyEnabled) "${draft.notifyMinutesBefore}分前" else "なし"
        sb.append("通知: $notifyStr\n")

        val priorityLabel = when (draft.priority) { 0 -> "高"; 1 -> "中"; 2 -> "低"; else -> "中" }
        sb.append("優先度: $priorityLabel\n")

        if (draft.recurrencePattern != null) {
            sb.append("繰り返し: ${recurrenceLabel(draft.recurrencePattern, draft.recurrenceDays)}\n")
        }

        sb.append("ロードマップ: ${if (draft.roadmapEnabled) "あり" else "なし"}\n")

        // タグ名を表示
        if (draft.tagIds.isNotEmpty()) {
            val allTags = tagDao.getAll().first()
            val tagNames = allTags.filter { it.id in draft.tagIds }.map { it.name }
            if (tagNames.isNotEmpty()) {
                sb.append("タグ: ${tagNames.joinToString(", ")}\n")
            }
        }

        return sb.toString().trimEnd()
    }

    private fun recurrenceLabel(pattern: RecurrencePattern, days: String?): String {
        return when (pattern) {
            RecurrencePattern.DAILY -> "毎日"
            RecurrencePattern.WEEKLY -> "毎週"
            RecurrencePattern.BIWEEKLY -> "隔週"
            RecurrencePattern.MONTHLY_DATE -> "毎月(日付)"
            RecurrencePattern.YEARLY -> "毎年"
            RecurrencePattern.WEEKLY_MULTI -> {
                val dayNames = mapOf(1 to "月", 2 to "火", 3 to "水", 4 to "木", 5 to "金", 6 to "土", 7 to "日")
                val names = days?.split(",")?.mapNotNull { dayNames[it.trim().toIntOrNull()] } ?: emptyList()
                "毎週 ${names.joinToString("・")}"
            }
            RecurrencePattern.MONTHLY_DATES -> {
                "毎月 ${days ?: ""}日"
            }
            else -> pattern.name
        }
    }

    // ── DB登録 ──

    /**
     * ドラフトから実際のTaskをDBに保存し、タグ・関連予定・通知を設定する。
     * @return 登録されたタスクのID
     */
    private suspend fun registerTask(draft: DraftTaskData): Int = withContext(Dispatchers.IO) {
        val task = Task(
            title = draft.title,
            description = draft.description,
            startDate = if (draft.isIndefinite) LocalDate.now().toString() else draft.startDate,
            endDate = draft.endDate,
            startTime = draft.startTime,
            endTime = draft.endTime,
            scheduleType = draft.scheduleType,
            recurrencePattern = draft.recurrencePattern,
            recurrenceDays = draft.recurrenceDays,
            recurrenceEndDate = draft.recurrenceEndDate,
            priority = draft.priority,
            isIndefinite = draft.isIndefinite,
            notifyEnabled = draft.notifyEnabled,
            notifyMinutesBefore = draft.notifyMinutesBefore,
            roadmapEnabled = draft.roadmapEnabled
        )

        val taskId = taskRepo.insert(task).toInt()

        // タグの紐付け
        for (tagId in draft.tagIds) {
            crossRefDao.insert(TaskTagCrossRef(taskId = taskId, tagId = tagId))
        }

        // 関連予定の紐付け
        for (relatedId in draft.relatedTaskIds) {
            val id1 = minOf(taskId, relatedId)
            val id2 = maxOf(taskId, relatedId)
            if (relationDao.getRelation(id1, id2) == null) {
                relationDao.insert(TaskRelation(taskId1 = id1, taskId2 = id2))
            }
        }

        // ロードマップステップの登録
        if (draft.roadmapEnabled && draft.roadmapSteps.isNotEmpty()) {
            for (step in draft.roadmapSteps) {
                roadmapStepDao.insert(
                    RoadmapStep(
                        taskId = taskId,
                        title = step.title,
                        date = step.date,
                        sortOrder = step.sortOrder
                    )
                )
            }
        }

        // 通知スケジュール
        if (draft.notifyEnabled && !draft.isIndefinite) {
            val savedTask = taskRepo.getById(taskId)
            if (savedTask != null) {
                AlarmScheduler.scheduleForTask(getApplication(), savedTask)
            }
        }

        Log.d(TAG, "Task registered: id=$taskId, title=${draft.title}")
        taskId
    }

    // ── 既存の予定検索・自由会話機能（維持） ──

    private suspend fun tryRuleBasedScheduleSearch(query: String, today: LocalDate): String? {
        var targetDateStr = ""
        var dateLabel = ""

        when {
            query.contains("今日") && query.contains("予定") -> {
                targetDateStr = today.format(DateTimeFormatter.ISO_LOCAL_DATE); dateLabel = "今日"
            }
            query.contains("明後日") && query.contains("予定") -> {
                targetDateStr = today.plusDays(2).format(DateTimeFormatter.ISO_LOCAL_DATE); dateLabel = "明後日"
            }
            query.contains("明日") && query.contains("予定") -> {
                targetDateStr = today.plusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE); dateLabel = "明日"
            }
        }
        if (targetDateStr.isBlank()) return null

        var keyword = ""
        val keywordMatch = Regex("「(.+)」").find(query) ?: Regex("(.+)の予定").find(query)
        if (keywordMatch != null) {
            keyword = keywordMatch.groupValues[1]
                .replace("今日", "").replace("明日", "").replace("明後日", "")
                .replace("の", "").trim()
        }

        return searchTasks(targetDateStr, keyword, dateLabel)
    }

    private suspend fun classifyIntentWithLlm(query: String, currentDate: String): IntentResult? {
        try {
            val prompt = """今日は ${currentDate} です。

ユーザーの入力を分析し、以下のJSONのみを出力してください。

【分類ルール】
- 予定を登録・追加・作成したい → intent = "task_register"
- 予定を検索・確認したい → intent = "schedule_search"
- それ以外 → intent = "general_chat"

【出力フォーマット（JSONのみ）】
{"intent":"task_register","target_date":"","keyword":""}
{"intent":"schedule_search","target_date":"YYYY-MM-DD","keyword":"検索語"}
{"intent":"general_chat","target_date":"","keyword":""}

【ユーザーの入力】
$query"""

            val response = AiEngineManager.generateResponse(prompt) ?: return null
            val jsonStr = extractJsonFromText(response) ?: return null
            val json = JSONObject(jsonStr)

            return IntentResult(
                intent = json.optString("intent", "general_chat"),
                targetDate = json.optString("target_date", "").replace("/", "-"),
                keyword = json.optString("keyword", ""),
                dateLabel = ""
            )
        } catch (e: Exception) {
            Log.e(TAG, "classifyIntentWithLlm error", e)
            return null
        }
    }

    private suspend fun generateFreeResponse(query: String, currentDate: String): String? {
        try {
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
- Markdown装飾は使わない
- 予定の詳細を聞かれた場合は上記の予定情報を参照する
- 予定情報がない場合は「予定が登録されていません」と正直に答える
- 予定の登録を頼まれたら「『予定を登録』と入力するか、直接内容を教えてください」と案内する"""

            return AiEngineManager.generateChatResponse(query, systemPrompt)
        } catch (e: Exception) {
            Log.e(TAG, "generateFreeResponse error", e)
            return null
        }
    }

    private suspend fun buildTaskContext(currentDate: String): String {
        return try {
            val today = LocalDate.parse(currentDate)
            val allTasks = taskRepo.getAll().first()
            val upcoming = allTasks.filter { task ->
                !task.isCompleted && !task.isDeleted && task.startDate.isNotBlank() &&
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

    private suspend fun tryLoadEngine(): Boolean {
        return try {
            AiEngineManager.loadEngine(getApplication())
            AiEngineManager.isLoaded()
        } catch (e: Exception) {
            Log.e(TAG, "Engine load failed", e)
            false
        }
    }

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

    private data class IntentResult(
        val intent: String,
        val targetDate: String,
        val keyword: String,
        val dateLabel: String
    )

    override fun onCleared() {
        super.onCleared()
    }

    companion object {
        private const val TAG = "AiChatVM"
    }
}

4. ファイル 3: ui/aichat/AiChatScreen.kt（全置換）
パス: app/src/main/java/com/example/taskschedulerv3/ui/aichat/AiChatScreen.kt

変更点:

ChatBubble が ChatContent の種類に応じて通常テキスト / 確認カード / 登録完了カードを表示
確認カードには「登録する」「修正する」「キャンセル」ボタン、およびオプション追加ボタン（タグ・メモ等）を配置
タグ選択ダイアログ、通知分前入力、優先度選択をカード内で操作可能
登録完了カードには成功アイコンとタスク名を表示

package com.example.taskschedulerv3.ui.aichat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.taskschedulerv3.data.db.AppDatabase
import com.example.taskschedulerv3.data.model.Tag
import com.example.taskschedulerv3.ui.schedulelist.ScheduleListViewModel
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(
    navController: NavController,
    vm: AiChatViewModel = viewModel()
) {
    val messages by vm.messages.collectAsState()
    val isTyping by vm.isTyping.collectAsState()
    val pendingDraft by vm.pendingDraft.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

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
                when (msg.content) {
                    is ChatContent.TaskConfirmation -> {
                        // 最新の確認カードのみインタラクティブにする
                        val isLatestConfirmation = msg.id == messages.lastOrNull {
                            it.content is ChatContent.TaskConfirmation
                        }?.id
                        TaskConfirmationCard(
                            draft = if (isLatestConfirmation && pendingDraft != null) {
                                pendingDraft!!
                            } else {
                                (msg.content as ChatContent.TaskConfirmation).draft
                            },
                            isActive = isLatestConfirmation && pendingDraft != null,
                            onConfirm = { vm.confirmRegistration() },
                            onCancel = { vm.cancelDraft() },
                            onUpdateDraft = { vm.updateDraft(it) }
                        )
                    }
                    is ChatContent.TaskRegistered -> {
                        TaskRegisteredCard(
                            title = (msg.content as ChatContent.TaskRegistered).title
                        )
                    }
                    else -> {
                        ChatBubble(message = msg)
                    }
                }
            }
            if (isTyping) {
                item { ChatTypingIndicator() }
            }
        }
    }
}

// ── 通常のチャットバブル ──

@Composable
fun ChatBubble(message: ChatMessage) {
    val alignment = if (message.isUser) Alignment.CenterEnd else Alignment.CenterStart
    val bgColor = if (message.isUser) MaterialTheme.colorScheme.primaryContainer
                  else MaterialTheme.colorScheme.secondaryContainer
    val textColor = if (message.isUser) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSecondaryContainer
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

// ── 確認カード ──

@Composable
fun TaskConfirmationCard(
    draft: DraftTaskData,
    isActive: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onUpdateDraft: (DraftTaskData) -> Unit
) {
    val listVm: ScheduleListViewModel = viewModel()
    val allTags by listVm.allTags.collectAsState()
    var showTagDialog by remember { mutableStateOf(false) }
    var showNotifyDialog by remember { mutableStateOf(false) }
    var showPriorityDialog by remember { mutableStateOf(false) }
    var showMemoDialog by remember { mutableStateOf(false) }

    // タグ選択ダイアログ
    if (showTagDialog && isActive) {
        TagSelectionDialog(
            allTags = allTags,
            selectedTagIds = draft.tagIds,
            onDismiss = { showTagDialog = false },
            onConfirm = { newIds ->
                onUpdateDraft(draft.copy(tagIds = newIds))
                showTagDialog = false
            }
        )
    }

    // 通知設定ダイアログ
    if (showNotifyDialog && isActive) {
        NotifySettingDialog(
            enabled = draft.notifyEnabled,
            minutes = draft.notifyMinutesBefore,
            onDismiss = { showNotifyDialog = false },
            onConfirm = { enabled, minutes ->
                onUpdateDraft(draft.copy(notifyEnabled = enabled, notifyMinutesBefore = minutes))
                showNotifyDialog = false
            }
        )
    }

    // 優先度選択ダイアログ
    if (showPriorityDialog && isActive) {
        PriorityDialog(
            currentPriority = draft.priority,
            onDismiss = { showPriorityDialog = false },
            onSelect = { priority ->
                onUpdateDraft(draft.copy(priority = priority))
                showPriorityDialog = false
            }
        )
    }

    // メモ入力ダイアログ
    if (showMemoDialog && isActive) {
        MemoInputDialog(
            currentMemo = draft.description ?: "",
            onDismiss = { showMemoDialog = false },
            onConfirm = { memo ->
                onUpdateDraft(draft.copy(description = memo.ifBlank { null }))
                showMemoDialog = false
            }
        )
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart
    ) {
        Card(
            modifier = Modifier.widthIn(max = 320.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // ヘッダー
                Text(
                    "登録内容の確認",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                // 各項目
                val typeLabel = when {
                    draft.isIndefinite -> "無期限"
                    draft.scheduleType == com.example.taskschedulerv3.data.model.ScheduleType.PERIOD -> "期間"
                    draft.scheduleType == com.example.taskschedulerv3.data.model.ScheduleType.RECURRING -> "繰り返し"
                    else -> "通常"
                }
                ConfirmRow("予定の種類", typeLabel)
                ConfirmRow("タスク名", draft.title)

                if (!draft.isIndefinite) {
                    ConfirmRow("日付", draft.startDate)
                    if (draft.endDate != null) ConfirmRow("終了日", draft.endDate)
                }

                if (draft.startTime != null) ConfirmRow("開始時刻", draft.startTime)
                if (draft.endTime != null) ConfirmRow("終了時刻", draft.endTime)
                if (draft.description != null) ConfirmRow("メモ", draft.description)

                val notifyLabel = if (draft.notifyEnabled) "${draft.notifyMinutesBefore}分前" else "なし"
                ConfirmRow("通知", notifyLabel)

                val priorityLabel = when (draft.priority) { 0 -> "高"; 2 -> "低"; else -> "中" }
                ConfirmRow("優先度", priorityLabel)

                ConfirmRow("ロードマップ", if (draft.roadmapEnabled) "あり" else "なし")

                // タグ表示
                if (draft.tagIds.isNotEmpty()) {
                    val tagNames = allTags.filter { it.id in draft.tagIds }.map { it.name }
                    if (tagNames.isNotEmpty()) {
                        ConfirmRow("タグ", tagNames.joinToString(", "))
                    }
                }

                // オプション追加ボタン
                if (isActive) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        item {
                            AssistChip(
                                onClick = { showTagDialog = true },
                                label = { Text("タグ", fontSize = 12.sp) },
                                leadingIcon = { Icon(Icons.Default.Add, null, Modifier.size(14.dp)) }
                            )
                        }
                        item {
                            AssistChip(
                                onClick = { showMemoDialog = true },
                                label = { Text("メモ", fontSize = 12.sp) },
                                leadingIcon = { Icon(Icons.Default.Edit, null, Modifier.size(14.dp)) }
                            )
                        }
                        item {
                            AssistChip(
                                onClick = { showNotifyDialog = true },
                                label = { Text("通知", fontSize = 12.sp) },
                                leadingIcon = { Icon(Icons.Default.Edit, null, Modifier.size(14.dp)) }
                            )
                        }
                        item {
                            AssistChip(
                                onClick = { showPriorityDialog = true },
                                label = { Text("優先度", fontSize = 12.sp) },
                                leadingIcon = { Icon(Icons.Default.Edit, null, Modifier.size(14.dp)) }
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    // アクションボタン
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Close, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("キャンセル", fontSize = 13.sp)
                        }
                        Button(
                            onClick = onConfirm,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("登録する", fontSize = 13.sp)
                        }
                    }

                    Text(
                        "修正はチャットで伝えてください（例:「時間を15時に変えて」）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfirmRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(80.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

// ── 登録完了カード ──

@Composable
fun TaskRegisteredCard(title: String) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart
    ) {
        Card(
            modifier = Modifier.widthIn(max = 300.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Column {
                    Text(
                        "登録完了",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

// ── ダイアログ群 ──

@Composable
fun TagSelectionDialog(
    allTags: List<Tag>,
    selectedTagIds: List<Int>,
    onDismiss: () -> Unit,
    onConfirm: (List<Int>) -> Unit
) {
    var currentSelection by remember { mutableStateOf(selectedTagIds.toSet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("タグを選択") },
        text = {
            if (allTags.isEmpty()) {
                Text("タグがまだ登録されていません。設定画面のタグ管理から作成できます。")
            } else {
                Column {
                    allTags.forEach { tag ->
                        val isSelected = tag.id in currentSelection
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    currentSelection = if (isSelected) {
                                        currentSelection - tag.id
                                    } else {
                                        currentSelection + tag.id
                                    }
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = {
                                    currentSelection = if (it) {
                                        currentSelection + tag.id
                                    } else {
                                        currentSelection - tag.id
                                    }
                                }
                            )
                            val tagColor = runCatching {
                                Color(android.graphics.Color.parseColor(tag.color))
                            }.getOrElse { MaterialTheme.colorScheme.primary }
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(tagColor, CircleShape)
                            )
                            Text(tag.name)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(currentSelection.toList()) }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        }
    )
}

@Composable
fun NotifySettingDialog(
    enabled: Boolean,
    minutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Boolean, Int) -> Unit
) {
    var isEnabled by remember { mutableStateOf(enabled) }
    var selectedMinutes by remember { mutableStateOf(minutes) }

    val options = listOf(0 to "予定時刻", 5 to "5分前", 10 to "10分前", 15 to "15分前",
                         30 to "30分前", 60 to "1時間前", 120 to "2時間前", 1440 to "1日前")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("通知設定") },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("通知を有効にする")
                    Switch(checked = isEnabled, onCheckedChange = { isEnabled = it })
                }
                if (isEnabled) {
                    Spacer(Modifier.height(12.dp))
                    Text("通知タイミング", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(8.dp))
                    options.forEach { (min, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedMinutes = min }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            RadioButton(
                                selected = selectedMinutes == min,
                                onClick = { selectedMinutes = min }
                            )
                            Text(label)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(isEnabled, selectedMinutes) }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        }
    )
}

@Composable
fun PriorityDialog(
    currentPriority: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("優先度") },
        text = {
            Column {
                listOf(0 to "高", 1 to "中", 2 to "低").forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(value) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        RadioButton(
                            selected = currentPriority == value,
                            onClick = { onSelect(value) }
                        )
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("閉じる") }
        }
    )
}

@Composable
fun MemoInputDialog(
    currentMemo: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(currentMemo) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("メモ") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp),
                placeholder = { Text("メモを入力...") },
                maxLines = 6
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        }
    )
}

// ── タイピングインジケーター ──

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

5. 変更しないファイルの確認
以下は全てシグネチャ互換が維持されるため変更不要です。

Task.kt — エンティティ定義そのまま。ViewModel内で直接Taskオブジェクトを生成してinsertする
TaskDao.kt — insert(task): Long をそのまま使用
TaskRepository.kt — insert(), getById(), getAll() をそのまま使用
TagDao.kt — getAll(): Flow<List<Tag>> で全タグ取得
TaskTagCrossRefDao.kt — insert(crossRef) でタグ紐付け
TaskRelationDao.kt — insert(relation), getRelation() で関連予定紐付け
RoadmapStepDao.kt — insert(step) でステップ登録
AlarmScheduler.kt — scheduleForTask(context, task) で通知設定
AppDatabase.kt — マイグレーション不要
NavGraph.kt — Screen.AiChat ルート既存
AiEngineManager.kt — generateResponse(), generateChatResponse() をそのまま使用
AddTaskBottomSheet.kt / AddTaskViewModel.kt — 独立した既存機能。影響なし
6. ビルド＆テスト手順
6.1 ビルド
ChatTaskModels.kt を新規作成
AiChatViewModel.kt と AiChatScreen.kt を全置換
Sync Project with Gradle Files → Clean → Rebuild
6.2 動作確認チェックリスト
#	テスト項目	期待結果
1	「予定を登録」と入力	AIが「タスク名を教えてください。」と聞く
2	「明日14時に歯医者」と入力	確認カードが表示される（通常/歯医者/2026-04-21/14:00/通知60分前）
3	「登録する」ボタン	登録完了カード表示。DBにタスクが保存されている
4	「時間を15時に変えて」	カードが更新され開始時刻が15:00に変わる
5	「キャンセル」ボタン	ドラフト破棄。「登録をキャンセルしました」メッセージ
6	タグ追加ボタン	タグ選択ダイアログ。選択後カードに反映
7	メモ追加ボタン	メモ入力ダイアログ。入力後カードに反映
8	通知ボタン	通知設定ダイアログ。ON/OFF・分前を変更可能
9	優先度ボタン	高/中/低選択。選択後カードに反映
10	「毎週月曜に定例会」	schedule_type=RECURRING、recurrence=WEEKLYで確認カード
11	「5月1日から3日まで出張」	schedule_type=PERIOD、end_date付きで確認カード
12	「いつか本を読む」	is_indefinite=trueで確認カード
13	「明日の予定は？」	従来通り予定検索結果が表示される
14	「集中力を上げるコツ」	従来通り自由会話応答が表示される
15	登録後に通知確認	設定した通知分前にアラームが発火する
6.3 Logcat フィルタ
tag:AiChatVM OR tag:AiEngineManager
7. アーキテクチャ図
ユーザー入力
    │
    ├── 構築中ドラフトあり → handleDraftModification()
    │       LLM or ルールベースで修正適用 → 再確認カード
    │
    ├── 登録意図検出 → handleTaskCreation()
    │       │
    │       ▼ LLM で一括抽出 (or ルールベースフォールバック)
    │       DraftTaskData を組み立て
    │       │
    │       ├── 必須項目不足 → AIが質問
    │       └── 揃っている → 確認カード表示
    │               │
    │               ├── 「登録する」 → registerTask()
    │               │       Task INSERT → タグ紐付け
    │               │       → 関連予定紐付け → 通知設定
    │               │       → 登録完了カード
    │               │
    │               ├── チャットで修正 → handleDraftModification()
    │               │
    │               └── 「キャンセル」 → ドラフト破棄
    │
    ├── 予定検索 → searchTasks() → 結果テキスト
    │
    └── 自由会話 → generateFreeResponse() → 応答テキスト
この指示書をコード生成AIにそのまま渡してください。新規1ファイル＋全置換2ファイルの計3ファイルの変更で、既存のDB構造・アラームスケジューラ・タグ管理・既存のAddTaskBottomSheetには一切影響を与えず、AIチャットからのタスク登録機能が追加されます。