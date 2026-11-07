package com.ztmanager.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ztmanager.R
import com.ztmanager.data.Prefs
import com.ztmanager.ui.AppState
import com.ztmanager.ui.theme.ZtTheme
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream
import java.io.OutputStreamWriter

/** Persistent shell session (sh or su) with a simple line based UI. */
private class ShellSession(root: Boolean, dir: File?, val onLine: (String, Boolean) -> Unit) {
    private val process: Process = ProcessBuilder(if (root) "su" else "sh")
        .directory(dir?.takeIf { it.isDirectory })
        .redirectErrorStream(false)
        .start()
    private val writer = OutputStreamWriter(process.outputStream)

    init {
        reader(process.inputStream, false)
        reader(process.errorStream, true)
    }

    private fun reader(input: InputStream, err: Boolean) = Thread {
        try {
            input.bufferedReader().forEachLine { onLine(it, err) }
        } catch (_: Exception) {
        }
    }.apply { isDaemon = true; start() }

    fun send(cmd: String) {
        try {
            writer.write(cmd)
            writer.write("\n")
            writer.flush()
        } catch (e: Exception) {
            onLine(e.message ?: "error", true)
        }
    }

    fun close() {
        try {
            writer.close()
        } catch (_: Exception) {
        }
        process.destroy()
    }
}

@Composable
fun TerminalScreen() {
    val lines = remember { mutableStateListOf<Pair<String, Boolean>>() }
    var input by remember { mutableStateOf("") }
    var root by remember { mutableStateOf(Prefs.rootMode) }
    var session by remember { mutableStateOf<ShellSession?>(null) }
    var restart by remember { mutableStateOf(0) }
    val history = remember { mutableStateListOf<String>() }
    val listState = rememberLazyListState()
    val dir = AppState.active.currentDir

    DisposableEffect(root, restart) {
        val s = try {
            ShellSession(root, dir) { line, err ->
                AppState.scope.launch {
                    lines.add(line to err)
                    if (lines.size > 5000) lines.subList(0, 1000).clear()
                }
            }
        } catch (e: Exception) {
            lines.add((e.message ?: "Cannot start shell") to true)
            null
        }
        session = s
        lines.add(((if (root) "# su" else "$ sh") + "  " + (dir?.path ?: "")) to false)
        onDispose { s?.close() }
    }
    LaunchedEffect(lines.size) { if (lines.isNotEmpty()) listState.scrollToItem(lines.lastIndex) }

    fun run(cmd: String) {
        if (cmd.isBlank()) return
        lines.add(((if (root) "# " else "$ ") + cmd) to false)
        history.remove(cmd)
        history.add(0, cmd)
        session?.send(cmd)
        input = ""
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ZtTheme.colors.toolbar),
                navigationIcon = { IconButton(onClick = { AppState.pop() }) { Icon(Icons.Filled.ArrowBack, null) } },
                title = { Text(stringResource(R.string.tool_terminal)) },
                actions = {
                    FilterChip(selected = root, onClick = { root = !root }, label = { Text("su") })
                    IconButton(onClick = { restart++ }) { Icon(Icons.Filled.Refresh, null) }
                    IconButton(onClick = { lines.clear() }) { Icon(Icons.Filled.Delete, null) }
                }
            )
        },
        containerColor = Color(0xFF0D0F12)
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
        ) {
            SelectionContainer(Modifier.weight(1f)) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
                    itemsIndexed(lines) { _, (text, err) ->
                        Text(
                            text,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = if (err) Color(0xFFFF8A80) else Color(0xFFD7DDE3)
                        )
                    }
                }
            }
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp)
            ) {
                listOf("ls -la", "pwd", "cd ..", "df -h", "id", "getprop ro.product.model", "uname -a").forEach { c ->
                    AssistChip(onClick = { run(c) }, label = { Text(c, fontSize = 11.sp) }, modifier = Modifier.padding(end = 6.dp))
                }
                history.take(10).forEach { c ->
                    AssistChip(onClick = { input = c }, label = { Text(c, fontSize = 11.sp) }, modifier = Modifier.padding(end = 6.dp))
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .navigationBarsPadding()
                    .padding(6.dp)
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send, autoCorrectEnabled = false),
                    keyboardActions = KeyboardActions(onSend = { run(input) }),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = { run(input) }) { Icon(Icons.Filled.Send, null) }
            }
        }
    }
}
