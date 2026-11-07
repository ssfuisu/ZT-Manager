package com.ztmanager.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ztmanager.R
import com.ztmanager.data.Prefs
import com.ztmanager.fs.Format
import com.ztmanager.fs.RootShell
import com.ztmanager.ui.AppState
import com.ztmanager.ui.Screen
import com.ztmanager.ui.browser.DialogSpec
import com.ztmanager.ui.browser.Dialogs
import com.ztmanager.ui.browser.dialogs.RadioRow
import com.ztmanager.ui.theme.ZtTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.Charset

private val CHARSETS = listOf(
    "UTF-8", "UTF-16LE", "UTF-16BE", "ISO-8859-1", "US-ASCII", "windows-1252", "windows-1254",
    "windows-1251", "KOI8-R", "GBK", "Big5", "Shift_JIS", "EUC-KR", "ISO-8859-9"
)

private class LoadedText(val text: String, val charset: String, val crlf: Boolean, val bom: Boolean)

private const val MAX_EDIT_SIZE = 16L * 1024 * 1024

private fun decode(bytes: ByteArray, forced: String?): LoadedText {
    var charset = forced
    var offset = 0
    var bom = false
    if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
        if (charset == null) charset = "UTF-8"
        offset = 3
        bom = true
    } else if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
        if (charset == null) charset = "UTF-16LE"
        offset = 2
        bom = true
    } else if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
        if (charset == null) charset = "UTF-16BE"
        offset = 2
        bom = true
    }
    if (charset == null) {
        charset = if (isValidUtf8(bytes)) "UTF-8" else "windows-1252"
    }
    val s = String(bytes, offset, bytes.size - offset, Charset.forName(charset))
    val crlf = s.contains("\r\n")
    return LoadedText(if (crlf) s.replace("\r\n", "\n") else s, charset, crlf, bom)
}

private fun isValidUtf8(b: ByteArray): Boolean {
    var i = 0
    val n = minOf(b.size, 1 shl 20)
    while (i < n) {
        val c = b[i].toInt() and 0xFF
        val len = when {
            c < 0x80 -> 1
            c shr 5 == 0x6 -> 2
            c shr 4 == 0xE -> 3
            c shr 3 == 0x1E -> 4
            else -> return false
        }
        if (i + len > b.size) return i + len > n
        for (k in 1 until len) if ((b[i + k].toInt() and 0xC0) != 0x80) return false
        i += len
    }
    return true
}

