# TaskSchedulerV3 実装ロードマップ

本ロードマップは、`taskscheduler_spec.md` の仕様を、ローカル AI が段階的に安全実装できるように分解した作業計画書である。
本書は「どのファイルを」「なぜ」「どう変えるか」を、GitHub を見られないローカル AI 向けに明確化することを目的とする。[page:5][page:6][page:7][page:8][page:9][page:10][page:11][page:12]

本ロードマップでは、ユーザーが指定した 13 項目以外の変更を禁止する。

---

## 1. 実装方針

実装は以下の順で進める。

1. [x] データ定義追加
2. [x] DB マイグレーション追加
3. [x] DAO / Repository 拡張
4. [x] 一覧表示用モデル拡張
5. [x] 詳細画面の関連予定表示
6. [x] ロードマップ CRUD 実装
7. [x] ロードマップ完了・進捗反映実装
8. [x] 一覧画面の表示差し替え実装
9. [x] スワイプアクション実装
10. [x] フィルター削除
11. [x] 絵文字表示追加
12. [x] 通知連携
13. [x] 総合テスト

この順番にする理由は、UI より先にデータ層を完成させた方が後戻りが少ないためである。[page:9][page:10][page:11][page:12]

---

## 2. 実装前ルール

ローカル AI は以下を厳守すること。

- 仕様書にない画面デザイン変更をしない
- 仕様書にないフィルターを追加しない
- 仕様書にない色変更をしない
- 仕様書にないリファクタをしない
- ファイル名変更や package 移動は、必要不可欠な場合を除いて行わない
- コンパイルが通る状態を段階ごとに維持する
- 1 段階ごとにビルド確認する

---

## 3. 現在の主要ファイル認識

ローカル AI はまず、以下のファイルを基準として変更対象を認識すること。[page:8][page:9][page:10][page:11][page:12]

### 3.1 データ層

- `app/src/main/java/com/example/taskschedulerv3/data/model/Task.kt`
- `app/src/main/java/com/example/taskschedulerv3/data/model/TaskRelation.kt`
- `app/src/main/java/com/example/taskschedulerv3/data/model/ScheduleType.kt`
- `app/src/main/java/com/example/taskschedulerv3/data/model/FilterOption.kt`
- `app/src/main/java/com/example/taskschedulerv3/data/db/AppDatabase.kt`
- `app/src/main/java/com/example/taskschedulerv3/data/db/TaskDao.kt`

### 3.2 UI / ViewModel 層

- `app/src/main/java/com/example/taskschedulerv3/ui/schedulelist/ScheduleListViewModel.kt`
- `app/src/main/java/com/example/taskschedulerv3/ui/schedulelist/ScheduleListScreenHighDensity.kt`
- `app/src/main/java/com/example/taskschedulerv3/ui/components/TaskRowItem.kt`
- `app/src/main/java/com/example/taskschedulerv3/ui/detail/...` 配下の詳細画面系ファイル

### 3.3 その他

- 通知関連クラス
- ナビゲーション関連クラス
- 予定作成／編集画面クラス

※ 詳細画面ファイル名が `DetailScreen.kt` または別名の可能性があるため、ローカルコード上で実ファイル名を確認してから作業すること。[page:12]

---

## 4. フェーズ1: データモデル拡張

## 4.1 目的

親子関係とロードマップを扱える最小データ構造を追加する。

## 4.2 変更対象ファイル

- `Task.kt`
- 新規 `RoadmapStep.kt`
- 必要に応じて `ScheduleType.kt`

## 4.3 作業内容

### 4.3.1 Task.kt の変更

Task Entity に以下を追加する。

- `parentTaskId: Long? = null`
- `roadmapEnabled: Boolean = false`
- `activeRoadmapStepId: Long? = null`

必要であれば、既存のフィールド名やデフォルト値に合わせて nullable / non-null を調整する。

### 4.3.2 RoadmapStep.kt 新規作成

新規 Entity を作成する。

必須項目:
- `id`
- `taskId`
- `title`
- `date`
- `sortOrder`
- `isCompleted`
- `completedAt`
- `notificationEnabled`
- `notificationRequestCode`

### 4.3.3 ScheduleType の確認

既存 schedule type が通常／無期限／繰返しなどを表している場合、ロードマップ表示判定にそのまま流用できるか確認する。[page:7]
使えない場合のみ、ロードマップ用判定値を追加する。

