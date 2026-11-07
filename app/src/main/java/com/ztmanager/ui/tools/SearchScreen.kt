package com.ztmanager.ui.tools

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ztmanager.R
import com.ztmanager.fs.FileEntry
import com.ztmanager.fs.FileType
import com.ztmanager.fs.Format
import com.ztmanager.ui.AppState
import com.ztmanager.ui.browser.Opener
import com.ztmanager.ui.common.FileIcon
import com.ztmanager.ui.theme.ZtTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

private fun wildcardToRegex(q: String, caseSensitive: Boolean): Regex {
    val hasWildcard = q.contains('*') || q.contains('?')
    val body = if (hasWildcard) {
        "^" + q.split("*").joinToString(".*") { part -> part.split("?").joinToString(".") { Regex.escape(it) } } + "$"
    } else ".*" + Regex.escape(q) + ".*"
    return if (caseSensitive) Regex(body) else Regex(body, RegexOption.IGNORE_CASE)
}

@Composable
fun SearchScreen(startDir: String) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var caseSensitive by remember { mutableStateOf(false) }
    var includeHidden by remember { mutableStateOf(false) }
    var onlyFolders by remember { mutableStateOf(false) }
    var minSizeMb by remember { mutableStateOf("") }
    var withinDays by remember { mutableStateOf("") }
    var showReplace by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var scanned by remember { mutableStateOf(0) }
    var current by remember { mutableStateOf("") }
    val results = remember { mutableStateListOf<FileEntry>() }
    val scope = rememberCoroutineScope()
    var job by remember { mutableStateOf<Job?>(null) }

    fun start() {
        job?.cancel()
        results.clear()
        scanned = 0
        if (query.isBlank() && content.isBlank() && minSizeMb.isBlank() && withinDays.isBlank()) return
        val nameRegex = if (query.isBlank()) null else wildcardToRegex(query.trim(), caseSensitive)
        val needle = content
        val minBytes = (minSizeMb.toLongOrNull() ?: 0L) * 1024 * 1024
        val since = withinDays.toLongOrNull()?.let { System.currentTimeMillis() - it * 24 * 3600 * 1000 } ?: 0L
        running = true
        job = scope.launch {
            withContext(Dispatchers.IO) {
                val stack = ArrayDeque<File>()
                stack.addLast(File(startDir))
                var count = 0
                while (stack.isNotEmpty()) {
                    coroutineContext.ensureActive()
                    val dir = stack.removeLast()
                    val children = dir.listFiles() ?: continue
                    for (f in children) {
                        if (!includeHidden && f.name.startsWith(".")) continue
                        count++
                        if (f.isDirectory) stack.addLast(f)
                        val nameOk = nameRegex?.matches(f.name) ?: true
                        if (!nameOk) continue
                        if (onlyFolders && !f.isDirectory) continue
                        if (minBytes > 0 && (f.isDirectory || f.length() < minBytes)) continue
                        if (since > 0 && f.lastModified() < since) continue
                        if (needle.isNotEmpty()) {
                            if (f.isDirectory || f.length() > 8L * 1024 * 1024 || !FileType.isTextLike(f.name) && FileType.of(f.name, false) != FileType.UNKNOWN) continue
                            val hit = try {
                                f.bufferedReader().useLines { lines -> lines.any { it.contains(needle, !caseSensitive) } }
                            } catch (e: Exception) {
                                false
                            }
                            if (!hit) continue
                        }
                        val e = FileEntry.fromFile(f, false)
                        withContext(Dispatchers.Main) {
                            if (results.size < 5000) results.add(e)
                        }
                    }
                    if (count % 50 == 0) withContext(Dispatchers.Main) {
                        scanned = count
                        current = dir.absolutePath
                    }
                }
                withContext(Dispatchers.Main) { scanned = count }
            }
            running = false
        }
        job?.invokeOnCompletion { running = false }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ZtTheme.colors.toolbar),
                navigationIcon = { IconButton(onClick = { job?.cancel(); AppState.pop() }) { Icon(Icons.Filled.ArrowBack, null) } },
                title = {
                    Column {
                        Text(stringResource(R.string.tool_search))
                        Text(startDir, style = MaterialTheme.typography.bodySmall, color = ZtTheme.colors.secondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                actions = {
                    if (running) IconButton(onClick = { job?.cancel() }) { Icon(Icons.Filled.Stop, null) }
                    else IconButton(onClick = { start() }) { Icon(Icons.Filled.Search, null) }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Column(Modifier.padding(horizontal = 12.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.search_name_hint)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { start() }),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.search_content_hint)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { start() }),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(Modifier.padding(vertical = 4.dp)) {
                    FilterChip(selected = caseSensitive, onClick = { caseSensitive = !caseSensitive }, label = { Text("Aa") })
                    Spacer(Modifier.width(6.dp))
                    FilterChip(selected = includeHidden, onClick = { includeHidden = !includeHidden }, label = { Text(stringResource(R.string.hidden)) })
                    Spacer(Modifier.width(6.dp))
                    FilterChip(selected = onlyFolders, onClick = { onlyFolders = !onlyFolders }, label = { Text(stringResource(R.string.folders_only)) })
                }
                Row {
                    OutlinedTextField(
                        value = minSizeMb,
                        onValueChange = { minSizeMb = it.filter { c -> c.isDigit() }.take(6) },
                        singleLine = true,
                        label = { Text(stringResource(R.string.search_min_size)) },
                        keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        modifier = Modifier.weight(1f).padding(end = 6.dp)
                    )
                    OutlinedTextField(
                        value = withinDays,
                        onValueChange = { withinDays = it.filter { c -> c.isDigit() }.take(5) },
                        singleLine = true,
                        label = { Text(stringResource(R.string.search_within_days)) },
                        keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                if (!running && content.isNotEmpty() && results.any { !it.isDirectory }) {
                    androidx.compose.material3.TextButton(onClick = { showReplace = true }) {
                        Text(stringResource(R.string.replace_in_results, results.count { !it.isDirectory }))
                    }
                }
                Text(
                    stringResource(R.string.search_status, results.size, scanned),
                    style = MaterialTheme.typography.bodySmall,
                    color = ZtTheme.colors.secondaryText
                )
            }
            if (running) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp))
                Text(current, style = MaterialTheme.typography.labelSmall, color = ZtTheme.colors.secondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 12.dp))
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(results, key = { it.path }) { e ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    job?.cancel()
                                    AppState.pop()
                                    Opener.open(context, AppState.active, e)
                                },
                                onLongClick = {
                                    job?.cancel()
                                    AppState.pop()
                                    File(e.path).parent?.let { AppState.active.navigate(it) }
                                }
                            )
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        FileIcon(e, 36.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(e.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                (File(e.path).parent ?: "") + if (e.isDirectory) "" else "  " + Format.size(e.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = ZtTheme.colors.secondaryText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }

    if (showReplace) {
        var replacement by remember { mutableStateOf("") }
        val doneFmt = stringResource(R.string.msg_replaced_in)
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showReplace = false },
            title = { Text(stringResource(R.string.replace_in_files)) },
            text = {
                Column {
                    Text(stringResource(R.string.replace_in_files_help, content), style = MaterialTheme.typography.bodySmall, color = ZtTheme.colors.secondaryText)
                    OutlinedTextField(
                        value = replacement,
                        onValueChange = { replacement = it },
                        label = { Text(stringResource(R.string.replace_with)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showReplace = false
                    val files = results.filter { !it.isDirectory }.map { File(it.path) }
                    val needle = content
                    val ignore = !caseSensitive
                    var changed = 0
                    com.ztmanager.task.TaskManager.start(
                        title = context.getString(R.string.replace_in_files),
                        onDone = { err -> if (err == null) AppState.toast(String.format(doneFmt, changed)) }
                    ) {
                        files.forEachIndexed { i, f ->
                            checkCancelled()
                            setItems(i, files.size)
                            setMessage(f.name)
                            val text = f.readText()
                            val out = text.replace(needle, replacement, ignoreCase = ignore)
                            if (out != text) {
                                f.writeText(out)
                                changed++
                            }
                        }
                    }
                }) { Text(stringResource(R.string.replace_all)) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { showReplace = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}
