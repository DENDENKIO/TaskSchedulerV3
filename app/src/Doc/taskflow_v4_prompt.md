# TaskFlow V4 実装指示プロンプト（ローカルAI向け）

---

## あなたへの役割指示

あなたはAndroid Kotlin / Jetpack Composeの上級エンジニアです。  
以下の仕様に従って、既存のAndroidプロジェクト「TaskSchedulerV3」を「TaskFlow V4」にリニューアルしてください。  
コードは必ず**コンパイル可能な完全なKotlinコード**として出力してください。省略やコメントアウトのみの出力は禁止です。

---

## 既存プロジェクトの前提条件

- **パッケージ名**: `com.example.taskschedulerv3`（変更しない）
- **言語**: Kotlin + Jetpack Compose (Material3)
- **ビルド**: Gradle KTS + KSP
- **Room DBバージョン**: 現在 version=3
- **既存エンティティ**: Task, Tag, TaskTagCrossRef, TaskRelation, PhotoMemo, TaskCompletion, PhotoTagCrossRef
- **既存依存ライブラリ**: Compose BOM, Navigation Compose, Room, Coil, DataStore, Material3, Material Icons Extended

---

## 実装対象ファイル一覧（フェーズ順）

### PHASE 1: テーマ基盤

#### [1-A] app/src/main/java/com/example/taskschedulerv3/ui/theme/Color.kt
**要件:**
- ダークモード用カラー定数を定義（`val XXXDark = Color(0xXXXXXX)` 形式）
- ライトモード用カラー定数を定義
- 優先度カラー関数: `fun priorityColor(priority: Int, isDark: Boolean): Color`
  - priority=0（高）→ ダーク: `Color(0xFFE57373)` / ライト: `Color(0xFFD32F2F)` （赤）
  - priority=1（中）→ ダーク: `Color(0xFFFFB74D)` / ライト: `Color(0xFFF57C00)` （橙）
  - priority=2（低）→ ダーク: `Color(0xFF81C784)` / ライト: `Color(0xFF388E3C)` （緑）
- 残存日数カラー関数: `fun remainingDaysColor(days: Int, isDark: Boolean): Color`
  - days < 0 または 0 → 赤 `Color(0xFFE57373)`
  - days <= 2 → 橙 `Color(0xFFFFB74D)`
  - days <= 7 → インジゴ `Color(0xFF8B7CF6)`
  - else → グレー `Color(0xFF9090A8)`

**ダークモード カラー定数（必須値）:**
```
BackgroundDark  = 0xFF0E0E11
SurfaceDark     = 0xFF15151A
SurfaceVariantDark = 0xFF1E1E26
PrimaryDark     = 0xFF8B7CF6
OnPrimaryDark   = 0xFF1A1040
PrimaryContainerDark = 0xFF2A2250
OnSurfaceDark   = 0xFFE8E8F0
OnSurfaceVariantDark = 0xFF9090A8
OutlineDark     = 0xFF2E2E3A
```

**ライトモード カラー定数（必須値）:**
```
BackgroundLight = 0xFFF7F7FC
SurfaceLight    = 0xFFFFFFFF
SurfaceVariantLight = 0xFFEEEEF8
PrimaryLight    = 0xFF5B4CF5
OnPrimaryLight  = 0xFFFFFFFF
PrimaryContainerLight = 0xFFE8E4FF
OnSurfaceLight  = 0xFF1A1A2E
OnSurfaceVariantLight = 0xFF5C5C70
OutlineLight    = 0xFFDDDDEE
```

---

#### [1-B] app/src/main/java/com/example/taskschedulerv3/ui/theme/Theme.kt
**要件:**
- `AppTheme` composable を定義
- `darkColorScheme` / `lightColorScheme` を上記Color.ktの定数から生成
- Android 12+ (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) の場合 `dynamicDarkColorScheme` / `dynamicLightColorScheme` を適用
- `useDarkTheme: Boolean = isSystemInDarkTheme()` パラメータを持つ
- `amoledBlack: Boolean = false` パラメータを持ち、trueの場合 background を `Color(0xFF000000)` に強制

---

#### [1-C] app/src/main/java/com/example/taskschedulerv3/ui/theme/Type.kt
**要件:**
- `NotoSansJP` FontFamily を定義（Google Fontsの `Noto Sans JP` を使用。フォントリソースがない場合はシステムフォントフォールバックでよい）
- `AppTypography` として `Typography` インスタンスを定義
- headlineLarge: Bold, 28sp / headlineMedium: SemiBold, 22sp / titleLarge: Medium, 18sp / titleMedium: Medium, 15sp / bodyLarge: Normal, 15sp / bodyMedium: Normal, 13sp / labelSmall: Medium, 11sp