## 4.4 完了条件

- Entity の定義がコンパイル通過
- 既存コード参照エラーが発生していない

---

## 5. フェーズ2: DB スキーマ変更と Migration

## 5.1 目的

既存データを壊さずに新項目を追加する。

## 5.2 変更対象ファイル

- `AppDatabase.kt`
- Migration 定義ファイルまたは AppDatabase 内 migration 記述部

## 5.3 作業内容

### 5.3.1 DB version 更新

Room の version を 1 つ上げる。[page:9]

### 5.3.2 Task テーブル拡張

Migration で以下を追加する。

- `parentTaskId`
- `roadmapEnabled`
- `activeRoadmapStepId`

初期値:
- `parentTaskId = null`
- `roadmapEnabled = 0`
- `activeRoadmapStepId = null`

### 5.3.3 RoadmapStep テーブル作成

新規テーブル作成 SQL を追加する。

### 5.3.4 既存 TaskRelation の扱い

`TaskRelation` が既存で未使用または双方向関連前提なら、今回の親子機能に必須でない限り触らない。[page:8]
仕様書では `parentTaskId` 採用のため、`TaskRelation` は原則そのままにする。

## 5.4 完了条件

- Migration 実行後にクラッシュしない
- 既存データが読める
- 新規テーブルが作成される

---

## 6. フェーズ3: DAO 拡張

## 6.1 目的

親子関係とロードマップ操作に必要な DB アクセスを追加する。

## 6.2 変更対象ファイル

- `TaskDao.kt`
- 必要なら `RoadmapStepDao.kt` 新規作成

## 6.3 作業内容

### 6.3.1 親子関係用クエリ

追加候補:
- `getTaskById(taskId)`
- `getChildrenTasks(parentTaskId)`
- `countChildren(parentTaskId)`
- `updateParentTaskId(taskId, parentTaskId)`
- `isDescendant(candidateChildId, candidateParentId)` 用の補助取得

### 6.3.2 ロードマップ用クエリ

追加候補:
- `getRoadmapSteps(taskId)`
- `getRoadmapStepById(stepId)`
- `insertRoadmapStep(step)`
- `updateRoadmapStep(step)`
- `deleteRoadmapStep(step)`
- `updateRoadmapSteps(steps)`
- `getNextIncompleteRoadmapStep(taskId)`
- `countCompletedRoadmapSteps(taskId)`
- `countAllRoadmapStages(taskId)`

### 6.3.3 トランザクション処理

以下は `@Transaction` でまとめる。

- ロードマップ段階完了 → activeRoadmapStepId 更新
- GOAL 完了 → Task 完了
- 並び替え → sortOrder 一括更新

## 6.4 完了条件

- DAO の全クエリがコンパイル通過
- SQL / Room 注釈エラーなし

---

## 7. フェーズ4: Repository / UseCase レベル拡張

## 7.1 目的

ViewModel が複雑化しすぎないよう、表示用変換と完了ロジックをまとめる。

## 7.2 変更対象候補

- 既存 Repository クラス
- Repository が無い場合は `ScheduleListViewModel.kt` 内のデータ変換部

## 7.3 作業内容

### 7.3.1 一覧表示用 DTO を作る

新規 `TaskListItemUiModel` などを作成する。

項目例:
- `taskId`
- `displayDate`
- `displayTitle`
- `displayProgressPercent`
- `emoji`
- `relatedCount`
- `isRoadmapTask`
- `activeStageLabel`
- `activeStageColorKey`

### 7.3.2 表示タイトル生成処理

ロードマップ進行中なら:
- `【ステップ名】元タイトル`

通常予定なら:
- `元タイトル`

### 7.3.3 表示日付生成処理

ロードマップ進行中なら:
- active stage の日付

通常予定なら:
- task 本体の日付

### 7.3.4 進捗率計算処理

- ロードマップなし → 既存進捗計算
- ロードマップあり → `(完了済み段階数 / 全段階数) * 100`

### 7.3.5 絵文字判定処理

優先順位:
1. ロードマップ → `🛣️`
2. 繰返し → `🔁`
3. 無期限 → `📝`
4. 通常 → `📅`

## 7.4 完了条件

- 一覧画面が UI Model を使って表示できる
- ViewModel 内条件分岐が極端に肥大化していない

---

