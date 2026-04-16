# TaskSchedulerV3 改修用 変更ファイル一覧ごとの詳細指示書

作成日: 2026-04-17  
対象: TaskSchedulerV3 master ベース  
用途: ローカルAI / コード生成AI / 外部実装担当が、改修対象ファイルごとに漏れなく理解・実装できるようにするための完全指示書

---

# 0. この資料の使い方

この資料は「どのファイルを、なぜ、どう変えるか」をファイル単位で固定するためのものです。

各ファイルに対して以下を定義します。

- 目的
- 変更理由
- 変更内容
- UI仕様
- データ仕様
- 実装ルール
- 注意点
- 依存先 / 連動先
- 完了条件

ローカルAIには、この指示書を渡した上で、対象ファイル単位で順番にコード生成させてください。

---

# 1. 改修全体像

今回の改修テーマは以下です。

1. 予定一覧画面でタグフィルタを簡単にする
2. 予定登録の保存ボタンを最下部から右上へ移動する
3. タグ登録の色設定を廃止し、タグ色を統一する
4. 繰り返し予定を設定画面側で登録・管理できるようにする
5. 写真撮影から即座に仮登録するクイック登録機能を追加する
6. 配色・一覧行・カード・余白を整理し、スタイリッシュかつ効率的なUIへ寄せる

---

# 2. 変更対象ファイル一覧

## 2.1 既存ファイル（変更）

- `app/src/main/java/com/example/taskschedulerv3/MainActivity.kt`
- `app/src/main/java/com/example/taskschedulerv3/navigation/...`（存在する場合）
- `app/src/main/java/com/example/taskschedulerv3/data/db/AppDatabase.kt`
- `app/src/main/java/com/example/taskschedulerv3/data/db/TaskDao.kt`
- `app/src/main/java/com/example/taskschedulerv3/data/db/TagDao.kt`（必要に応じて補助クエリ追加）
- `app/src/main/java/com/example/taskschedulerv3/data/model/Task.kt`
- `app/src/main/java/com/example/taskschedulerv3/data/model/Tag.kt`
- `app/src/main/java/com/example/taskschedulerv3/data/model/PhotoMemo.kt`（本改修では基本流用、必要なら参照調整）
- `app/src/main/java/com/example/taskschedulerv3/ui/schedulelist/...`
- `app/src/main/java/com/example/taskschedulerv3/ui/addtask/...`
- `app/src/main/java/com/example/taskschedulerv3/ui/tag/...`
- `app/src/main/java/com/example/taskschedulerv3/ui/settings/...`
- `app/src/main/java/com/example/taskschedulerv3/ui/recurring/...`（既存があれば改修）
- `app/src/main/java/com/example/taskschedulerv3/ui/photo/...`（クイック登録導線との接続が必要な場合）
- `app/src/main/java/com/example/taskschedulerv3/ui/components/...`（共通部品を切り出す場合）
- `app/src/main/AndroidManifest.xml`（必要に応じてカメラ導線再確認）

## 2.2 新規ファイル（追加）

- `app/src/main/java/com/example/taskschedulerv3/data/model/QuickDraftTask.kt`
- `app/src/main/java/com/example/taskschedulerv3/data/db/QuickDraftTaskDao.kt`
- `app/src/main/java/com/example/taskschedulerv3/data/repository/QuickDraftRepository.kt`
- `app/src/main/java/com/example/taskschedulerv3/ui/quickdraft/QuickDraftListScreen.kt`
- `app/src/main/java/com/example/taskschedulerv3/ui/quickdraft/QuickDraftEditScreen.kt`
- `app/src/main/java/com/example/taskschedulerv3/ui/quickdraft/QuickDraftViewModel.kt`
- `app/src/main/java/com/example/taskschedulerv3/ui/components/TagFilterRow.kt`
- `app/src/main/java/com/example/taskschedulerv3/ui/components/RemainingDaysBadge.kt`
- `app/src/main/java/com/example/taskschedulerv3/ui/components/TaskListRow.kt`
- `app/src/main/java/com/example/taskschedulerv3/ui/components/DisplayModeTabs.kt`
- `app/src/main/java/com/example/taskschedulerv3/ui/recurring/RecurringTaskManageScreen.kt`（既存が弱い場合）
- `app/src/main/java/com/example/taskschedulerv3/ui/recurring/RecurringTaskEditorSheet.kt`

