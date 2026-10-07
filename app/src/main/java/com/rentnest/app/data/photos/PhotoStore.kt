package com.rentnest.app.data.photos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Item photos live in app storage as JPEGs. An item's photo key is the absolute file path;
 * keys without a leading "/" are the built-in category placeholders drawn by ItemArt.
 */
@Singleton
class PhotoStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val dir get() = File(context.filesDir, "photos").apply { mkdirs() }

    /** A fresh file the camera app can write into. */
    fun newCaptureUri(): Uri {
        val file = File(File(context.cacheDir, "camera").apply { mkdirs() }, "capture-${UUID.randomUUID()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.photos", file)
    }

    /** Copies [source] into app storage, upright and at most [MAX_EDGE] px. Returns the photo key, or null if unreadable. */
    suspend fun import(source: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            // A bounds-only decode always returns null; the size lands in [bounds].
            resolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_EDGE) sample *= 2
            val decoded = resolver.openInputStream(source)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@runCatching null
            val rotation = resolver.openInputStream(source)?.use { exifRotation(ExifInterface(it)) } ?: 0
            val upright = scaleAndRotate(decoded, rotation)
            val out = File(dir, "${UUID.randomUUID()}.jpg")
            out.outputStream().use { upright.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            out.absolutePath
        }.onFailure { Log.w("PhotoStore", "Couldn't import $source", it) }.getOrNull()
    }

    private fun exifRotation(exif: ExifInterface): Int = when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }

    private fun scaleAndRotate(src: Bitmap, rotation: Int): Bitmap {
        val scale = minOf(1f, MAX_EDGE.toFloat() / maxOf(src.width, src.height))
        if (scale == 1f && rotation == 0) return src
        val m = Matrix().apply { postScale(scale, scale); postRotate(rotation.toFloat()) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    companion object {
        const val MAX_EDGE = 1600
        fun isFile(key: String) = key.startsWith("/")
    }
}
