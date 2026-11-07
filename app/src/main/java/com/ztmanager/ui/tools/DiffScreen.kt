package com.ztmanager.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ztmanager.R
import com.ztmanager.ui.AppState
import com.ztmanager.ui.theme.ZtTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private enum class DiffKind { SAME, ADDED, REMOVED }
private data class DiffLine(val kind: DiffKind, val a: Int, val b: Int, val text: String)
private data class DiffResult(val lines: List<DiffLine>, val identical: Boolean, val note: String?)

private const val MAX_LINES = 4000

/** Line based diff using the longest common subsequence. */
private fun diff(a: File, b: File): DiffResult {
    val ba = a.readBytes()
    val bb = b.readBytes()
    if (ba.contentEquals(bb)) return DiffResult(emptyList(), true, null)
    val la = String(ba).replace("\r\n", "\n").split('\n')
    val lb = String(bb).replace("\r\n", "\n").split('\n')
    if (la.size > MAX_LINES || lb.size > MAX_LINES) {
        val first = (0 until minOf(la.size, lb.size)).firstOrNull { la[it] != lb[it] } ?: minOf(la.size, lb.size)
        return DiffResult(emptyList(), false, "first difference at line ${first + 1}")
    }
    val n = la.size
    val m = lb.size
    val dp = Array(n + 1) { IntArray(m + 1) }
    for (i in n - 1 downTo 0) for (j in m - 1 downTo 0) {
        dp[i][j] = if (la[i] == lb[j]) dp[i + 1][j + 1] + 1 else maxOf(dp[i + 1][j], dp[i][j + 1])
    }
    val out = ArrayList<DiffLine>()
    var i = 0
    var j = 0
    while (i < n && j < m) {
        when {
            la[i] == lb[j] -> { out.add(DiffLine(DiffKind.SAME, i + 1, j + 1, la[i])); i++; j++ }
            dp[i + 1][j] >= dp[i][j + 1] -> { out.add(DiffLine(DiffKind.REMOVED, i + 1, 0, la[i])); i++ }
            else -> { out.add(DiffLine(DiffKind.ADDED, 0, j + 1, lb[j])); j++ }
        }
    }
    while (i < n) { out.add(DiffLine(DiffKind.REMOVED, i + 1, 0, la[i])); i++ }
    while (j < m) { out.add(DiffLine(DiffKind.ADDED, 0, j + 1, lb[j])); j++ }
    return DiffResult(out, false, null)
}

@Composable
fun DiffScreen(pathA: String, pathB: String) {
    val result by produceState<Result<DiffResult>?>(null) {
        value = withContext(Dispatchers.Default) { runCatching { diff(File(pathA), File(pathB)) } }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ZtTheme.colors.toolbar),
                navigationIcon = { IconButton(onClick = { AppState.pop() }) { Icon(Icons.Filled.ArrowBack, null) } },
                title = {
                    Column {
                        Text(stringResource(R.string.action_compare))
                        Text(File(pathA).name + "  vs  " + File(pathB).name, style = MaterialTheme.typography.bodySmall, color = ZtTheme.colors.secondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            val r = result
            when {
                r == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                r.isFailure -> Text(r.exceptionOrNull()?.message ?: "Error", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
                r.getOrThrow().identical -> Text(stringResource(R.string.files_identical), Modifier.padding(16.dp))
                r.getOrThrow().note != null -> Text(stringResource(R.string.files_differ) + ": " + r.getOrThrow().note, Modifier.padding(16.dp))
                else -> {
                    val lines = r.getOrThrow().lines
                    val h = rememberScrollState()
                    SelectionContainer {
                        LazyColumn(Modifier.fillMaxSize().horizontalScroll(h)) {
                            items(lines) { l ->
                                val bg = when (l.kind) {
                                    DiffKind.ADDED -> Color(0x3343A047)
                                    DiffKind.REMOVED -> Color(0x33E53935)
                                    DiffKind.SAME -> Color.Transparent
                                }
                                Row(Modifier.background(bg).padding(horizontal = 6.dp)) {
                                    Text(if (l.a > 0) l.a.toString() else "", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = ZtTheme.colors.secondaryText, modifier = Modifier.width(40.dp))
                                    Text(if (l.b > 0) l.b.toString() else "", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = ZtTheme.colors.secondaryText, modifier = Modifier.width(40.dp))
                                    Text(
                                        when (l.kind) { DiffKind.ADDED -> "+ "; DiffKind.REMOVED -> "- "; else -> "  " } + l.text,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