---

# 3. ファイル別 詳細指示

---

## 3.1 `MainActivity.kt`

### 目的
- 全体導線を整理する
- 一覧中心UIの切替を自然に扱う
- 仮登録画面や繰り返し管理画面への遷移を追加する

### 変更理由
- 現在の画面構成はシンプルになっているが、今回追加する「仮登録管理」「繰り返し管理」への導線を整理する必要がある
- 一覧画面での表示モード切替に合わせてFABの役割も切り替えたい可能性がある

### 変更内容
- 一覧 / 設定 の既存下部ナビ構成は基本維持する
- 一覧画面への遷移は現状維持
- 設定画面から「繰り返し予定管理」へ遷移可能にする
- 一覧画面から「仮登録管理」へ遷移、または一覧画面内部で仮登録表示に切り替え可能にする
- FAB表示条件を見直す

### FAB仕様
- 通常一覧では `AddTaskBottomSheet` を開く
- 仮登録表示モード中は `カメラ起動` を優先するか、別ボタンで扱う
- 初期実装は混乱回避のため以下を推奨:
  - 一覧画面FAB = 通常タスク追加
  - 仮登録は画面内の専用ボタンまたは上部アイコンからカメラ起動

### 実装ルール
- 既存ナビゲーション構造を大きく壊さない
- 追加ルートは明示的な定数化を推奨
- 画面の状態管理をMainActivityに持ちすぎない

### 依存先
- schedulelist 画面
- settings 画面
- quickdraft 画面
- recurring 管理画面

### 完了条件
- 一覧 / 設定 から必要画面へ遷移できる
- 既存追加導線が壊れていない
- 仮登録や繰り返し導線が迷わない

---

## 3.2 `AppDatabase.kt`

### 目的
- 新しい仮登録テーブルをRoomに追加する
- versionを上げ、Migrationを定義する

### 変更理由
- 写真撮影直後に通常Taskへ即保存すると未整理データが増えるため、仮登録専用テーブルが必要

### 変更内容
- `version = 6` に更新
- `QuickDraftTask` entity を `entities = [...]` に追加
- `abstract fun quickDraftTaskDao(): QuickDraftTaskDao` を追加
- `MIGRATION_5_6` を追加
- `addMigrations(...)` に migration を追加

### Migration仕様
作成するテーブル:

```sql
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
```

### 実装ルール
- 既存テーブルの削除や再生成はしない
- 既存データを破壊しない
- exportSchema は現状に合わせる

### 完了条件
- DBが version 6 で起動する
- 旧データからのアップグレードでクラッシュしない
- quick_draft_tasks が正常生成される

---

## 3.3 `Task.kt`

### 目的
- 既存Taskの繰り返し仕様を再利用・拡張可能な状態で明示化する

### 変更理由
- 繰り返し予定を強化するため、既存カラムの意味を統一しないと実装がブレる

### 変更内容
- 基本的には既存構造を維持
- 必要であればコメントまたは enum の意味を明文化
- `recurrencePattern` のパターン値を整理
- `recurrenceDays` は CSV文字列として扱う前提を固定

### 推奨パターン
- `NONE`
- `EVERY_N_DAYS`
- `WEEKLY_MULTI`
- `MONTHLY_DATES`

### `recurrenceDays` の使い方
- `EVERY_N_DAYS`: 間隔日数を文字列で保存してもよいが、別カラムがないなら `recurrenceDays = "3"` のように扱う
- `WEEKLY_MULTI`: 曜日番号CSV 例 `2,4,6`
- `MONTHLY_DATES`: 月内日付CSV 例 `5,20,28`

### 注意点
- ここで新カラムを安易に増やさない
- まずは既存カラムで要件を満たす

### 完了条件
- 繰り返しロジック実装側がTaskだけで必要情報を判断できる

---

## 3.4 `Tag.kt`

### 目的
- タグ色をUIで使わない運用に切り替える

### 変更理由
- タグ色設定は手間が大きく、今回の要件では廃止したい

