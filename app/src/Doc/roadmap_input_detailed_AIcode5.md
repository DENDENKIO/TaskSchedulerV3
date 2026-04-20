可能です。アプリ内から過去にギャラリーへ保存された重複画像を一括削除する機能を追加できます。

Android のスコープドストレージの仕組みとして、**アプリ自身が `MediaStore` に保存した画像は、そのアプリから追加のパーミッションなしで削除できます**（Android 10 以降）。現行コードでは `Pictures/TaskSchedulerV3` フォルダに保存しているので、そのパスをクエリして一括削除できます。

これを設定画面に「ギャラリーの重複画像を削除」ボタンとして実装するのが最もシンプルです。

実装は2ファイルだけの修正で済みます。

**`PhotoFileManager.kt`** にギャラリー画像の一括削除メソッドを追加し、**`SettingsScreen.kt`** / **`SettingsViewModel.kt`** にUIとアクションを追加します。以下が具体的な変更内容です。

---

### ファイル 1: `util/PhotoFileManager.kt` — メソッド追加

前回の指示書で全置換する `PhotoFileManager.kt` の末尾（`resizeBitmap` の後）に以下のメソッドを追加してください。

```kotlin
    /**
     * 過去にアプリがMediaStore（公開ギャラリー）の Pictures/TaskSchedulerV3 に保存した
     * 画像を一括検索・削除する。
     *
     * Android 10以降: アプリ自身が保存した画像はパーミッション不要で削除可能。
     * Android 9以下: DATA列のパスでフィルタし、contentResolver.delete で削除。
     *
     * @return 削除した画像の件数
     */
    fun cleanupGalleryDuplicates(context: Context): Int {
        var deletedCount = 0
        try {
            val contentResolver = context.contentResolver
            val collection = android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI

            // Pictures/TaskSchedulerV3 フォルダのエントリをクエリ
            val selection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                "${android.provider.MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
            } else {
                "${android.provider.MediaStore.Images.Media.DATA} LIKE ?"
            }
            val selectionArgs = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                arrayOf("Pictures/TaskSchedulerV3/%")
            } else {
                arrayOf("%/Pictures/TaskSchedulerV3/%")
            }

            // 対象のUri一覧を取得
            val urisToDelete = mutableListOf<android.net.Uri>()
            contentResolver.query(
                collection, arrayOf(android.provider.MediaStore.Images.Media._ID),
                selection, selectionArgs, null
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media._ID)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val uri = android.content.ContentUris.withAppendedId(collection, id)
                    urisToDelete.add(uri)
                }
            }

            // 1件ずつ削除（アプリ自身が保存したものはパーミッション不要）
            for (uri in urisToDelete) {
                try {
                    val rows = contentResolver.delete(uri, null, null)
                    if (rows > 0) deletedCount++
                } catch (e: SecurityException) {
                    // 他アプリが保存した画像は削除不可 → スキップ
                    android.util.Log.w("PhotoFileManager", "Cannot delete (not owned): $uri")
                } catch (e: Exception) {
                    android.util.Log.w("PhotoFileManager", "Delete failed: $uri", e)
                }
            }

            android.util.Log.d("PhotoFileManager", "Gallery cleanup: $deletedCount / ${urisToDelete.size} deleted")
        } catch (e: Exception) {
            android.util.Log.e("PhotoFileManager", "cleanupGalleryDuplicates error", e)
        }
        return deletedCount
    }
```

---

### ファイル 2: `ui/settings/SettingsViewModel.kt` — メソッド追加

`SettingsViewModel` クラスの末尾（`clearState()` の後）に以下を追加:

```kotlin
    // ギャラリー重複削除
    private val _galleryCleanupResult = MutableStateFlow<String?>(null)
    val galleryCleanupResult: StateFlow<String?> = _galleryCleanupResult.asStateFlow()

    fun cleanupGalleryDuplicates() = viewModelScope.launch(Dispatchers.IO) {
        val count = PhotoFileManager.cleanupGalleryDuplicates(getApplication())
        _galleryCleanupResult.value = if (count > 0) {
            "${count}件の重複画像をギャラリーから削除しました"
        } else {
            "削除対象の画像はありませんでした"
        }
    }

    fun clearGalleryCleanupResult() {
        _galleryCleanupResult.value = null
    }
```

必要な import を追加:

```kotlin
import kotlinx.coroutines.Dispatchers
import com.example.taskschedulerv3.util.PhotoFileManager
```

---

### ファイル 3: `ui/settings/SettingsScreen.kt` — UI追加

`SettingsScreen` のAI設定セクションの **手前**（`// ==================== AI設定セクション ====================` の直前）に以下を追加:

```kotlin
            // ==================== ギャラリー整理 ==================== //
            val galleryCleanupResult by vm.galleryCleanupResult.collectAsState()
            var showCleanupDialog by remember { mutableStateOf(false) }

            if (showCleanupDialog) {
                AlertDialog(
                    onDismissRequest = { showCleanupDialog = false },
                    title = { Text("ギャラリーの重複画像を削除") },
                    text = {
                        Text("過去にこのアプリが「最近」に保存した重複画像を一括削除します。\n\n元の写真（カメラで撮影したオリジナル）には影響しません。")
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            vm.cleanupGalleryDuplicates()
                            showCleanupDialog = false
                        }) { Text("削除する", color = MaterialTheme.colorScheme.error) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showCleanupDialog = false }) { Text("キャンセル") }
                    }
                )
            }

            LaunchedEffect(galleryCleanupResult) {
                galleryCleanupResult?.let { msg ->
                    snackbarHostState.showSnackbar(msg)
                    vm.clearGalleryCleanupResult()
                }
            }

            SettingsItem(
                title = "ギャラリーの重複画像を削除",
                subtitle = "「最近」に増えた仮登録の重複画像を一括削除",
                onClick = { showCleanupDialog = true }
            )
            HorizontalDivider()
```

---

これで設定画面に「ギャラリーの重複画像を削除」項目が追加されます。タップすると確認ダイアログが表示され、実行すると `Pictures/TaskSchedulerV3` フォルダ内のアプリが保存した画像が `MediaStore` から一括削除されます。カメラアプリが保存したオリジナル写真（`DCIM` フォルダ等）には一切影響しません。また、前回の指示書の `PhotoFileManager` 修正を適用すれば、以降は公開ギャラリーへの保存自体が行われなくなるため、今後は重複が発生しなくなります。