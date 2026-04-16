# ローカルAIへ貼る分割プロンプトセット（第1弾〜第8弾）

作成日: 2026-04-17  
用途: TaskSchedulerV3 の改修をローカルAIへ段階的に依頼するための分割プロンプト集

使い方:
- 各弾を順番にローカルAIへ貼る
- 1回で全体を作らせない
- 毎回「既存構造を壊さず、差分実装にする」と明示する
- 各弾の出力後にビルド確認してから次へ進む

---

# 共通の前置き（毎回最初に付ける）

あなたは Android Kotlin / Jetpack Compose / Room / Navigation Compose に精通した上級エンジニアです。  
既存プロジェクト TaskSchedulerV3 の master ローカルソースを直接読んで、**既存構造に合わせて差分実装**してください。  
勝手に全面リネーム・全面再構成・不要なアーキテクチャ変更はしないでください。  
**既存ファイル名・既存パッケージ・既存状態管理を優先して尊重**してください。  
もし既存コードと指示がズレる場合は、既存コードに整合する形で安全に読み替えてください。  
出力は**変更ファイルごとに完全コード**で、`=== file path ===` 形式で区切ってください。  
省略や疑似コードは禁止です。  
import を含め、コンパイル可能な形にしてください。  
まず対象ファイルを特定し、不足があれば対象追加も提案してください。  

共通要件:
- 一覧中心UIを維持する
- ダークテーマ優先
- 背景 `#0E0E10`
- Surface `#16161A`
- Accent `#7C6AFF`
- Danger `#F46060`
- Warning `#F0A030`
- Success `#3DD68C`
- タグ色入力UIは廃止方向
- 保存ボタンは右上へ移動
- 仮登録は通常Taskと分離して扱う
- 破壊的Migrationは禁止
- 既存OCR / PhotoMemo は再利用する

---

# 第1弾: DB基盤と仮登録モデル追加

以下を実装してください。

目的:
- クイック登録用の仮登録データを保持する基盤を作る
- DB version を上げて安全に migration する

必須対応:
1. `QuickDraftTask` エンティティ新規追加
2. `QuickDraftTaskDao` 新規追加
3. `AppDatabase.kt` を修正して version を 6 に上げる
4. `MIGRATION_5_6` を追加して `quick_draft_tasks` テーブルを作る
5. `quickDraftTaskDao()` を追加
6. 既存DBを壊さない

Entity仕様:
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

status候補:
- DRAFT
- CONVERTED
- DISCARDED

DAO必須メソッド:
- getDrafts()
- getById(id)
- insert(...)
- update(...)
- delete(...)
- updateStatus(...)

出力対象候補:
- `.../data/model/QuickDraftTask.kt`
- `.../data/db/QuickDraftTaskDao.kt`
- `.../data/db/AppDatabase.kt`

最後に以下も出してください:
- 変更対象ファイル一覧
- 追加import一覧
- ビルド時に注意すべき点

---

# 第2弾: Repository層と変換処理追加

以下を実装してください。

目的:
- 仮登録のCRUDと、本登録への変換処理をRepositoryにまとめる

必須対応:
1. `QuickDraftRepository` 新規作成
2. 仮登録一覧取得
3. 仮登録作成
4. 仮登録更新
5. 仮登録削除
6. 仮登録から通常Taskへの変換
7. 変換後に draft status を `CONVERTED` へ変更
8. 既存 `TaskRepository` との接続が必要なら最小差分で実施

変換処理要件:
- QuickDraftTask から Task を生成する
- タイトルは draft.title を使う
- メモは draft.description と OCR を必要に応じて統合してよい
- 写真がある場合、既存 PhotoMemo 連携を可能な範囲で行う
- 変換失敗時に draft が中途半端な状態にならないようにする

既存コード優先ルール:
- 既存Repository命名規則に合わせる
- 既存DIなしなら手動注入のまま合わせる
- suspend / Flow の使い方も既存に合わせる

出力対象候補:
- `.../data/repository/QuickDraftRepository.kt`
- 必要なら `TaskRepository.kt`
- 必要なら `PhotoMemoDao.kt` または関連Repositoryの差分

