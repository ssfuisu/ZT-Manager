package com.ztmanager.ui.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SubdirectoryArrowLeft
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ztmanager.R
import com.ztmanager.data.Prefs
import com.ztmanager.fs.FileEntry
import com.ztmanager.fs.Format
import com.ztmanager.fs.StorageUtil
import com.ztmanager.ui.AppState
import com.ztmanager.ui.common.FileIcon
import com.ztmanager.ui.theme.ZtTheme
import java.io.File

@Composable
fun PaneView(pane: PaneState, showPaneTabs: Boolean, showActiveMarker: Boolean, modifier: Modifier = Modifier) {
    LaunchedEffect(pane) { pane.ensureLoaded() }
    val isActive = AppState.activePaneIndex == pane.index
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(pane.index) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    AppState.activePaneIndex = pane.index
                }
            }
    ) {
        PathBar(pane, showPaneTabs, isActive && showActiveMarker)
        if (pane.filter.isNotEmpty() || FilterState.visibleFor == pane.index) FilterBar(pane)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val list = pane.visible
            when {
                pane.loading && pane.entries.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                pane.error != null -> Message(Icons.Filled.Warning, pane.error!!, Modifier.align(Alignment.Center))
                list.isEmpty() -> Message(Icons.Filled.FolderOpen, stringResource(R.string.empty_folder), Modifier.align(Alignment.Center))
                Prefs.gridView -> FileGrid(pane, list)
                else -> FileList(pane, list)
            }
            if (pane.loading && pane.entries.isNotEmpty()) {
                LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp).align(Alignment.TopCenter))
            }
        }
    }
}

object FilterState {
    var visibleFor by androidx.compose.runtime.mutableIntStateOf(-1)
}