### 変更内容
- 短期ではモデルそのものは大きく変えない
- `color` カラムは残してよい
- ただしUI・ViewModel・Repository 側では固定値運用にする

### 実装ルール
- DBから完全削除するのは別フェーズ
- 今回は互換性優先

### 完了条件
- タグ色入力UIがなくても既存DBと矛盾しない

---

## 3.5 `PhotoMemo.kt`

### 目的
- クイック登録との関係を整理する

### 変更理由
- 仮登録とPhotoMemoの責務分離が必要

### 変更内容
- 基本は変更不要
- 必要なら QuickDraftTask 本登録時に PhotoMemo と連動できるよう、リポジトリ側で接続する

### 実装ルール
- 仮登録の主役は PhotoMemo ではなく QuickDraftTask にする
- PhotoMemo は画像/OCR保存の既存資産として活かす

### 完了条件
- PhotoMemoを壊さず、仮登録との共存ができる

---

## 3.6 `TaskDao.kt`

### 目的
- 一覧画面の絞り込み機能を支える
- 繰り返しタスク取得を可能にする

### 変更理由
- タグフィルタと繰り返し表示が要件に含まれる

### 追加候補クエリ

```kotlin
@Query("SELECT * FROM tasks WHERE isDeleted = 0 ORDER BY startDate ASC, startTime ASC")
fun getAllActiveTasks(): Flow<List<Task>>

@Query("SELECT t.* FROM tasks t INNER JOIN task_tag_cross_ref c ON t.id = c.taskId WHERE c.tagId = :tagId AND t.isDeleted = 0 ORDER BY t.startDate ASC, t.startTime ASC")
fun getTasksByTag(tagId: Int): Flow<List<Task>>

@Query("SELECT * FROM tasks WHERE recurrencePattern IS NOT NULL AND isDeleted = 0 ORDER BY startDate ASC")
fun getRecurringTasks(): Flow<List<Task>>

@Query("SELECT * FROM tasks WHERE isCompleted = 1 AND isDeleted = 0 ORDER BY updatedAt DESC")
fun getCompletedTasks(): Flow<List<Task>>
```

### 追加検討クエリ
- 今日のタスク
- 期限近いタスク
- タグ+状態の複合条件

### 注意点
- SQLを増やしすぎて管理不能にしない
- 単純条件はViewModel側フィルタでもよい
- ただし件数が多いならDB側フィルタ優先

### 完了条件
- 一覧画面のモード切替とタグフィルタがTaskDao経由で実現可能

---

## 3.7 `TagDao.kt`

### 目的
- 一覧用タグフィルタの元データを安定取得する

### 変更理由
- タグフィルタが一覧の主要機能になる

### 変更内容
- 基本は既存取得系を活用
- 必要なら一覧表示用にソート済み全件取得メソッドを追加

### 追加候補

```kotlin
@Query("SELECT * FROM tags ORDER BY sortOrder ASC, name ASC")
fun getAllForFilter(): Flow<List<Tag>>
```

### 完了条件
- 一覧上部チップを安定表示できる

---

## 3.8 `QuickDraftTask.kt`（新規）

### 目的
- 仮登録の専用モデルを定義する

### 実装内容

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

### statusの値
- `DRAFT`
- `CONVERTED`
- `DISCARDED`

### 完了条件
- 仮登録情報を通常Taskと分離して保持できる

---

## 3.9 `QuickDraftTaskDao.kt`（新規）

### 目的
- 仮登録データのCRUDを担当する

### 必須メソッド
- 一覧取得
- 追加
- 更新
- 削除
- ステータス更新
- ID指定取得

### 推奨インターフェース

