package com.ztmanager.ui.hex

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ztmanager.R
import com.ztmanager.fs.Format
import com.ztmanager.ui.AppState
import com.ztmanager.ui.Screen
import com.ztmanager.ui.browser.DialogSpec
import com.ztmanager.ui.browser.Dialogs
import com.ztmanager.ui.browser.dialogs.RadioRow
import com.ztmanager.ui.theme.ZtTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.util.Locale

private const val ROW = 16
private const val PAGE = 64 * 1024

/** Reads a file in pages so that very large files can be viewed. */
private class PagedFile(val file: File) {
    val size: Long = file.length()
    private var raf: RandomAccessFile? = null
    private val pages = object : LinkedHashMap<Long, ByteArray>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, ByteArray>?): Boolean = size > 32
    }

    @Synchronized
    fun read(offset: Long, len: Int): ByteArray {
        val out = ByteArray(minOf(len.toLong(), size - offset).coerceAtLeast(0L).toInt())
        var pos = 0
        while (pos < out.size) {
            val abs = offset + pos
            val pageIdx = abs / PAGE
            val page = pages.getOrPut(pageIdx) { loadPage(pageIdx) }
            val inPage = (abs - pageIdx * PAGE).toInt()
            val n = minOf(out.size - pos, page.size - inPage)
            if (n <= 0) break
            System.arraycopy(page, inPage, out, pos, n)
            pos += n
        }
        return out
    }

    private fun loadPage(idx: Long): ByteArray {
        val r = raf ?: RandomAccessFile(file, "r").also { raf = it }
        val start = idx * PAGE
        val len = minOf(PAGE.toLong(), size - start).toInt().coerceAtLeast(0)
        val buf = ByteArray(len)
        r.seek(start)
        r.readFully(buf)
        return buf
    }

    @Synchronized
    fun invalidate() {
        pages.clear()
    }

    fun close() {
        try {
            raf?.close()
        } catch (_: Exception) {
        }
    }
}

private fun printable(b: Int): Char = if (b in 0x20..0x7E) b.toChar() else '.'

