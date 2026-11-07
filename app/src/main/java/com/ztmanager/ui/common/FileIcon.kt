package com.ztmanager.ui.common

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.media.ThumbnailUtils
import android.os.Build
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.ztmanager.data.Prefs
import com.ztmanager.fs.FileEntry
import com.ztmanager.fs.FileType
import com.ztmanager.ui.theme.ZtTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object Thumbnails {
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun get(key: String): Bitmap? = cache.get(key)

    fun load(context: android.content.Context, entry: FileEntry, px: Int): Bitmap? {
        val key = entry.path + ":" + entry.lastModified + ":" + px
        cache.get(key)?.let { return it }
        val bmp = try {
            when (entry.type) {
                FileType.IMAGE -> decodeSampled(entry.path, px)
                FileType.VIDEO -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    ThumbnailUtils.createVideoThumbnail(File(entry.path), Size(px, px), null)
                else null
                FileType.APK -> apkIcon(context, entry.path)?.toBitmap(px, px)
                FileType.PDF -> pdfFirstPage(entry.path, px)
                FileType.AUDIO -> audioArt(entry.path, px)
                else -> null
            }
        } catch (e: Throwable) {
            null
        }
        if (bmp != null) cache.put(key, bmp)
        return bmp
    }

    fun decodeSampled(path: String, req: Int): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, opts)
        if (opts.outWidth <= 0) return null
        var sample = 1
        while (opts.outWidth / (sample * 2) >= req && opts.outHeight / (sample * 2) >= req) sample *= 2
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    fun pdfFirstPage(path: String, px: Int): Bitmap? {
        android.os.ParcelFileDescriptor.open(File(path), android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            android.graphics.pdf.PdfRenderer(fd).use { r ->
                if (r.pageCount == 0) return null
                r.openPage(0).use { page ->
                    val w = px
                    val h = (px.toFloat() * page.height / page.width.coerceAtLeast(1)).toInt().coerceIn(1, px * 2)
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(android.graphics.Color.WHITE)
                    page.render(bmp, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    return bmp
                }
            }
        }
    }

    fun audioArt(path: String, px: Int): Bitmap? {
        val r = android.media.MediaMetadataRetriever()
        return try {
            r.setDataSource(path)
            val data = r.embeddedPicture ?: return null
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(data, 0, data.size, opts)
            var sample = 1
            while (opts.outWidth / (sample * 2) >= px) sample *= 2
            BitmapFactory.decodeByteArray(data, 0, data.size, BitmapFactory.Options().apply { inSampleSize = sample })
        } catch (e: Exception) {
            null
        } finally {
            try {
                r.release()
            } catch (_: Exception) {
            }
        }
    }

    fun apkIcon(context: android.content.Context, path: String): Drawable? {
        val pm = context.packageManager
        val info = pm.getPackageArchiveInfo(path, 0) ?: return null
        val ai = info.applicationInfo ?: return null
        ai.sourceDir = path
        ai.publicSourceDir = path
        return ai.loadIcon(pm)
    }
}

@Composable
fun FileIcon(
    entry: FileEntry,
    size: Dp = 40.dp,
    selected: Boolean = false
) {
    val context = LocalContext.current
    val showThumb = Prefs.showThumbnails && !entry.inArchive &&
        (entry.type == FileType.IMAGE || entry.type == FileType.VIDEO || entry.type == FileType.APK ||
            entry.type == FileType.PDF || entry.type == FileType.AUDIO)
    val pxSize = with(androidx.compose.ui.platform.LocalDensity.current) { size.roundToPx() }
    val thumb: ImageBitmap? by produceState<ImageBitmap?>(null, entry.path, entry.lastModified, showThumb) {
        value = if (showThumb) withContext(Dispatchers.IO) {
            Thumbnails.load(context, entry, pxSize)?.asImageBitmap()
        } else null
    }
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        val t = thumb
        when {
            t != null -> Image(
                bitmap = t,
                contentDescription = null,
                contentScale = if (entry.type == FileType.APK || entry.type == FileType.PDF) ContentScale.Fit else ContentScale.Crop,
                modifier = Modifier
                    .size(size)
                    .clip(RoundedCornerShape(6.dp))
            )
            entry.isDirectory -> Icon(
                entry.type.icon,
                contentDescription = null,
                tint = ZtTheme.colors.folder,
                modifier = Modifier.size(size)
            )
            else -> TypeBadge(entry, size)
        }
        if (entry.isSymlink) {
            Icon(
                Icons.Filled.Link, null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .size(size * 0.38f)
                    .background(Color(0xFF455A64), RoundedCornerShape(50))
            )
        }
        if (entry.encrypted) {
            Icon(
                Icons.Filled.Lock, null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(size * 0.38f)
                    .background(Color(0xFFD84315), RoundedCornerShape(50))
            )
        }
        if (selected) {
            Box(
                Modifier
                    .size(size)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.CheckCircle, null, tint = Color.White, modifier = Modifier.size(size * 0.6f))
            }
        }
    }
}

@Composable
private fun TypeBadge(entry: FileEntry, size: Dp) {
    val type = entry.type
    Box(
        Modifier
            .size(size * 0.86f)
            .background(type.color, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (type == FileType.UNKNOWN && entry.extension.isNotEmpty() && entry.extension.length <= 4) {
            Text(
                entry.extension.uppercase(),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.26f).sp,
                maxLines = 1
            )
        } else {
            Icon(type.icon, null, tint = Color.White, modifier = Modifier.size(size * 0.5f))
        }
    }
}