```kotlin
@Dao
interface QuickDraftTaskDao {
    @Query("SELECT * FROM quick_draft_tasks WHERE status = 'DRAFT' ORDER BY createdAt DESC")
    fun getDrafts(): Flow<List<QuickDraftTask>>

    @Query("SELECT * FROM quick_draft_tasks WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): QuickDraftTask?

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

### 完了条件
- 仮登録の作成・更新・変換・削除が可能

---

## 3.10 `QuickDraftRepository.kt`（新規）

### 目的
- 仮登録と本登録変換処理をまとめる

### 責務
- 写真撮影後の仮登録作成
- 仮登録一覧取得
- 仮登録更新
- Taskへの変換
- 変換後の状態更新

### 本登録処理の要件
- QuickDraftTask から Task を生成
- 必要に応じて PhotoMemo.taskId を更新
- draft.status = CONVERTED にする

### 注意点
- 変換処理は1トランザクション相当で扱うのが理想
- 不整合を防ぐ

### 完了条件
- 仮登録→本登録の一連処理がリポジトリ1箇所で完結する

---

## 3.11 `ui/schedulelist/...` 一式

### 目的
- 一覧画面を主役に再設計する

### 変更理由
- 今回最重要の改修箇所

### 必須変更
1. 表示モードタブの追加
   - 今日
   - すべて
   - 完了
   - 繰り返し
   - 仮登録
2. タグフィルタRow追加
3. タスク行デザイン整理
4. 仮登録表示対応
5. 一覧上での情報整理強化

### 表示モード仕様
- TODAY: 当日関連タスク
- ALL: 全タスク
- DONE: 完了タスク
- RECURRING: recurrencePatternあり
- DRAFT: QuickDraftTask一覧

### タグフィルタ仕様
- 上部横スクロールチップ
- `すべて` を含む
- 単一選択で開始
- state管理はViewModel

### 行デザイン仕様
- 左端ライン 3px
- タイトル1行優先
- 下に細い進捗バー
- タグは最大2つ表示
- 残日数チップを右側に表示

### 連動先
- TagDao
- TaskDao
- QuickDraftRepository / ViewModel

### 完了条件
- 一覧画面だけでかなりの確認・絞り込みができる

---

## 3.12 `TagFilterRow.kt`（新規）

### 目的
- タグフィルタUIを部品化する

### UI仕様
- `LazyRow`
- FilterChip 群
- 先頭に `すべて`
- active/inactive の色差あり

### props例
- tags: List<Tag>
- selectedTagId: Int?
- onTagSelected: (Int?) -> Unit

### 完了条件
- 一覧画面から独立して再利用可能

---

## 3.13 `DisplayModeTabs.kt`（新規）

### 目的
- 表示モード切替UIを共通部品化

### モード
- TODAY
- ALL
- DONE
- RECURRING
- DRAFT

### 完了条件
- 一覧画面上部で自然に切り替えられる

---

## 3.14 `TaskListRow.kt`（新規）

### 目的
- タスクリスト1行の見た目を統一する

### 必須要素
- 優先度ライン
- チェック
- タイトル
- 進捗バー
- タグ
- 残日数
- 時刻（必要時）

### 色ルール
- 高: 赤
- 中: 橙
- 低: 緑
- 完了: muted化

### 完了条件
- 一覧の視認性が上がり、行の密度が適切

---

## 3.15 `RemainingDaysBadge.kt`（新規）

### 目的
- 期限情報を統一UIで表示する

### ロジック
- 今日 / 期限切れ: 赤
- 1〜3日: 橙
- 4〜7日: 黄またはaccent寄り
- 8日以上: muted
- 完了: muted or done色

### props例
- remainingDays: Int
- isDone: Boolean

### 完了条件
- 期限状態が一目でわかる

---

## 3.16 `ui/addtask/...` 一式

### 目的
- 保存導線を右上化する

### 必須変更
- 下部保存ボタン削除
- Header右上に保存を移動
- 入力状態から `canSave` を判定

### 推奨構成
- TopBar: 閉じる / タイトル / 保存
- Body: 入力フォーム

### UIルール
- 保存ボタンはスクロール位置に依存しない
- 保存中は再タップ不可
- バリデーションエラー表示を明確にする

### 完了条件
- 下までスクロールしなくても保存できる

---

## 3.17 `ui/tag/...` 一式

### 目的
- タグ色設定を廃止する

### 必須変更
- 色入力欄削除
- 色プレビュー削除
- 保存時固定色投入

### 固定値案
- `#7A7A8C`

### 注意点
- DBカラムは当面残す
- UIだけ消す

### 完了条件
- タグ登録画面に色入力が存在しない

---

## 3.18 `ui/settings/...` 一式