@Composable
fun HexEditorScreen(path: String) {
    val file = remember { File(path) }
    val paged = remember { PagedFile(file) }
    DisposableEffect(Unit) { onDispose { paged.close() } }
    val edits = remember { mutableStateMapOf<Long, Byte>() }
    var refresh by remember { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Long?>(null) }
    var selected by remember { mutableStateOf(-1L) }
    var showSearch by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val rows = ((paged.size + ROW - 1) / ROW).toInt()
    val colors = ZtTheme.colors
    val savedMsg = stringResource(R.string.msg_saved)
    val failedMsg = stringResource(R.string.msg_save_failed)
    val notFound = stringResource(R.string.msg_not_found)
    val canWrite = file.canWrite()

    fun save() {
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    RandomAccessFile(file, "rw").use { r ->
                        for ((off, b) in edits) {
                            r.seek(off)
                            r.write(b.toInt())
                        }
                    }
                    true
                } catch (e: Exception) {
                    false
                }
            }
            if (ok) {
                edits.clear()
                paged.invalidate()
                refresh++
                AppState.toast(savedMsg)
            } else AppState.toast(failedMsg)
        }
    }

    fun jumpTo(offset: Long) {
        val o = offset.coerceIn(0, (paged.size - 1).coerceAtLeast(0))
        selected = o
        scope.launch { listState.scrollToItem((o / ROW).toInt()) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.toolbar),
                navigationIcon = { IconButton(onClick = { AppState.pop() }) { Icon(Icons.Filled.ArrowBack, null) } },
                title = {
                    Column {
                        Text((if (edits.isNotEmpty()) "* " else "") + file.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                        Text(Format.size(paged.size) + "  (" + paged.size + " B)", style = MaterialTheme.typography.bodySmall, color = colors.secondaryText)
                    }
                },
                actions = {
                    val gotoTitle = stringResource(R.string.go_to_offset)
                    IconButton(onClick = {
                        Dialogs.show(DialogSpec.Input(gotoTitle, "0x", "0x1F00 / 7936") { v ->
                            val t = v.trim().lowercase(Locale.ROOT)
                            val n = if (t.startsWith("0x")) t.removePrefix("0x").toLongOrNull(16) else t.toLongOrNull()
                            if (n != null) jumpTo(n)
                        })
                    }) { Icon(Icons.Filled.Tag, null) }
                    IconButton(onClick = { showSearch = true }) { Icon(Icons.Filled.Search, null) }
                    if (edits.isNotEmpty() && canWrite) IconButton(onClick = { save() }) { Icon(Icons.Filled.Save, null) }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, null) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.open_as_text)) }, onClick = {
                            menu = false
                            AppState.replaceTop(Screen.Editor(file.absolutePath))
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.discard)) }, enabled = edits.isNotEmpty(), onClick = {
                            menu = false
                            edits.clear()
                        })
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(colors.pathBar)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("Offset  ", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = colors.secondaryText)
                Text((0 until ROW).joinToString(" ") { String.format("%02X", it) }, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = colors.secondaryText)
            }
            LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth()) {
                items(rows) { row ->
                    val base = row.toLong() * ROW
                    val bytes = remember(row, refresh) { paged.read(base, ROW) }
                    Row(Modifier.padding(horizontal = 8.dp, vertical = 1.dp)) {
                        Text(
                            String.format("%08X", base),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        for (i in 0 until ROW) {
                            val off = base + i
                            if (i < bytes.size) {
                                val edited = edits[off]
                                val v = (edited ?: bytes[i]).toInt() and 0xFF
                                Text(
                                    String.format("%02X", v),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = if (edited != null) FontWeight.Bold else FontWeight.Normal,
                                    color = when {
                                        edited != null -> Color(0xFFFF7043)
                                        v == 0 -> colors.secondaryText
                                        else -> MaterialTheme.colorScheme.onSurface
                                    },
                                    modifier = Modifier
                                        .background(if (off == selected) colors.selection else Color.Transparent)
                                        .clickable {
                                            selected = off
                                            if (canWrite) editing = off
                                        }
                                        .padding(end = 3.dp)
                                )
                            } else {
                                Text("   ", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                            }
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            buildString {
                                for (i in bytes.indices) append(printable(((edits[base + i] ?: bytes[i]).toInt()) and 0xFF))
                            },
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = colors.secondaryText,
                            maxLines = 1
                        )
                    }
                }
            }
            if (selected >= 0 && selected < paged.size) {
                val b = (edits[selected] ?: paged.read(selected, 1).firstOrNull() ?: 0.toByte()).toInt() and 0xFF
                Surface(color = colors.pathBar) {
                    Text(
                        String.format("0x%X  |  hex %02X  dec %d  oct %o  bin %s", selected, b, b, b, Integer.toBinaryString(b).padStart(8, '0')),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(8.dp)
                    )
                }
            }
        }
    }

    editing?.let { off ->
        var value by remember(off) {
            mutableStateOf(String.format("%02X", (edits[off] ?: paged.read(off, 1).firstOrNull() ?: 0.toByte()).toInt() and 0xFF))
        }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(String.format("0x%08X", off)) },
            text = {
                OutlinedTextField(
                    value = value,
                    onValueChange = { v -> value = v.filter { it.isLetterOrDigit() }.take(2).uppercase(Locale.ROOT) },
                    label = { Text("HEX") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    value.toIntOrNull(16)?.let { edits[off] = it.toByte() }
                    editing = null
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    if (showSearch) {
        var query by remember { mutableStateOf("") }
        var hexMode by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showSearch = false },
            title = { Text(stringResource(R.string.find)) },
            text = {
                Column {
                    OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    RadioRow(stringResource(R.string.search_text), !hexMode) { hexMode = false }
                    RadioRow(stringResource(R.string.search_hex), hexMode) { hexMode = true }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showSearch = false
                    val pattern: ByteArray? = if (hexMode) {
                        val clean = query.filter { !it.isWhitespace() }
                        if (clean.length % 2 != 0) null
                        else runCatching { ByteArray(clean.length / 2) { clean.substring(it * 2, it * 2 + 2).toInt(16).toByte() } }.getOrNull()
                    } else query.toByteArray()
                    if (pattern != null && pattern.isNotEmpty()) {
                        val from = selected + 1
                        scope.launch {
                            val found = withContext(Dispatchers.IO) { search(file, pattern, from.coerceAtLeast(0)) }
                            if (found >= 0) jumpTo(found) else AppState.toast(notFound)
                        }
                    }
                }) { Text(stringResource(R.string.find)) }
            },
            dismissButton = { TextButton(onClick = { showSearch = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

private fun search(file: File, pattern: ByteArray, from: Long): Long {
    RandomAccessFile(file, "r").use { r ->
        val size = r.length()
        val chunk = 1 shl 20
        val buf = ByteArray(chunk + pattern.size)
        var pos = from
        while (pos < size) {
            r.seek(pos)
            val n = r.read(buf, 0, minOf(buf.size.toLong(), size - pos).toInt())
            if (n <= 0) break
            var i = 0
            while (i <= n - pattern.size) {
                var match = true
                for (k in pattern.indices) if (buf[i + k] != pattern[k]) {
                    match = false
                    break
                }
                if (match) return pos + i
                i++
            }
            pos += chunk
        }
    }
    return -1
}
