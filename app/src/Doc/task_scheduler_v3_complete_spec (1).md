# TaskSchedulerV3 改修仕様書（完全版ドラフト）

作成日: 2026-04-17  
対象: TaskSchedulerV3 master ベースの現行アプリ改修  
目的: 現行V3をベースに、効率化・スタイリッシュ化・一覧中心運用・仮登録導線強化を行う

---

## 1. 目的

本改修では、TaskSchedulerV3 の現在の master 状態を基準として、以下を実現する。

1. 予定一覧画面でタグフィルタを簡単に行えるようにする
2. 予定登録の保存ボタンを最下部から右上に変更する
3. タグ登録の色設定を廃止し、タグ色を統一する
4. 繰り返し予定を実用的に登録・管理できるようにする
5. 写真から即座に仮登録できるクイック登録機能を追加する
6. 全体の配色・余白・カードデザイン・導線を整理し、より見やすく効率的なUIにする

本仕様書は、他のコード生成AIやローカルAIがそのまま改修実装に着手できる粒度で記述する。

---

## 2. 前提

- ベースは TaskSchedulerV3 の **master** とする
- 現在のアプリは一覧中心の構成に寄っており、下部ナビはシンプル構成で運用されている前提で設計する
- 既存のOCR関連・写真関連の実装は活かす
- 既存の Task / Tag / PhotoMemo / Room / Compose 構成は可能な限り流用する
- 破壊的変更は避け、段階的改修を前提とする

---

## 3. 改修コンセプト

### 3.1 全体方針

コンセプトは以下とする。

**「一覧で迷わず、最短で登録できる、静かで上質なスケジューラ」**

狙いは次の通り。

- カレンダーで探すより、一覧で絞り込んで即確認できる
- 予定登録は長いフォームを下までスクロールしなくても保存できる
- 色を減らし、重要度と残日数だけを視覚的に強調する
- 頻度の低い繰り返し設定は通常登録から分離し、設定画面側で管理する
- 写真から取り逃しなく仮登録し、あとで整理できる

---

## 4. デザイン方針

添付モック `taskflow-v4-concept-1.html` と `tasklist_mockup.html` の方向性を参考に、以下の方針で統一する。

### 4.1 配色方針

#### 基本トーン
- ダークを主軸にする
- 明るすぎる装飾色は増やさない
- アクセントは1色中心
- 緊急・注意・完了だけ意味色を使う

#### 推奨カラーパレット（ダーク）
- Background: `#0E0E10`
- Surface: `#16161A`
- Surface-2: `#1C1C21`
- Surface-3: `#242429`
- Border: `rgba(255,255,255,0.07)`
- Text: `#E8E8F0`
- TextMuted: `#7A7A8C`
- Accent: `#7C6AFF`
- AccentSub: `#2DD4BF`
- Danger: `#F46060`
- Warning: `#F0A030`
- Success: `#3DD68C`
- Info: `#5FB0F7`

#### 推奨カラーパレット（ライト）
- Background: `#F5F4F0`
- Surface: `#FAFAF7`
- Surface-2: `#FFFFFF`
- SurfaceOff: `#EEECE8`
- Border: `#D0CDC7`
- Text: `#1C1A16`
- TextMuted: `#6A6965`
- Primary: `#01696F`
- PrimaryHover: `#0C4E54`

### 4.2 色の使い分け
- タグ色は廃止し、タグは全て共通トーンで表示する
- 優先度はカード左側のライン色で示す
- 残日数はチップ色で示す
- 進捗は細いプログレスバーで示す
- 画面全体に多色をばらまかない

### 4.3 タイポグラフィ
- 日本語フォントは `Noto Sans JP` を基準とする
- 数字・進捗率・残日数は視認性のため `JetBrains Mono` 併用可
- サイズ感:
  - 画面タイトル: 18sp〜24sp
  - タスクタイトル: 14sp〜15sp
  - 補助情報: 11sp〜12sp
  - チップ文字: 10sp〜11sp

### 4.4 レイアウト方針
- 余白は詰めすぎないが、情報密度は落とさない
- 画面上部にフィルタ群を集約する
- 一覧行は1画面に十分な件数が見える高さに抑える
- カードは大きすぎず、一覧の視認を優先する
- 主要操作は上部または親指位置に集約する