---

### PHASE 2: データ層変更

#### [2-A] app/src/main/java/com/example/taskschedulerv3/data/model/PhotoMemo.kt
**要件:**
- 既存カラム（id, taskId, date, title, memo, imagePath, createdAt）は変更なし
- 以下の2カラムを追加:
  ```kotlin
  val ocrText: String? = null          // OCR認識結果テキスト
  val sourceType: String = "CAMERA"    // "CAMERA" | "GALLERY" | "OCR"
  ```

---

#### [2-B] app/src/main/java/com/example/taskschedulerv3/data/db/AppDatabase.kt
**要件:**
- `version = 4` に変更（3→4）
- `MIGRATION_3_4` を追加:
  ```kotlin
  val MIGRATION_3_4 = object : Migration(3, 4) {
      override fun migrate(database: SupportSQLiteDatabase) {
          database.execSQL("ALTER TABLE photo_memos ADD COLUMN ocrText TEXT")
          database.execSQL("ALTER TABLE photo_memos ADD COLUMN sourceType TEXT NOT NULL DEFAULT 'CAMERA'")
      }
  }
  ```
- `addMigrations(...)` に `MIGRATION_3_4` を追加

---

#### [2-C] app/src/main/java/com/example/taskschedulerv3/data/db/PhotoMemoDao.kt
**要件:**
- 既存クエリはそのまま保持
- 以下のクエリを追加:
  ```kotlin
  @Query("SELECT * FROM photo_memos WHERE taskId = :taskId ORDER BY createdAt DESC")
  fun getPhotosForTask(taskId: Int): Flow<List<PhotoMemo>>

  @Query("UPDATE photo_memos SET ocrText = :ocrText WHERE id = :photoId")
  suspend fun updateOcrText(photoId: Int, ocrText: String)
  ```

---

#### [2-D] app/src/main/java/com/example/taskschedulerv3/util/DateUtils.kt
**要件:**
- 既存メソッドは保持
- 以下を追加:
  ```kotlin
  fun calculateRemainingDays(endDate: String?): Int {
      if (endDate == null) return Int.MAX_VALUE
      val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
      val end = LocalDate.parse(endDate, formatter)
      val today = LocalDate.now()
      return ChronoUnit.DAYS.between(today, end).toInt()
  }
  ```
- import: `java.time.LocalDate`, `java.time.format.DateTimeFormatter`, `java.time.temporal.ChronoUnit`

---

### PHASE 3: 共通コンポーネント

#### [3-A] app/src/main/java/com/example/taskschedulerv3/ui/components/RemainingDaysChip.kt
**要件:**
- `@Composable fun RemainingDaysChip(days: Int, modifier: Modifier = Modifier)` を実装
- days < 0 → 「期限切れ Xd」赤背景 `Color(0xFFFFCDD2)` 赤テキスト `Color(0xFFD32F2F)`
- days == 0 → 「今日」赤背景 / 赤テキスト
- days <= 2 → 「残り Xd」橙背景 `Color(0xFFFFE0B2)` 橙テキスト `Color(0xFFF57C00)`
- days <= 7 → 「残り Xd」薄紫背景 `Color(0xFFEDE7F6)` 紫テキスト `Color(0xFF673AB7)`
- else → 「残り Xd」グレー背景 `Color(0xFFF5F5F5)` グレーテキスト `Color(0xFF757575)`
- ダークモードの場合: 背景を `bgColor.copy(alpha = 0.25f)` に調整
- `isSystemInDarkTheme()` で分岐
- `Surface(shape = RoundedCornerShape(50), ...)` で pill形状

---

#### [3-B] app/src/main/java/com/example/taskschedulerv3/ui/components/ProgressRingCard.kt
**要件:**
- `@Composable fun ProgressRingCard(completed: Int, total: Int, modifier: Modifier = Modifier)` を実装
- `Card` に `primaryContainer` 背景、角丸16dp
- Row: 左に `CircularProgressIndicator`（64dp, strokeWidth=6dp）、中央に完了率%テキスト（labelSmall, Bold）
- 右に: 「今日のタスク」bodyMedium + 「X/Y 完了」titleLarge + `LinearProgressIndicator`（高さ6dp, clip RoundedCornerShape(3dp)）

---

