package com.ztmanager.ui.tools

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ztmanager.R
import com.ztmanager.fs.FileEntry
import com.ztmanager.fs.FileOps
import com.ztmanager.fs.Format
import com.ztmanager.ui.AppState
import com.ztmanager.ui.common.FileIcon
import com.ztmanager.ui.theme.ZtTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private data class SizedEntry(val entry: FileEntry, val size: Long, val files: Int)

/** Shows which folders and files take the most space. */
@Composable
fun AnalyzerScreen(startDir: String) {
    var dir by remember { mutableStateOf(File(startDir)) }
    val rows = remember { mutableStateListOf<SizedEntry>() }
    var loading by remember { mutableStateOf(false) }
    val cache = remember { HashMap<String, Pair<Long, Int>>() }

    LaunchedEffect(dir) {
        rows.clear()
        loading = true
        val children = withContext(Dispatchers.IO) { dir.listFiles()?.toList() ?: emptyList() }
        for (c in children) {
            val (size, count) = cache[c.absolutePath] ?: withContext(Dispatchers.IO) {
                if (c.isDirectory) FileOps.stats(listOf(c)).let { it.size to it.files } else c.length() to 1
            }.also { cache[c.absolutePath] = it }
            rows.add(SizedEntry(FileEntry.fromFile(c, false), size, count))
            rows.sortByDescending { it.size }
        }
        loading = false
    }

    BackHandler {
        val parent = dir.parentFile
        if (dir.absolutePath != File(startDir).absolutePath && parent != null) dir = parent else AppState.pop()
    }

    val total = rows.sumOf { it.size }.coerceAtLeast(1)
    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ZtTheme.colors.toolbar),
                navigationIcon = { IconButton(onClick = { AppState.pop() }) { Icon(Icons.Filled.ArrowBack, null) } },
                title = {
                    Column {
                        Text(stringResource(R.string.tool_analyzer))
                        Text(dir.absolutePath + "  " + Format.size(rows.sumOf { it.size }), style = MaterialTheme.typography.bodySmall, color = ZtTheme.colors.secondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            LazyColumn(Modifier.fillMaxSize()) {
                items(rows, key = { it.entry.path }) { s ->
                    val frac = s.size.toFloat() / total
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = s.entry.isDirectory) { dir = File(s.entry.path) }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        FileIcon(s.entry, 36.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row {
                                Text(s.entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Text(Format.size(s.size), style = MaterialTheme.typography.bodySmall)
                            }
                            Spacer(Modifier.height(4.dp))
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(frac.coerceIn(0.005f, 1f))
                                        .background(if (s.entry.isDirectory) ZtTheme.colors.folder else MaterialTheme.colorScheme.primary)
                                )
                            }
                            Text(
                                String.format("%.1f%%", frac * 100) + if (s.entry.isDirectory) "  " + stringResource(R.string.n_files, s.files) else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = ZtTheme.colors.secondaryText
                            )
                        }
                    }
                }
            }
        }
    }
}