### 目的
- 低頻度管理機能を集約する

### 追加する項目
- 繰り返し予定管理
- 仮登録管理

### UI方針
- 設定項目リストから遷移
- 頻繁に使わないものをここに寄せる

### 完了条件
- 繰り返し管理が通常追加導線から外れる

---

## 3.19 `ui/recurring/...` 一式

### 目的
- 繰り返し予定を設定画面側で登録・編集・確認できるようにする

### 必須対応ルール
1. N日ごと
2. 曜日複数指定
3. 毎月日付複数指定

### 画面構成
- 一覧
- 追加/編集シート
- ルール説明表示

### 入力要素
- タイトル
- 開始日
- 終了条件（任意）
- ルール種別
- 詳細入力
- 通知

### 完了条件
- 3種類の繰り返しが登録できる
- 一覧で登録済み内容を確認できる

---

## 3.20 `ui/quickdraft/QuickDraftListScreen.kt`（新規）

### 目的
- 仮登録一覧を表示する

### 一覧表示項目
- サムネイル
- タイトル
- 作成日時
- OCRテキスト抜粋
- 編集
- 本登録
- 削除

### 完了条件
- 仮登録が一覧で管理できる

---

## 3.21 `ui/quickdraft/QuickDraftEditScreen.kt`（新規）

### 目的
- 仮登録内容を整理して本登録へ進める

### 入力項目
- タイトル
- メモ
- 日付
- 時刻
- 優先度
- タグ
- OCR確認
- 写真確認

### ボタン
- 保存
- 本登録
- 削除

### 完了条件
- 仮登録から本登録まで迷わず進める

---

## 3.22 `ui/quickdraft/QuickDraftViewModel.kt`（新規）

### 目的
- 仮登録作成から本登録までの状態管理を担う

### 責務
- カメラ撮影後の即仮登録
- 自動タイトル生成
- 仮登録取得
- 更新
- 本登録変換

### 自動タイトル生成仕様
形式は以下を推奨:
- `yyyy-MM-dd HH:mm 仮登録`

### 完了条件
- カメラ撮影後に仮登録が自動作成される

---

## 3.23 `AndroidManifest.xml`

### 目的
- カメラ導線が今後も問題なく動くように確認する

### 確認項目
- CAMERA permission
- FileProvider
- 既存OCR関連設定

### 注意点
- すでに設定済みなら重複追記しない

### 完了条件
- カメラ起動で権限・URIまわりの問題がない

---

# 4. 実装順序（厳守推奨）

## Step 1
- AppDatabase
- QuickDraftTask
- QuickDraftTaskDao
- QuickDraftRepository

## Step 2
- schedulelist 一式
- TagFilterRow
- DisplayModeTabs
- TaskListRow
- RemainingDaysBadge

## Step 3
- addtask 一式（右上保存化）

## Step 4
- tag 一式（色削除）

## Step 5
- settings / recurring 一式

## Step 6
- quickdraft 一式

## Step 7
- MainActivity / navigation 接続

## Step 8
- UI磨き込み

---

# 5. ローカルAIへの運用方法

ローカルAIに一度に全体を投げず、以下の順で渡してください。

1. この詳細指示書
2. 「まず AppDatabase と QuickDraftTask 周りだけ実装して」
3. 次に「schedulelist関連だけ実装して」
4. 次に「addtask だけ」
5. 次に「tag だけ」
6. 次に「recurring だけ」
7. 次に「quickdraft だけ」
8. 最後に MainActivity 接続

この分割の方が破綻が少ない。

---

# 6. 完了判定チェックリスト

- [ ] 一覧でタグフィルタが簡単に使える
- [ ] 一覧で繰り返し表示に切り替えられる
- [ ] 一覧で仮登録表示に切り替えられる
- [ ] 予定登録画面の保存が右上にある
- [ ] 下部保存ボタンが残っていない
- [ ] タグ登録画面に色設定がない
- [ ] 繰り返し予定が3方式で登録できる
- [ ] 写真から仮登録が作れる
- [ ] 仮登録を編集して本登録できる
- [ ] DB migrationで既存データが壊れない
- [ ] ダークUIで視認性が高い
- [ ] タスク行の密度と可読性が両立している