@Composable
private fun FilterBar(pane: PaneState) {
    TextField(
        value = pane.filter,
        onValueChange = { pane.filter = it },
        singleLine = true,
        placeholder = { Text(stringResource(R.string.filter_hint)) },
        leadingIcon = { Icon(Icons.Filled.Search, null) },
        trailingIcon = {
            IconButton(onClick = {
                pane.filter = ""
                FilterState.visibleFor = -1
            }) { Icon(Icons.Filled.Close, null) }
        },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = ZtTheme.colors.pathBar,
            unfocusedContainerColor = ZtTheme.colors.pathBar
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun Message(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, modifier: Modifier) {
    Column(modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = ZtTheme.colors.secondaryText, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(8.dp))
        Text(text, color = ZtTheme.colors.secondaryText, textAlign = TextAlign.Center)
    }
}

private data class Crumb(val label: String, val onClick: () -> Unit, val icon: androidx.compose.ui.graphics.vector.ImageVector? = null)

@Composable
private fun PathBar(pane: PaneState, showPaneTabs: Boolean, activeMarker: Boolean) {
    val internalLabel = stringResource(R.string.internal_storage)
    val context = LocalContext.current
    val crumbs = remember(pane.location) {
        val list = ArrayList<Crumb>()
        fun dirCrumbs(path: String) {
            val internal = StorageUtil.internalRoot
            var acc: String
            val rest: String
            if (path == internal || path.startsWith("$internal/")) {
                list.add(Crumb(internalLabel, { pane.navigate(internal) }, Icons.Filled.PhoneAndroid))
                acc = internal
                rest = path.removePrefix(internal).trim('/')
            } else {
                val vol = StorageUtil.volumes(context).firstOrNull { path == it.path || path.startsWith(it.path + "/") }
                if (vol != null) {
                    list.add(Crumb(vol.name, { pane.navigate(vol.path) }, Icons.Filled.SdCard))
                    acc = vol.path
                    rest = path.removePrefix(vol.path).trim('/')
                } else {
                    list.add(Crumb("/", { pane.navigate("/") }))
                    acc = ""
                    rest = path.trim('/')
                }
            }
            if (rest.isNotEmpty()) {
                for (part in rest.split('/')) {
                    acc = "$acc/$part"
                    val target = acc
                    list.add(Crumb(part, { pane.navigate(target) }))
                }
            }
        }
        when (val l = pane.location) {
            is Location.Dir -> dirCrumbs(l.path)
            is Location.Archive -> {
                val f = File(l.file)
                list.add(Crumb(f.name, { pane.enterArchiveDir("") }, Icons.Filled.Archive))
                var acc = ""
                if (l.inner.isNotEmpty()) for (part in l.inner.split('/')) {
                    acc = if (acc.isEmpty()) part else "$acc/$part"
                    val target = acc
                    list.add(Crumb(part, { pane.enterArchiveDir(target) }))
                }
            }
        }
        list
    }
    val scroll = rememberScrollState()
    LaunchedEffect(crumbs) { scroll.scrollTo(scroll.maxValue) }
    Column(Modifier.background(ZtTheme.colors.pathBar)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
        ) {
            if (showPaneTabs) {
                Row(Modifier.padding(start = 8.dp, end = 4.dp)) {
                    for (i in 0..1) {
                        val sel = AppState.activePaneIndex == i
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .size(24.dp)
                                .background(
                                    if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(6.dp)
                                )
                                .clickable { AppState.activePaneIndex = i }
                        ) {
                            Text(
                                (i + 1).toString(),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(scroll)
                    .padding(horizontal = 6.dp)
            ) {
                crumbs.forEachIndexed { i, c ->
                    if (i > 0) Icon(Icons.Filled.ChevronRight, null, tint = ZtTheme.colors.secondaryText, modifier = Modifier.size(16.dp))
                    val last = i == crumbs.lastIndex
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable(onClick = c.onClick)
                            .padding(horizontal = 6.dp, vertical = 6.dp)
                    ) {
                        if (c.icon != null) {
                            Icon(c.icon, null, modifier = Modifier.size(16.dp), tint = if (last) MaterialTheme.colorScheme.primary else ZtTheme.colors.secondaryText)
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            c.label,
                            fontSize = 13.sp,
                            maxLines = 1,
                            fontWeight = if (last) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (last) MaterialTheme.colorScheme.primary else ZtTheme.colors.secondaryText
                        )
                    }
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(if (activeMarker) ZtTheme.colors.activePaneMarker else Color.Transparent)
        )
    }
}

private fun canGoUp(pane: PaneState): Boolean = when (val l = pane.location) {
    is Location.Dir -> File(l.path).parent != null
    is Location.Archive -> true
}

@Composable
private fun FileList(pane: PaneState, list: List<FileEntry>) {
    LazyColumn(state = pane.listState, modifier = Modifier.fillMaxSize()) {
        if (canGoUp(pane) && !pane.isSelecting) {
            item(key = "..") { ParentRow(pane) }
        }
        items(list, key = { it.path }) { entry ->
            FileRow(pane, entry, entry.path in pane.selection)
        }
        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
private fun ParentRow(pane: PaneState) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { pane.up() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.SubdirectoryArrowLeft, null, tint = ZtTheme.colors.secondaryText)
        }
        Spacer(Modifier.width(14.dp))
        Text("..", style = MaterialTheme.typography.bodyLarge, color = ZtTheme.colors.secondaryText)
    }
}

@Composable
private fun FileRow(pane: PaneState, entry: FileEntry, selected: Boolean) {
    val context = LocalContext.current
    val itemsLabel = if (entry.isDirectory && entry.childCount >= 0)
        stringResource(R.string.n_items, entry.childCount) else ""
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) ZtTheme.colors.selection else Color.Transparent)
            .combinedClickable(
                onClick = {
                    if (pane.isSelecting) pane.toggle(entry.path) else Opener.open(context, pane, entry)
                },
                onLongClick = {
                    val targets = if (selected && pane.selection.size > 1) pane.selectedEntries else listOf(entry)
                    Dialogs.show(DialogSpec.ContextMenu(pane, targets))
                }
            )
            .padding(horizontal = 14.dp, vertical = 7.dp)
            .alpha(if (entry.isHidden) 0.6f else 1f)
    ) {
        Box(
            Modifier
                .clickable { pane.toggle(entry.path) }
        ) {
            FileIcon(entry, 40.dp, selected)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    Format.date(entry.lastModified),
                    style = MaterialTheme.typography.bodySmall,
                    color = ZtTheme.colors.secondaryText,
                    maxLines = 1
                )
                if (Prefs.rootMode && entry.permissions.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        entry.permissions,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = ZtTheme.colors.secondaryText,
                        maxLines = 1
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    if (entry.isDirectory) itemsLabel else Format.size(entry.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = ZtTheme.colors.secondaryText,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun FileGrid(pane: PaneState, list: List<FileEntry>) {
    val context = LocalContext.current
    LazyVerticalGrid(
        columns = GridCells.Adaptive(92.dp),
        state = pane.gridState,
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(list, key = { it.path }) { entry ->
            val selected = entry.path in pane.selection
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .background(if (selected) ZtTheme.colors.selection else Color.Transparent, RoundedCornerShape(8.dp))
                    .combinedClickable(
                        onClick = { if (pane.isSelecting) pane.toggle(entry.path) else Opener.open(context, pane, entry) },
                        onLongClick = {
                            val targets = if (selected && pane.selection.size > 1) pane.selectedEntries else listOf(entry)
                            Dialogs.show(DialogSpec.ContextMenu(pane, targets))
                        }
                    )
                    .padding(8.dp)
                    .alpha(if (entry.isHidden) 0.6f else 1f)
            ) {
                Box(Modifier.clickable { pane.toggle(entry.path) }) { FileIcon(entry, 56.dp, selected) }
                Spacer(Modifier.height(4.dp))
                Text(
                    entry.name,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