最後に以下も出してください:
- 本登録処理のデータフロー説明
- 今の時点で未実装の依存箇所一覧

---

# 第3弾: 一覧画面の表示モード切替とタグフィルタ

以下を実装してください。

目的:
- 一覧画面で「今日 / すべて / 完了 / 繰り返し / 仮登録」の切替をできるようにする
- タグフィルタを簡単にする

必須対応:
1. schedulelist 画面の既存構造を読んで改修
2. 表示モードタブを追加
3. タグフィルタRowを追加
4. TagDao / TaskDao に不足クエリがあれば追加
5. ViewModel に `selectedTagId` と `displayMode` を追加
6. 仮登録モード時は QuickDraft 一覧を出せるようにする

表示モード:
- TODAY
- ALL
- DONE
- RECURRING
- DRAFT

タグフィルタ仕様:
- 横スクロールチップ
- 先頭に「すべて」
- 初期は単一選択
- 将来複数選択へ拡張しやすいstate設計

UI仕様:
- ダーク基調
- チップのアクティブ色は Accent
- 非アクティブは Surface
- 情報密度を下げすぎない

出力対象候補:
- `.../ui/schedulelist/...Screen.kt`
- `.../ui/schedulelist/...ViewModel.kt`
- `.../data/db/TaskDao.kt`
- `.../data/db/TagDao.kt`
- 必要なら `.../ui/components/TagFilterRow.kt`
- 必要なら `.../ui/components/DisplayModeTabs.kt`

最後に以下も出してください:
- 一覧表示ロジックの分岐説明
- 既存コードへ与える影響

---

# 第4弾: タスクリスト行UIの刷新

以下を実装してください。

目的:
- 一覧1行の視認性を上げる
- 優先度、進捗、残日数をすぐ見分けられるようにする

必須対応:
1. タスクリスト1行UIを共通部品化または既存行UIを改修
2. 左端に優先度ラインを追加
3. チェックUIを整理
4. タイトル、タグ、進捗バー、残日数チップを見やすく配置
5. 完了時は行を少しMuted化
6. タグは最大2件表示 + 超過分は省略可能

色ルール:
- 高優先度: `#F46060`
- 中優先度: `#F0A030`
- 低優先度: `#3DD68C`
- 通常: muted border系

残日数ルール:
- 今日 / 期限切れ: 赤
- 1〜3日: 橙
- 4〜7日: Accent寄り or 黄系
- 8日以上: muted
- 完了: muted

出力対象候補:
- `.../ui/components/TaskListRow.kt`
- `.../ui/components/RemainingDaysBadge.kt`
- `.../ui/schedulelist/...Screen.kt`
- 必要ならテーマ関連

最後に以下も出してください:
- 行UIの設計意図
- 視認性改善ポイント

---

# 第5弾: 予定追加画面の右上保存化

以下を実装してください。

目的:
- 予定追加画面の保存ボタンを最下部から右上に移す
- 長いフォームでも保存しやすくする

必須対応:
1. addtask 画面の既存構造を確認
2. 下部保存ボタンを削除
3. TopBar 右上に保存ボタン追加
4. 左に閉じる/戻る、中央にタイトル
5. `canSave` 判定を ViewModel か state に追加
6. 保存中の二重送信を防ぐ
7. エラー時の扱いを既存に合わせて維持

UI仕様:
- TopBar固定
- スクロールしても保存導線が消えない
- 保存不可時は非活性表示
- 保存中はローディング状態

出力対象候補:
- `.../ui/addtask/...Screen.kt`
- `.../ui/addtask/...ViewModel.kt`
- 関連stateファイル

最後に以下も出してください:
- 既存保存フローとの差分説明
- 削除したUI要素一覧

---

# 第6弾: タグ管理の色入力廃止

以下を実装してください。

目的:
- タグ登録の色設定をやめて、タグを統一UIにする

必須対応:
1. tag 画面の既存フォームを確認
2. 色入力欄を削除
3. 色プレビューがあれば削除
4. 保存時に固定色をセット
5. タグ表示側も共通色UIに寄せる
6. 既存DBの `color` カラムは残してよい

固定色候補:
- `#7A7A8C`

