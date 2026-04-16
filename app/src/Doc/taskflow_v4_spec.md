# TaskFlow V4 — 詳細仕様書 & 実装ロードマップ
**バージョン:** 1.0.0-draft  
**作成日:** 2026-04-16  
**対象リポジトリ:** TaskSchedulerV3 (com.example.taskschedulerv3)  
**ターゲットブランチ:** master

---

## 1. プロジェクト概要

### 1.1 コンセプト
「一目で全体が見える、触れて気持ちいいスケジューラー」  
- カレンダー画面を廃止し、残存日数・優先度・写真・OCRを軸にした情報密度の高いホーム
- Material You + Dynamic Color 採用、ダークモード完全対応（AMOLED黒オプション）
- タスク登録時に **写真撮影 / ギャラリー選択 / OCR範囲指定** の3機能を統合

### 1.2 既存コードの前提
| 項目 | 現状 |
|---|---|
| パッケージ名 | com.example.taskschedulerv3 |
| DB version | 3 |
| 言語 | Kotlin (Jetpack Compose) |
| ビルドシステム | Gradle KTS + KSP |
| Room entities | Task, Tag, TaskTagCrossRef, TaskRelation, PhotoMemo, TaskCompletion, PhotoTagCrossRef |
| 既存依存 | Compose BOM, Navigation Compose, Room, Coil, DataStore, Material3, Material Icons Extended |

---

## 2. 廃止・統合・追加する画面一覧

| 画面 | V3 | V4対応 |
|---|---|---|
| schedulelist | ✅ 存在 | → **HomeScreen** としてリニューアル |
| calendar | ✅ 存在 | ❌ **廃止**（残存日数チップで代替） |
| addtask | ✅ 存在 | → ボトムシート化 + 写真/OCRセクション追加 |
| taskdetail | ✅ 存在 | → UI刷新（写真プレビュー表示追加） |
| recurring | ✅ 存在 | → 設定画面内サブ画面へ統合 |
| indefinite | ✅ 存在 | → HomeScreen タブに統合 |
| tag | ✅ 存在 | → 設定画面内サブ画面へ統合 |
| relation | ✅ 存在 | → TaskDetailScreen 内インライン表示 |
| photo | ✅ 存在 | → TaskDetailScreen 内フォトギャラリーとして統合 |
| trash | ✅ 存在 | → 設定画面内サブ画面へ統合 |
| settings | ✅ 存在 | → 拡張（テーマ、通知、タグ、繰り返し、ゴミ箱を内包） |
| notification | ✅ 存在 | → 設定画面サブとして維持 |

---

## 3. 新しいナビゲーション構造

### 3.1 BottomNavBar（3タブ構成）
```
[ ホーム (Home) ] [ 一覧 (List) ] [ 設定 (Settings) ]
```
- FABはホーム・一覧画面で右下に常時表示
- タスク追加はボトムシートとして画面下からスライドアップ
- Predictive Back Gesture 対応（Android 14+）

### 3.2 画面遷移マップ
```
MainActivity
└── NavHost
    ├── HomeScreen          ← BottomNav[0]
    │   └── TaskDetailSheet (モーダル)
    ├── TaskListScreen      ← BottomNav[1]
    │   └── TaskDetailSheet (モーダル)
    ├── SettingsScreen      ← BottomNav[2]
    │   ├── TagManageScreen
    │   ├── RecurringScreen
    │   ├── TrashScreen
    │   └── NotificationSettingsScreen
    └── AddTaskBottomSheet  (FABから起動, どこからでもアクセス可)
```

---

## 4. ディレクトリ構成（変更後）