#### [3-C] app/src/main/java/com/example/taskschedulerv3/ui/components/TaskCard.kt
**要件:**
- `@Composable fun TaskCard(task: Task, tags: List<Tag>, photos: List<PhotoMemo>, remainingDays: Int, onChecked: (Boolean) -> Unit, onCardClick: () -> Unit, modifier: Modifier = Modifier)` を実装
- Card: `RoundedCornerShape(12.dp)`, elevation=1dp
- 左端3dpの優先度カラーストライプ: `priorityColor(task.priority, isSystemInDarkTheme())`
- Row内: Checkbox + タスク名（titleMedium, 完了時LineThrough, 2行まで）
- 時刻テキスト（startTime〜endTime）+ `RemainingDaysChip(remainingDays)`
- タグ: `LazyRow` + `AssistChip` (labelSmall)
- 写真: `PhotoThumbnailRow(photos, maxShow=3)` (photos.isNotEmpty() 時のみ)

---

#### [3-D] app/src/main/java/com/example/taskschedulerv3/ui/components/PhotoThumbnailRow.kt
**要件:**
- `@Composable fun PhotoThumbnailRow(photos: List<PhotoMemo>, maxShow: Int = 3, modifier: Modifier = Modifier)` を実装
- `LazyRow`, horizontalArrangement=spacedBy(4.dp)
- 各サムネイル: `AsyncImage`（Coil）, size=48dp x 48dp, clip RoundedCornerShape(6.dp), contentScale=Crop
- maxShow件を超える場合は「+N」オーバーレイ表示（半透明黒背景+白テキスト）
- imagePath の存在確認: `File(imagePath).exists()` がfalseの場合は Icons.Outlined.BrokenImage を表示

---

### PHASE 4: HomeScreen

#### [4-A] app/src/main/java/com/example/taskschedulerv3/ui/home/HomeViewModel.kt
**要件:**
- `class HomeViewModel(private val taskRepository: TaskRepository, private val photoRepository: PhotoRepository) : ViewModel()`
- StateFlow: `todayTasks: StateFlow<List<Task>>`, `completedTodayCount: StateFlow<Int>`, `totalTodayCount: StateFlow<Int>`
- `fun toggleComplete(task: Task)`: `viewModelScope.launch(Dispatchers.IO)` 内でtask.isCompletedを反転してupsert
- `fun getTagsForTask(taskId: Int): Flow<List<Tag>>`
- `fun getPhotosForTask(taskId: Int): Flow<List<PhotoMemo>>`
- 今日のタスク = `startDate == LocalDate.now().toString()` または `endDate >= today`

---

#### [4-B] app/src/main/java/com/example/taskschedulerv3/ui/home/HomeScreen.kt
**要件:**
- `@Composable fun HomeScreen(viewModel: HomeViewModel, onNavigateToAddTask: () -> Unit, onNavigateToDetail: (Int) -> Unit)`
- `Scaffold` with `TopAppBar`: アプリ名「TaskFlow」（titleLarge, PrimaryColor）, 通知アイコン, 設定アイコン
- `ProgressRingCard(completed, total)` を最上部に配置
- フィルターChipRow: 「今日」「直近7日」「無期限」「全て」
- `LazyColumn`: `TaskCard` × todayTasks件数
  - 完了タスクは折りたたみセクション（`AnimatedVisibility`で展開/折りたたみ）
- `FloatingActionButton`: 右下固定, `onNavigateToAddTask()`

---

### PHASE 5: タスク追加ボトムシート

#### [5-A] app/src/main/java/com/example/taskschedulerv3/ui/addtask/AddTaskBottomSheet.kt
**要件:**
- `@Composable fun AddTaskBottomSheet(onDismiss: () -> Unit, onSave: () -> Unit, viewModel: AddTaskViewModel)`
- `ModalBottomSheet` (Material3) を使用
- フォーム項目（縦並び、`Column` + `verticalScroll`）:
  1. タスク名 `OutlinedTextField`（必須, 単行）
  2. メモ `OutlinedTextField`（任意, 最大3行）
  3. 開始日（`DatePickerDialog` トリガーボタン）
  4. 終了日（任意, DatePickerDialog）
  5. 開始時刻（任意, `TimePickerDialog`）
  6. 終了時刻（任意, `TimePickerDialog`）
  7. 優先度セレクタ: `Row` に 高/中/低 の `FilterChip` 3つ
  8. タグセレクタ: `FlowRow`（Compose Foundation）に既存タグをチップ表示、複数選択可
  9. 繰り返し設定トグル (`Switch`)
  10. `AddTaskPhotoSection()` ← 写真/OCR統合セクション
  11. 通知トグル (`Switch`) + 通知時刻ピッカー
  12. 保存ボタン: `Button(onClick=onSave, modifier=Modifier.fillMaxWidth())`
