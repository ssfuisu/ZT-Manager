package com.ztmanager.fs

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.graphics.Matrix
import android.os.Build
import com.ztmanager.task.TaskContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** Batch image conversion and resizing. */
object ImageTools {
    enum class OutFormat(val ext: String) { JPEG("jpg"), PNG("png"), WEBP("webp") }

    private fun rotationOf(path: String): Int = try {
        when (ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    } catch (e: Exception) {
        0
    }

    @Suppress("DEPRECATION")
    private fun compressFormat(f: OutFormat): Bitmap.CompressFormat = when (f) {
        OutFormat.JPEG -> Bitmap.CompressFormat.JPEG
        OutFormat.PNG -> Bitmap.CompressFormat.PNG
        OutFormat.WEBP -> if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
    }

    fun convert(tc: TaskContext, files: List<File>, format: OutFormat, quality: Int, maxSide: Int, deleteSource: Boolean): Int {
        var done = 0
        files.forEachIndexed { i, src ->
            tc.checkCancelled()
            tc.setItems(i, files.size)
            tc.setMessage(src.name)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(src.absolutePath, bounds)
            if (bounds.outWidth <= 0) return@forEachIndexed
            var sample = 1
            if (maxSide > 0) {
                while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
            }
            var bmp = BitmapFactory.decodeFile(src.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: throw IOException("Cannot decode ${src.name}")
            val rot = rotationOf(src.absolutePath)
            if (rot != 0) {
                val m = Matrix().apply { postRotate(rot.toFloat()) }
                val r = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
                if (r != bmp) bmp.recycle()
                bmp = r
            }
            val longest = maxOf(bmp.width, bmp.height)
            if (maxSide in 1 until longest) {
                val scale = maxSide.toFloat() / longest
                val r = Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt().coerceAtLeast(1), (bmp.height * scale).toInt().coerceAtLeast(1), true)
                if (r != bmp) bmp.recycle()
                bmp = r
            }
            val base = src.name.substringBeforeLast('.', src.name)
            var out = File(src.parentFile, base + "." + format.ext)
            if (out.absolutePath == src.absolutePath || out.exists()) out = FileOps.uniqueFile(src.parentFile!!, base + "." + format.ext)
            try {
                FileOutputStream(out).use { o ->
                    if (!bmp.compress(compressFormat(format), quality.coerceIn(1, 100), o)) throw IOException("Cannot encode ${out.name}")
                }
            } catch (e: Throwable) {
                out.delete()
                throw e
            } finally {
                bmp.recycle()
            }
            out.setLastModified(src.lastModified())
            if (deleteSource) src.delete()
            done++
        }
        tc.setItems(files.size, files.size)
        return done
    }
}