```
app/src/main/java/com/example/taskschedulerv3/
├── MainActivity.kt                  ← BottomNavBarに変更
├── navigation/
│   └── NavGraph.kt                  ← ルート定義を更新
├── data/
│   ├── model/
│   │   ├── Task.kt                  ← 変更なし（V3のまま流用）
│   │   ├── PhotoMemo.kt             ← カラム追加（ocrText, sourceType）
│   │   ├── Tag.kt                   ← 変更なし
│   │   ├── TaskRelation.kt          ← 変更なし
│   │   ├── TaskTagCrossRef.kt       ← 変更なし
│   │   ├── PhotoTagCrossRef.kt      ← 変更なし
│   │   ├── TaskCompletion.kt        ← 変更なし
│   │   ├── RecurrencePattern.kt     ← 変更なし
│   │   ├── ScheduleType.kt          ← 変更なし
│   │   ├── FilterOption.kt          ← 変更なし
│   │   └── SortOption.kt            ← 変更なし
│   ├── db/
│   │   ├── AppDatabase.kt           ← version=4 に変更、MIGRATION_3_4追加
│   │   ├── TaskDao.kt               ← 変更なし
│   │   ├── PhotoMemoDao.kt          ← クエリ追加（getPhotosForTask, updateOcrText）
│   │   ├── TagDao.kt                ← 変更なし
│   │   ├── TaskRelationDao.kt       ← 変更なし
│   │   ├── TaskTagCrossRefDao.kt    ← 変更なし
│   │   ├── TaskCompletionDao.kt     ← 変更なし
│   │   ├── PhotoTagCrossRefDao.kt   ← 変更なし
│   │   └── converter/Converters.kt  ← 変更なし
│   └── repository/
│       ├── TaskRepository.kt        ← 変更なし
│       └── PhotoRepository.kt       ← NEW: 写真・OCR管理リポジトリ
├── ui/
│   ├── home/
│   │   ├── HomeScreen.kt            ← NEW: 完全リニューアル
│   │   └── HomeViewModel.kt         ← NEW
│   ├── tasklist/
│   │   ├── TaskListScreen.kt        ← schedulelist をリネーム＋刷新
│   │   └── TaskListViewModel.kt
│   ├── addtask/
│   │   ├── AddTaskBottomSheet.kt    ← addtask をボトムシート化
│   │   ├── AddTaskViewModel.kt
│   │   └── AddTaskPhotoSection.kt   ← NEW: 写真/OCRセクション
│   ├── taskdetail/
│   │   ├── TaskDetailScreen.kt      ← 写真プレビュー・関連タスク統合
│   │   └── TaskDetailViewModel.kt
│   ├── photo/
│   │   ├── TaskPhotoPickerSheet.kt  ← NEW: 3機能統合ボトムシート
│   │   ├── TaskPhotoViewModel.kt    ← NEW
│   │   └── OcrCropOverlay.kt        ← NEW: ドラッグ範囲選択オーバーレイ
│   ├── settings/
│   │   ├── SettingsScreen.kt        ← 拡張（タグ・繰り返し・ゴミ箱を内包）
│   │   ├── TagManageScreen.kt       ← tag/ から移動
│   │   ├── RecurringScreen.kt       ← recurring/ から移動
│   │   ├── TrashScreen.kt           ← trash/ から移動
│   │   └── NotificationSettingsScreen.kt ← notification/ から移動
│   ├── components/
│   │   ├── TaskCard.kt              ← NEW: リニューアルしたカードUI
│   │   ├── PriorityBadge.kt         ← NEW
│   │   ├── RemainingDaysChip.kt     ← NEW: 残存日数チップ
│   │   ├── ProgressRingCard.kt      ← NEW: 今日の進捗サマリーカード
│   │   └── PhotoThumbnailRow.kt     ← NEW: 写真サムネイル横スクロール
│   └── theme/
│       ├── Color.kt                 ← V4カラーシステム（下記参照）
│       ├── Theme.kt                 ← DynamicColor対応
│       └── Type.kt                  ← Noto Sans JP 設定
└── util/
    └── DateUtils.kt                 ← 残存日数計算を追加
```

---

## 5. データベース変更仕様

### 5.1 PhotoMemo エンティティ（変更あり）
```kotlin
// 既存カラムはそのまま保持
@Entity(tableName = "photo_memos", ...)
data class PhotoMemo(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val taskId: Int? = null,
    val date: String,            // yyyy-MM-dd (既存)
    val title: String? = null,   // (既存)
    val memo: String? = null,    // (既存)
    val imagePath: String,       // (既存)
    val createdAt: Long = ...,   // (既存)
    // ▼ V4で追加するカラム
    val ocrText: String? = null,              // OCR認識結果テキスト
    val sourceType: String = "CAMERA"         // "CAMERA" | "GALLERY" | "OCR"
)
```

