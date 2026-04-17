# ローカルAI用プロンプト（Widget高速仮登録 詳細版）

既存のローカルコードを直接読み、ホーム画面ウィジェットから最速で仮登録する機能を差分実装してください。  
**重要: すでに同等機能が存在する場合は再実装せず、不足分だけを改修してください。**

## 最初に必ず行う確認
コード出力の前に、以下を確認してレポートしてください。

1. 既存 widget 実装の有無
2. AppWidgetProvider の有無
3. widget 用レイアウト xml の有無
4. widget 設定Activityの有無
5. camera / FileProvider 実装の場所
6. PhotoMemo / OCR の既存利用経路
7. QuickDraft 相当機能の有無
8. QuickDraft 管理画面または近い画面の有無
9. tag 一覧取得方法
10. MainActivity / navigation から仮登録管理へ行く方法

出力は「現状確認レポート」のみ。まだコードは出さないでください。

---

## その後に実装する内容

目的:
- Widget上でタグを選択、または未選択にできる
- カメラボタン押下で即カメラ起動する
- 撮影後に何も入力せず QuickDraft を自動保存する
- 保存後に仮登録管理画面または編集画面へ遷移する

### 実装ルール
- widget から直接DB保存しない
- 必ず中継Activity（例: QuickCaptureActivity）を経由する
- widget ごとに selectedTagId を保持する
- タグ切替は順送り方式にする
- 既存 camera / FileProvider / OCR / PhotoMemo 実装を流用する
- 複数widgetが置かれても状態競合しないようにする

### タグ切替仕様
タグ候補は以下順で取得してください。
1. Tagテーブル
2. sortOrder
3. name

切替順は以下です。
- 未選択 → タグ1 → タグ2 → ... → 最後のタグ → 未選択

widget 上には現在タグを表示してください。
例:
- タグ: 未選択
- タグ: 会議

### widgetごとの状態保持
- `appWidgetId` ごとに `selectedTagId` を保存する
- SharedPreferences または既存設定基盤を使う
- Widget削除時は対応する設定をcleanupする

### カメラ起動仕様
Widget のカメラボタン押下時は以下の流れにしてください。
1. AppWidgetProvider から QuickCaptureActivity を起動
2. QuickCaptureActivity が appWidgetId を受け取る
3. selectedTagId を読み出す
4. 一意な画像ファイル名を作る
5. FileProvider URI を作る
6. camera intent または ActivityResultContracts.TakePicture を起動
7. 撮影成功時のみ QuickDraft を保存
8. 失敗またはキャンセル時は一時ファイルを掃除して終了

### QuickDraft 保存仕様
保存項目:
- title = `yyyy-MM-dd HH:mm 仮登録`
- description = null で可
- photoPath = 撮影画像パス
- tagId = widget選択タグ（未選択なら null）
- status = DRAFT
- ocrText = 後で非同期でも可

### OCR方針
- 高速仮登録が主目的なので、保存を先に完了する
- OCR は既存実装があれば保存後に非同期で走らせてよい

### 保存後の遷移
初期実装は以下を推奨。
- QuickDraftListScreen へ遷移

もし既存の構成上、編集画面へ直接遷移する方が自然なら、作成IDを渡して編集画面へ遷移してもよい。

### 必須出力対象
- widget provider 関連ファイル
- widget layout xml / provider info xml（必要なら）
- QuickCaptureActivity
- widget preference 管理クラス
- QuickDraftRepository 差分
- AndroidManifest.xml 差分（必要なら）
- navigation / MainActivity 差分（必要なら）

### 最後に必ず付ける内容
1. 変更概要
2. widget → camera → save → draft manage の導線図
3. 複数widget時の状態管理説明
4. 手動テスト項目
5. 既存実装と衝突しうる注意点

### 出力形式
- `=== file path ===` 形式で区切る
- import を含む完全コード
- 疑似コード禁止
- 既存機能が十分な部分は変更しない
