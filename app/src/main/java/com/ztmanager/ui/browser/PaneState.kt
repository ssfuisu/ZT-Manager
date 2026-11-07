package com.ztmanager.ui.browser

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ztmanager.ZtApp
import com.ztmanager.archive.ArchiveHandle
import com.ztmanager.archive.ArchiveManager
import com.ztmanager.archive.ArchiveTree
import com.ztmanager.data.Prefs
import com.ztmanager.fs.FileEntry
import com.ztmanager.fs.FileOps
import com.ztmanager.ui.AppState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed class Location {
    data class Dir(val path: String) : Location()
    data class Archive(val file: String, val inner: String) : Location()

    val key: String
        get() = when (this) {
            is Dir -> path
            is Archive -> "$file!/$inner"
        }
}

class ArchiveSession(val file: File, val handle: ArchiveHandle) {
    var tree by mutableStateOf(ArchiveTree(handle.items))
        private set

    fun reload() {
        tree = ArchiveTree(handle.items)
    }
}

class PaneState(val index: Int, initialPath: String) {
    var location by mutableStateOf<Location>(Location.Dir(initialPath))
        private set
    var entries by mutableStateOf<List<FileEntry>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var selection by mutableStateOf<Set<String>>(emptySet())
    var filter by mutableStateOf("")
    var archive by mutableStateOf<ArchiveSession?>(null)
        private set

    val listState = LazyListState()
    val gridState = LazyGridState()
    private val scrollMemory = HashMap<String, Int>()
    private var loadJob: Job? = null
    private var lastRealDir: String = initialPath
    private var loadedOnce = false

    val visible: List<FileEntry> by derivedStateOf { sortEntries(entries) }

    val selectedEntries: List<FileEntry>
        get() = entries.filter { it.path in selection }

    val isSelecting: Boolean get() = selection.isNotEmpty()

    val currentDir: File?
        get() = (location as? Location.Dir)?.let { File(it.path) }

    val title: String
        get() = when (val l = location) {
            is Location.Dir -> if (l.path == "/") "/" else File(l.path).name
            is Location.Archive -> if (l.inner.isEmpty()) File(l.file).name else l.inner.substringAfterLast('/')
        }

    fun ensureLoaded() {
        if (!loadedOnce) {
            loadedOnce = true
            load()
        }
    }

    private fun sortEntries(list: List<FileEntry>): List<FileEntry> {
        var l = if (Prefs.showHidden) list else list.filter { !it.isHidden }
        val f = filter
        if (f.isNotBlank()) l = l.filter { it.name.contains(f, ignoreCase = true) }
        val byName = Comparator<FileEntry> { a, b -> NaturalOrder.compare(a.name, b.name) }
        val cmp: Comparator<FileEntry> = when (Prefs.sortBy) {
            Prefs.SORT_SIZE -> compareBy<FileEntry> { it.size }.then(byName)
            Prefs.SORT_DATE -> compareBy<FileEntry> { it.lastModified }.then(byName)
            Prefs.SORT_TYPE -> compareBy<FileEntry> { it.extension }.then(byName)
            else -> byName
        }
        val ordered = if (Prefs.sortDesc) cmp.reversed() else cmp
        return if (Prefs.foldersFirst) l.sortedWith(compareBy<FileEntry> { !it.isDirectory }.then(ordered))
        else l.sortedWith(ordered)
    }

    private fun saveScroll() {
        scrollMemory[location.key] = if (Prefs.gridView) gridState.firstVisibleItemIndex else listState.firstVisibleItemIndex
    }

    fun navigate(path: String) {
        saveScroll()
        closeArchive()
        location = Location.Dir(path)
        lastRealDir = path
        selection = emptySet()
        filter = ""
        if (index == 0) Prefs.leftPath = path else Prefs.rightPath = path
        Prefs.addRecent(path)
        load()
    }

    fun refresh() {
        archive?.reload()
        load(keepScroll = true)
    }

