# Widget高速仮登録 仕様書（詳細版・書き直し）

作成日: 2026-04-17  
対象: TaskSchedulerV3 ローカルコードベース  
目的: ホーム画面ウィジェットから、**タグ選択 → カメラ即起動 → 撮影 → 自動保存 → 仮登録管理画面へ遷移** を最短手数で実現する

---

## 1. この機能のゴール

この機能の目的は、思いついた内容・紙メモ・レシート・ホワイトボード・掲示物などを、アプリを開かずに最短で仮登録することです。

### 最終的にユーザーが行う操作
1. ホーム画面のウィジェットを見る
2. 必要ならタグを切り替える
3. カメラボタンを押す
4. 写真を撮る
5. 何も追加入力せずに仮登録が自動作成される
6. そのまま仮登録管理画面または対象ドラフト編集画面が開く

### 目標
- アプリ本体を先に開かなくてよい
- タップ回数を最小にする
- タグ付けを撮影前に済ませる
- 撮影直後の入力は必須にしない
- 保存失敗や画像紛失が起きない構造にする

---

## 2. ユーザーフロー詳細

### 2.1 基本フロー

```text
ホーム画面
  ↓
Widget上で現在タグ確認
  ↓
(必要ならタグ切替)
  ↓
カメラボタン押下
  ↓
QuickCaptureActivity 起動
  ↓
保存先画像URI生成
  ↓
ACTION_IMAGE_CAPTURE 起動
  ↓
ユーザー撮影
  ↓
撮影成功
  ↓
QuickDraftTask 自動生成
  ↓
選択タグを draft に付与
  ↓
必要なら OCR 非同期開始
  ↓
仮登録管理画面 または 仮登録編集画面へ遷移
```

### 2.2 タグ未選択時のフロー

```text
Widgetタグ = 未選択
  ↓
カメラボタン押下
  ↓
撮影
  ↓
tagId = null で QuickDraft 保存
  ↓
仮登録管理へ遷移
```

### 2.3 タグ選択済み時のフロー

```text
Widgetタグ = 会議メモ
  ↓
カメラボタン押下
  ↓
撮影
  ↓
tagId = 会議メモのID を付けて QuickDraft 保存
  ↓
仮登録管理へ遷移
```

---

## 3. Widgetで実現したい体験

### 3.1 Widget上で見える内容

最小構成は以下。

- 現在選択中タグ名
- タグ切替ボタン
- カメラボタン
- 補助テキスト（任意）

### 3.2 推奨UI構成

#### 1行目
- 左: 現在タグラベル `タグ: 未選択` または `タグ: 会議`
- 右: タグ切替ボタン

#### 2行目
- 中央または大きめにカメラボタン

#### 3行目（任意）
- `撮影すると仮登録に保存` のような補助文

### 3.3 操作ルール
- タグ切替は1タップで次のタグへ進む
- 最後のタグの次は「未選択」に戻る
- カメラボタンは常に表示
- タグ未選択でも撮影可能

---

## 4. タグ選択の仕様をかなり詳細に定義

### 4.1 なぜ順送り切替方式にするか

Widgetは通常のCompose画面のように自由なドロップダウンや複雑UIが弱い。  
そのため、最速性と安定性を優先して、**タグ切替ボタンを押すたびに候補タグを順送りで切り替える**方式を採用する。

### 4.2 順送りのルール

タグ一覧が以下だとする。

```text
[未選択, 会議, 買い物, アイデア, 仕事, 重要]
```

タグ切替ボタン押下ごとの遷移は以下。

```text
未選択 → 会議 → 買い物 → アイデア → 仕事 → 重要 → 未選択 → ...
```

### 4.3 タグ候補の取得元
- DBの Tag テーブルを読む
- 非表示タグや削除済みタグがあるなら除外
- 表示順は以下優先
  1. sortOrder
  2. name

### 4.4 widgetごとの状態保持
各Widgetインスタンスごとに、選択タグ状態を保持する。

#### 保存する理由
- ユーザーが複数Widgetを置く可能性がある
- Widget A は「仕事」、Widget B は「買い物」にしたい可能性がある

#### 保存単位
- `appWidgetId` 単位

#### 保存値
- `selectedTagId: Int?`
- `selectedTagName: String`（表示高速化のため任意）

### 4.5 保存先
推奨は `SharedPreferences`。

例:
- `widget_quick_capture_prefs`
- key: `selected_tag_id_<appWidgetId>`

---

## 5. カメラ起動の仕様をかなり詳細に定義

### 5.1 直接起動ではなく中継Activityを使う理由

Widgetから直接DB保存や複雑な処理をすると失敗しやすい。  
そのため、Widgetのカメラボタン押下では、**必ず QuickCaptureActivity を起動**し、そこで以下を行う。

