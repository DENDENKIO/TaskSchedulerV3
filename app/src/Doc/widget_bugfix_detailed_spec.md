# Widget高速仮登録 不具合修正仕様書（横長・タグ反応・即カメラ起動対応版）

作成日: 2026-04-17  
対象: TaskSchedulerV3 ローカルコード  
目的: 現在発生している Widget の不具合を修正し、**横長・反応するタグ切替・カメラ即起動・QuickDraft高速保存** を成立させる

---

## 1. 今回修正する不具合

現在のWidget実装には、少なくとも以下の問題がある。

1. Widgetサイズが大きすぎて、ホーム画面上で邪魔になる
2. Widgetのタグ切替をタップしても反応しない
3. Widgetのカメラボタンを押すと MainActivity などアプリ本体が起動してしまう
4. 高速仮登録の目的なのに、撮影までの導線が遠い

本仕様書では、上記をすべて修正する。

---

## 2. 修正後の完成イメージ

### 2.1 Widgetの役割
このWidgetは「アプリを開く入口」ではなく、**高速仮登録専用ランチャー** として扱う。

### 2.2 ユーザーの最短操作
1. ホーム画面でWidgetを見る
2. 必要ならタグ切替を1回以上押す
3. カメラボタンを押す
4. カメラが即起動する
5. 撮影すると自動で仮登録される
6. 仮登録管理画面または仮登録編集画面が開く

### 2.3 Widget見た目
横長1段を基本とし、縦に肥大化しないようにする。

#### 推奨構成
- 左: 現在タグ表示
- 中: タグ切替ボタン
- 右: 大きめのカメラボタン

### 2.4 レイアウトイメージ

```text
┌────────────────────────────────────┐
│ タグ: 会議       [タグ切替]   [📷] │
└────────────────────────────────────┘
```

未選択時:

```text
┌────────────────────────────────────┐
│ タグ: 未選択     [タグ切替]   [📷] │
└────────────────────────────────────┘
```

---

## 3. 横長Widget仕様

### 3.1 基本方針
- 高さを抑える
- 横方向に情報を並べる
- テキスト行数を増やさない
- 説明文や冗長な補助表示は削る

### 3.2 appwidget-provider の修正方針
provider xml は以下の方針で修正する。

- `minHeight` を低くする
- `minWidth` は横長前提にする
- `resizeMode` は `horizontal` もしくは `horizontal|vertical`
- 必要なら `minResizeHeight` を低めにする
- Android 12+ のサイズ別レイアウトがある場合は、横長用を優先する

### 3.3 やってはいけないこと
- 2段3段に情報を詰め込まない
- タグ一覧をWidget上で全部展開しない
- 説明文を常時表示しない

---

## 4. タグ切替不具合の修正仕様

### 4.1 問題の想定原因
以下のいずれか、または複合。

- `setOnClickPendingIntent()` の設定漏れ
- タグ切替ボタンと他ボタンで `PendingIntent` が衝突している
- requestCode が全Widget共通になっている
- 再描画時に click action が消えている
- Widget全体クリックが子ボタンより優先されている

### 4.2 修正方針
タグ切替は必ず **Broadcast action** で処理する。

#### action 名の推奨
- `ACTION_WIDGET_NEXT_TAG`

### 4.3 タップ時の処理
1. AppWidgetProvider が `ACTION_WIDGET_NEXT_TAG` を受信
2. `appWidgetId` を取得
3. 現在の `selectedTagId` を preference から取得
4. 有効タグ一覧を取得
5. 次のタグへ進める
6. 新しい `selectedTagId` を保存
7. Widgetを再描画

### 4.4 順送り切替ルール
タグ候補例:

```text
未選択 → 会議 → 買い物 → アイデア → 仕事 → 未選択
```

### 4.5 タグ候補の取得ルール
- Tagテーブルから取得
- 削除済みや非表示タグがあるなら除外
- 並び順は `sortOrder`, `name` を優先

### 4.6 未選択状態
未選択は有効な状態として必ず含める。

### 4.7 反応しない不具合を防ぐ必須条件
- タグ切替ボタン専用の `PendingIntent` を持つ
- `requestCode` は `appWidgetId` を含める
- `FLAG_UPDATE_CURRENT` を使う
- 再描画時に `setOnClickPendingIntent()` を毎回設定する
- Widget全体クリックでタグボタンを潰さない

---

## 5. カメラボタンで本体が開く不具合の修正仕様

### 5.1 原因
現在、カメラボタンまたはWidget全体に MainActivity 起動用 PendingIntent が割り当てられている可能性が高い。

### 5.2 修正方針
**カメラボタンは MainActivity を開かない。**  
必ず `QuickCaptureActivity` を直接起動する。

### 5.3 絶対ルール
- カメラボタン = `PendingIntent.getActivity(... QuickCaptureActivity ...)`
- MainActivity 起動処理をカメラボタンに使わない
- Widget全体の root click を無効化するか、MainActivity 起動を外す

### 5.4 カメラボタン押下の正しい流れ
1. Widget のカメラボタン押下
2. `QuickCaptureActivity` 起動
3. `appWidgetId` 取得
4. `selectedTagId` 取得
5. 画像保存先ファイルを作成
6. FileProvider URI 生成
7. カメラを即起動

---

## 6. QuickCaptureActivity の詳細仕様

### 6.1 このActivityの役割
Widget とカメラ保存処理の中継点。

