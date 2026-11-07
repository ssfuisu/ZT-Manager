package com.ztmanager.ui.browser

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FlipToBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.ztmanager.task.TaskManager
import com.ztmanager.ui.AppState
import com.ztmanager.ui.FileClipboard
import com.ztmanager.ui.Screen
import com.ztmanager.ui.theme.ZtTheme
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun BrowserScreen() {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val pane = AppState.active
    var lastBack by remember { mutableLongStateOf(0L) }
    val pressAgain = stringResource(R.string.msg_press_back_again)

    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }
    BackHandler(enabled = !drawerState.isOpen) {
        when {
            pane.isSelecting -> pane.clearSelection()
            FilterState.visibleFor == pane.index || pane.filter.isNotEmpty() -> {
                pane.filter = ""
                FilterState.visibleFor = -1
            }
            pane.up() -> {}
            else -> {
                val now = System.currentTimeMillis()
                if (now - lastBack < 2000) (context as? Activity)?.finish()
                else {
                    lastBack = now
                    AppState.toast(pressAgain)
                }
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = { AppDrawer(onClose = { scope.launch { drawerState.close() } }) }
    ) {
        Scaffold(
            topBar = { BrowserTopBar(pane, onMenu = { scope.launch { drawerState.open() } }) },
            bottomBar = { BottomBars(pane) },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            BoxWithConstraints(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                val wide = maxWidth >= 600.dp
                if (wide && !Prefs.singlePane) {
                    Row(Modifier.fillMaxSize()) {
                        PaneView(AppState.panes[0], showPaneTabs = false, showActiveMarker = true, modifier = Modifier.weight(1f))
                        VerticalDivider(color = ZtTheme.colors.divider)
                        PaneView(AppState.panes[1], showPaneTabs = false, showActiveMarker = true, modifier = Modifier.weight(1f))
                    }
                } else if (Prefs.singlePane) {
                    PaneView(pane, showPaneTabs = true, showActiveMarker = false)
                } else {
                    val pager = rememberPagerState(initialPage = AppState.activePaneIndex) { 2 }
                    LaunchedEffect(pager.currentPage) { AppState.activePaneIndex = pager.currentPage }
                    LaunchedEffect(AppState.activePaneIndex) {
                        if (pager.currentPage != AppState.activePaneIndex) pager.animateScrollToPage(AppState.activePaneIndex)
                    }
                    HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
                        PaneView(AppState.panes[page], showPaneTabs = true, showActiveMarker = false)
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowserTopBar(pane: PaneState, onMenu: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val selecting = pane.isSelecting
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = ZtTheme.colors.toolbar),
        navigationIcon = {
            if (selecting) IconButton(onClick = { pane.clearSelection() }) { Icon(Icons.Filled.Close, null) }
            else IconButton(onClick = onMenu) { Icon(Icons.Filled.Menu, null) }
        },
        title = {
            Column {
                Text(
                    if (selecting) stringResource(R.string.n_selected, pane.selection.size) else pane.title.ifEmpty { "/" },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                val list = pane.visible
                val dirs = list.count { it.isDirectory }
                val sub = when (val l = pane.location) {
                    is Location.Archive -> (pane.archive?.handle?.formatName ?: "") + "  " +
                        stringResource(R.string.folders_files, dirs, list.size - dirs)
                    is Location.Dir -> stringResource(R.string.folders_files, dirs, list.size - dirs)
                }
                Text(sub, style = MaterialTheme.typography.bodySmall, color = ZtTheme.colors.secondaryText, maxLines = 1)
            }
        },
        actions = {
            if (selecting) {
                IconButton(onClick = { pane.selectAll() }) { Icon(Icons.Filled.SelectAll, stringResource(R.string.select_all)) }
                IconButton(onClick = { pane.invertSelection() }) { Icon(Icons.Filled.FlipToBack, stringResource(R.string.invert_selection)) }
                if (pane.selection.size >= 2) {
                    IconButton(onClick = { pane.selectRange() }) { Icon(Icons.Filled.UnfoldMore, stringResource(R.string.select_range)) }
                }
            } else {
                IconButton(onClick = { FilterState.visibleFor = if (FilterState.visibleFor == pane.index) -1 else pane.index }) {
                    Icon(Icons.Filled.FilterList, stringResource(R.string.filter_hint))
                }
                val canCreate = pane.location is Location.Dir
                if (canCreate) {
                    IconButton(onClick = { Dialogs.show(DialogSpec.NewItem(pane)) }) {
                        Icon(Icons.Filled.Add, stringResource(R.string.action_new))
                    }
                }
            }
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, null) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                fun item(label: String, action: () -> Unit): @Composable () -> Unit = {
                    DropdownMenuItem(text = { Text(label) }, onClick = { menu = false; action() })
                }
                item(stringResource(R.string.action_refresh)) { pane.refresh() }()
                item(stringResource(R.string.action_sort)) { Dialogs.show(DialogSpec.Sort) }()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.show_hidden)) },
                    trailingIcon = { Checkbox(checked = Prefs.showHidden, onCheckedChange = null) },
                    onClick = { Prefs.showHidden = !Prefs.showHidden }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.grid_view)) },
                    trailingIcon = { Checkbox(checked = Prefs.gridView, onCheckedChange = null) },
                    onClick = { Prefs.gridView = !Prefs.gridView }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.single_pane)) },
                    trailingIcon = { Checkbox(checked = Prefs.singlePane, onCheckedChange = null) },
                    onClick = { Prefs.singlePane = !Prefs.singlePane }
                )
                HorizontalDivider()
                val goTitle = stringResource(R.string.action_go_to)
                val pathHint = stringResource(R.string.path)
                item(goTitle) {
                    val start = pane.currentDir?.path ?: "/"
                    Dialogs.show(DialogSpec.Input(goTitle, start, pathHint) { p ->
                        val f = File(p.trim())
                        if (f.isDirectory) pane.navigate(f.absolutePath)
                        else if (f.isFile) Opener.openFile(context, pane, f, false)
                        else AppState.toast(context.getString(R.string.msg_not_found))
                    })
                }()
                item(stringResource(R.string.same_folder_other_pane)) {
                    pane.currentDir?.let { AppState.panes[1 - pane.index].navigate(it.path) }
                }()
                item(stringResource(R.string.swap_panes)) {
                    val a = AppState.panes[0].currentDir?.path
                    val b = AppState.panes[1].currentDir?.path
                    if (a != null && b != null) {
                        AppState.panes[0].navigate(b)
                        AppState.panes[1].navigate(a)
                    }
                }()
                pane.currentDir?.let { dir ->
                    item(stringResource(R.string.action_add_bookmark)) {
                        Prefs.addBookmark(dir.path)
                        AppState.toast(context.getString(R.string.msg_bookmark_added))
                    }()
                    item(stringResource(R.string.tool_search)) { AppState.push(Screen.Search(dir.path)) }()
                    item(stringResource(R.string.tool_analyzer)) { AppState.push(Screen.Analyzer(dir.path)) }()
                }
                item(stringResource(R.string.tool_trash)) { AppState.push(Screen.Trash) }()
                item(stringResource(R.string.settings)) { AppState.push(Screen.Settings) }()
            }
        }
    )
}