    fun load(keepScroll: Boolean = false) {
        loadedOnce = true
        loadJob?.cancel()
        val loc = location
        val session = archive
        loadJob = AppState.scope.launch {
            loading = true
            error = null
            try {
                val list = withContext(Dispatchers.IO) {
                    when (loc) {
                        is Location.Dir -> FileOps.list(loc.path, Prefs.showFolderCount)
                        is Location.Archive -> session?.tree?.list(loc.inner) ?: emptyList()
                    }
                }
                entries = list
                val existing = list.map { it.path }.toSet()
                selection = selection.filter { it in existing }.toSet()
                if (!keepScroll) {
                    val pos = scrollMemory[loc.key] ?: 0
                    try {
                        listState.scrollToItem(pos)
                        gridState.scrollToItem(pos)
                    } catch (_: Exception) {
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                entries = emptyList()
                error = e.message ?: e.javaClass.simpleName
            } finally {
                loading = false
            }
        }
    }

    /** Goes one level up. Returns false when already at the top. */
    fun up(): Boolean {
        when (val l = location) {
            is Location.Dir -> {
                val parent = File(l.path).parent ?: return false
                navigate(parent)
                return true
            }
            is Location.Archive -> {
                if (l.inner.isEmpty()) {
                    val f = File(l.file)
                    val cache = ZtApp.instance.cacheDir.absolutePath
                    val target = if (f.absolutePath.startsWith(cache)) lastRealDir else (f.parent ?: lastRealDir)
                    navigate(target)
                } else {
                    saveScroll()
                    location = Location.Archive(l.file, l.inner.substringBeforeLast('/', ""))
                    selection = emptySet()
                    load()
                }
                return true
            }
        }
    }

    fun openArchive(file: File) {
        saveScroll()
        AppState.scope.launch {
            loading = true
            try {
                val handle = withContext(Dispatchers.IO) { ArchiveManager.openInteractive(file) }
                closeArchive()
                archive = ArchiveSession(file, handle)
                if (!file.absolutePath.startsWith(ZtApp.instance.cacheDir.absolutePath)) {
                    file.parent?.let { lastRealDir = it }
                }
                location = Location.Archive(file.absolutePath, "")
                selection = emptySet()
                filter = ""
                load()
            } catch (e: CancellationException) {
                // user dismissed the password dialog
            } catch (e: Exception) {
                AppState.toast(e.message ?: e.javaClass.simpleName, true)
            } finally {
                loading = false
            }
        }
    }

    fun enterArchiveDir(inner: String) {
        val l = location as? Location.Archive ?: return
        saveScroll()
        location = Location.Archive(l.file, inner)
        selection = emptySet()
        filter = ""
        load()
    }

    fun closeArchive() {
        val s = archive ?: return
        archive = null
        AppState.scope.launch(Dispatchers.IO) { s.handle.close() }
    }

    fun toggle(path: String) {
        selection = if (path in selection) selection - path else selection + path
    }

    fun selectAll() {
        selection = visible.map { it.path }.toSet()
    }

    fun invertSelection() {
        selection = visible.map { it.path }.filter { it !in selection }.toSet()
    }

    /** Selects every entry between the first and the last selected one. */
    fun selectRange() {
        val list = visible
        val idx = list.indices.filter { list[it].path in selection }
        if (idx.size < 2) return
        selection = selection + list.subList(idx.first(), idx.last() + 1).map { it.path }
    }

    fun clearSelection() {
        selection = emptySet()
    }
}

/** Case-insensitive comparison that orders "file2" before "file10". */
object NaturalOrder : Comparator<String> {
    override fun compare(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]
            val cb = b[j]
            if (ca.isDigit() && cb.isDigit()) {
                val si = i
                val sj = j
                while (i < a.length && a[i].isDigit()) i++
                while (j < b.length && b[j].isDigit()) j++
                val na = a.substring(si, i).trimStart('0')
                val nb = b.substring(sj, j).trimStart('0')
                if (na.length != nb.length) return na.length - nb.length
                val c = na.compareTo(nb)
                if (c != 0) return c
            } else {
                val c = ca.lowercaseChar().compareTo(cb.lowercaseChar())
                if (c != 0) return c
                i++
                j++
            }
        }
        return (a.length - i) - (b.length - j)
    }
}