---

## 5. 画面構成

### 5.1 全体構成

下部ナビ構成は現行のシンプル構成をベースにする。

#### 維持する構成
- 一覧
- 設定

#### 一覧画面の中で切り替える対象
- 通常タスク
- 繰り返しタスク表示
- 仮登録タスク表示

### 5.2 画面一覧

1. 予定一覧画面（主画面）
2. 予定登録ボトムシート / 登録画面
3. タグ管理画面
4. 設定画面
5. 繰り返し予定管理画面（設定配下）
6. 仮登録管理画面
7. 仮登録編集画面
8. タスク詳細画面

---

## 6. 一覧画面仕様

### 6.1 目的
一覧画面を最重要画面とする。
ユーザーはここで「探す」「絞る」「確認する」「追加する」の大半を完結できるようにする。

### 6.2 上部構成
上から順に以下を並べる。

1. TopBar
   - タイトル
   - 検索アイコン
   - ソート / 表示切替アイコン
2. 表示タブ
   - 今日
   - すべて
   - 完了
   - 繰り返し
   - 仮登録
3. タグフィルタチップ横スクロール
   - すべて
   - タグ1
   - タグ2
   - タグ3 ...
4. 必要なら状態フィルタ
   - 未完了
   - 高優先度
   - 期限近い

### 6.3 タグフィルタ仕様

#### 目的
予定一覧画面でタグフィルタを簡単にできるようにする。

#### UI仕様
- 横スクロール可能な `FilterChip` 群で表示
- 初期状態は「すべて」選択
- 1タップで絞り込み
- もう一度押すと解除
- 複数タグ同時選択も可能にするかは実装判断だが、初期実装は単一選択でよい

#### 推奨仕様
- 最初は単一選択で実装
- 将来的に複数選択しやすいように `selectedTagIds: Set<Int>` で内部管理

#### 表示色
- アクティブ: accent 系背景
- 非アクティブ: surface 系背景
- タグ固有色は使わない

### 6.4 タスクリスト行仕様

各行の構成:
- 左端に優先度ライン（3px）
- チェックボックス
- タイトル
- 進捗バー
- タグ表示（最大2件 + 省略表記）
- 残日数チップ
- 必要に応じて時刻表示

#### 優先度ライン色
- 高: 赤
- 中: 橙
- 低: 緑
- なし/通常: 枠色相当

#### 残日数チップ
- 今日 / 期限切れ: 赤系
- 1〜3日: 橙系
- 4〜7日: 黄〜アクセント系
- 8日以上: 落ち着いたグレーまたは緑系
- 完了: muted系

### 6.5 仮登録表示仕様
- 一覧上の「仮登録」タブ選択時に表示
- 本登録前の仮データを一覧表示
- タイトルは自動生成された日時名を表示
- サムネイル表示あり
- 編集 / 本登録 / 削除の導線を各行に持つ

---

## 7. 予定登録画面仕様

### 7.1 変更点

**保存ボタンを最下部から廃止し、右上端に配置する。**

### 7.2 UI方針
- 上部ヘッダーに「閉じる」「保存」を配置
- 下部固定保存ボタンは削除
- 長いフォームでもスクロール量に関係なく保存可能

### 7.3 推奨UI構成
- 左上: 戻る / 閉じる
- 中央: 「予定を追加」
- 右上: 保存テキストボタンまたはアイコン付きボタン

### 7.4 保存状態制御
- 必須項目未入力時は非活性
- 保存中はローディング表示に切替
- 保存成功時は自動で一覧へ戻る

### 7.5 入力項目
- タイトル
- メモ
- 日付
- 時刻
- 優先度
- タグ
- 通知
- 進捗
- 既存の写真/OCR関連が必要ならここに統合

---

## 8. タグ管理仕様

### 8.1 変更点

**タグ登録で色の登録機能を削除し、色はすべて統一する。**

### 8.2 実装方針
短期はDBに `color` カラムが存在しても、UIから色入力欄を削除する。
保存時は固定値を設定する。

### 8.3 固定値案
- `#7A7A8C` のような muted 系固定値
- もしくはテーマ依存色を使う場合は DB 側にダミー値保存

