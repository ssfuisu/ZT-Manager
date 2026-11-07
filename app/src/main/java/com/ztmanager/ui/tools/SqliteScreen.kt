package com.ztmanager.ui.tools

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ztmanager.R
import com.ztmanager.ui.AppState
import com.ztmanager.ui.browser.dialogs.InfoRow
import com.ztmanager.ui.theme.ZtTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private data class QueryResult(val columns: List<String>, val rows: List<List<String>>, val total: Long, val message: String?)

private const val PAGE_SIZE = 100

private fun cell(c: Cursor, i: Int): String = when (c.getType(i)) {
    Cursor.FIELD_TYPE_NULL -> "NULL"
    Cursor.FIELD_TYPE_BLOB -> "[BLOB " + (c.getBlob(i)?.size ?: 0) + " B]"
    else -> c.getString(i) ?: "NULL"
}

private fun readCursor(c: Cursor, limit: Int): Pair<List<String>, List<List<String>>> {
    val cols = c.columnNames.toList()
    val rows = ArrayList<List<String>>()
    while (c.moveToNext() && rows.size < limit) rows.add((0 until c.columnCount).map { cell(c, it) })
    return cols to rows
}

private fun quoteId(name: String) = "\"" + name.replace("\"", "\"\"") + "\""

/** SQLite browser: lists tables, pages through rows and runs SQL statements. */
@Composable
fun SqliteScreen(path: String) {
    val file = remember { File(path) }
    var db by remember { mutableStateOf<SQLiteDatabase?>(null) }
    var openError by remember { mutableStateOf<String?>(null) }
    var readOnly by remember { mutableStateOf(false) }
    var tables by remember { mutableStateOf<List<String>>(emptyList()) }
    var table by remember { mutableStateOf<String?>(null) }
    var page by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<QueryResult?>(null) }
    var sqlMode by remember { mutableStateOf(false) }
    var sql by remember { mutableStateOf("") }
    var rowDetail by remember { mutableStateOf<Pair<List<String>, List<String>>?>(null) }
    var version by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val okMsg = stringResource(R.string.sql_ok)

    LaunchedEffect(Unit) {
        try {
            val d = withContext(Dispatchers.IO) {
                try {
                    if (file.canWrite()) SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
                    else throw IllegalStateException()
                } catch (e: Exception) {
                    readOnly = true
                    SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
                }
            }
            db = d
            tables = withContext(Dispatchers.IO) {
                d.rawQuery("SELECT name FROM sqlite_master WHERE type IN ('table','view') ORDER BY name", null).use { c ->
                    val l = ArrayList<String>()
                    while (c.moveToNext()) l.add(c.getString(0))
                    l
                }
            }
            table = tables.firstOrNull()
        } catch (e: Exception) {
            openError = e.message ?: e.javaClass.simpleName
        }
    }
    DisposableEffect(Unit) { onDispose { db?.close() } }

    LaunchedEffect(table, page, version, db) {
        val d = db ?: return@LaunchedEffect
        val t = table ?: return@LaunchedEffect
        if (sqlMode) return@LaunchedEffect
        result = withContext(Dispatchers.IO) {
            try {
                val total = d.rawQuery("SELECT COUNT(*) FROM " + quoteId(t), null).use { c -> if (c.moveToFirst()) c.getLong(0) else 0L }
                d.rawQuery("SELECT * FROM " + quoteId(t) + " LIMIT $PAGE_SIZE OFFSET " + (page * PAGE_SIZE), null).use { c ->
                    val (cols, rows) = readCursor(c, PAGE_SIZE)
                    QueryResult(cols, rows, total, null)
                }
            } catch (e: Exception) {
                QueryResult(emptyList(), emptyList(), 0, e.message)
            }
        }
    }

    fun runSql() {
        val d = db ?: return
        val q = sql.trim().trimEnd(';')
        if (q.isEmpty()) return
        scope.launch {
            result = withContext(Dispatchers.IO) {
                try {
                    val head = q.substringBefore(' ').uppercase()
                    if (head in setOf("SELECT", "PRAGMA", "WITH", "EXPLAIN", "VALUES")) {
                        d.rawQuery(q, null).use { c ->
                            val (cols, rows) = readCursor(c, 1000)
                            QueryResult(cols, rows, rows.size.toLong(), null)
                        }
                    } else {
                        d.execSQL(q)
                        QueryResult(emptyList(), emptyList(), 0, okMsg)
                    }
                } catch (e: Exception) {
                    QueryResult(emptyList(), emptyList(), 0, e.message)
                }
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
                        Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.sqlite_tables, tables.size) + if (readOnly) "  " + stringResource(R.string.read_only) else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = ZtTheme.colors.secondaryText
                        )
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
            openError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                return@Column
            }
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                FilterChip(selected = sqlMode, onClick = { sqlMode = true; result = null }, label = { Text("SQL") }, modifier = Modifier.padding(end = 6.dp))
                tables.forEach { t ->
                    FilterChip(
                        selected = !sqlMode && t == table,
                        onClick = {
                            sqlMode = false
                            table = t
                            page = 0
                            version++
                        },
                        label = { Text(t) },
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
            }
            if (sqlMode) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
                    OutlinedTextField(
                        value = sql,
                        onValueChange = { sql = it },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        placeholder = { Text("SELECT * FROM ...") },
                        maxLines = 5,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { runSql() }) { Icon(Icons.Filled.PlayArrow, null, tint = MaterialTheme.colorScheme.primary) }
                }
            }
            val r = result
            if (r != null) {
                r.message?.let { Text(it, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), color = if (it == okMsg) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }
                if (!sqlMode && r.total > 0) {
                    val pages = ((r.total + PAGE_SIZE - 1) / PAGE_SIZE).toInt()
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Text(stringResource(R.string.sqlite_rows, r.total), style = MaterialTheme.typography.bodySmall, color = ZtTheme.colors.secondaryText, modifier = Modifier.weight(1f))
                        IconButton(onClick = { if (page > 0) page-- }, enabled = page > 0) { Icon(Icons.Filled.KeyboardArrowLeft, null) }
                        Text("${page + 1} / $pages", style = MaterialTheme.typography.bodySmall)
                        IconButton(onClick = { if (page + 1 < pages) page++ }, enabled = page + 1 < pages) { Icon(Icons.Filled.KeyboardArrowRight, null) }
                    }
                }
                if (r.columns.isNotEmpty()) ResultGrid(r) { row -> rowDetail = r.columns to row }
            }
        }
    }

    rowDetail?.let { (cols, row) ->
        AlertDialog(
            onDismissRequest = { rowDetail = null },
            title = { Text(stringResource(R.string.sqlite_row)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    cols.forEachIndexed { i, c -> InfoRow(c, row.getOrElse(i) { "" }) }
                }
            },
            confirmButton = { TextButton(onClick = { rowDetail = null }) { Text(stringResource(R.string.close)) } }
        )
    }
}

@Composable
private fun ResultGrid(r: QueryResult, onRow: (List<String>) -> Unit) {
    val h = rememberScrollState()
    val cellWidth = 150.dp
    Column(Modifier.fillMaxSize().horizontalScroll(h)) {
        Row(Modifier.background(ZtTheme.colors.pathBar)) {
            r.columns.forEach { c ->
                Text(
                    c,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(cellWidth).padding(8.dp)
                )
            }
        }
        HorizontalDivider(color = ZtTheme.colors.divider)
        SelectionContainer {
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(r.rows) { i, row ->
                    Row(
                        Modifier
                            .background(if (i % 2 == 1) MaterialTheme.colorScheme.surfaceContainerLow else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable { onRow(row) }
                    ) {
                        row.forEach { v ->
                            Text(
                                v,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = if (v == "NULL") ZtTheme.colors.secondaryText else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.width(cellWidth).padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
