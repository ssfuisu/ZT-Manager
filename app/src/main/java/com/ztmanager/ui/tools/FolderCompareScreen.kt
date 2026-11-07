package com.ztmanager.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ztmanager.R
import com.ztmanager.fs.FileOps
import com.ztmanager.fs.Format
import com.ztmanager.task.TaskManager
import com.ztmanager.ui.AppState
import com.ztmanager.ui.browser.NaturalOrder
import com.ztmanager.ui.theme.ZtTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

private enum class DiffState { LEFT_ONLY, RIGHT_ONLY, DIFFERENT, SAME }

private data class CompareRow(
    val rel: String,
    val state: DiffState,
    val leftSize: Long,
    val rightSize: Long,
    val leftTime: Long,
    val rightTime: Long
)

private fun scan(root: File, recursive: Boolean): Map<String, File> {
    val out = HashMap<String, File>()
    val base = root.absolutePath.length + 1
    if (recursive) {
        root.walkTopDown().filter { it.isFile }.forEach { out[it.absolutePath.substring(base)] = it }
    } else {
        root.listFiles()?.filter { it.isFile }?.forEach { out[it.name] = it }
    }
    return out
}

private fun compare(left: File, right: File, recursive: Boolean): List<CompareRow> {
    val l = scan(left, recursive)
    val r = scan(right, recursive)
    return (l.keys + r.keys).map { rel ->
        val a = l[rel]
        val b = r[rel]
        val state = when {
            a == null -> DiffState.RIGHT_ONLY
            b == null -> DiffState.LEFT_ONLY
            a.length() != b.length() || abs(a.lastModified() - b.lastModified()) > 2000 -> DiffState.DIFFERENT
            else -> DiffState.SAME
        }
        CompareRow(rel, state, a?.length() ?: -1, b?.length() ?: -1, a?.lastModified() ?: 0, b?.lastModified() ?: 0)
    }.sortedWith { x, y -> NaturalOrder.compare(x.rel, y.rel) }
}

/** Compares two folders (usually the two panes) and copies missing or changed files. */
@Composable
fun FolderCompareScreen(leftPath: String, rightPath: String) {
    val context = LocalContext.current
    var left by remember { mutableStateOf(File(leftPath)) }
    var right by remember { mutableStateOf(File(rightPath)) }
    var recursive by remember { mutableStateOf(true) }
    var showSame by remember { mutableStateOf(false) }
    var rows by remember { mutableStateOf<List<CompareRow>?>(null) }
    var selection by remember { mutableStateOf<Set<String>>(emptySet()) }
    var run by remember { mutableIntStateOf(0) }

    LaunchedEffect(left, right, recursive, run) {
        rows = null
        selection = emptySet()
        rows = withContext(Dispatchers.IO) { compare(left, right, recursive) }
    }

    val all = rows
    val visible = all?.filter { showSame || it.state != DiffState.SAME } ?: emptyList()
    val selected = visible.filter { it.rel in selection }

    fun copy(toRight: Boolean) {
        val src = if (toRight) left else right
        val dst = if (toRight) right else left
        val items = selected.filter { if (toRight) it.state != DiffState.RIGHT_ONLY else it.state != DiffState.LEFT_ONLY }
        if (items.isEmpty()) return
        TaskManager.start(
            context.getString(R.string.task_copy),
            context.getString(R.string.msg_done),
            onDone = { run++; AppState.refreshAll() }
        ) {
            items.forEachIndexed { i, row ->
                checkCancelled()
                setItems(i, items.size)
                val from = File(src, row.rel)
                val target = File(dst, row.rel)
                target.parentFile?.mkdirs()
                setMessage(row.rel)
                from.inputStream().use { input -> target.outputStream().use { out -> FileOps.pipe(this, input, out) } }
                target.setLastModified(from.lastModified())
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ZtTheme.colors.toolbar),
                navigationIcon = { IconButton(onClick = { AppState.pop() }) { Icon(Icons.Filled.ArrowBack, null) } },
                title = {
                    Column {
                        Text(stringResource(R.string.tool_folder_compare))
                        if (all != null) {
                            Text(
                                stringResource(
                                    R.string.compare_summary,
                                    all.count { it.state == DiffState.LEFT_ONLY },
                                    all.count { it.state == DiffState.RIGHT_ONLY },
                                    all.count { it.state == DiffState.DIFFERENT },
                                    all.count { it.state == DiffState.SAME }
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = ZtTheme.colors.secondaryText,
                                maxLines = 1
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { val t = left; left = right; right = t }) { Icon(Icons.Filled.SwapHoriz, null) }
                    IconButton(onClick = { selection = visible.map { it.rel }.toSet() }) { Icon(Icons.Filled.SelectAll, null) }
                    IconButton(onClick = { run++ }) { Icon(Icons.Filled.Refresh, null) }
                }
            )
        },
        bottomBar = {
            if (selected.isNotEmpty()) {
                Surface(color = ZtTheme.colors.toolbar, tonalElevation = 3.dp) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(12.dp)
                    ) {
                        Button(onClick = { copy(false) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.ArrowBack, null)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.compare_copy_left))
                        }
                        Button(onClick = { copy(true) }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.compare_copy_right))
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Filled.ArrowForward, null)
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                Text("A: " + left.absolutePath, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("B: " + right.absolutePath, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    FilterChip(selected = recursive, onClick = { recursive = !recursive }, label = { Text(stringResource(R.string.compare_subfolders)) }, modifier = Modifier.padding(end = 6.dp))
                    FilterChip(selected = showSame, onClick = { showSame = !showSame }, label = { Text(stringResource(R.string.compare_show_same)) })
                }
            }
            if (all == null) LinearProgressIndicator(Modifier.fillMaxWidth())
            else if (visible.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.compare_identical), color = ZtTheme.colors.secondaryText)
                }
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(visible, key = { it.rel }) { row ->
                    val checked = row.rel in selection
                    val (label, color) = when (row.state) {
                        DiffState.LEFT_ONLY -> stringResource(R.string.compare_only_a) to Color(0xFF42A5F5)
                        DiffState.RIGHT_ONLY -> stringResource(R.string.compare_only_b) to Color(0xFFAB47BC)
                        DiffState.DIFFERENT -> stringResource(R.string.compare_different) to Color(0xFFFFA726)
                        DiffState.SAME -> stringResource(R.string.compare_same) to Color(0xFF66BB6A)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selection = if (checked) selection - row.rel else selection + row.rel }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Checkbox(checked = checked, onCheckedChange = { c -> selection = if (c) selection + row.rel else selection - row.rel })
                        Column(Modifier.weight(1f)) {
                            Text(row.rel, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                (if (row.leftSize >= 0) "A " + Format.size(row.leftSize) + " " + Format.date(row.leftTime) else "") +
                                    (if (row.leftSize >= 0 && row.rightSize >= 0) "   " else "") +
                                    (if (row.rightSize >= 0) "B " + Format.size(row.rightSize) + " " + Format.date(row.rightTime) else ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = ZtTheme.colors.secondaryText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier
                                .background(color, androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