### 6.2 このActivityで行うこと
- widget 起動かどうか判定
- `appWidgetId` を受け取る
- widget preference から `selectedTagId` を取得
- 一意な画像ファイル名を生成
- 保存先ファイルと URI を生成
- `TakePicture` または既存 camera 実装を起動
- 成功時に QuickDraft 自動保存
- 失敗時に一時ファイル掃除
- 保存後に QuickDraft 管理画面へ遷移

### 6.3 やってはいけないこと
- QuickCaptureActivity 起動後に MainActivity を経由してからカメラ起動する
- 撮影前に余計な確認画面を出す
- 成功後に一旦ホームへ戻るだけで終える

---

## 7. 画像保存仕様

### 7.1 保存先
既存 photo 保存実装に合わせる。  
なければ app-specific な安全な保存先を使う。

### 7.2 ファイル名
一意であること。

推奨:
```text
IMG_yyyyMMdd_HHmmss_widget.jpg
```

### 7.3 撮影方法
既存実装があるならそれを流用。  
新規なら `ActivityResultContracts.TakePicture()` を優先。

### 7.4 撮影失敗時
- 空ファイル削除
- QuickDraft は作らない
- Activity を閉じる

---

## 8. QuickDraft高速保存仕様

### 8.1 保存タイミング
撮影成功直後。

### 8.2 ユーザー入力
撮影後は入力不要。  
自動保存してよい。

### 8.3 保存内容
- title = `yyyy-MM-dd HH:mm 仮登録`
- description = null で可
- photoPath = 撮影画像パス
- tagId = Widget選択タグ or null
- status = `DRAFT`
- ocrText = null で可、あとで更新可

### 8.4 OCRの扱い
高速性を優先するため、OCRは保存後に非同期でよい。

### 8.5 保存失敗時
- 可能な範囲で画像ファイル掃除
- 保存失敗を通知
- 中途半端な draft を残しにくくする

---

## 9. 保存後の画面遷移仕様

### 9.1 推奨遷移先
第一候補: `QuickDraftListScreen`

理由:
- 保存されたことが確認しやすい
- 連続撮影後の整理に向く

### 9.2 代替
作成した draft の編集画面へ直接遷移してもよい。  
ただし既存構成に自然な場合のみ。

### 9.3 遷移時の注意
- 新しいTask追加画面ではない
- MainActivityトップに戻るだけではない
- 必ず仮登録の確認ができる場所へ行く

---

## 10. Widgetごとの状態保持

### 10.1 必要な理由
複数Widgetを置いたときにタグが混ざらないようにするため。

### 10.2 保存単位
- `appWidgetId` ごと

### 10.3 保存する値
- `selectedTagId`
- 必要なら `selectedTagLabel`

### 10.4 Widget削除時
- 該当 `appWidgetId` の設定を cleanup する

---

## 11. PendingIntent 設計仕様

### 11.1 ボタンごとに分離する
- タグ切替ボタン
- カメラボタン

### 11.2 requestCode 設計
`appWidgetId` を含めて衝突を防ぐ。

例:
- nextTag = `appWidgetId * 10 + 1`
- openCamera = `appWidgetId * 10 + 2`

### 11.3 flag
- `FLAG_UPDATE_CURRENT`
- `FLAG_IMMUTABLE` または既存方針準拠

### 11.4 注意
- 似たIntentを同一と扱われないようにする
- 必要なら action / data / requestCode を変える

---

## 12. Widget全体クリック仕様

### 12.1 基本方針
今回のWidgetでは、**Widget全体クリックでアプリ本体を開かない**。

### 12.2 理由
- 子ボタンとの競合を避けるため
- 高速仮登録の主役はカメラボタンだから
- 誤ってMainActivityが開く不具合を防ぐため

### 12.3 仕様
- root layout に MainActivity 起動 PendingIntent を設定しない
- 必要なら root click 自体をなくす

---

## 13. UIの見た目指示

### 13.1 色
- 背景: `#16161A`
- 補助面: `#1C1C21`
- アクセント: `#7C6AFF`
- 文字: `#E8E8F0`
- 補助文字: `#7A7A8C`

### 13.2 タグ表示
- 文言: `タグ: 未選択` または `タグ: 会議`
- 省略しすぎず、横長内に収める

### 13.3 カメラボタン
- Widget内で一番大きいタップ領域
- 一目で撮影ボタンとわかる
- 右端固定を推奨

---

## 14. ローカルAIへの厳密な修正指示

以下を満たすこと。

1. Widget を横長1段前提へ修正する
2. `appwidget-provider` のサイズ指定を小さくする
3. タグ切替を Broadcast action に変更または修正する
4. タグ切替に反応しない原因を取り除く
5. カメラボタンから MainActivity 起動を排除する
6. カメラボタンは QuickCaptureActivity を直接起動する
7. 撮影成功後は QuickDraft を自動保存する
8. 保存後は QuickDraft 管理画面または編集画面へ遷移する
9. Widget全体クリックが子ボタンを邪魔しないようにする
10. 複数Widget時にタグ状態が競合しないようにする

---

## 15. 受け入れ条件

- Widgetが横長で邪魔にならない
- タグ切替を押すと確実に表示が変わる
- カメラボタンを押してもMainActivityは開かない
- カメラボタンを押すと即カメラが起動する
- 撮影成功後にQuickDraftが自動保存される
- 保存後に仮登録管理画面または編集画面へ遷移する
- 複数Widget時でも各Widgetのタグが独立している
- 既存 camera / OCR / PhotoMemo 実装を壊していない
