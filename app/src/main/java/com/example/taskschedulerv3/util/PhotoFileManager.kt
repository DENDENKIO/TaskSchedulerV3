package com.example.taskschedulerv3.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object PhotoFileManager {

    private const val PHOTOS_DIR = "photos"
    private const val MAX_LONG_SIDE = 1024
    private const val JPEG_QUALITY = 80

    /**
     * Creates a temporary file in the photos directory and returns its content Uri
     * for use with ActivityResultContracts.TakePicture.
     */
    fun createTempPhotoUri(context: Context): Pair<Uri, File> {
        val dir = File(context.filesDir, PHOTOS_DIR).apply { mkdirs() }
        val file = File(dir, "temp_camera_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return uri to file
    }

    /**
     * Resizes and saves an image from a given Uri (gallery pick or camera file).
     * Returns the saved file path, or null on failure.
     * Long side capped at 1024px, JPEG quality 80.
     */
    fun saveResizedPhoto(context: Context, sourceUri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(sourceUri) ?: return null
            val original = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            val resized = resizeBitmap(original)
            val dir = File(context.filesDir, PHOTOS_DIR).apply { mkdirs() }
            val outFile = File(dir, "photo_${System.currentTimeMillis()}.jpg")
            FileOutputStream(outFile).use { out ->
                resized.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
            if (resized !== original) resized.recycle()
            outFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Saves an image from a local file (e.g., temp camera file) with resize.
     * Returns the saved file path, or null on failure.
     */
    fun saveResizedPhotoFromFile(context: Context, sourceFile: File): String? {
        return try {
            val original = BitmapFactory.decodeFile(sourceFile.absolutePath) ?: return null
            val resized = resizeBitmap(original)
            val dir = File(context.filesDir, PHOTOS_DIR).apply { mkdirs() }
            val outFile = File(dir, "photo_${System.currentTimeMillis()}.jpg")
            FileOutputStream(outFile).use { out ->
                resized.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
            if (resized !== original) resized.recycle()
            // Clean up temp file
            if (sourceFile.name.startsWith("temp_camera_")) sourceFile.delete()
            outFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Deletes a photo file from the filesystem.
     */
    fun deletePhoto(imagePath: String) {
        try { File(imagePath).delete() } catch (_: Exception) {}
    }

    /**
     * Returns a Uri suitable for Coil to load from an absolute file path.
     */
    fun pathToUri(imagePath: String): Uri = Uri.parse("file://$imagePath")

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
}
