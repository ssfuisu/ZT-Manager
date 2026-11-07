package com.ztmanager.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.ztmanager.fs.FileEntry
import com.ztmanager.fs.Format
import com.ztmanager.fs.Trash
import com.ztmanager.task.TaskManager
import com.ztmanager.ui.AppState
import com.ztmanager.ui.common.FileIcon
import com.ztmanager.ui.theme.ZtTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun TrashScreen() {
    val context = LocalContext.current
    var items by remember { mutableStateOf<List<Trash.Item>?>(null) }
    var selection by remember { mutableStateOf<Set<String>>(emptySet()) }
    var reload by remember { mutableIntStateOf(0) }
    var confirmEmpty by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<List<Trash.Item>?>(null) }

    LaunchedEffect(reload) {
        items = withContext(Dispatchers.IO) { Trash.list() }
        selection = selection.filter { id -> items?.any { it.id == id } == true }.toSet()
    }

    fun restore(list: List<Trash.Item>) {
        selection = emptySet()
        TaskManager.start(
            context.getString(R.string.task_restore),
            context.getString(R.string.msg_restored),
            onDone = { reload++; AppState.refreshAll() }
        ) { Trash.restore(this, list) }
    }

    fun deleteForever(list: List<Trash.Item>) {
        selection = emptySet()
        TaskManager.start(
            context.getString(R.string.task_delete),
            context.getString(R.string.msg_deleted),
            onDone = { reload++ }
        ) { Trash.delete(this, list) }
    }

    val all = items
    val selected = all?.filter { it.id in selection } ?: emptyList()

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ZtTheme.colors.toolbar),
                navigationIcon = {
                    if (selection.isNotEmpty()) IconButton(onClick = { selection = emptySet() }) { Icon(Icons.Filled.Close, null) }
                    else IconButton(onClick = { AppState.pop() }) { Icon(Icons.Filled.ArrowBack, null) }
                },
                title = {
                    Column {
                        Text(if (selection.isNotEmpty()) stringResource(R.string.n_selected, selection.size) else stringResource(R.string.tool_trash))
                        if (all != null) {
                            Text(
                                stringResource(R.string.n_items, all.size) + "  " + Format.size(all.sumOf { it.size }),
                                style = MaterialTheme.typography.bodySmall,
                                color = ZtTheme.colors.secondaryText
                            )
                        }
                    }
                },
                actions = {
                    if (all != null && all.isNotEmpty()) {
                        IconButton(onClick = { selection = all.map { it.id }.toSet() }) { Icon(Icons.Filled.SelectAll, null) }
                        IconButton(onClick = { confirmEmpty = true }) { Icon(Icons.Filled.DeleteForever, stringResource(R.string.empty_trash)) }
                    }
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
                        Button(onClick = { restore(selected) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Restore, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.action_restore))
                        }
                        OutlinedButton(onClick = { confirmDelete = selected }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Delete, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.delete_permanently))
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                all == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                all.isEmpty() -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Delete, null, tint = ZtTheme.colors.secondaryText, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.trash_empty), color = ZtTheme.colors.secondaryText)
                }
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(all, key = { it.id }) { item ->
                        val isSel = item.id in selection
                        val entry = FileEntry(
                            name = item.name, path = item.file.absolutePath, isDirectory = item.isDirectory,
                            size = item.size, lastModified = item.deletedAt
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isSel) ZtTheme.colors.selection else Color.Transparent)
                                .combinedClickable(
                                    onClick = {
                                        selection = if (selection.isNotEmpty()) {
                                            if (isSel) selection - item.id else selection + item.id
                                        } else setOf(item.id)
                                    },
                                    onLongClick = { selection = selection + item.id }
                                )
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Box(Modifier.clickable { selection = if (isSel) selection - item.id else selection + item.id }) {
                                FileIcon(entry, 40.dp, isSel)
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(item.originalPath, style = MaterialTheme.typography.bodySmall, color = ZtTheme.colors.secondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    stringResource(R.string.deleted_at, Format.date(item.deletedAt)) + "  " + Format.size(item.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ZtTheme.colors.secondaryText
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text(stringResource(R.string.empty_trash)) },
            text = { Text(stringResource(R.string.empty_trash_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmEmpty = false
                    all?.let { deleteForever(it) }
                }) { Text(stringResource(R.string.empty_trash), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
    confirmDelete?.let { list ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.delete_permanently)) },
            text = { Text(stringResource(R.string.confirm_delete_many, list.size)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    deleteForever(list)
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}
