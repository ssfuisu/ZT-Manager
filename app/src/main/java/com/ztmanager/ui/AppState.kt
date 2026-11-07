package com.ztmanager.ui

import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ztmanager.ZtApp
import com.ztmanager.data.Prefs
import com.ztmanager.fs.StorageUtil
import com.ztmanager.ui.browser.PaneState
import kotlinx.coroutines.MainScope
import java.io.File

sealed interface Screen {
    data object Browser : Screen
    data class Editor(val path: String, val readOnly: Boolean = false) : Screen
    data class Hex(val path: String) : Screen
    data class Images(val path: String) : Screen
    data class ApkInfo(val path: String) : Screen
    data object Apps : Screen
    data class Terminal(val dir: String = "") : Screen
    data class Search(val dir: String) : Screen
    data class Analyzer(val dir: String) : Screen
    data class Diff(val a: String, val b: String) : Screen
    data class TextView(val title: String, val text: String) : Screen
    data class Audio(val path: String) : Screen
    data class Video(val path: String) : Screen
    data class Pdf(val path: String) : Screen
    data class Sqlite(val path: String) : Screen
    data object Trash : Screen
    data class Duplicates(val dir: String) : Screen
    data class Category(val kind: Int) : Screen
    data object Share : Screen
    data object TextTools : Screen
    data object DeviceInfo : Screen
    data class Font(val path: String) : Screen
    data class FolderCompare(val left: String, val right: String) : Screen
    data object Settings : Screen
    data object About : Screen
}

object AppState {
    val scope = MainScope()

    val panes: List<PaneState> by lazy {
        val home = StorageUtil.internalRoot
        listOf(
            PaneState(0, Prefs.leftPath.takeIf { it.isNotEmpty() && File(it).isDirectory } ?: home),
            PaneState(1, Prefs.rightPath.takeIf { it.isNotEmpty() && File(it).isDirectory } ?: home)
        )
    }

    var activePaneIndex by mutableIntStateOf(0)
    val active: PaneState get() = panes[activePaneIndex]
    val other: PaneState get() = panes[1 - activePaneIndex]

    val stack = mutableStateListOf<Screen>(Screen.Browser)
    val current: Screen get() = stack.last()

    var permissionGranted by mutableStateOf(false)
    var locked by mutableStateOf(false)
    var unlockedOnce = false
    var backgroundAt = 0L

    fun push(screen: Screen) {
        stack.add(screen)
    }

    fun pop(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    fun replaceTop(screen: Screen) {
        if (stack.size > 1) stack[stack.lastIndex] = screen else stack.add(screen)
    }

    fun refreshAll() {
        panes.forEach { it.refresh() }
    }

    fun toast(msg: String?, long: Boolean = false) {
        if (msg.isNullOrBlank()) return
        Toast.makeText(ZtApp.instance, msg, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
    }
}

object FileClipboard {
    data class ArchiveClip(val archive: File, val baseInner: String, val paths: List<String>)

    var files by mutableStateOf<List<File>>(emptyList())
        private set
    var archiveClip by mutableStateOf<ArchiveClip?>(null)
        private set
    var cut by mutableStateOf(false)
        private set

    val isEmpty: Boolean get() = files.isEmpty() && archiveClip == null
    val count: Int get() = if (archiveClip != null) archiveClip!!.paths.size else files.size

    fun setFiles(list: List<File>, isCut: Boolean) {
        files = list
        archiveClip = null
        cut = isCut
    }

    fun setArchive(clip: ArchiveClip) {
        files = emptyList()
        archiveClip = clip
        cut = false
    }

    fun clear() {
        files = emptyList()
        archiveClip = null
        cut = false
    }
}