- 保存前バリデーション: タスク名が空なら `SnackbarHostState` でエラー表示

---

#### [5-B] app/src/main/java/com/example/taskschedulerv3/ui/addtask/AddTaskPhotoSection.kt
**要件:**
- `@Composable fun AddTaskPhotoSection(photos: List<PhotoMemo>, onOpenPicker: () -> Unit, onRemovePhoto: (PhotoMemo) -> Unit)`
- セクションタイトル: 「写真・メモ」（titleMedium）
- 3ボタン横並び Row:
  - `OutlinedButton` 「📷 カメラ」
  - `OutlinedButton` 「🖼 ギャラリー」
  - `OutlinedButton` 「🔍 OCR」
- 各ボタンタップで `onOpenPicker()` を呼び、どのアクションかは ViewModel の state で管理
- 選択済み写真のサムネイル横スクロール: `PhotoThumbnailRow`
- 各サムネイルに削除ボタン（X アイコン、右上角）を重ねて表示

---

### PHASE 6: 写真・OCR機能

#### [6-A] app/src/main/java/com/example/taskschedulerv3/ui/photo/OcrCropOverlay.kt
**要件:**
- `@Composable fun OcrCropOverlay(bitmap: Bitmap, onCropConfirmed: (android.graphics.Rect) -> Unit, onCancel: () -> Unit)`
- `Box(Modifier.fillMaxSize())` の上にBitmapを `Image` で表示（contentScale=Fit）
- `Canvas(Modifier.fillMaxSize())` でオーバーレイ描画
- `Modifier.pointerInput(Unit) { detectDragGestures { ... } }` でドラッグ検出
  - `startOffset`, `currentOffset` を `mutableStateOf(Offset.Zero)` で管理
  - ドラッグ中: 半透明青 `Color(0xFF2196F3).copy(alpha=0.3f)` の矩形を `drawRect` で描画
  - 矩形の境界線: `Stroke(width = 2.dp.toPx())` の `Color(0xFF2196F3)` で `drawRect`
- 下部に `Row`: 「キャンセル」`TextButton` + 「OCR実行」`Button`
- 「OCR実行」タップ: Image座標→Bitmap実座標に変換し `onCropConfirmed(Rect(...))` を呼ぶ
- 座標変換: `bitmapX = (offsetX / composableWidth) * bitmap.width` で比例計算

---

#### [6-B] app/src/main/java/com/example/taskschedulerv3/ui/photo/TaskPhotoViewModel.kt
**要件:**
- `class TaskPhotoViewModel(private val photoRepository: PhotoRepository) : ViewModel()`
- StateFlow: `pickerMode: StateFlow<PickerMode>` （PickerMode = enum NONE/CAMERA/GALLERY/OCR）
- StateFlow: `ocrBitmap: StateFlow<Bitmap?>` （OCR対象の選択された画像）
- StateFlow: `ocrResult: StateFlow<String>` （OCR結果テキスト）
- StateFlow: `photos: StateFlow<List<PhotoMemo>>` （選択済み写真リスト）
- `fun setPickerMode(mode: PickerMode)`
- `fun addCameraPhoto(uri: Uri, context: Context, taskId: Int?)` → ファイル保存→DB登録
- `fun addGalleryPhoto(uri: Uri, context: Context, taskId: Int?)` → ファイルコピー→DB登録
- `fun runOcr(bitmap: Bitmap, cropRect: android.graphics.Rect)` → ML Kit実行, ocrResult更新
  ```kotlin
  fun runOcr(bitmap: Bitmap, cropRect: android.graphics.Rect) {
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
          .addOnSuccessListener { result -> _ocrResult.value = result.text }
          .addOnFailureListener { e -> _ocrResult.value = "OCRエラー: ${e.message}" }
  }
  ```
- `fun saveOcrResult(taskId: Int?, context: Context)` → OCR結果をPhotoMemoとして保存

---

#### [6-C] app/src/main/java/com/example/taskschedulerv3/ui/photo/TaskPhotoPickerSheet.kt
**要件:**
- `@Composable fun TaskPhotoPickerSheet(taskId: Int?, onDismiss: () -> Unit, viewModel: TaskPhotoViewModel)`
- `ModalBottomSheet` を使用
- `pickerMode` が NONE のとき: 3ボタン選択画面を表示
  ```
  [📷 カメラで撮影]  [🖼 ギャラリーから選択]  [🔍 OCRで読み取り]
  ```