### 8.4 中期方針
将来的にタグ色カラムを完全廃止する場合は別Migrationで行う。
本改修ではまず「入力UIを削除」「表示は共通化」を優先する。

### 8.5 タグ表示仕様
- 背景は共通の `surface-3`
- 文字色は `text-muted` または `primary`
- サイズは小さめ、圧迫感を出さない

---

## 9. 繰り返し予定仕様

### 9.1 変更点

**繰り返し予定登録をできるように実装する。**  
ただし、登録頻度が低いため、通常の追加導線ではなく設定画面から登録・管理する。

### 9.2 管理場所
- 設定画面配下に「繰り返し予定管理」を追加
- 一覧画面では「繰り返し」フィルタで確認可能にする

### 9.3 登録ルール
対応する繰り返しルールは以下。

1. 指定日から **〇日ごと**
2. **曜日指定**（複数曜日指定可能）
3. **毎月日にち指定**（複数日にち指定可能）

### 9.4 UI構成
繰り返し予定管理画面では以下の構成にする。

- 上部タイトル
- 登録済み繰り返し予定一覧
- 追加ボタン
- 追加 / 編集シート
  - タイトル
  - 説明
  - 開始日
  - 終了条件（任意）
  - ルール種別
  - ルール詳細
  - 通知
  - 保存

### 9.5 ルール詳細仕様

#### A. 〇日ごと
- 開始日を基準日とする
- 例: 3日ごと
- 入力項目: 間隔日数（整数 >= 1）

#### B. 曜日指定
- 月〜日から複数選択可能
- 例: 火曜日のみ、月水金
- 保存形式はCSVでよい

#### C. 毎月日にち指定
- 1〜31から複数選択可能
- 例: 5日、20日
- 月末処理ルールは仕様で明示する

### 9.6 月末補正ルール
例えば31日指定で31日がない月は以下のどちらかを選択する。

推奨仕様:
- 存在しない日はその月はスキップする

別案:
- 月末日に補正する

今回の仕様書では **スキップ** を推奨する。
理由は想定外の自動補正よりも挙動が読みやすいから。

### 9.7 一覧での確認
一覧画面の「繰り返し」タブまたはフィルタで表示できるようにする。
表示内容:
- タイトル
- ルール要約（例: 毎週 火 / 木）
- 次回予定日
- 有効/無効状態

---

## 10. クイック登録仕様

### 10.1 変更点

**写真を撮って仮登録するクイック登録機能を追加する。**

### 10.2 フロー
1. カメラを起動
2. 写真を撮る
3. 仮登録が自動生成される
4. 仮登録管理画面に蓄積される
5. あとで編集して本登録する
6. 本登録後は予定一覧に出現する

### 10.3 自動タイトル
仮登録されたタスク名は今日の日時を使って自動生成する。

推奨形式:
- `2026-04-17 02:22 仮登録`

または日本語表記:
- `4/17 02:22 撮影メモ`

推奨は後者より前者。ソート性が高いため。

### 10.4 仮登録管理画面
表示項目:
- サムネイル
- 仮タイトル
- 作成日時
- OCR結果の一部（あれば）
- 編集ボタン
- 本登録ボタン
- 削除ボタン

### 10.5 仮登録編集画面
編集できる項目:
- タイトル
- 説明
- 日付
- 時刻
- タグ
- 優先度
- 写真確認
- OCRテキスト確認

### 10.6 本登録処理
本登録時の挙動:
- QuickDraftTask から Task を生成
- 必要なら PhotoMemo を taskId に紐付ける
- 仮登録は `CONVERTED` に変更、または一覧から除外

---

## 11. データ設計

### 11.1 Task
既存の `Task` は継続利用する。
既に以下の繰り返し系要素がある前提で活かす。

- recurrencePattern
- recurrenceDays
- recurrenceEndDate
- isIndefinite
- progress

### 11.2 Tag
`color` は短期的には残すが、UIでは使わない。
保存時は固定値を入れる。

### 11.3 PhotoMemo
既存の `ocrText`, `sourceType` はそのまま活用する。
クイック登録の本登録後紐付け先として利用する。

