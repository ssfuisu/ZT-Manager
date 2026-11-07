package com.ztmanager.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ztmanager.R
import com.ztmanager.data.Prefs
import com.ztmanager.fs.FileEntry
import com.ztmanager.fs.FileOps
import com.ztmanager.fs.Format
import com.ztmanager.fs.Trash
import com.ztmanager.task.TaskManager
import com.ztmanager.ui.AppState
import com.ztmanager.ui.common.FileIcon
import com.ztmanager.ui.theme.ZtTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

private class DupGroup(val size: Long, val files: List<File>)

private fun digest(f: File, limit: Long): String {
    val md = MessageDigest.getInstance("MD5")
    FileInputStream(f).use { input ->
        val buf = ByteArray(256 * 1024)
        var left = limit
        while (left > 0) {
            val n = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
            if (n < 0) break
            md.update(buf, 0, n)
            left -= n
        }
    }
    return com.ztmanager.fs.Format.hex(md.digest())
}

/** Finds identical files: same size, then same partial hash, then same full hash. */
@Composable
fun DuplicatesScreen(startDir: String) {
    val context = LocalContext.current
    var groups by remember { mutableStateOf<List<DupGroup>?>(null) }
    var status by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf(-1f) }
    var selection by remember { mutableStateOf<Set<String>>(emptySet()) }
    var run by remember { mutableIntStateOf(0) }
    val scanning = stringResource(R.string.dup_scanning)
    val comparing = stringResource(R.string.dup_comparing)

    LaunchedEffect(run) {
        groups = null
        selection = emptySet()
        val result = withContext(Dispatchers.IO) {
            val bySize = HashMap<Long, MutableList<File>>()
            val stack = ArrayDeque<File>()
            stack.addLast(File(startDir))
            var count = 0
            while (stack.isNotEmpty()) {
                coroutineContext.ensureActive()
                val dir = stack.removeLast()
                val children = dir.listFiles() ?: continue
                for (f in children) {
                    if (f.name == Trash.DIR_NAME) continue
                    if (f.isDirectory) {
                        if (!FileOps.isSymlink(f)) stack.addLast(f)
                    } else if (f.length() > 0) {
                        bySize.getOrPut(f.length()) { ArrayList() }.add(f)
                        count++
                    }
                }
                if (count % 200 == 0) withContext(Dispatchers.Main) { status = "$scanning $count" }
            }
            val candidates = bySize.filter { it.value.size > 1 }
            val total = candidates.values.sumOf { it.size }.coerceAtLeast(1)
            var done = 0
            val out = ArrayList<DupGroup>()
            for ((size, files) in candidates) {
                coroutineContext.ensureActive()
                val partial = files.groupBy { runCatching { digest(it, 64 * 1024) }.getOrDefault(it.path) }
                for (sameStart in partial.values.filter { it.size > 1 }) {
                    val full = if (size <= 64 * 1024) mapOf("" to sameStart)
                    else sameStart.groupBy { runCatching { digest(it, Long.MAX_VALUE) }.getOrDefault(it.path) }
                    full.values.filter { it.size > 1 }.forEach { out.add(DupGroup(size, it.sortedBy { f -> f.absolutePath })) }
                }
                done += files.size
                withContext(Dispatchers.Main) {
                    progress = done.toFloat() / total
                    status = comparing
                }
            }
            out.sortedByDescending { it.size * (it.files.size - 1) }
        }
        groups = result
        progress = -1f
    }

    val g = groups
    val wasted = g?.sumOf { it.size * (it.files.size - 1) } ?: 0L
    val selectedFiles = g?.flatMap { it.files }?.filter { it.absolutePath in selection } ?: emptyList()

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ZtTheme.colors.toolbar),
                navigationIcon = { IconButton(onClick = { AppState.pop() }) { Icon(Icons.Filled.ArrowBack, null) } },
                title = {
                    Column {
                        Text(stringResource(R.string.tool_duplicates))
                        Text(
                            if (g == null) status else stringResource(R.string.dup_summary, g.size, Format.size(wasted)),
                            style = MaterialTheme.typography.bodySmall,
                            color = ZtTheme.colors.secondaryText,
                            maxLines = 1
                        )
                    }
                },
                actions = {
                    if (g != null && g.isNotEmpty()) {
                        IconButton(onClick = {
                            // keep the first file of every group, select the rest
                            selection = g.flatMap { it.files.drop(1) }.map { it.absolutePath }.toSet()
                        }) { Icon(Icons.Filled.DoneAll, stringResource(R.string.dup_select_extra)) }
                    }
                }
            )
        },
        bottomBar = {
            if (selectedFiles.isNotEmpty()) {
                Surface(color = ZtTheme.colors.toolbar, tonalElevation = 3.dp) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(12.dp)
                    ) {
                        Text(
                            stringResource(R.string.n_selected, selectedFiles.size) + "  " + Format.size(selectedFiles.sumOf { it.length() }),
                            modifier = Modifier.weight(1f)
                        )
                        Button(onClick = {
                            val files = selectedFiles
                            val toTrash = Prefs.useTrash && files.all { Trash.supports(it) }
                            TaskManager.start(
                                context.getString(R.string.task_delete),
                                context.getString(if (toTrash) R.string.msg_moved_to_trash else R.string.msg_deleted),
                                onDone = { run++; AppState.refreshAll() }
                            ) { if (toTrash) Trash.moveToTrash(this, files) else FileOps.delete(this, files) }
                        }) {
                            Icon(Icons.Filled.Delete, null)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.action_delete))
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (g == null) {
                if (progress < 0) LinearProgressIndicator(Modifier.fillMaxWidth())
                else LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            }
            if (g != null && g.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.dup_none), color = ZtTheme.colors.secondaryText)
                }
            }
            LazyColumn(Modifier.fillMaxSize()) {
                g?.forEachIndexed { gi, group ->
                    item(key = "h$gi") {
                        Text(
                            stringResource(R.string.dup_group, group.files.size, Format.size(group.size)),
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ZtTheme.colors.pathBar)
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                    items(group.files, key = { "f$gi:" + it.absolutePath }) { f ->
                        val checked = f.absolutePath in selection
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selection = if (checked) selection - f.absolutePath else selection + f.absolutePath }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Checkbox(checked = checked, onCheckedChange = { c -> selection = if (c) selection + f.absolutePath else selection - f.absolutePath })
                            FileIcon(FileEntry.fromFile(f, false), 36.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(f.parent ?: "", style = MaterialTheme.typography.bodySmall, color = ZtTheme.colors.secondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}
