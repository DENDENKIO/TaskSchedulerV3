# TaskSchedulerV3 ローカルAI用 上から順に処理させるプロンプト

以下の順番で処理してください。  
**重要: 既に実装されている機能があれば省略し、不足分だけを差分実装してください。**  
既存ローカルコードを直接読まずに推測で全面再構成しないでください。

---

## Step 1: 現状確認レポートのみ出力

まずコードは出さず、現在のローカルリポジトリを確認してください。
以下を「実装済みで十分 / 実装済みだが改修必要 / 未実装 / 要調査」で分類してください。

確認項目:
1. MainActivity / navigation
2. AppDatabase / version / entities
3. Task / Tag / PhotoMemo model
4. TaskDao / TagDao / PhotoMemoDao
5. schedulelist
6. addtask
7. tag 管理
8. recurring 管理
9. QuickDraft 相当機能
10. widget 機能
11. camera / FileProvider / OCR 導線

出力形式:
- セクションごとに対象ファイルパス
- 現状
- 判定
- 改修必要性

コードはまだ出さないでください。

---

## Step 2: 差分対象一覧を確定

Step 1 の結果をもとに、今回本当に変更すべきファイルだけを列挙してください。

出力形式:
- 変更必須ファイル
- 変更候補ファイル
- 新規追加が必要なファイル
- 触らない方がよい既存ファイル

まだコードは出さないでください。

---

## Step 3: QuickDraft 基盤の不足分だけ実装

QuickDraft 相当機能が未実装または不足している場合のみ、差分実装してください。

要件:
- 仮登録保存先
- 仮登録一覧取得
- 仮登録更新
- 仮登録削除
- 仮登録から本登録への変換
- 既存 PhotoMemo / OCR があれば連携
- 既存の同等機能があれば重複新設しない

出力:
- 変更ファイルごとの完全コード
- DB migration が必要ならその理由
- 手動テスト項目

---

## Step 4: 一覧画面のタグフィルタ / 表示モードの不足分だけ実装

schedulelist の既存コードを確認し、不足分のみ実装してください。

要件:
- タグフィルタ上部Row
- TODAY / ALL / DONE / RECURRING / DRAFT の不足分
- DRAFT は QuickDraft 一覧と接続
- RECURRING は recurring タスクだけ表示
- 既存で十分な機能は残す

出力:
- 変更ファイルごとの完全コード
- 表示モード仕様表
- 手動テスト項目

---

## Step 5: 一覧行UIの不足分だけ改善

既存の一覧1行UIを確認し、不足分のみ改修してください。

要件:
- 左優先度ライン
- 進捗バー
- 残日数バッジ
- タグ最大2件表示
- 完了時 muted 化
- 既存UIが十分なら流用優先

出力:
- 変更ファイルごとの完全コード
- 視認性改善ポイント
- 手動テスト項目

---

## Step 6: addtask の保存導線を確認し、不足なら右上保存化

既に右上保存なら変更しないでください。  
下部保存のみなら、右上保存へ差分改修してください。

要件:
- TopBar 右上保存
- 左戻る / 閉じる
- canSave 制御
- 下部保存削除
- 保存処理は既存再利用

出力:
- 変更ファイルごとの完全コード
- 何を削除したか
- 手動テスト項目

---

## Step 7: tag 色入力の有無を確認し、不足ならUIから廃止

既に tag 色入力がなければ変更不要です。  
残っている場合のみ差分改修してください。

要件:
- 色入力欄削除
- 色プレビュー削除
- 固定色保存
- DB color カラムは残してよい

出力:
- 変更ファイルごとの完全コード
- 将来DBから消す場合の注意点
- 手動テスト項目

---

## Step 8: recurring 管理の不足分だけ補完

既存 recurring が十分なら大きく変えないでください。  
不足する場合のみ差分実装してください。

要件:
- settings 配下の recurring 導線
- EVERY_N_DAYS
- WEEKLY_MULTI
- MONTHLY_DATES
- recurrencePattern / recurrenceDays を既存Taskに整合
- RECURRING モードと接続

出力:
- 変更ファイルごとの完全コード
- recurring 保存ルール表
- 手動テスト項目

---

## Step 9: widget 高速仮登録を実装

widget 機能を確認し、既に十分なら不足分のみ差分追加してください。  
未実装なら新規追加してください。

要件:
- widget 上でタグ選択、または未選択状態にできる
- タグは順送り切替方式を推奨
- カメラボタン押下で即カメラ起動
- 撮影成功後に QuickDraft 自動保存
- 保存後に仮登録管理画面または編集画面へ遷移
- widget ごとに selectedTagId を保持
- camera / FileProvider / OCR 既存実装を壊さない

推奨構成:
- AppWidgetProvider
- widget preference store
- QuickCaptureActivity
- QuickDraftRepository 連携

出力:
- 変更ファイルごとの完全コード
- widget 導線図
- 複数widget時の状態管理説明
- 手動テスト項目

---

## Step 10: 最終接続確認

最後に MainActivity / navigation / settings / schedulelist / quickdraft / widget 導線が整合しているか確認し、不足があれば最小差分で修正してください。

出力:
- 最終変更ファイルごとの完全コード
- 変更概要
- 未解決事項
- 総合手動テスト項目

---

## 厳守事項

- 既存コードを読んだ結果、すでに実装済みなら省略する
- 重複クラスを作らない
- 重複画面を作らない
- package を勝手に変えない
- 全面リファクタをしない
- 疑似コード禁止
- import を含めてコンパイル可能なコードのみ出す
- 出力は `=== file path ===` 形式で区切る
- 各Stepの最後に「変更概要」「追従修正候補」「手動テスト項目」を必ず付ける