### 5.2 Migration 3→4
```kotlin
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE photo_memos ADD COLUMN ocrText TEXT")
        database.execSQL("ALTER TABLE photo_memos ADD COLUMN sourceType TEXT NOT NULL DEFAULT 'CAMERA'")
    }
}
```

### 5.3 AppDatabase 変更
```kotlin
@Database(
    entities = [...],  // 変更なし
    version = 4,       // 3 → 4 に変更
    exportSchema = false
)
// addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4) に追記
```

---

## 6. 依存関係追加（build.gradle.kts）

```kotlin
// ML Kit 日本語OCR（オフライン対応）
implementation("com.google.mlkit:text-recognition-japanese:16.0.1")

// Coil（既存）はそのまま使用

// 追加不要：カメラはActivityResultContracts.TakePicture()で実装
// 追加不要：ギャラリーはActivityResultContracts.GetContent()で実装
```

### AndroidManifest.xml への追記
```xml
<!-- OCRオフラインモデル（アプリインストール時に自動DL） -->
<application ...>
    <meta-data
        android:name="com.google.mlkit.vision.DEPENDENCIES"
        android:value="ocr_japanese" />
</application>

<!-- カメラ権限 -->
<uses-permission android:name="android.permission.CAMERA" />
<uses-feature android:name="android.hardware.camera" android:required="false" />

<!-- ファイルプロバイダー（カメラ撮影用URI） -->
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

### res/xml/file_paths.xml（新規作成）
```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <external-files-path name="photos" path="Pictures/" />
</paths>
```

---

## 7. テーマ・カラーシステム詳細仕様

### 7.1 カラーパレット（V4）

#### ダークモード（デフォルト）
```kotlin
// ui/theme/Color.kt
val BackgroundDark         = Color(0xFF0E0E11)  // AMOLED対応ダーク黒
val SurfaceDark            = Color(0xFF15151A)  // カード背景
val SurfaceVariantDark     = Color(0xFF1E1E26)  // 入力フィールド背景
val PrimaryDark            = Color(0xFF8B7CF6)  // インジゴアクセント
val OnPrimaryDark          = Color(0xFF1A1040)
val PrimaryContainerDark   = Color(0xFF2A2250)
val SecondaryDark          = Color(0xFF5BC0DE)  // 情報用シアン
val TertiaryDark           = Color(0xFFE57373)  // 高優先度・警告
val SuccessDark            = Color(0xFF81C784)  // 完了・低優先度
val WarningDark            = Color(0xFFFFB74D)  // 中優先度
val OnSurfaceDark          = Color(0xFFE8E8F0)  // メインテキスト
val OnSurfaceVariantDark   = Color(0xFF9090A8)  // サブテキスト
val OutlineDark            = Color(0xFF2E2E3A)  // ボーダー
val DividerDark            = Color(0xFF1E1E2A)
```

#### ライトモード
```kotlin
val BackgroundLight        = Color(0xFFF7F7FC)
val SurfaceLight           = Color(0xFFFFFFFF)
val SurfaceVariantLight    = Color(0xFFEEEEF8)
val PrimaryLight           = Color(0xFF5B4CF5)
val OnPrimaryLight         = Color(0xFFFFFFFF)
val PrimaryContainerLight  = Color(0xFFE8E4FF)
val SecondaryLight         = Color(0xFF0097B2)
val TertiaryLight          = Color(0xFFD32F2F)
val SuccessLight           = Color(0xFF388E3C)
val WarningLight           = Color(0xFFF57C00)
val OnSurfaceLight         = Color(0xFF1A1A2E)
val OnSurfaceVariantLight  = Color(0xFF5C5C70)
val OutlineLight           = Color(0xFFDDDDEE)
```

### 7.2 優先度カラーマッピング
```kotlin
fun priorityColor(priority: Int, isDark: Boolean): Color = when (priority) {
    0 -> if (isDark) Color(0xFFE57373) else Color(0xFFD32F2F)  // 高：赤
    1 -> if (isDark) Color(0xFFFFB74D) else Color(0xFFF57C00)  // 中：橙
    2 -> if (isDark) Color(0xFF81C784) else Color(0xFF388E3C)  // 低：緑
    else -> Color.Gray
}
```

### 7.3 残存日数チップ カラーロジック
```kotlin
fun remainingDaysColor(days: Int, isDark: Boolean): Color = when {
    days < 0   -> Color(0xFFE57373)  // 期限切れ：赤
    days == 0  -> Color(0xFFE57373)  // 今日：赤
    days <= 2  -> Color(0xFFFFB74D)  // 2日以内：橙
    days <= 7  -> Color(0xFF8B7CF6)  // 1週間以内：インジゴ
    else       -> Color(0xFF9090A8)  // 余裕あり：グレー
}
fun remainingDaysLabel(days: Int): String = when {
    days < 0  -> "期限切れ ${-days}日"
    days == 0 -> "今日"
    else      -> "残り ${days}日"
}
```

### 7.4 Typography（Noto Sans JP）
```kotlin
// ui/theme/Type.kt
val AppTypography = Typography(
    headlineLarge  = TextStyle(fontFamily = NotoSansJP, fontWeight = FontWeight.Bold,   fontSize = 28.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontFamily = NotoSansJP, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 30.sp),
    titleLarge     = TextStyle(fontFamily = NotoSansJP, fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 26.sp),
    titleMedium    = TextStyle(fontFamily = NotoSansJP, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 22.sp),
    bodyLarge      = TextStyle(fontFamily = NotoSansJP, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium     = TextStyle(fontFamily = NotoSansJP, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 20.sp),
    labelSmall     = TextStyle(fontFamily = NotoSansJP, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp)
)
```

---

## 8. 画面別 詳細UI仕様

### 8.1 HomeScreen（ホーム画面）

#### レイアウト構成
```
┌───────────────────────────────────────┐
│  [アプリ名ロゴ]           [🔔] [⚙]   │  ← TopAppBar (surface colored)
├───────────────────────────────────────┤
│  ProgressRingCard                     │
│  ┌─────────────────────────────────┐  │
│  │  [進捗リング 64dp]  今日のタスク │  │
│  │      5/8 完了         63%       │  │
│  │  ████████░░░░                   │  │
│  └─────────────────────────────────┘  │
├───────────────────────────────────────┤
│  [今日▼] [直近▼] [無期限▼]            │  ← FilterChip Row (横スクロール)
├───────────────────────────────────────┤
│  LazyColumn: TaskCard × n             │
│  ┌────────────────────────────────┐   │
│  │▌ [優先度ドット] タスク名       │   │  ← ▌は2dp幅の優先度カラーストライプ
│  │  09:00〜10:00  [残り3日]       │   │
│  │  [タグチップ] [タグチップ]     │   │
│  │  [📷1枚] ──────────────────── │   │  ← 写真添付ある場合のみ
│  └────────────────────────────────┘   │
│  ─── 完了済み(3件) ──────── [▼]      │  ← 折りたたみセクション
├───────────────────────────────────────┤
│          [＋ FAB: タスクを追加]       │  ← 右下FAB
└───────────────────────────────────────┘
```

#### TaskCard Compose コード仕様
```kotlin
@Composable
fun TaskCard(
    task: Task,
    tags: List<Tag>,
    photos: List<PhotoMemo>,
    remainingDays: Int,
    onChecked: (Boolean) -> Unit,
    onCardClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // カード全体
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCardClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // 左端の優先度カラーストライプ（2dp幅）
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(priorityColor(task.priority, isSystemInDarkTheme()))
            )
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                // タイトル行
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = task.isCompleted, onCheckedChange = onChecked)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleMedium,
                        textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                // 時刻 + 残存日数チップ
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (task.startTime != null) {
                        Text(
                            text = "${task.startTime}${if (task.endTime != null) "〜${task.endTime}" else ""}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    RemainingDaysChip(days = remainingDays)
                }
                // タグチップ（横スクロール）
                if (tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(tags) { tag ->
                            AssistChip(
                                onClick = {},
                                label = { Text(text = tag.name, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
                // 写真サムネイル
                if (photos.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    PhotoThumbnailRow(photos = photos, maxShow = 3)
                }
            }
        }
    }
}
```

### 8.2 ProgressRingCard Compose コード仕様
```kotlin
@Composable
fun ProgressRingCard(completed: Int, total: Int) {
    val progress = if (total > 0) completed.toFloat() / total else 0f
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            // 進捗リング
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(64.dp)) {
                CircularProgressIndicator(
                    progress = progress,
                    modifier = Modifier.size(64.dp),
                    strokeWidth = 6.dp,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text("今日のタスク", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("$completed / $total 完了", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}
```

### 8.3 AddTaskBottomSheet（タスク追加ボトムシート）

#### フォーム構成
```
BottomSheetScaffold / ModalBottomSheet
├── ドラッグハンドル（中央）
├── テキストフィールド: タスク名（必須）
├── テキストフィールド: メモ（任意）
├── 日付セクション
│   ├── 開始日ピッカー（DatePickerDialog）
│   └── 終了日ピッカー（任意）
├── 時刻セクション
│   ├── 開始時刻ピッカー（TimePickerDialog）
│   └── 終了時刻ピッカー（任意）
├── 優先度セレクタ（高/中/低 3ボタン）
├── タグセレクタ（チップ複数選択）
├── 繰り返し設定トグル
│   └── （展開時）RecurrencePanel
├── AddTaskPhotoSection   ← 写真/OCR統合セクション
│   ├── [📷 カメラ] [🖼 ギャラリー] [🔍 OCR]
│   └── 選択済み写真サムネイル（横スクロール）
├── 通知設定トグル
└── [保存ボタン]（Primary, 幅全体）
```

---

## 9. 写真・OCR機能 詳細仕様

### 9.1 TaskPhotoPickerSheet — 3機能統合コンポーネント

#### 9.1.1 カメラ機能（sourceType = "CAMERA"）
```kotlin
// ActivityResultContracts.TakePicture() を使用
val cameraUri: Uri = FileProvider.getUriForFile(
    context,
    "${context.packageName}.fileprovider",
    File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "photo_${System.currentTimeMillis()}.jpg")
)
val cameraLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.TakePicture()
) { success ->
    if (success) {
        // cameraUri → PhotoMemo(sourceType="CAMERA") として保存
    }
}
// カメラ権限チェック → PermissionState(Companion.CAMERA) → cameraLauncher.launch(cameraUri)
```

#### 9.1.2 ギャラリー機能（sourceType = "GALLERY"）
```kotlin
val galleryLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.GetContent()
) { uri: Uri? ->
    uri?.let {
        // URIをファイルにコピー → imagePath → PhotoMemo(sourceType="GALLERY") として保存
    }
}
// galleryLauncher.launch("image/*")
```

#### 9.1.3 OCR機能（sourceType = "OCR"）

##### フロー
```
1. ギャラリーから画像を選択
2. OcrCropOverlay で認識範囲をドラッグ選択
   - Canvas上に半透明青のオーバーレイ描画
   - PointerInput でドラッグstart/end座標を取得
   - 選択範囲を示す矩形をdrawRect で描画
3. [OCR実行] ボタンタップ
4. 選択範囲でBitmapをcrop
5. ML Kit TextRecognizer(JapaneseTextRecognizerOptions) でOCR実行
6. 結果テキストをダイアログで表示
7. [タスクに貼り付け] → AddTaskBottomSheetのメモ欄に追記
8. PhotoMemo(sourceType="OCR", ocrText=結果) として保存
```

##### OcrCropOverlay コード仕様
```kotlin
@Composable
fun OcrCropOverlay(
    bitmap: Bitmap,
    onCropComplete: (Rect) -> Unit  // 選択範囲をピクセル座標で返す
) {
    var startOffset by remember { mutableStateOf(Offset.Zero) }
    var endOffset by remember { mutableStateOf(Offset.Zero) }
    var isDragging by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        startOffset = offset
                        isDragging = true
                    },
                    onDrag = { _, dragAmount ->
                        endOffset += dragAmount
                    },
                    onDragEnd = {
                        isDragging = false
                        // Compose座標 → Bitmap座標に変換してコールバック
                        onCropComplete(
                            Rect(
                                left = minOf(startOffset.x, endOffset.x).toInt(),
                                top = minOf(startOffset.y, endOffset.y).toInt(),
                                right = maxOf(startOffset.x, endOffset.x).toInt(),
                                bottom = maxOf(startOffset.y, endOffset.y).toInt()
                            )
                        )
                    }
                )
            }
    ) {
        Image(bitmap = bitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize())
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (isDragging || (startOffset != Offset.Zero && endOffset != Offset.Zero)) {
                // 選択範囲の半透明オーバーレイ
                drawRect(
                    color = Color(0xFF2196F3).copy(alpha = 0.3f),
                    topLeft = Offset(minOf(startOffset.x, endOffset.x), minOf(startOffset.y, endOffset.y)),
                    size = Size(
                        abs(endOffset.x - startOffset.x),
                        abs(endOffset.y - startOffset.y)
                    )
                )
                // ボーダーライン
                drawRect(
                    color = Color(0xFF2196F3),
                    topLeft = Offset(minOf(startOffset.x, endOffset.x), minOf(startOffset.y, endOffset.y)),
                    size = Size(
                        abs(endOffset.x - startOffset.x),
                        abs(endOffset.y - startOffset.y)
                    ),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }
    }
}
```

##### ML Kit OCR実行コード仕様
```kotlin
fun runOcr(bitmap: Bitmap, cropRect: Rect, onResult: (String) -> Unit) {
    val croppedBitmap = Bitmap.createBitmap(
        bitmap,
        cropRect.left.coerceAtLeast(0),
        cropRect.top.coerceAtLeast(0),
        (cropRect.right - cropRect.left).coerceAtLeast(1),
        (cropRect.bottom - cropRect.top).coerceAtLeast(1)
    )
    val image = InputImage.fromBitmap(croppedBitmap, 0)
    val recognizer = TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
    recognizer.process(image)
        .addOnSuccessListener { result ->
            onResult(result.text)
        }
        .addOnFailureListener { e ->
            onResult("OCRエラー: ${e.message}")
        }
}
```

---

## 10. TaskListScreen（一覧画面）仕様

### 10.1 フィルター・ソート
```
TopBar: [🔍 検索バー] [🔽 フィルター] [↕ ソート]
FilterChips（横スクロール）: 全て / 今日 / 直近7日 / 無期限 / [タグ名...]
SortOptions: 期限順 / 優先度順 / 作成日順 / タイトル順
```

### 10.2 TaskCard（一覧版）
HomeScreenと同一の `TaskCard` コンポーネントを使用。

### 10.3 空状態
```kotlin
// タスクが0件の場合
EmptyState(
    icon = Icons.Outlined.Assignment,
    message = "タスクがありません",
    action = { Text("＋ 最初のタスクを追加") },
    onActionClick = { /* FABと同じAddTaskBottomSheet開く */ }
)
```

---

## 11. SettingsScreen（設定画面）仕様

### 11.1 セクション構成
```
設定
├── テーマ設定
│   ├── ダークモード切替（System / Light / Dark / AMOLED）
│   └── Dynamic Color（Android 12+: ON/OFF）
├── 通知設定 → NotificationSettingsScreen へ遷移
├── タグ管理 → TagManageScreen へ遷移
├── 繰り返しタスク → RecurringScreen へ遷移
├── ゴミ箱 → TrashScreen へ遷移
└── アプリ情報（バージョン）
```

---

## 12. RemainingDaysChip コンポーネント仕様

```kotlin
@Composable
fun RemainingDaysChip(days: Int, modifier: Modifier = Modifier) {
    val (bgColor, textColor, label) = when {
        days < 0  -> Triple(Color(0xFFFFCDD2), Color(0xFFD32F2F), "期限切れ ${-days}日")
        days == 0 -> Triple(Color(0xFFFFCDD2), Color(0xFFD32F2F), "今日")
        days <= 2 -> Triple(Color(0xFFFFE0B2), Color(0xFFF57C00), "残り ${days}日")
        days <= 7 -> Triple(Color(0xFFEDE7F6), Color(0xFF673AB7), "残り ${days}日")
        else      -> Triple(Color(0xFFF5F5F5), Color(0xFF757575), "残り ${days}日")
    }
    // ダークモード時は背景色を30%不透明に調整
    Surface(
        shape = RoundedCornerShape(50),
        color = if (isSystemInDarkTheme()) bgColor.copy(alpha = 0.25f) else bgColor,
        modifier = modifier.height(22.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}
```

---

## 13. DateUtils 追加仕様

```kotlin
// util/DateUtils.kt に追加
fun calculateRemainingDays(endDate: String?): Int {
    if (endDate == null) return Int.MAX_VALUE  // 無期限
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val end = LocalDate.parse(endDate, formatter)
    val today = LocalDate.now()
    return ChronoUnit.DAYS.between(today, end).toInt()
}
```

---

## 14. 実装ロードマップ（フェーズ分け）

### Phase 1 — テーマ・ナビゲーション基盤 【優先度: 最高】
1. `ui/theme/Color.kt` → V4カラーシステムに更新
2. `ui/theme/Theme.kt` → DynamicColor対応（Android 12+）
3. `ui/theme/Type.kt` → Noto Sans JP 設定
4. `MainActivity.kt` → BottomNavBar 3タブ構成に変更
5. `navigation/NavGraph.kt` → 新しいルート定義に更新

### Phase 2 — データ層変更 【優先度: 高】
1. `data/model/PhotoMemo.kt` → `ocrText`, `sourceType` カラム追加
2. `data/db/AppDatabase.kt` → version=4, MIGRATION_3_4 追加
3. `data/db/PhotoMemoDao.kt` → クエリ追加
4. `data/repository/PhotoRepository.kt` → 新規作成
5. `util/DateUtils.kt` → `calculateRemainingDays()` 追加

### Phase 3 — 共通コンポーネント 【優先度: 高】
1. `ui/components/RemainingDaysChip.kt` → 新規作成
2. `ui/components/ProgressRingCard.kt` → 新規作成
3. `ui/components/TaskCard.kt` → リニューアル
4. `ui/components/PhotoThumbnailRow.kt` → 新規作成
5. `ui/components/PriorityBadge.kt` → 新規作成

### Phase 4 — HomeScreen 【優先度: 高】
1. `ui/home/HomeViewModel.kt` → 新規作成
2. `ui/home/HomeScreen.kt` → 完全リニューアル

### Phase 5 — AddTaskBottomSheet 【優先度: 高】
1. `ui/addtask/AddTaskBottomSheet.kt` → ボトムシート化
2. `ui/addtask/AddTaskViewModel.kt` → 更新
3. `ui/addtask/AddTaskPhotoSection.kt` → 新規作成

### Phase 6 — 写真・OCR機能 【優先度: 高】
1. `AndroidManifest.xml` → カメラ権限, FileProvider, MLKit設定追記
2. `res/xml/file_paths.xml` → 新規作成
3. `build.gradle.kts` → ML Kit依存追加
4. `ui/photo/OcrCropOverlay.kt` → 新規作成
5. `ui/photo/TaskPhotoPickerSheet.kt` → 新規作成
6. `ui/photo/TaskPhotoViewModel.kt` → 新規作成

### Phase 7 — TaskListScreen, TaskDetailScreen 【優先度: 中】
1. `ui/tasklist/TaskListScreen.kt` → schedulelist をリニューアル
2. `ui/tasklist/TaskListViewModel.kt` → 更新
3. `ui/taskdetail/TaskDetailScreen.kt` → 写真プレビュー統合, 関連タスクインライン表示

### Phase 8 — SettingsScreen統合 【優先度: 中】
1. `ui/settings/SettingsScreen.kt` → 拡張（タグ/繰り返し/ゴミ箱を内包）
2. 各サブ画面を settings/ 配下に移動

---

## 15. 削除するファイル・ディレクトリ

以下は新構造に統合されるため削除可能：
```
app/src/main/java/com/example/taskschedulerv3/ui/calendar/    ← 全削除
```
※ その他のディレクトリは名称変更・移動・統合で対応。既存ロジックはできる限り流用する。

---

## 16. 注意事項・制約

1. **データ破壊禁止**: Migrationは必ずALTER TABLEで追加のみ。DELETEやDROPは行わない
2. **テスト端末最小SDK**: minSdk = 26（Android 8.0）のまま維持
3. **Dynamic Color**: `Build.VERSION.SDK_INT >= Build.VERSION_CODES.S` のガードを必ず入れる
4. **ML KitのOCRモデル**: 日本語モデルは `ocr_japanese` を指定。英語と日本語を両方認識させる場合は `ocr_japanese,ocr` とカンマ区切り
5. **カメラURI**: `FileProvider` 経由でURIを取得。直接ファイルパスを渡さない（Android 7.0+の制約）
6. **CoroutineScope**: DB操作は必ず `viewModelScope.launch(Dispatchers.IO)` 内で実施
7. **Compose Navigation**: BottomSheet表示は `ModalBottomSheetLayout` または `ModalBottomSheet`（M3）を使用
