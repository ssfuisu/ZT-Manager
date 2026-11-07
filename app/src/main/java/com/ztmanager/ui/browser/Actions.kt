package com.ztmanager.ui.browser

import android.content.Context
import com.ztmanager.R
import com.ztmanager.archive.ArchiveCreator
import com.ztmanager.archive.ArchiveManager
import com.ztmanager.archive.ArchiveTree
import com.ztmanager.archive.CreateFormat
import com.ztmanager.archive.ExtractContext
import com.ztmanager.fs.FileEntry
import com.ztmanager.data.Prefs
import com.ztmanager.fs.FileCrypto
import com.ztmanager.fs.FileOps
import com.ztmanager.fs.SplitTools
import com.ztmanager.fs.Trash
import com.ztmanager.task.TaskManager
import com.ztmanager.ui.AppState
import com.ztmanager.ui.FileClipboard
import java.io.File

object Actions {

    fun copyFiles(context: Context, files: List<File>, dest: File, move: Boolean) {
        if (files.isEmpty()) return
        TaskManager.start(
            title = context.getString(if (move) R.string.task_move else R.string.task_copy),
            successMessage = context.getString(R.string.msg_done),
            onDone = { AppState.refreshAll() }
        ) {
            FileOps.copy(this, files, dest, move)
        }
    }

    fun paste(context: Context, pane: PaneState) {
        val clip = FileClipboard.archiveClip
        when (val loc = pane.location) {
            is Location.Dir -> {
                val dest = File(loc.path)
                if (clip != null) {
                    extract(context, clip.archive, clip.paths, clip.baseInner, dest)
                    FileClipboard.clear()
                } else {
                    val move = FileClipboard.cut
                    copyFiles(context, FileClipboard.files, dest, move)
                    if (move) FileClipboard.clear()
                }
            }
            is Location.Archive -> {
                if (clip != null) {
                    AppState.toast(context.getString(R.string.msg_not_supported))
                    return
                }
                addToArchive(context, pane, FileClipboard.files, FileClipboard.cut)
                FileClipboard.clear()
            }
        }
    }

    fun addToArchive(context: Context, pane: PaneState, files: List<File>, deleteAfter: Boolean) {
        val session = pane.archive ?: return
        val loc = pane.location as? Location.Archive ?: return
        if (!session.handle.canModify) {
            AppState.toast(context.getString(R.string.msg_archive_read_only))
            return
        }
        TaskManager.start(
            title = context.getString(R.string.task_add_to_archive),
            successMessage = context.getString(R.string.msg_done),
            onDone = { AppState.refreshAll() }
        ) {
            session.handle.addFiles(files, loc.inner, ExtractContext(this))
            if (deleteAfter) files.forEach { FileOps.deleteRecursive(it) }
        }
    }

    fun delete(context: Context, pane: PaneState, entries: List<FileEntry>, permanent: Boolean = !Prefs.useTrash) {
        if (entries.isEmpty()) return
        pane.clearSelection()
        if (entries.first().inArchive) {
            val session = pane.archive ?: return
            if (!session.handle.canModify) {
                AppState.toast(context.getString(R.string.msg_archive_read_only))
                return
            }
            val items = session.tree.collect(entries.map { it.path })
            TaskManager.start(
                title = context.getString(R.string.task_delete),
                successMessage = context.getString(R.string.msg_deleted),
                onDone = { AppState.refreshAll() }
            ) {
                session.handle.delete(items)
            }
            return
        }
        val files = entries.map { File(it.path) }
        val toTrash = !permanent && files.all { Trash.supports(it) }
        TaskManager.start(
            title = context.getString(if (toTrash) R.string.task_trash else R.string.task_delete),
            successMessage = context.getString(if (toTrash) R.string.msg_moved_to_trash else R.string.msg_deleted),
            onDone = { AppState.refreshAll() }
        ) {
            if (toTrash) Trash.moveToTrash(this, files) else FileOps.delete(this, files)
        }
    }

    /**
     * Extracts [paths] (inner archive paths, or everything when null) from [archive]
     * into [dest]. [baseInner] is stripped from all paths.
     */
    fun extract(context: Context, archive: File, paths: List<String>?, baseInner: String, dest: File) {
        TaskManager.start(
            title = context.getString(R.string.task_extract),
            successMessage = context.getString(R.string.msg_extracted),
            onDone = { AppState.refreshAll() }
        ) {
            setMessage(archive.name)
            val handle = ArchiveManager.openInteractive(archive)
            try {
                val items = if (paths == null) handle.items else ArchiveTree(handle.items).collect(paths)
                if (!dest.exists()) dest.mkdirs()
                handle.extract(items.sortedBy { it.path }, dest, baseInner, ExtractContext(this))
            } finally {
                handle.close()
            }
        }
    }

