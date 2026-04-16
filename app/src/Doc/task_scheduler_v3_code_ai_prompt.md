# TaskSchedulerV3 改修用 コードAI向け実装プロンプト

あなたは Android Kotlin / Jetpack Compose / Room / Navigation Compose に精通した上級エンジニアです。
既存プロジェクト TaskSchedulerV3 の master を基準として、以下の仕様で改修してください。

## 目的
- 予定一覧画面でタグフィルタを簡単にする
- 予定登録の保存ボタンを最下部から右上に変更する
- タグ登録の色設定を廃止し、色を統一する
- 繰り返し予定登録を実装する
- 写真から仮登録するクイック登録機能を追加する
- UI全体をスタイリッシュで見やすい一覧中心レイアウトへ調整する

## 前提
- ベースは master
- 既存の OCR / PhotoMemo は活用する
- DB は現状 version 5 前提、今回の改修で version 6 に上げる
- 破壊的変更は避ける

## UI方針
- ダークテーマ優先
- 背景 `#0E0E10`
- Surface `#16161A`
- Accent `#7C6AFF`
- Danger `#F46060`
- Warning `#F0A030`
- Success `#3DD68C`
- タグ色は全廃し、共通見た目にする
- 一覧行はコンパクトにする
- 保存は右上アクションにする

## 実装要件

### 1. 一覧画面
- タグフィルタ用の FilterChip 横スクロールを追加
- 表示モード切替を追加: 今日 / すべて / 完了 / 繰り返し / 仮登録
- タスク行に優先度ライン、進捗バー、残日数チップ、タグ最大2件表示を実装
- 仮登録表示モード追加

### 2. 予定登録画面
- 下部保存ボタンを削除
- 右上に保存アクション追加
- ヘッダー左に閉じる、中央にタイトル、右に保存
- 必須項目未入力時は保存不可

### 3. タグ管理
- 色入力欄を削除
- 保存時は固定色文字列を入れる
- タグ表示は全て共通色にする

### 4. 繰り返し予定
- 設定画面配下に繰り返し予定管理画面を追加
- 対応ルール:
  - N日ごと
  - 曜日複数指定
  - 毎月日付複数指定
- 一覧画面では繰り返しだけ表示できるようにする

### 5. クイック登録
- 新規 Entity `QuickDraftTask` を追加
- カメラ撮影後に自動で仮登録を作る
- タイトルは現在日時文字列で自動生成
- 仮登録一覧画面と編集画面を追加
- 本登録時に Task を生成する
- 必要なら PhotoMemo を taskId に紐付ける

## 追加する Entity
```kotlin
@Entity(tableName = "quick_draft_tasks")
data class QuickDraftTask(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String? = null,
    val photoPath: String? = null,
    val ocrText: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val status: String = "DRAFT"
)
```

## Migration
- AppDatabase version を 6 に上げる
- `MIGRATION_5_6` を追加して `quick_draft_tasks` を作成する

## 変更対象ファイル
- MainActivity.kt
- AppDatabase.kt
- TaskDao.kt
- Tag 関連UI
- schedulelist 関連UI
- addtask 関連UI
- settings 関連UI
- 新規 quickdraft 関連一式
- 必要なら recurring 管理画面

## 出力ルール
- 省略せず完全な Kotlin コードを出力する
- 変更ファイルごとに区切って出力する
- 新規ファイルはファイルパス付きで出力する
- import を含めてコンパイル可能な形にする
- コメントは簡潔にする
- 既存構造に寄せる

## 優先順
1. 一覧タグフィルタ
2. 右上保存化
3. タグ色UI削除
4. 繰り返し管理
5. 仮登録
6. UI磨き込み