1. appWidgetId 取得
2. selectedTagId 取得
3. 画像保存先URI生成
4. Camera Intent 起動
5. 結果受け取り
6. QuickDraft 保存
7. 管理画面へ遷移

### 5.2 QuickCaptureActivity の責務
- widget intent を受ける
- `appWidgetId` を読む
- widget preference から `selectedTagId` を取得
- 一意な画像ファイル名を生成
- `FileProvider` 用の保存URIを生成
- `TakePicture` または `ACTION_IMAGE_CAPTURE + EXTRA_OUTPUT` で撮影
- 成功なら QuickDraft を保存
- 失敗なら不要ファイルを掃除
- 保存後に仮登録管理画面へ遷移

### 5.3 撮影前の一時状態
Activity内に以下を保持する。
- pendingPhotoUri
- pendingPhotoPath
- launchSource = WIDGET
- launchWidgetId
- selectedTagId

### 5.4 推奨撮影方法
- `ActivityResultContracts.TakePicture()` を優先
- 既存コードが `ACTION_IMAGE_CAPTURE` を使っているなら、それに合わせてもよい

### 5.5 EXTRA_OUTPUT を使う理由
カメラアプリからサムネイルだけ返されるのを防ぎ、**元画像ファイルを確実に保存**するため。  
保存先URIを先に確保して撮影すること。

---

## 6. 画像保存仕様

### 6.1 保存先
既存実装に合わせるが、推奨は以下のいずれか。
- app-specific external files dir
- internal files dir
- 既存 photo 保存ディレクトリ

### 6.2 ファイル名
一意性を確保する。

推奨:
```text
IMG_yyyyMMdd_HHmmss_widget.jpg
```

### 6.3 FileProvider
- 既存の FileProvider 設定があれば再利用
- ない場合は provider と xml を追加

### 6.4 撮影失敗時
- 空ファイルや不要ファイルを削除
- QuickDraft は作成しない
- 必要ならトーストまたは即終了

---

## 7. QuickDraft自動保存の仕様をかなり詳細に定義

### 7.1 保存タイミング
- 撮影成功直後
- ユーザーの追加入力なしで保存する

### 7.2 保存対象
QuickDraftTask へ保存する。

### 7.3 保存項目
- title
- description
- photoPath
- ocrText
- tagId
- createdAt
- updatedAt
- status
- captureSource（もし追加可能なら）

### 7.4 title 自動生成ルール
フォーマットは固定する。

推奨:
```text
yyyy-MM-dd HH:mm 仮登録
```

例:
```text
2026-04-17 10:21 仮登録
```

### 7.5 tagId 付与ルール
- widget で未選択なら `null`
- 選択済みならその tagId
- 保存前に tagId が現存するか確認してもよい
- 消えていたら null にフォールバック

### 7.6 description 初期値
- 基本は null
- OCR結果が即取れるなら要約なしで全文を入れてもよい
- ただし高速性優先なら後回し

### 7.7 OCR 実行方針
高速仮登録の主目的は「取り逃がさないこと」なので、OCRは以下のどちらか。

#### 推奨
- 保存後に非同期でOCRを回す
- draft作成は先に完了させる

#### 代替
- 既存OCR実装が高速かつ安定なら保存前に実施してもよい

### 7.8 status
- 初期値は `DRAFT`

---

## 8. 保存後の画面遷移

### 8.1 基本方針
撮影成功後は、ユーザーが「ちゃんと保存された」ことをすぐ確認できるようにする。

### 8.2 遷移先候補

#### 推奨1
- 仮登録管理画面（一覧）

利点:
- 複数の仮登録を続けて処理しやすい
- 全体を把握しやすい

#### 推奨2
- 作成した draft の編集画面

利点:
- すぐ内容を補足できる

### 8.3 実装方針
初期実装は以下を推奨。
- 保存後は **QuickDraftListScreen** へ遷移
- もし1件ずつすぐ編集したい運用なら、作成IDを渡して Edit へ遷移

### 8.4 Intent仕様
- `FLAG_ACTIVITY_NEW_TASK`
- 必要なら `CLEAR_TOP` や `SINGLE_TOP`
- 遷移先が一覧か編集かを extra で制御してもよい

---

## 9. 複数Widgetが置かれた場合の仕様

### 9.1 状態競合を避ける
Widget A と Widget B は別々の selectedTagId を持つこと。

### 9.2 例
- Widget A: 仕事
- Widget B: 買い物

それぞれカメラボタンを押したときに、対応するタグで保存されること。