    fun extractEach(context: Context, archives: List<File>) {
        TaskManager.start(
            title = context.getString(R.string.task_extract),
            successMessage = context.getString(R.string.msg_extracted),
            onDone = { AppState.refreshAll() }
        ) {
            archives.forEachIndexed { i, archive ->
                checkCancelled()
                setItems(i, archives.size)
                val dest = File(archive.parentFile, folderNameFor(archive))
                dest.mkdirs()
                val handle = ArchiveManager.openInteractive(archive)
                try {
                    handle.extract(handle.items, dest, "", ExtractContext(this))
                } finally {
                    handle.close()
                }
            }
        }
    }

    fun folderNameFor(archive: File): String {
        var name = archive.name
        val lower = name.lowercase()
        for (ext in listOf(".tar.gz", ".tar.bz2", ".tar.xz", ".tar.lzma")) {
            if (lower.endsWith(ext)) return name.substring(0, name.length - ext.length)
        }
        val dot = name.lastIndexOf('.')
        if (dot > 0) name = name.substring(0, dot)
        return name
    }

    fun compress(
        context: Context,
        files: List<File>,
        output: File,
        format: CreateFormat,
        level: Int,
        password: String?,
        deleteSource: Boolean
    ) {
        TaskManager.start(
            title = context.getString(R.string.task_compress),
            successMessage = context.getString(R.string.msg_compressed),
            onDone = { AppState.refreshAll() }
        ) {
            setMessage(output.name)
            ArchiveCreator.create(this, files, output, format, level, password)
            if (deleteSource) files.forEach { FileOps.deleteRecursive(it) }
        }
    }

    fun encrypt(context: Context, files: List<File>, password: String, deleteSource: Boolean) {
        TaskManager.start(
            title = context.getString(R.string.task_encrypt),
            successMessage = context.getString(R.string.msg_encrypted),
            onDone = { AppState.refreshAll() }
        ) {
            files.forEachIndexed { i, f ->
                setItems(i, files.size)
                FileCrypto.encrypt(this, f, password, deleteSource)
            }
        }
    }

    fun decrypt(context: Context, files: List<File>, password: String, deleteSource: Boolean) {
        val wrong = context.getString(R.string.wrong_password)
        TaskManager.start(
            title = context.getString(R.string.task_decrypt),
            successMessage = context.getString(R.string.msg_decrypted),
            onDone = { AppState.refreshAll() }
        ) {
            files.forEachIndexed { i, f ->
                setItems(i, files.size)
                try {
                    FileCrypto.decrypt(this, f, password, deleteSource)
                } catch (e: FileCrypto.WrongPasswordException) {
                    throw java.io.IOException(wrong)
                }
            }
        }
    }

    fun split(context: Context, file: File, partSize: Long) {
        TaskManager.start(
            title = context.getString(R.string.task_split),
            successMessage = context.getString(R.string.msg_done),
            onDone = { AppState.refreshAll() }
        ) { SplitTools.split(this, file, partSize) }
    }

    fun merge(context: Context, first: File) {
        TaskManager.start(
            title = context.getString(R.string.task_merge),
            successMessage = context.getString(R.string.msg_done),
            onDone = { AppState.refreshAll() }
        ) { SplitTools.merge(this, first) }
    }

    fun testArchive(context: Context, archive: File) {
        var bad: List<String> = emptyList()
        val title = context.getString(R.string.action_test_archive)
        TaskManager.start(
            title = context.getString(R.string.task_test),
            onDone = { err ->
                if (err == null) {
                    val msg = if (bad.isEmpty()) context.getString(R.string.test_ok, archive.name)
                    else context.getString(R.string.test_failed, bad.size) + "\n\n" + bad.take(50).joinToString("\n")
                    Dialogs.show(DialogSpec.Message(title, msg))
                }
            }
        ) {
            setMessage(archive.name)
            val handle = ArchiveManager.openInteractive(archive)
            try {
                bad = handle.test(ExtractContext(this))
            } finally {
                handle.close()
            }
        }
    }
}