@Composable
fun TextEditorScreen(path: String, readOnlyInitial: Boolean) {
    val context = LocalContext.current
    var file by remember { mutableStateOf(if (path.isEmpty()) null else File(path)) }
    var loadId by remember { mutableIntStateOf(0) }
    var forcedCharset by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf<LoadedText?>(if (path.isEmpty()) LoadedText("", "UTF-8", false, false) else null) }
    var error by remember { mutableStateOf<String?>(null) }
    var modified by remember { mutableStateOf(false) }
    var readOnly by remember { mutableStateOf(readOnlyInitial) }
    var editor by remember { mutableStateOf<CodeEditText?>(null) }
    var version by remember { mutableIntStateOf(0) }
    var cursor by remember { mutableIntStateOf(0) }
    var showSearch by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    var showEncoding by remember { mutableStateOf(false) }
    var showConvert by remember { mutableStateOf(false) }

    val dark = ZtTheme.colors.isDark
    val colors = ZtTheme.colors
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val savedMsg = stringResource(R.string.msg_saved)
    val saveFailed = stringResource(R.string.msg_save_failed)
    val tooLarge = stringResource(R.string.msg_file_too_large)

    LaunchedEffect(file, loadId) {
        val f = file ?: return@LaunchedEffect
        loaded = null
        error = null
        try {
            loaded = withContext(Dispatchers.IO) {
                if (f.length() > MAX_EDIT_SIZE) throw IllegalStateException(tooLarge)
                decode(f.readBytes(), forcedCharset)
            }
            modified = false
        } catch (e: Exception) {
            error = e.message ?: e.javaClass.simpleName
        }
    }

    LaunchedEffect(version, Prefs.editorHighlight, editor) {
        val ed = editor ?: return@LaunchedEffect
        val name = file?.name ?: ""
        val lang = SyntaxHighlighter.langFor(name)
        val text = ed.text ?: return@LaunchedEffect
        if (!Prefs.editorHighlight || lang == SyntaxHighlighter.Lang.NONE) {
            SyntaxHighlighter.clear(text)
            return@LaunchedEffect
        }
        delay(if (version == 0) 0 else 450)
        val snapshot = text.toString()
        val palette = if (dark) SyntaxHighlighter.darkPalette else SyntaxHighlighter.lightPalette
        val spans = withContext(Dispatchers.Default) { SyntaxHighlighter.compute(snapshot, lang, palette) }
        ed.text?.let { SyntaxHighlighter.apply(it, spans) }
    }

    fun write(target: File, onDone: () -> Unit = {}) {
        val ed = editor ?: return
        val l = loaded ?: return
        var content = ed.text?.toString() ?: ""
        if (l.crlf) content = content.replace("\n", "\r\n")
        AppState.scope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    val cs = Charset.forName(l.charset)
                    var bytes = content.toByteArray(cs)
                    if (l.bom) {
                        val bom = when (l.charset) {
                            "UTF-8" -> byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
                            "UTF-16LE" -> byteArrayOf(0xFF.toByte(), 0xFE.toByte())
                            "UTF-16BE" -> byteArrayOf(0xFE.toByte(), 0xFF.toByte())
                            else -> ByteArray(0)
                        }
                        bytes = bom + bytes
                    }
                    try {
                        target.writeBytes(bytes)
                        true
                    } catch (e: Exception) {
                        if (Prefs.rootMode) {
                            val tmp = File(context.cacheDir, "root/save.tmp")
                            tmp.parentFile?.mkdirs()
                            tmp.writeBytes(bytes)
                            RootShell.writeFromCache(tmp, target.absolutePath).also { tmp.delete() }
                        } else false
                    }
                } catch (e: Exception) {
                    false
                }
            }
            if (ok) {
                modified = false
                file = target
                AppState.toast(savedMsg)
                AppState.refreshAll()
                onDone()
            } else {
                AppState.toast(saveFailed, true)
            }
        }
    }

    val saveAsTitle = stringResource(R.string.action_save_as)
    val pathHint = stringResource(R.string.path)
    fun saveAs(onDone: () -> Unit = {}) {
        val start = file?.absolutePath ?: ((AppState.active.currentDir?.path ?: "/sdcard") + "/untitled.txt")
        Dialogs.show(DialogSpec.Input(saveAsTitle, start, pathHint) { p ->
            if (p.isNotBlank()) write(File(p.trim()), onDone)
        })
    }

    fun save(onDone: () -> Unit = {}) {
        val f = file
        if (f == null) saveAs(onDone) else write(f, onDone)
    }

    BackHandler {
        if (showSearch) showSearch = false
        else if (modified) confirmExit = true
        else AppState.pop()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.toolbar),
                navigationIcon = {
                    IconButton(onClick = { if (modified) confirmExit = true else AppState.pop() }) {
                        Icon(Icons.Filled.ArrowBack, null)
                    }
                },
                title = {
                    Column {
                        Text(
                            (if (modified) "* " else "") + (file?.name ?: stringResource(R.string.untitled)),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (readOnly) Text(stringResource(R.string.read_only), style = MaterialTheme.typography.bodySmall, color = colors.secondaryText)
                    }
                },
                actions = {
                    IconButton(onClick = { editor?.undo() }, enabled = !readOnly) { Icon(Icons.Filled.Undo, null) }
                    IconButton(onClick = { editor?.redo() }, enabled = !readOnly) { Icon(Icons.Filled.Redo, null) }
                    IconButton(onClick = { showSearch = !showSearch }) { Icon(Icons.Filled.Search, null) }
                    if (!readOnly) IconButton(onClick = { save() }) { Icon(Icons.Filled.Save, null) }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, null) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(saveAsTitle) }, onClick = { menu = false; saveAs() })
                        val gotoTitle = stringResource(R.string.go_to_line)
                        val lineHint = stringResource(R.string.line_number)
                        DropdownMenuItem(text = { Text(gotoTitle) }, onClick = {
                            menu = false
                            Dialogs.show(DialogSpec.Input(gotoTitle, "", lineHint) { v ->
                                val n = v.trim().toIntOrNull()
                                val ed = editor
                                if (n != null && ed != null) {
                                    ed.requestFocus()
                                    ed.setSelection(ed.offsetOfLine(n))
                                }
                            })
                        })
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.word_wrap)) },
                            trailingIcon = { Checkbox(Prefs.editorWordWrap, null) },
                            onClick = { Prefs.editorWordWrap = !Prefs.editorWordWrap }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.line_numbers)) },
                            trailingIcon = { Checkbox(Prefs.editorLineNumbers, null) },
                            onClick = { Prefs.editorLineNumbers = !Prefs.editorLineNumbers }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.syntax_highlight)) },
                            trailingIcon = { Checkbox(Prefs.editorHighlight, null) },
                            onClick = { Prefs.editorHighlight = !Prefs.editorHighlight }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.read_only)) },
                            trailingIcon = { Checkbox(readOnly, null) },
                            onClick = { readOnly = !readOnly }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text(stringResource(R.string.font_larger)) }, onClick = {
                            Prefs.editorFontSize = (Prefs.editorFontSize + 1).coerceAtMost(32)
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.font_smaller)) }, onClick = {
                            Prefs.editorFontSize = (Prefs.editorFontSize - 1).coerceAtLeast(8)
                        })
                        if (file != null) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.encoding)) }, onClick = { menu = false; showEncoding = true })
                            DropdownMenuItem(text = { Text(stringResource(R.string.convert_encoding)) }, onClick = { menu = false; showConvert = true })
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.line_ending_crlf)) },
                                trailingIcon = { Checkbox(loaded?.crlf == true, null) },
                                onClick = {
                                    loaded?.let { l ->
                                        loaded = LoadedText(l.text, l.charset, !l.crlf, l.bom)
                                        modified = true
                                    }
                                }
                            )
                            DropdownMenuItem(text = { Text(stringResource(R.string.open_as_hex)) }, onClick = {
                                menu = false
                                file?.let { AppState.replaceTop(Screen.Hex(it.absolutePath)) }
                            })
                        }
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
        ) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val l = loaded
                when {
                    error != null -> Text(error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                    l == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    else -> key(loadId, file) {
                        AndroidView(
                            factory = { ctx ->
                                CodeEditText(ctx).also { ed ->
                                    ed.setContent(l.text)
                                    ed.onTextEdited = {
                                        if (!modified) modified = true
                                        version++
                                    }
                                    ed.onCursorMoved = { cursor = it }
                                    editor = ed
                                }
                            },
                            update = { ed ->
                                ed.setColors(colors.gutter.toArgb(), colors.gutterText.toArgb(), textColor, colors.divider.toArgb())
                                ed.setFontSizeSp(Prefs.editorFontSize.toFloat())
                                ed.setWordWrap(Prefs.editorWordWrap)
                                ed.showLineNumbers = Prefs.editorLineNumbers
                                ed.autoIndent = Prefs.editorAutoIndent
                                if (ed.readOnly != readOnly) ed.readOnly = readOnly
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
            if (showSearch) editor?.let { SearchBar(it) { showSearch = false } }
            StatusBar(editor, cursor, loaded, version)
        }
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text(stringResource(R.string.unsaved_changes)) },
            text = { Text(stringResource(R.string.unsaved_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmExit = false
                    save { AppState.pop() }
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { confirmExit = false; AppState.pop() }) { Text(stringResource(R.string.discard)) }
                    TextButton(onClick = { confirmExit = false }) { Text(stringResource(R.string.cancel)) }
                }
            }
        )
    }

    if (showConvert) {
        AlertDialog(
            onDismissRequest = { showConvert = false },
            title = { Text(stringResource(R.string.convert_encoding)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.convert_encoding_help), style = MaterialTheme.typography.bodySmall, color = colors.secondaryText)
                    CHARSETS.forEach { cs ->
                        RadioRow(cs, loaded?.charset == cs) {
                            showConvert = false
                            loaded?.let { l ->
                                loaded = LoadedText(l.text, cs, l.crlf, l.bom && cs.startsWith("UTF"))
                                modified = true
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showConvert = false }) { Text(stringResource(R.string.close)) } }
        )
    }

    if (showEncoding) {
        AlertDialog(
            onDismissRequest = { showEncoding = false },
            title = { Text(stringResource(R.string.encoding)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    CHARSETS.forEach { cs ->
                        RadioRow(cs, loaded?.charset == cs) {
                            showEncoding = false
                            forcedCharset = cs
                            loadId++
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showEncoding = false }) { Text(stringResource(R.string.close)) } }
        )
    }
}

@Composable
private fun StatusBar(editor: CodeEditText?, cursor: Int, loaded: LoadedText?, version: Int) {
    val ed = editor ?: return
    val (line, col) = remember(cursor, version) { ed.lineColOf(cursor) }
    Surface(color = ZtTheme.colors.pathBar) {
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text("Ln $line, Col $col", style = MaterialTheme.typography.labelSmall, color = ZtTheme.colors.secondaryText)
            Spacer(Modifier.weight(1f))
            Text(
                (loaded?.charset ?: "") + "  " + (if (loaded?.crlf == true) "CRLF" else "LF") + "  " +
                    ed.lineTotal + " L  " + Format.size((ed.text?.length ?: 0).toLong()),
                style = MaterialTheme.typography.labelSmall,
                color = ZtTheme.colors.secondaryText
            )
        }
    }
}

