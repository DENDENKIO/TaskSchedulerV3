package com.example.taskschedulerv3.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object PhotoFileManager {

    private const val PHOTOS_DIR = "task_photos"
    private const val MAX_LONG_SIDE = 1024
    private const val JPEG_QUALITY = 80

    /**
     * 写真保存先ディレクトリを取得・作成する。
     * アプリ内部ストレージ（filesDir）を使用し、公開ギャラリーには保存しない。
     */
    private fun getPhotosDir(context: Context): File {
        return File(context.filesDir, PHOTOS_DIR).apply { mkdirs() }
    }

    /**
     * カメラ撮影用の一時ファイルとそのcontent Uriを作成する。
     */
    fun createTempPhotoUri(context: Context): Pair<Uri, File> {
        val dir = getPhotosDir(context)
        val file = File(dir, "temp_camera_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return uri to file
    }

    /**
     * Uri（ギャラリー選択）からリサイズして内部ストレージに保存する。
     * 公開ギャラリーには書き込まないため画像の重複が発生しない。
     * 返値: 保存先ファイルの絶対パス、失敗時は null
     */
    fun saveResizedPhoto(context: Context, sourceUri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(sourceUri) ?: return null
            val original = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            if (original == null) return null

            val resized = resizeBitmap(original)
            val path = saveToInternalStorage(context, resized)
            if (resized !== original) resized.recycle()
            original.recycle()
            path
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * ローカルファイル（カメラ一時ファイル等）からリサイズして内部ストレージに保存する。
     * 返値: 保存先ファイルの絶対パス、失敗時は null
     */
    fun saveResizedPhotoFromFile(context: Context, sourceFile: File): String? {
        return try {
            val original = BitmapFactory.decodeFile(sourceFile.absolutePath) ?: return null
            val resized = resizeBitmap(original)
            val path = saveToInternalStorage(context, resized)
            if (resized !== original) resized.recycle()
            original.recycle()
            // 一時ファイルを削除
            if (sourceFile.name.startsWith("temp_camera_")) sourceFile.delete()
            path
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 写真ファイルを削除する。
     */
    fun deletePhoto(imagePath: String) {
        try { File(imagePath).delete() } catch (_: Exception) {}
    }

    /**
     * 画像を回転させて新しいファイルとして保存し、旧ファイルを削除する。
     * 返値: 新しいファイルの絶対パス、失敗時は null
     */
    fun rotateImage(context: Context, imagePath: String, degrees: Float = 90f): String? {
        return try {
            val original = BitmapFactory.decodeFile(imagePath) ?: return null
            val matrix = Matrix().apply { postRotate(degrees) }
            val rotated = Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)

            val path = saveToInternalStorage(context, rotated)
            if (rotated !== original) rotated.recycle()
            original.recycle()

            // 旧ファイルを削除
            File(imagePath).delete()

            path
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Bitmap をアプリ内部ストレージに JPEG で保存する。
     * 公開ギャラリーには一切書き込まない → 画像重複が発生しない。
     */
    private fun saveToInternalStorage(context: Context, bitmap: Bitmap): String? {
        return try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(Date())
            val fileName = "TaskScheduler_$timeStamp.jpg"
            val dir = getPhotosDir(context)
            val outFile = File(dir, fileName)

            FileOutputStream(outFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
            outFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 絶対ファイルパスから Coil が読み込める Uri を返す。
     */
    fun pathToUri(imagePath: String): Uri {
        return Uri.fromFile(File(imagePath))
    }

    private fun resizeBitmap(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val longSide = maxOf(w, h)
        if (longSide <= MAX_LONG_SIDE) return bitmap
        val scale = MAX_LONG_SIDE.toFloat() / longSide
        val newW = (w * scale).toInt()
        val newH = (h * scale).toInt()
        return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
    }

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
}
