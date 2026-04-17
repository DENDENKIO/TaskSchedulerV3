# ローカルAI用プロンプト（Widget不具合修正・横長高速仮登録版）

既存のローカルコードを直接読んで、現在のWidget実装の不具合を修正してください。  
**重要: 既存機能を壊さず、差分で修正してください。**  
**推測で全面再構成しないでください。**

## まず最初に確認すること（コードはまだ出さない）
以下を確認し、「現状確認レポート」として出力してください。

1. 既存 AppWidgetProvider の有無とファイルパス
2. widget レイアウト xml の有無とファイルパス
3. appwidget-provider xml の有無とサイズ設定
4. Widget全体クリックで MainActivity を開く処理の有無
5. タグ切替ボタンの click action 実装有無
6. カメラボタンの click action 実装有無
7. `PendingIntent` の requestCode / action / flags の実装内容
8. `appWidgetId` ごとの状態保持の有無
9. QuickCaptureActivity の有無
10. QuickDraft 保存処理の既存利用先
11. camera / FileProvider / OCR 実装箇所
12. QuickDraft 管理画面または近い遷移先

出力はレポートのみ。まだコードは出さないでください。

---

## その後に行う修正

目的:
- Widgetを横長にする
- タグ切替ボタンを確実に反応させる
- カメラボタンで MainActivity を開かず、即カメラ起動する
- 撮影後に QuickDraft を自動保存し、仮登録管理画面へ遷移する

### 修正要件1: Widgetの横長化
- appwidget-provider xml を確認
- `minHeight` を下げる
- `minWidth` を横長前提に調整する
- `resizeMode` を `horizontal` または `horizontal|vertical` にする
- Widgetレイアウトは横並び構成にする
- 左: 現在タグ表示
- 中: タグ切替
- 右: カメラボタン
- 説明文など不要な縦要素は削減する

### 修正要件2: タグ切替を確実に動かす
- タグ切替は Broadcast action で処理する
- action 名は既存に合わせるが、なければ `ACTION_WIDGET_NEXT_TAG` を使う
- `appWidgetId` を必ず extra で渡す
- タグ候補は Tag テーブルから取得する
- 並び順は sortOrder, name を優先
- 順送りルールは `未選択 -> タグ1 -> タグ2 -> ... -> 最後 -> 未選択`
- `selectedTagId` は `appWidgetId` ごとに保存する
- SharedPreferences または既存設定基盤を使う
- 再描画時に `setOnClickPendingIntent()` を必ず再設定する

### 修正要件3: カメラボタンで本体を開かない
- カメラボタンに MainActivity 用 PendingIntent を使わない
- Widget root に MainActivity click があるなら削除または無効化する
- カメラボタンは `QuickCaptureActivity` を `PendingIntent.getActivity(...)` で直接起動する
- `requestCode` は `appWidgetId` を含めてユニークにする
- `FLAG_UPDATE_CURRENT` を使う

### 修正要件4: カメラ即起動 → QuickDraft自動保存
- QuickCaptureActivity を確認し、なければ追加
- Activity起動後は余計な画面を出さず即カメラ起動する
- 保存先画像ファイルを一意名で作る
- FileProvider URI を生成する
- 撮影成功時のみ QuickDraft を自動保存する
- title は `yyyy-MM-dd HH:mm 仮登録`
- tagId は widget 選択タグ、未選択なら null
- status = DRAFT
- OCRは保存後の非同期でもよい
- 失敗またはキャンセル時は一時ファイルを掃除する

### 修正要件5: 保存後の遷移
- 保存後は MainActivity トップへ戻るだけにしない
- QuickDraft 管理画面または編集画面へ遷移する
- 既存の導線に自然な方を選ぶ

### 修正要件6: 複数Widget対応
- Widget A と Widget B の selectedTagId が混ざらないこと
- `appWidgetId` ごとに状態保持すること
- Widget削除時は cleanup すること

### 重要な禁止事項
- 既存 package を勝手に変えない
- 全面リファクタしない
- Widget から直接複雑DB処理をしない
- カメラボタンでMainActivityを開いたままにしない
- root click が子ボタンを潰す状態を残さない
- 疑似コードを出さない

### 必須出力対象
- Widget provider 関連ファイル
- widget layout xml
- appwidget-provider xml
- QuickCaptureActivity
- widget preference 管理クラス
- QuickDraftRepository 差分
- AndroidManifest.xml 差分（必要なら）
- navigation / MainActivity 差分（必要なら）

### 出力形式
- `=== file path ===` 形式で区切る
- import を含む完全コード
- 変更しないファイルは出さない
- 最後に以下を必ず付ける
  1. 変更概要
  2. 不具合原因の要約
  3. widget → camera → save → manage の導線図
  4. 複数widgetの状態管理説明
  5. 手動テスト項目