### 9.3 実装要件
- `appWidgetId` ごとに selectedTagId を保存
- onUpdate 時に各Widgetごとに RemoteViews を更新
- タグ切替 action は appWidgetId を必ず持つ
- カメラ起動 action も appWidgetId を必ず持つ

---

## 10. 推奨クラス構成

### 10.1 AppWidgetProvider
例: `QuickCaptureWidgetProvider`

責務:
- widget の描画更新
- タグ切替 action の受信
- カメラ起動 action の `PendingIntent` 設定
- Widget削除時の preference cleanup

### 10.2 WidgetPreferenceStore
例: `QuickCaptureWidgetPrefs`

責務:
- selectedTagId の保存
- selectedTagId の読込
- Widget削除時 cleanup

### 10.3 QuickCaptureActivity
責務:
- widget起点の撮影処理一式
- URI生成
- カメラ起動
- QuickDraft 保存
- 画面遷移

### 10.4 QuickDraftRepository
責務:
- draft 作成
- OCR後更新
- tagId 付き保存

---

## 11. PendingIntent / action 設計

### 11.1 必要な action 例
- `ACTION_WIDGET_NEXT_TAG`
- `ACTION_WIDGET_OPEN_CAMERA`

### 11.2 付与する extra
- `EXTRA_APPWIDGET_ID`
- 必要なら `EXTRA_LAUNCH_SOURCE = "widget"`

### 11.3 注意点
- requestCode を appWidgetId ベースで分ける
- `FLAG_UPDATE_CURRENT` を使い、最新 extra を反映させる
- widget 再描画時に毎回 PendingIntent を再設定する

---

## 12. 詳細な処理手順（内部処理）

### 12.1 タグ切替ボタン押下
1. AppWidgetProvider が action を受ける
2. appWidgetId を取得
3. DBから利用可能タグ一覧を取得、またはキャッシュ/軽量取得
4. 現在の selectedTagId を読む
5. 次のタグへ進める
6. preference に保存
7. widget を再描画

### 12.2 カメラボタン押下
1. AppWidgetProvider がカメラ起動用 PendingIntent を発火
2. QuickCaptureActivity が起動
3. intent から appWidgetId を取得
4. preference から selectedTagId を取得
5. 保存先ファイル作成
6. FileProvider URI 生成
7. camera launcher 実行

### 12.3 撮影成功
1. URI / path を確定
2. QuickDraftTask を自動生成
3. title を日時で生成
4. tagId を付与
5. photoPath 保存
6. status = DRAFT
7. DB保存
8. OCR非同期処理を必要に応じて開始
9. QuickDraft 管理画面へ遷移
10. QuickCaptureActivity を閉じる

### 12.4 撮影失敗 or キャンセル
1. 一時ファイル削除
2. draft は作らない
3. Activity を終了
4. 必要ならユーザーに軽い通知

---

## 13. 失敗系の仕様

### 13.1 タグが削除されていた
- selectedTagId が無効なら null にする
- widget 表示は未選択へ戻す

### 13.2 カメラ権限または端末事情で起動失敗
- QuickCaptureActivity 内で安全に終了
- 必要ならエラーメッセージを表示

### 13.3 保存先生成失敗
- QuickDraft は作らない
- Activity を終了

### 13.4 DB保存失敗
- 画像だけ残る孤児ファイルを極力掃除
- 保存失敗通知

---

## 14. UI色・見た目指示

### 14.1 Widgetカラー
- 背景: `#16161A`
- 補助面: `#1C1C21`
- アクセント: `#7C6AFF`
- 文字: `#E8E8F0`
- 補助文字: `#7A7A8C`

### 14.2 現在タグ表示
- `タグ: 未選択`
- `タグ: 会議`
- 背景は控えめな surface

### 14.3 カメラボタン
- Accent を主色にする
- 1タップで押せる十分なサイズ
- Widget上で一番目立つ要素にする

---

## 15. ローカルAIへの厳密な実装指示

### 指示の要点
- widget から直接 DB 保存や複雑処理をしない
- 必ず QuickCaptureActivity を中継する
- widget ごとに selectedTagId を保存する
- タグ切替は順送り方式にする
- カメラ成功後に QuickDraft を自動保存する
- 保存後は仮登録管理画面へ遷移する
- OCRは高速性優先で非同期可
- 既存 camera / FileProvider / PhotoMemo / OCR 実装を流用する

---

## 16. 完了条件

- widget からタグ切替できる
- 未選択状態でも撮影できる
- カメラボタンで即起動する
- 撮影成功で QuickDraft が自動保存される
- 保存時に tagId が反映される
- 保存後に仮登録管理画面または編集画面へ遷移する
- 複数widgetでタグ状態が競合しない
- 既存 camera / OCR / PhotoMemo 実装を壊していない
- 失敗時に不要ファイルが残りにくい
