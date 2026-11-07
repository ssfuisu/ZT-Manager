package com.ztmanager.ui.browser

import android.content.Context
import com.ztmanager.R
import com.ztmanager.ZtApp
import com.ztmanager.archive.ArchiveManager
import com.ztmanager.data.Prefs
import com.ztmanager.fs.FileEntry
import com.ztmanager.fs.FileType
import com.ztmanager.fs.RootShell
import com.ztmanager.ui.AppState
import com.ztmanager.ui.Screen
import com.ztmanager.ui.common.Intents
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class OpenAs { TEXT, HEX, IMAGE, ARCHIVE, APK, AUDIO, VIDEO, PDF, SQLITE, EXTERNAL }

object Opener {

    fun open(context: Context, pane: PaneState, entry: FileEntry) {
        if (entry.inArchive) {
            if (entry.isDirectory) pane.enterArchiveDir(entry.path) else openArchiveEntry(context, pane, entry)
            return
        }
        if (entry.isDirectory) {
            pane.navigate(entry.path)
            return
        }
        openFile(context, pane, File(entry.path), fromArchive = false)
    }

    private fun openArchiveEntry(context: Context, pane: PaneState, entry: FileEntry) {
        val session = pane.archive ?: return
        val item = session.handle.items.firstOrNull { it.path == entry.path && !it.isDir } ?: return
        AppState.scope.launch {
            try {
                val f = withContext(Dispatchers.IO) { ArchiveManager.extractToCache(session.handle, item) }
                openFile(context, pane, f, fromArchive = true)
            } catch (e: CancellationException) {
            } catch (e: Exception) {
                AppState.toast(e.message ?: context.getString(R.string.msg_failed), true)
            }
        }
    }

    /** Makes a root-only file readable by copying it into the cache. */
    private suspend fun readable(file: File): File {
        if (file.canRead() || !Prefs.rootMode) return file
        return withContext(Dispatchers.IO) {
            val dest = File(ZtApp.instance.cacheDir, "root/" + file.name)
            if (RootShell.readToCache(file.absolutePath, dest)) dest else file
        }
    }

    fun openFile(context: Context, pane: PaneState, file: File, fromArchive: Boolean) {
        AppState.scope.launch {
            val f = readable(file)
            val readOnly = fromArchive || f != file
            when (FileType.of(f.name, false)) {
                FileType.ARCHIVE -> if (fromArchive) pane.openArchive(f) else Dialogs.show(DialogSpec.ArchiveTap(f, pane))
                FileType.APK -> Dialogs.show(DialogSpec.ApkTap(f, pane, fromArchive))
                FileType.IMAGE -> AppState.push(Screen.Images(f.absolutePath))
                FileType.TEXT, FileType.CODE -> AppState.push(Screen.Editor(f.absolutePath, readOnly))
                FileType.AUDIO -> if (Prefs.builtInViewers) AppState.push(Screen.Audio(f.absolutePath)) else Intents.openExternal(context, f)
                FileType.VIDEO -> if (Prefs.builtInViewers) AppState.push(Screen.Video(f.absolutePath)) else Intents.openExternal(context, f)
                FileType.PDF -> if (Prefs.builtInViewers) AppState.push(Screen.Pdf(f.absolutePath)) else Intents.openExternal(context, f)
                FileType.DATABASE -> AppState.push(Screen.Sqlite(f.absolutePath))
                FileType.FONT -> AppState.push(Screen.Font(f.absolutePath))
                FileType.DOCUMENT -> Intents.openExternal(context, f)
                else -> Dialogs.show(DialogSpec.OpenAsChooser(f, pane, readOnly))
            }
        }
    }

    fun openAs(context: Context, pane: PaneState, file: File, kind: OpenAs, readOnly: Boolean = false) {
        when (kind) {
            OpenAs.TEXT -> AppState.push(Screen.Editor(file.absolutePath, readOnly))
            OpenAs.HEX -> AppState.push(Screen.Hex(file.absolutePath))
            OpenAs.IMAGE -> AppState.push(Screen.Images(file.absolutePath))
            OpenAs.ARCHIVE -> pane.openArchive(file)
            OpenAs.APK -> AppState.push(Screen.ApkInfo(file.absolutePath))
            OpenAs.AUDIO -> AppState.push(Screen.Audio(file.absolutePath))
            OpenAs.VIDEO -> AppState.push(Screen.Video(file.absolutePath))
            OpenAs.PDF -> AppState.push(Screen.Pdf(file.absolutePath))
            OpenAs.SQLITE -> AppState.push(Screen.Sqlite(file.absolutePath))
            OpenAs.EXTERNAL -> Intents.openExternal(context, file, "*/*")
        }
    }
}