@Composable
private fun BottomBars(pane: PaneState) {
    val context = LocalContext.current
    val bgTasks = TaskManager.tasks.filter { it.background }
    val showSelection = pane.isSelecting
    val showClipboard = !showSelection && !FileClipboard.isEmpty
    if (!showSelection && !showClipboard && bgTasks.isEmpty()) return
    Surface(color = ZtTheme.colors.toolbar, tonalElevation = 3.dp) {
        Column(Modifier.navigationBarsPadding()) {
            bgTasks.forEach { t ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { t.background = false }
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(t.title + "  " + t.message, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (t.progress < 0) LinearProgressIndicator(Modifier.fillMaxWidth())
                    else LinearProgressIndicator(progress = { t.progress }, modifier = Modifier.fillMaxWidth())
                }
            }
            if (showSelection) {
                val entries = pane.selectedEntries
                val inArchive = entries.firstOrNull()?.inArchive == true
                Row(Modifier.fillMaxWidth().height(60.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (inArchive) {
                        BarButton(Icons.Filled.Unarchive, stringResource(R.string.action_extract), Modifier.weight(1f)) {
                            Dialogs.show(DialogSpec.ContextMenu(pane, entries))
                        }
                        BarButton(Icons.Filled.ContentCopy, stringResource(R.string.action_copy), Modifier.weight(1f)) {
                            val s = pane.archive
                            val loc = pane.location as? Location.Archive
                            if (s != null && loc != null) {
                                FileClipboard.setArchive(FileClipboard.ArchiveClip(s.file, loc.inner, entries.map { it.path }))
                            }
                            pane.clearSelection()
                        }
                    } else {
                        BarButton(Icons.Filled.ContentCopy, stringResource(R.string.action_copy), Modifier.weight(1f)) {
                            FileClipboard.setFiles(entries.map { File(it.path) }, false)
                            pane.clearSelection()
                        }
                        BarButton(Icons.Filled.ContentCut, stringResource(R.string.action_cut), Modifier.weight(1f)) {
                            FileClipboard.setFiles(entries.map { File(it.path) }, true)
                            pane.clearSelection()
                        }
                        BarButton(Icons.Filled.Archive, stringResource(R.string.action_compress), Modifier.weight(1f)) {
                            val files = entries.map { File(it.path) }
                            Dialogs.show(DialogSpec.Compress(pane, files, pane.currentDir ?: files.first().parentFile!!))
                        }
                    }
                    BarButton(Icons.Filled.Delete, stringResource(R.string.action_delete), Modifier.weight(1f)) {
                        if (Prefs.confirmDelete || inArchive) Dialogs.show(DialogSpec.Delete(pane, entries))
                        else Actions.delete(context, pane, entries)
                    }
                    BarButton(Icons.Filled.MoreVert, stringResource(R.string.more), Modifier.weight(1f)) {
                        Dialogs.show(DialogSpec.ContextMenu(pane, entries))
                    }
                }
            } else if (showClipboard) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        if (FileClipboard.cut) Icons.Filled.ContentCut else Icons.Filled.ContentCopy, null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(
                            if (FileClipboard.cut) R.string.clipboard_cut else R.string.clipboard_copy,
                            FileClipboard.count
                        ),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    TextButton(onClick = { FileClipboard.clear() }) { Text(stringResource(R.string.cancel)) }
                    val canPaste = pane.location is Location.Dir ||
                        (pane.archive?.handle?.canModify == true && FileClipboard.archiveClip == null)
                    Button(enabled = canPaste, onClick = { Actions.paste(context, pane) }) {
                        Icon(Icons.Filled.ContentPaste, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_paste))
                    }
                }
            }
        }
    }
}

@Composable
private fun BarButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxHeight()
            .clickable(onClick = onClick)
            .padding(top = 8.dp)
    ) {
        Icon(icon, label, tint = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