### 11.4 新規追加エンティティ案

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
    val status: String = "DRAFT" // DRAFT, CONVERTED, DISCARDED
)
```

### 11.5 Room version
現行が version 5 前提のため、本改修は **version 6** を想定する。

### 11.6 Migration案

```kotlin
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS quick_draft_tasks (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                title TEXT NOT NULL,
                description TEXT,
                photoPath TEXT,
                ocrText TEXT,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                status TEXT NOT NULL DEFAULT 'DRAFT'
            )
            """.trimIndent()
        )
    }
}
```

---

## 12. DAO変更仕様

### 12.1 TaskDao
追加が必要な代表クエリ例:

```kotlin
@Query("SELECT * FROM tasks WHERE isDeleted = 0 ORDER BY startDate ASC, startTime ASC")
fun getAllActiveTasks(): Flow<List<Task>>

@Query("SELECT t.* FROM tasks t INNER JOIN task_tag_cross_ref c ON t.id = c.taskId WHERE c.tagId = :tagId AND t.isDeleted = 0 ORDER BY t.startDate ASC, t.startTime ASC")
fun getTasksByTag(tagId: Int): Flow<List<Task>>

@Query("SELECT * FROM tasks WHERE recurrencePattern IS NOT NULL AND isDeleted = 0 ORDER BY startDate ASC")
fun getRecurringTasks(): Flow<List<Task>>
```

### 12.2 TagDao
タグ取得系は活かす。
一覧用に `getAll()` をそのまま使う。

### 12.3 QuickDraftTaskDao（新規）

```kotlin
@Dao
interface QuickDraftTaskDao {
    @Query("SELECT * FROM quick_draft_tasks WHERE status = 'DRAFT' ORDER BY createdAt DESC")
    fun getDrafts(): Flow<List<QuickDraftTask>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(draft: QuickDraftTask): Long

    @Update
    suspend fun update(draft: QuickDraftTask)

    @Delete
    suspend fun delete(draft: QuickDraftTask)

    @Query("UPDATE quick_draft_tasks SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: Int, status: String, updatedAt: Long)
}
```

---

## 13. Repository変更仕様

### 13.1 TaskRepository
- タグフィルタ用メソッド追加
- 繰り返し一覧取得メソッド追加
- 本登録変換メソッド追加

### 13.2 QuickDraftRepository（新規）
責務:
- 仮登録の作成
- 仮登録一覧取得
- 仮登録編集
- 仮登録からTaskへの変換
- 仮登録削除

---

## 14. ViewModel変更仕様

### 14.1 ScheduleListViewModel
追加状態:
- selectedTagId / selectedTagIds
- currentDisplayMode (`TODAY`, `ALL`, `DONE`, `RECURRING`, `DRAFT`)
- searchQuery

### 14.2 AddTaskViewModel
変更点:
- 保存ボタンが右上に移るため、フォーム全体の状態と `canSave` を管理
- 下部保存ボタン前提のUIロジックがあれば削除

### 14.3 TagViewModel
変更点:
- 色選択state削除
- 保存時固定色投入

### 14.4 RecurringTaskViewModel（新規または既存拡張）
責務:
- 繰り返し予定一覧取得
- ルール別入力状態管理
- 保存・編集・有効化/無効化

### 14.5 QuickDraftViewModel（新規）
責務:
- カメラ撮影完了時に仮登録自動生成
- 仮登録一覧取得
- 編集状態管理
- 本登録実行

---

## 15. Navigation変更仕様

### 15.1 一覧画面
- 現状の一覧ルートは維持
- 一覧内で表示モード切替

### 15.2 設定画面
設定配下に以下を追加:
- 繰り返し予定管理
- 仮登録管理（設定配下でも可、一覧からも遷移可）

### 15.3 仮登録編集画面
- 一覧の仮登録タブから遷移可能

---

## 16. UIコンポーネント仕様

### 16.1 FilterChip
- タグフィルタ
- 表示モードフィルタ
- 軽量な丸チップ
- 選択中だけ accent 系

### 16.2 TaskRow
- 1行コンパクト
- 左優先度ライン
- 進捗バー
- 残日数チップ
- タグ最大2つ表示

### 16.3 RemainingDaysBadge
状態に応じて色変更。

### 16.4 DraftRow
- サムネイル付き
- 仮登録状態バッジ
- 編集 / 本登録 / 削除

---

## 17. 変更対象コード（詳細）

以下は優先的に改修対象とするファイル群である。

### 17.1 予定一覧関連
- `ui/schedulelist/...`
  - タグフィルタRow追加
  - 繰り返しフィルタ追加
  - 仮登録表示モード追加
  - TaskRowのUI整理

### 17.2 予定登録関連
- `ui/addtask/...`
  - 保存ボタンを右上へ移動
  - 下部保存ボタン削除
  - ヘッダー構成追加

### 17.3 タグ関連
- `ui/tag/...`
  - 色入力項目削除
  - 固定色保存
  - 一覧表示の色統一

### 17.4 設定関連
- `ui/settings/...`
  - 繰り返し予定管理導線追加
  - 仮登録管理導線追加

### 17.5 DB関連
- `data/db/AppDatabase.kt`
  - version 6
  - `QuickDraftTask` entity追加
  - `QuickDraftTaskDao` 追加
  - `MIGRATION_5_6` 追加

### 17.6 新規追加候補
- `data/model/QuickDraftTask.kt`
- `data/db/QuickDraftTaskDao.kt`
- `data/repository/QuickDraftRepository.kt`
- `ui/quickdraft/QuickDraftListScreen.kt`
- `ui/quickdraft/QuickDraftEditScreen.kt`
- `ui/quickdraft/QuickDraftViewModel.kt`
- `ui/recurring/RecurringTaskManageScreen.kt`

---

## 18. 実装ロードマップ

### Phase 1: UIの即効改善
1. 一覧画面にタグフィルタ追加
2. 一覧画面に「繰り返し」「仮登録」表示切替追加
3. 予定登録画面の保存ボタンを右上に移動
4. 下部保存ボタンを削除

### Phase 2: タグ整理
1. タグ色入力UI削除
2. タグ表示色共通化
3. タグ保存時に固定色を入れる

### Phase 3: 繰り返し予定管理
1. 設定画面に繰り返し予定管理導線追加
2. 繰り返し予定一覧画面作成
3. 〇日ごと / 曜日指定 / 毎月日付指定 を実装
4. 一覧画面で繰り返しフィルタ表示対応

### Phase 4: クイック登録
1. `QuickDraftTask` テーブル追加
2. カメラ撮影後の仮登録自動生成実装
3. 仮登録管理画面作成
4. 仮登録編集画面作成
5. 本登録変換実装

### Phase 5: 見た目の磨き込み
1. 配色統一
2. 行間・余白・角丸の最適化
3. タスク行デザイン統一
4. ダーク / ライトの整合確認

---

## 19. 受け入れ条件

### 19.1 一覧
- 一覧画面でタグタップだけで絞り込みできる
- タグ固有色がなくても視認性が落ちない
- 繰り返しタスクのみ表示できる
- 仮登録のみ表示できる

### 19.2 登録
- 登録画面で右上保存が機能する
- 下部保存ボタンは存在しない
- 長いフォームでも保存導線が迷わない

### 19.3 タグ
- タグ登録時に色選択UIがない
- 保存後タグは共通デザインで表示される

### 19.4 繰り返し
- 〇日ごと登録できる
- 複数曜日指定できる
- 毎月複数日指定できる
- 一覧で確認できる

### 19.5 仮登録
- カメラ撮影後に仮登録が自動で作成される
- 仮登録一覧で確認できる
- 仮登録から本登録できる
- 本登録後は通常一覧で確認できる

---

## 20. 実装上の注意

1. OCR関連は既存ロジックを再利用する
2. 破壊的Migrationは避ける
3. タグ色はまずUIから消し、DBからの完全削除は後回しにする
4. クイック登録は Task へ直接保存せず、仮登録を経由する
5. 一覧画面の情報密度を維持するため、1行の高さは抑える
6. ダークテーマ優先で設計し、ライトテーマは補完する

---

## 21. コードAI向け出力物

この仕様書をもとに、別紙として以下を作成する。

1. ローカルAI向け実装指示プロンプト
2. 変更対象ファイル一覧とファイル単位の要件
3. 新規ファイル雛形
4. Migrationコード例
5. Compose UIコード方針