## 8. フェーズ5: 親子関係UI実装

## 8.1 目的

新規作成・詳細画面・スワイプ経由で親子設定できるようにする。

## 8.2 変更対象ファイル

- 予定作成画面
- 予定編集画面
- 詳細画面
- `ScheduleListScreenHighDensity.kt`
- `TaskRowItem.kt`

## 8.3 作業内容

### 8.3.1 新規作成画面

- 親予定選択 UI を追加
- 親未選択なら通常保存
- 親選択時は `parentTaskId` 保存

### 8.3.2 詳細画面

- 「関連予定」セクション追加
- 親予定表示
- 子予定一覧表示
- 各項目タップで詳細遷移
- 「関連付け編集」ボタン追加

### 8.3.3 一覧画面表示

- `関連n` を小さく表示
- 親子がない場合は非表示

### 8.3.4 バリデーション

- 自己参照禁止
- 循環禁止

## 8.4 完了条件

- 新規・後付け両方で親子設定可能
- 詳細で親子確認可能
- 一覧に件数表示される

---

## 9. フェーズ6: ロードマップ CRUD UI 実装

## 9.1 目的

詳細画面からロードマップを編集・確認できるようにする。

## 9.2 変更対象ファイル

- 詳細画面
- 新規 `RoadmapEditScreen.kt` または既存詳細配下ダイアログ/画面
- ナビゲーション定義

## 9.3 作業内容

### 9.3.1 詳細画面への入口追加

- 「ロードマップ編集」ボタン追加
- ロードマップ未登録時も開ける

### 9.3.2 編集画面機能

- ステップ一覧表示
- 追加
- 編集
- 削除
- 完了切替
- ドラッグ＆ドロップ並び替え

### 9.3.3 START / GOAL 表示

- Task 本体を START としてタイムライン先頭に表示
- 最後尾ステップを GOAL 表示

### 9.3.4 縦タイムラインUI

- `LazyColumn` 等で構成
- 左側に縦線
- 各ステップにノード表示
- 完了状態でノード色変化

## 9.4 完了条件

- ロードマップの登録・編集・削除・並び替えができる
- 詳細画面に縦タイムラインが表示される

---

## 10. フェーズ7: ロードマップ完了ロジック実装

## 10.1 目的

完了ボタンや右スワイプで、単純完了ではなく段階進行が起きるようにする。

## 10.2 変更対象ファイル

- `ScheduleListViewModel.kt`
- 詳細画面 ViewModel
- Repository / DAO transaction

## 10.3 作業内容

### 10.3.1 開始段階完了

- Task 本体を開始段階完了として扱う
- `activeRoadmapStepId` を次ステップへ更新

### 10.3.2 中間段階完了

- 現在アクティブ step を完了
- 次の未完了 step を active にする

### 10.3.3 最終段階完了

- Task 全体を complete にする
- activeRoadmapStepId を null にする

### 10.3.4 進捗率更新

- 完了ごとに再計算
- 一覧と詳細の両方へ反映

## 10.4 完了条件

- 右スワイプまたは完了操作で段階進行する
- GOAL 完了で task 完了になる

---

## 11. フェーズ8: 一覧差し替え表示実装

## 11.1 目的

ロードマップ中は「今やるべき次の段階」だけを一覧に出す。

## 11.2 変更対象ファイル

- `ScheduleListViewModel.kt`
- `TaskRowItem.kt`
- `ScheduleListScreenHighDensity.kt`

## 11.3 作業内容

### 11.3.1 表示タイトル差し替え

例:
- 初期: `高知旅行募集案内配布`
- 次段階: `【参加者確認】高知旅行募集案内配布`
- 次段階: `【締切】高知旅行募集案内配布`

### 11.3.2 表示日付差し替え

- active stage の date を使用

### 11.3.3 接頭辞部分の色付け

`AnnotatedString` などを使って、`【参加者確認】` 部分だけ色を変える。

### 11.3.4 絵文字表示

ロードマップ中は `🛣️` を先頭表示する。

## 11.4 完了条件

- 一覧で現在段階に応じた日付・タイトルへ切り替わる
- 接頭辞だけ色付けできる

---

## 12. フェーズ9: スワイプアクション実装

## 12.1 目的

一覧から素早く関連付け・完了操作を実行できるようにする。

## 12.2 変更対象ファイル

