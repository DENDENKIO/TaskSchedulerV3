package com.example.taskschedulerv3.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import android.content.ContentValues
import android.os.Build
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*

object PhotoFileManager {

    private const val PHOTOS_DIR = "TaskSchedulerV3"
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
            val path = saveToPublicGallery(context, resized)
            if (resized !== original) resized.recycle()
            path
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
            val path = saveToPublicGallery(context, resized)
            if (resized !== original) resized.recycle()
            // Clean up temp file
            if (sourceFile.name.startsWith("temp_camera_")) sourceFile.delete()
            path
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
     * Rotates a photo file by the specified degrees and saves it to a new file,
     * deleting the old file. Returns the new file path, or null on failure.
     */
    fun rotateImage(context: Context, imagePath: String, degrees: Float = 90f): String? {
        return try {
            val original = BitmapFactory.decodeFile(imagePath) ?: return null
            val matrix = Matrix().apply { postRotate(degrees) }
            val rotated = Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
            
            val path = saveToPublicGallery(context, rotated)
            if (rotated !== original) rotated.recycle()
            original.recycle()
            
            // Delete old file
            File(imagePath).delete()
            
            path
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Saves a bitmap to the public Pictures gallery in a dedicated subfolder.
     * Returns the absolute file path.
     */
    private fun saveToPublicGallery(context: Context, bitmap: Bitmap): String? {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "TaskScheduler_$timeStamp.jpg"

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ Scoped Storage
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/$PHOTOS_DIR")
            }

            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues) ?: return null
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
            }
            
            // Write EXIF tagging
            context.contentResolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                val exif = ExifInterface(pfd.fileDescriptor)
                exif.setAttribute(ExifInterface.TAG_SOFTWARE, "TaskSchedulerV3")
                exif.saveAttributes()
            }
            
            getRealPathFromURI(context, uri)
        } else {
            // Legacy Storage
            val picturesDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES)
            val appDir = File(picturesDir, PHOTOS_DIR).apply { mkdirs() }
            val outFile = File(appDir, fileName)
            FileOutputStream(outFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
            
            // Write EXIF tagging
            val exif = ExifInterface(outFile.absolutePath)
            exif.setAttribute(ExifInterface.TAG_SOFTWARE, "TaskSchedulerV3")
            exif.saveAttributes()
            
            // Trigger media scan
            android.media.MediaScannerConnection.scanFile(context, arrayOf(outFile.absolutePath), arrayOf("image/jpeg"), null)
            outFile.absolutePath
        }
    }

    private fun getRealPathFromURI(context: Context, contentUri: Uri): String? {
        var path: String? = null
        val projection = arrayOf(MediaStore.Images.Media.DATA)
        val cursor = context.contentResolver.query(contentUri, projection, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val columnIndex = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                path = it.getString(columnIndex)
            }
        }
        return path ?: contentUri.toString()
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