- `pickerMode` が CAMERA のとき:
  - `rememberLauncherForActivityResult(ActivityResultContracts.TakePicture())` を起動
  - 撮影完了で `viewModel.addCameraPhoto(uri, context, taskId)` を呼ぶ
- `pickerMode` が GALLERY のとき:
  - `rememberLauncherForActivityResult(ActivityResultContracts.GetContent())` を起動
  - 選択完了で `viewModel.addGalleryPhoto(uri, context, taskId)` を呼ぶ
- `pickerMode` が OCR のとき:
  - ギャラリーで画像選択 → `ocrBitmap` に設定
  - `OcrCropOverlay(bitmap, onCropConfirmed = { rect -> viewModel.runOcr(bitmap, rect) })`
  - OCR結果ダイアログ: `ocrResult` が空でない場合表示、「タスクに貼り付け」ボタン
- カメラ権限チェック: `rememberPermissionState(Manifest.permission.CAMERA)` で非保持なら `LaunchedEffect` でリクエスト

---

### PHASE 7: ナビゲーション更新

#### [7-A] app/src/main/java/com/example/taskschedulerv3/MainActivity.kt
**要件:**
- `BottomNavigationBar` に変更: 3タブ「ホーム」「一覧」「設定」
- 各タブのアイコン: `Icons.Outlined.Home`, `Icons.Outlined.FormatListBulleted`, `Icons.Outlined.Settings`
- `NavHost` を `Scaffold` 内の `content` に配置
- `AddTaskBottomSheet` は `showAddTaskSheet` state で制御（FABタップでtrueに）
- `AppTheme` でラップ、`darkTheme = isSystemInDarkTheme()`

---

#### [7-B] app/src/main/java/com/example/taskschedulerv3/navigation/NavGraph.kt
**要件:**
- `NavHost(navController, startDestination = "home")` を定義
- routes:
  - `"home"` → `HomeScreen(...)`
  - `"tasklist"` → `TaskListScreen(...)`
  - `"settings"` → `SettingsScreen(...)`
  - `"settings/tags"` → `TagManageScreen(...)`
  - `"settings/recurring"` → `RecurringScreen(...)`
  - `"settings/trash"` → `TrashScreen(...)`
  - `"settings/notification"` → `NotificationSettingsScreen(...)`
  - `"taskdetail/{taskId}"` → `TaskDetailScreen(..., taskId = backStackEntry.arguments?.getString("taskId")?.toInt())`
- `AddTaskBottomSheet` はNavigation外、Scaffoldレベルで管理

---

## build.gradle.kts への追加依存

```kotlin
// 以下を dependencies { } ブロックに追加
implementation("com.google.mlkit:text-recognition-japanese:16.0.1")
```

---

## AndroidManifest.xml への追加

```xml
<!-- <manifest> 直下に追加 -->
<uses-permission android:name="android.permission.CAMERA" />
<uses-feature android:name="android.hardware.camera" android:required="false" />

<!-- <application> 内に追加 -->
<meta-data
    android:name="com.google.mlkit.vision.DEPENDENCIES"
    android:value="ocr_japanese" />

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

---

## app/src/main/res/xml/file_paths.xml（新規作成）

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <external-files-path name="photos" path="Pictures/" />
</paths>
```

---

## 実装上の注意点（必ず守ること）

1. **Room Migration**: `MIGRATION_3_4` は `ALTER TABLE photo_memos ADD COLUMN` のみ。既存データを削除・変更しない
2. **SDK Guard**: DynamicColorは `Build.VERSION.SDK_INT >= Build.VERSION_CODES.S` で分岐
3. **CoroutineScope**: DB操作は `viewModelScope.launch(Dispatchers.IO)` 内のみ
4. **FileProvider**: カメラURI生成は `FileProvider.getUriForFile()` 必須
5. **Compose Navigation**: `ModalBottomSheet` は Navigation外で `showBottomSheet: Boolean` stateで管理
6. **既存コードの保持**: `ui/settings/`, `ui/taskdetail/`, `ui/recurring/`, `ui/indefinite/`, `ui/trash/`, `ui/tag/` は今回の実装対象外。既存コードをそのまま維持し、SettingsScreenからナビゲーションで遷移させる
7. **import文**: 各ファイルに必要なimport文を必ず記載する。`com.example.taskschedulerv3.*` の解決漏れに注意
8. **コンパイルエラー禁止**: コードは必ずコンパイル可能な完全形で出力すること

---

## 出力形式

各ファイルについて以下の形式で出力してください:

```
=== [ファイルパス] ===
// [Kotlinコード全文]
```

フェーズ1から順番に出力し、各フェーズ完了後に「✅ PHASEn 完了」と明示してください。