- `TaskRowItem.kt`
- `ScheduleListScreenHighDensity.kt`

## 12.3 作業内容

### 12.3.1 左スワイプ

- 関連付けアクション表示
- スワイプ確定で親子設定ダイアログまたは画面起動

### 12.3.2 右スワイプ

- 完了アクション表示
- 通常予定は既存完了
- ロードマップ予定は段階完了

### 12.3.3 UI 上の注意

- 誤操作防止のため、閾値を適切に設定
- 既存タップ遷移と競合しないよう調整

## 12.4 完了条件

- 左右スワイプが意図通り動作する
- タップ詳細遷移が壊れていない

---

## 13. フェーズ10: フィルター修正

## 13.1 目的

ユーザー指定に従い、優先度フィルターを削除する。

## 13.2 変更対象ファイル

- `FilterOption.kt`
- 一覧画面フィルタUI関連
- ViewModel のフィルタ処理

## 13.3 作業内容

- `高 / 中 / 低` フィルター定義削除
- 該当 UI チップ削除
- 関連ロジック削除
- 他フィルターへの影響確認

## 13.4 完了条件

- 優先度フィルターが UI から消える
- 他フィルターはそのまま動作する

---

## 14. フェーズ11: 絵文字反映

## 14.1 目的

予定種別をひと目で区別しやすくする。

## 14.2 変更対象ファイル

- `TaskRowItem.kt`
- 詳細画面タイトル表示部
- 必要なら一覧用 UI Model

## 14.3 作業内容

### 14.3.1 採用絵文字

- 通常予定: `📅`
- 無期限予定: `📝`
- 繰返し予定: `🔁`
- ロードマップ予定: `🛣️`

### 14.3.2 表示優先順位

- ロードマップ > 繰返し > 無期限 > 通常

### 14.3.3 補助表示

親子関係の `🌱` `↪️` は一覧主表示には使わず、必要なら詳細補助ラベルでのみ利用する。

## 14.4 完了条件

- 一覧で絵文字表示される
- 種別に応じて正しく切り替わる

---

## 15. フェーズ12: 通知連携

## 15.1 目的

ロードマップ各段階の通知を有効化する。

## 15.2 変更対象ファイル

- 通知スケジューラ
- BroadcastReceiver
- 詳細画面 / 編集画面
- RoadmapStep 保存処理

## 15.3 作業内容

### 15.3.1 通知登録

- RoadmapStep 保存時、通知 ON なら通知登録
- request code を保持

### 15.3.2 通知文言

例:
- `【参加者確認】高知旅行募集案内配布 の予定日です`

### 15.3.3 通知更新

- 日付変更時に再登録
- 削除時キャンセル
- 完了時不要ならキャンセル

### 15.3.4 active step 切替時確認

- 次段階が有効通知なら再評価

## 15.4 完了条件

- 各ロードマップ段階で通知が飛ぶ
- 削除・変更で通知が適切更新される

---

## 16. フェーズ13: 総合テスト

## 16.1 テスト観点

### 16.1.1 親子関係

- 親なし通常予定作成
- 親付き子予定作成
- 後から親変更
- 循環設定防止
- 詳細画面遷移

### 16.1.2 ロードマップ

- ステップ追加
- 並び替え
- 完了進行
- 一覧差し替え
- GOAL 完了
- 進捗率計算

### 16.1.3 通知

- 通知登録
- 編集で更新
- 削除でキャンセル

### 16.1.4 一覧UI

- 左スワイプ関連付け
- 右スワイプ完了
- 絵文字表示
- 関連件数表示
- 優先度フィルター削除確認

## 16.2 リリース前確認

- アプリ起動クラッシュなし
- DB migration 成功
- 一覧表示崩れなし
- 詳細画面崩れなし
- 既存機能への副作用が最小

---

## 17. ローカルAI向け実装順の最終指示

ローカル AI は次の単位でコミットまたは作業保存すること。

1. Entity / DB 変更
2. DAO / Repository 変更
3. 親子関係 UI
4. ロードマップ編集 UI
5. ロードマップ進行ロジック
6. 一覧差し替え表示
7. スワイプ
8. フィルター削除
9. 絵文字
10. 通知
11. テスト修正

各段階で必ず以下を実施すること。

- ビルド
- 影響ファイル確認
- 不要変更が混ざっていないか確認
- 仕様書外の編集がないか確認