注意:
- DB Migration で color を消さない
- UIからのみ廃止する
- 既存タグ編集画面があるならそこも同様に対応

出力対象候補:
- `.../ui/tag/...Screen.kt`
- `.../ui/tag/...ViewModel.kt`
- 必要なら `TagRepository` 関連差分

最後に以下も出してください:
- 今後DBからcolorを消す場合の注意点

---

# 第7弾: 繰り返し予定管理画面の実装

以下を実装してください。

目的:
- 繰り返し予定を通常追加導線ではなく、設定画面側で登録・管理できるようにする

必須対応:
1. 設定画面に「繰り返し予定管理」導線追加
2. 繰り返し予定管理画面を作成または既存改修
3. 対応ルールを3種類実装
   - N日ごと
   - 曜日複数指定
   - 毎月日付複数指定
4. 一覧画面で `RECURRING` モード時に表示可能にする
5. 既存Taskの recurrence関連カラムへ整合させる

ルール保存方針:
- recurrencePattern に種別文字列
- recurrenceDays にCSV文字列
- recurrenceEndDate は既存に合わせる

仕様:
- N日ごと: `EVERY_N_DAYS`, recurrenceDays=`3`
- 曜日複数: `WEEKLY_MULTI`, recurrenceDays=`1,3,5`
- 毎月日付複数: `MONTHLY_DATES`, recurrenceDays=`5,20,28`

月末補正:
- 存在しない日付はその月はスキップ

出力対象候補:
- `.../ui/settings/...Screen.kt`
- `.../ui/recurring/RecurringTaskManageScreen.kt`
- `.../ui/recurring/RecurringTaskEditorSheet.kt`
- 必要なら `.../ui/recurring/...ViewModel.kt`
- 必要なら `Task.kt` または repository の差分

最後に以下も出してください:
- recurrencePattern / recurrenceDays の保存ルール表
- 将来拡張時の注意点

---

# 第8弾: クイック登録UIと最終ナビ接続

以下を実装してください。

目的:
- 写真撮影から仮登録し、一覧や編集画面で本登録まで進められるようにする
- MainActivity / navigation へ最終接続する

必須対応:
1. `QuickDraftViewModel` 実装
2. `QuickDraftListScreen` 実装
3. `QuickDraftEditScreen` 実装
4. カメラ撮影後に即 draft を作成する導線を追加
5. 自動タイトル生成 `yyyy-MM-dd HH:mm 仮登録`
6. 一覧から編集 / 本登録 / 削除できるようにする
7. MainActivity または navigation に接続する
8. 既存PhotoMemo / OCR導線がある場合は可能な範囲で再利用する

画面仕様:
- List:
  - サムネイル
  - タイトル
  - 作成日時
  - OCR抜粋
  - 編集
  - 本登録
  - 削除
- Edit:
  - タイトル
  - メモ
  - 日付
  - 時刻
  - 優先度
  - タグ
  - OCR確認
  - 写真確認
  - 保存 / 本登録 / 削除

実装ルール:
- 通常Taskへ即保存しない
- 必ず QuickDraftTask を経由する
- 本登録後は draft を `CONVERTED` にする
- 一覧 `DRAFT` モードでも表示できるようにする

出力対象候補:
- `.../ui/quickdraft/QuickDraftViewModel.kt`
- `.../ui/quickdraft/QuickDraftListScreen.kt`
- `.../ui/quickdraft/QuickDraftEditScreen.kt`
- `.../MainActivity.kt`
- 必要なら navigation 関連ファイル
- 必要なら photo/camera 連携差分

最後に以下も出してください:
- 最終導線図
- 手動テスト項目一覧
- まだ残る改善余地

---

# 最後にローカルAIへ毎回付ける締めの指示

出力前に以下を必ず行ってください。

1. 既存ファイル構造を確認する
2. 既存命名規則に合わせる
3. コンパイル不能な仮コードを出さない
4. 不明な既存型は推測で壊さず、既存に寄せて安全に書く
5. 出力は変更ファイルだけに限定する
6. 各ファイルの先頭に file path を付ける
7. 最後に「変更概要」と「追加で必要になりそうな追従修正」を箇条書きする