@Composable
private fun SearchBar(editor: CodeEditText, onClose: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    var caseSensitive by remember { mutableStateOf(false) }
    var regex by remember { mutableStateOf(false) }
    var showReplace by remember { mutableStateOf(false) }
    val notFound = stringResource(R.string.msg_not_found)

    fun pattern(): Regex? = try {
        val opts = if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
        Regex(if (regex) query else Regex.escape(query), opts)
    } catch (e: Exception) {
        null
    }

    fun find(forward: Boolean) {
        if (query.isEmpty()) return
        val text = editor.text?.toString() ?: return
        val r = pattern() ?: return
        val from = if (forward) editor.selectionEnd else editor.selectionStart
        val match = if (forward) {
            r.find(text, from.coerceIn(0, text.length)) ?: r.find(text, 0)
        } else {
            val all = r.findAll(text).toList()
            all.lastOrNull { it.range.first < from } ?: all.lastOrNull()
        }
        if (match == null) {
            AppState.toast(notFound)
            return
        }
        editor.requestFocus()
        editor.setSelection(match.range.first, match.range.last + 1)
    }

    fun replaceOne() {
        val ed = editor.text ?: return
        val s = editor.selectionStart
        val e = editor.selectionEnd
        if (s != e && !editor.readOnly) {
            val selected = ed.subSequence(s, e).toString()
            val r = pattern()
            if (r != null && r.matches(selected)) {
                val rep = if (regex) r.replace(selected, replacement) else replacement
                ed.replace(s, e, rep)
                editor.setSelection(s + rep.length)
            }
        }
        find(true)
    }

    fun replaceAll() {
        if (editor.readOnly || query.isEmpty()) return
        val ed = editor.text ?: return
        val r = pattern() ?: return
        val text = ed.toString()
        val out = if (regex) r.replace(text, replacement) else r.replace(text, Regex.escapeReplacement(replacement))
        if (out != text) ed.replace(0, ed.length, out)
    }

    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 3.dp) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.find)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { find(true) }),
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { find(false) }) { Icon(Icons.Filled.KeyboardArrowUp, null) }
                IconButton(onClick = { find(true) }) { Icon(Icons.Filled.KeyboardArrowDown, null) }
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, null) }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.horizontalScroll(rememberScrollState())) {
                FilterChip(selected = caseSensitive, onClick = { caseSensitive = !caseSensitive }, label = { Text("Aa") })
                Spacer(Modifier.width(6.dp))
                FilterChip(selected = regex, onClick = { regex = !regex }, label = { Text(".*") })
                Spacer(Modifier.width(6.dp))
                FilterChip(selected = showReplace, onClick = { showReplace = !showReplace }, label = { Text(stringResource(R.string.replace)) })
            }
            if (showReplace) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = replacement,
                        onValueChange = { replacement = it },
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.replace_with)) },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { replaceOne() }) { Text(stringResource(R.string.replace)) }
                    TextButton(onClick = { replaceAll() }) { Text(stringResource(R.string.replace_all)) }
                }
            }
        }
    }
}
