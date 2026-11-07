package com.ztmanager.ui.viewer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.ztmanager.R
import com.ztmanager.fs.FileType
import com.ztmanager.fs.Format
import com.ztmanager.ui.AppState
import com.ztmanager.ui.browser.NaturalOrder
import com.ztmanager.ui.browser.dialogs.InfoRow
import com.ztmanager.ui.common.Intents
import com.ztmanager.ui.common.Thumbnails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ImageViewerScreen(path: String) {
    val context = LocalContext.current
    val start = remember { File(path) }
    val images = remember {
        start.parentFile?.listFiles()
            ?.filter { it.isFile && FileType.of(it.name, false) == FileType.IMAGE }
            ?.sortedWith { a, b -> NaturalOrder.compare(a.name, b.name) }
            ?.takeIf { it.isNotEmpty() } ?: listOf(start)
    }
    val initial = images.indexOfFirst { it.absolutePath == start.absolutePath }.coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = initial) { images.size }
    var showUi by remember { mutableStateOf(true) }
    var info by remember { mutableStateOf<File?>(null) }
    var rotation by remember { mutableFloatStateOf(0f) }
    val current = images[pager.currentPage.coerceIn(0, images.lastIndex)]

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            ZoomableImage(images[page], if (page == pager.currentPage) rotation else 0f) { showUi = !showUi }
        }
        if (showUi) {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.55f),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                ),
                navigationIcon = { IconButton(onClick = { AppState.pop() }) { Icon(Icons.Filled.ArrowBack, null) } },
                title = {
                    Column {
                        Text(current.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                        Text("${pager.currentPage + 1} / ${images.size}", style = MaterialTheme.typography.bodySmall)
                    }
                },
                actions = {
                    IconButton(onClick = { rotation = (rotation + 90f) % 360f }) { Icon(Icons.Filled.RotateRight, null) }
                    IconButton(onClick = { Intents.share(context, listOf(current)) }) { Icon(Icons.Filled.Share, null) }
                    IconButton(onClick = { Intents.setAs(context, current) }) { Icon(Icons.Filled.Wallpaper, null) }
                    IconButton(onClick = { Intents.openExternal(context, current) }) { Icon(Icons.Filled.OpenInNew, null) }
                    IconButton(onClick = { info = current }) { Icon(Icons.Filled.Info, null) }
                }
            )
        }
    }

    info?.let { f ->
        val dims = remember(f) {
            val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(f.absolutePath, o)
            "${o.outWidth} x ${o.outHeight}"
        }
        val exif = remember(f) { readExif(f) }
        AlertDialog(
            onDismissRequest = { info = null },
            title = { Text(f.name) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    InfoRow(stringResource(R.string.path), f.absolutePath)
                    InfoRow(stringResource(R.string.resolution), dims)
                    InfoRow(stringResource(R.string.size), Format.size(f.length()))
                    InfoRow(stringResource(R.string.modified), Format.dateLong(f.lastModified()))
                    exif.forEach { (k, v) -> InfoRow(k, v) }
                }
            },
            confirmButton = { TextButton(onClick = { info = null }) { Text(stringResource(R.string.close)) } }
        )
    }
}

private fun readExif(f: File): List<Pair<String, String>> = try {
    val e = android.media.ExifInterface(f.absolutePath)
    val tags = listOf(
        "Camera" to android.media.ExifInterface.TAG_MODEL,
        "Make" to android.media.ExifInterface.TAG_MAKE,
        "Date" to android.media.ExifInterface.TAG_DATETIME_ORIGINAL,
        "Exposure" to android.media.ExifInterface.TAG_EXPOSURE_TIME,
        "F-number" to android.media.ExifInterface.TAG_F_NUMBER,
        "ISO" to android.media.ExifInterface.TAG_ISO_SPEED_RATINGS,
        "Focal length" to android.media.ExifInterface.TAG_FOCAL_LENGTH,
        "Flash" to android.media.ExifInterface.TAG_FLASH,
        "Software" to android.media.ExifInterface.TAG_SOFTWARE
    )
    val out = tags.mapNotNull { (label, tag) -> e.getAttribute(tag)?.takeIf { it.isNotBlank() }?.let { label to it } }.toMutableList()
    val ll = FloatArray(2)
    if (e.getLatLong(ll)) out.add("GPS" to String.format("%.6f, %.6f", ll[0], ll[1]))
    out
} catch (e: Exception) {
    emptyList()
}

@Composable
private fun ZoomableImage(file: File, rotation: Float, onTap: () -> Unit) {
    val maxSide = LocalConfiguration.current.let { maxOf(it.screenWidthDp, it.screenHeightDp) } * 3
    val bitmap: ImageBitmap? by produceState<ImageBitmap?>(null, file) {
        value = withContext(Dispatchers.IO) {
            try {
                Thumbnails.decodeSampled(file.absolutePath, maxSide.coerceAtMost(4096))?.asImageBitmap()
            } catch (e: Throwable) {
                null
            }
        }
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val b = bitmap
        if (b == null) {
            CircularProgressIndicator(color = Color.White)
        } else {
            Image(
                bitmap = b,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { onTap() },
                            onDoubleTap = {
                                if (scale > 1f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else scale = 2.5f
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        // Only consume gestures while zooming so the pager can still swipe.
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent()
                                if (event.changes.size > 1 || scale > 1f) {
                                    val zoom = event.calculateZoom()
                                    val pan = event.calculatePan()
                                    scale = (scale * zoom).coerceIn(1f, 8f)
                                    offset = if (scale > 1f) offset + pan else Offset.Zero
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                        rotationZ = rotation
                    }
            )
        }
    }
}
