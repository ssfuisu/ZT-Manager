package com.ztmanager.ui.viewer

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ztmanager.R
import com.ztmanager.ui.AppState
import com.ztmanager.ui.browser.DialogSpec
import com.ztmanager.ui.browser.Dialogs
import com.ztmanager.ui.common.Intents
import com.ztmanager.ui.theme.ZtTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** PdfRenderer can only have one page open at a time, so access is serialized. */
private class PdfDoc(file: File) {
    private val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(fd)
    private val lock = Mutex()
    val pageCount: Int = renderer.pageCount
    val ratios: List<Float> = (0 until pageCount).map { i ->
        renderer.openPage(i).use { p -> p.width.toFloat() / p.height.coerceAtLeast(1) }
    }
    private val cache = object : android.util.LruCache<Int, Bitmap>(6) {}

    suspend fun render(index: Int, width: Int): Bitmap = lock.withLock {
        cache.get(index)?.takeIf { it.width == width }?.let { return@withLock it }
        withContext(Dispatchers.IO) {
            renderer.openPage(index).use { page ->
                val h = (width.toFloat() * page.height / page.width.coerceAtLeast(1)).toInt().coerceAtLeast(1)
                val bmp = Bitmap.createBitmap(width, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(android.graphics.Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                cache.put(index, bmp)
                bmp
            }
        }
    }

    fun close() {
        try {
            renderer.close()
            fd.close()
        } catch (_: Exception) {
        }
    }
}

@Composable
fun PdfViewerScreen(path: String) {
    val context = LocalContext.current
    val file = remember { File(path) }
    val docResult by produceState<Result<PdfDoc>?>(null) {
        value = withContext(Dispatchers.IO) { runCatching { PdfDoc(file) } }
    }
    DisposableEffect(docResult) { onDispose { docResult?.getOrNull()?.close() } }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val currentPage by remember { derivedStateOf { listState.firstVisibleItemIndex + 1 } }
    val goTitle = stringResource(R.string.go_to_page)
    val pageHint = stringResource(R.string.page)

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ZtTheme.colors.toolbar),
                navigationIcon = { IconButton(onClick = { AppState.pop() }) { Icon(Icons.Filled.ArrowBack, null) } },
                title = {
                    Column {
                        Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                        docResult?.getOrNull()?.let {
                            Text("$currentPage / ${it.pageCount}", style = MaterialTheme.typography.bodySmall, color = ZtTheme.colors.secondaryText)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {
                        Dialogs.show(DialogSpec.Input(goTitle, "", pageHint) { v ->
                            v.trim().toIntOrNull()?.let { n -> scope.launch { listState.scrollToItem((n - 1).coerceAtLeast(0)) } }
                        })
                    }) { Icon(Icons.Filled.Tag, null) }
                    IconButton(onClick = { Intents.openExternal(context, file) }) { Icon(Icons.Filled.OpenInNew, null) }
                }
            )
        },
        containerColor = Color(0xFF3A3D42)
    ) { padding ->
        val r = docResult
        BoxWithConstraints(Modifier.padding(padding).fillMaxSize()) {
            when {
                r == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                r.isFailure -> Text(r.exceptionOrNull()?.message ?: "Error", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                else -> {
                    val doc = r.getOrThrow()
                    val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }.coerceAtMost(2000)
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    do {
                                        val event = awaitPointerEvent()
                                        if (event.changes.size > 1) {
                                            scale = (scale * event.calculateZoom()).coerceIn(1f, 5f)
                                            offset = if (scale > 1f) offset + Offset(event.calculatePan().x, 0f) else Offset.Zero
                                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                                        }
                                    } while (event.changes.any { it.pressed })
                                }
                            }
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offset.x
                            }
                    ) {
                        items(doc.pageCount) { i ->
                            val bmp by produceState<ImageBitmap?>(null, i, widthPx) {
                                value = try {
                                    doc.render(i, widthPx).asImageBitmap()
                                } catch (e: Exception) {
                                    null
                                }
                            }
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .aspectRatio(doc.ratios[i].coerceIn(0.2f, 5f))
                                    .background(Color.White),
                                contentAlignment = Alignment.Center
                            ) {
                                val b = bmp
                                if (b != null) Image(b, null, Modifier.fillMaxSize())
                                else CircularProgressIndicator()
                            }
                        }
                    }
                }
            }
        }
    }
}
