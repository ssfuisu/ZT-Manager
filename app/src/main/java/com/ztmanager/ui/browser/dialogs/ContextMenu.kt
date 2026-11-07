package com.ztmanager.ui.browser.dialogs

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddToHomeScreen
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FileCopy
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GetApp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ztmanager.R
import com.ztmanager.data.Prefs
import com.ztmanager.apk.SplitInstaller
import com.ztmanager.fs.ChecksumFiles
import com.ztmanager.fs.FileCrypto
import com.ztmanager.fs.FileEntry
import com.ztmanager.fs.SplitTools
import com.ztmanager.fs.FileType
import com.ztmanager.fs.Format
import com.ztmanager.ui.AppState
import com.ztmanager.ui.FileClipboard
import com.ztmanager.ui.Screen
import com.ztmanager.ui.browser.Actions
import com.ztmanager.ui.browser.DialogSpec
import com.ztmanager.ui.browser.Dialogs
import com.ztmanager.ui.browser.Location
import com.ztmanager.ui.browser.Opener
import com.ztmanager.ui.common.FileIcon
import com.ztmanager.ui.common.Intents
import com.ztmanager.ui.common.Shortcuts
import java.io.File

@Composable
fun ContextMenuDialog(spec: DialogSpec.ContextMenu, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val pane = spec.pane
    val entries = spec.entries
    val single = entries.singleOrNull()
    val actions = ArrayList<MenuAction>()
    val otherDir = AppState.panes[1 - pane.index].currentDir

    val sOpen = stringResource(R.string.action_open)
    val sOpenWith = stringResource(R.string.action_open_as)
    val sCopy = stringResource(R.string.action_copy)
    val sCut = stringResource(R.string.action_cut)
    val sDelete = stringResource(R.string.action_delete)
    val sProps = stringResource(R.string.action_properties)
    val sExtractTo = stringResource(R.string.action_extract_to)
    val sExtractHere = stringResource(R.string.action_extract_here)
    val sPickFolder = stringResource(R.string.title_select_folder)
    val leadingIcon: (@Composable () -> Unit)? = if (single != null) {
        { FileIcon(single, size = 36.dp) }
    } else null

    if (entries.first().inArchive) {
        val session = pane.archive
        val loc = pane.location as? Location.Archive
        if (single != null && !single.isDirectory) {
            actions += MenuAction(sOpen, Icons.Filled.OpenInNew) { Opener.open(context, pane, single) }
        }
        if (session != null && loc != null) {
            val archiveFile = session.file
            val paths = entries.map { it.path }
            actions += MenuAction(sExtractHere, Icons.Filled.Unarchive) {
                Actions.extract(context, archiveFile, paths, loc.inner, archiveFile.parentFile ?: archiveFile)
            }
            actions += MenuAction(sExtractTo, Icons.Filled.Unarchive) {
                Dialogs.show(DialogSpec.FolderPicker(sPickFolder, otherDir?.path ?: archiveFile.parent ?: "/") { dest ->
                    Actions.extract(context, archiveFile, paths, loc.inner, dest)
                })
            }
            if (otherDir != null) {
                actions += MenuAction(stringResource(R.string.action_extract_to_other), Icons.Filled.SwapHoriz) {
                    Actions.extract(context, archiveFile, paths, loc.inner, otherDir)
                }
            }
            actions += MenuAction(sCopy, Icons.Filled.ContentCopy) {
                FileClipboard.setArchive(FileClipboard.ArchiveClip(archiveFile, loc.inner, paths))
                pane.clearSelection()
            }
            if (session.handle.canModify) {
                actions += MenuAction(sDelete, Icons.Filled.Delete) {
                    Dialogs.show(DialogSpec.Delete(pane, entries))
                }
            }
        }
        actions += MenuAction(sProps, Icons.Filled.Info) { Dialogs.show(DialogSpec.Properties(pane, entries)) }
        MenuDialog(
            title = single?.name ?: stringResource(R.string.n_selected, entries.size),
            subtitle = single?.let { if (it.isDirectory) null else Format.size(it.size) },
            actions = actions,
            onDismiss = onDismiss,
            leading = leadingIcon
        )
        return
    }

    val files = entries.map { File(it.path) }
    if (single != null) {
        val f = File(single.path)
        if (single.isDirectory) {
            actions += MenuAction(sOpen, Icons.Filled.FolderOpen) { pane.navigate(single.path) }
            actions += MenuAction(stringResource(R.string.action_open_other_pane), Icons.Filled.SwapHoriz) {
                AppState.panes[1 - pane.index].navigate(single.path)
                AppState.activePaneIndex = 1 - pane.index
            }
        } else {
            actions += MenuAction(sOpen, Icons.Filled.OpenInNew) { Opener.open(context, pane, single) }
            actions += MenuAction(sOpenWith, Icons.Filled.Code) {
                Dialogs.show(DialogSpec.OpenAsChooser(f, pane, false))
            }
        }
        if (FileType.isArchive(single.name) && !single.isDirectory) {
            actions += MenuAction(stringResource(R.string.action_browse_archive), Icons.Filled.Archive) { pane.openArchive(f) }
            actions += MenuAction(sExtractHere, Icons.Filled.Unarchive) {
                Actions.extract(context, f, null, "", f.parentFile!!)
            }
            val folder = Actions.folderNameFor(f)
            actions += MenuAction(stringResource(R.string.action_extract_to_folder, "$folder/"), Icons.Filled.Unarchive) {
                Actions.extract(context, f, null, "", File(f.parentFile, folder))
            }
            actions += MenuAction(sExtractTo, Icons.Filled.Unarchive) {
                Dialogs.show(DialogSpec.FolderPicker(sPickFolder, otherDir?.path ?: f.parent ?: "/") { dest ->
                    Actions.extract(context, f, null, "", dest)
                })
            }
            actions += MenuAction(stringResource(R.string.action_test_archive), Icons.Filled.CheckCircle) {
                Actions.testArchive(context, f)
            }
        }
        if (!single.isDirectory && SplitTools.isFirstPart(f)) {
            actions += MenuAction(stringResource(R.string.action_merge), Icons.Filled.CallMerge) { Actions.merge(context, f) }
        }
        if (!single.isDirectory && SplitInstaller.isBundle(f)) {
            actions += MenuAction(stringResource(R.string.action_install_bundle), Icons.Filled.GetApp) { Actions.installBundle(context, f) }
        }
        if (!single.isDirectory && ChecksumFiles.isChecksumFile(f)) {
            actions += MenuAction(stringResource(R.string.action_verify_checksums), Icons.Filled.Fingerprint) { Actions.verifyChecksums(context, f) }
        }
        if (single.type == FileType.APK) {
            actions += MenuAction(stringResource(R.string.action_install), Icons.Filled.GetApp) { Intents.install(context, f) }
            actions += MenuAction(stringResource(R.string.action_apk_info), Icons.Filled.Android) {
                AppState.push(Screen.ApkInfo(f.absolutePath))
            }
        }
    } else if (entries.all { !it.isDirectory && FileType.isArchive(it.name) }) {
        actions += MenuAction(stringResource(R.string.action_extract_each), Icons.Filled.Unarchive) {
            Actions.extractEach(context, files)
        }
    }

    actions += MenuAction(stringResource(R.string.action_compress), Icons.Filled.Archive) {
        val dir = pane.currentDir ?: files.first().parentFile!!
        Dialogs.show(DialogSpec.Compress(pane, files, dir))
    }
    actions += MenuAction(sCopy, Icons.Filled.ContentCopy) {
        FileClipboard.setFiles(files, false)
        pane.clearSelection()
    }
    actions += MenuAction(sCut, Icons.Filled.ContentCut) {
        FileClipboard.setFiles(files, true)
        pane.clearSelection()
    }
    if (otherDir != null) {
        actions += MenuAction(stringResource(R.string.action_copy_to_other), Icons.Filled.SwapHoriz) {
            pane.clearSelection()
            Actions.copyFiles(context, files, otherDir, false)
        }
        actions += MenuAction(stringResource(R.string.action_move_to_other), Icons.Filled.SwapHoriz) {
            pane.clearSelection()
            Actions.copyFiles(context, files, otherDir, true)
        }
    }
    actions += MenuAction(stringResource(R.string.action_copy_to), Icons.Filled.ContentCopy) {
        Dialogs.show(DialogSpec.FolderPicker(sPickFolder, otherDir?.path ?: pane.currentDir?.path ?: "/") { dest ->
            pane.clearSelection()
            Actions.copyFiles(context, files, dest, false)
        })
    }
    actions += MenuAction(stringResource(R.string.action_move_to), Icons.Filled.ContentCut) {
        Dialogs.show(DialogSpec.FolderPicker(sPickFolder, otherDir?.path ?: pane.currentDir?.path ?: "/") { dest ->
            pane.clearSelection()
            Actions.copyFiles(context, files, dest, true)
        })
    }
    if (single != null) {
        actions += MenuAction(stringResource(R.string.action_rename), Icons.Filled.Edit) {
            Dialogs.show(DialogSpec.Rename(pane, single))
        }
    } else {
        actions += MenuAction(stringResource(R.string.action_batch_rename), Icons.Filled.DriveFileRenameOutline) {
            Dialogs.show(DialogSpec.BatchRename(pane, entries))
        }
    }
    actions += MenuAction(sDelete, Icons.Filled.Delete) {
        if (Prefs.confirmDelete) Dialogs.show(DialogSpec.Delete(pane, entries))
        else Actions.delete(context, pane, entries)
    }
    if (entries.none { it.isDirectory }) {
        actions += MenuAction(stringResource(R.string.action_share), Icons.Filled.Share) { Intents.share(context, files) }
        if (entries.all { it.extension == FileCrypto.EXTENSION }) {
            actions += MenuAction(stringResource(R.string.action_decrypt), Icons.Filled.LockOpen) {
                Dialogs.show(DialogSpec.Crypto(pane, files, decrypt = true))
            }
        } else {
            actions += MenuAction(stringResource(R.string.action_encrypt), Icons.Filled.EnhancedEncryption) {
                Dialogs.show(DialogSpec.Crypto(pane, files, decrypt = false))
            }
        }
        if (entries.all { it.type == FileType.IMAGE }) {
            actions += MenuAction(stringResource(R.string.action_convert_image), Icons.Filled.Transform) {
                Dialogs.show(DialogSpec.ImageConvert(pane, files))
            }
        }
        actions += MenuAction(stringResource(R.string.action_create_checksums), Icons.Filled.Fingerprint) {
            Dialogs.show(DialogSpec.Checksum(pane, files))
        }
        if (single != null) {
            actions += MenuAction(stringResource(R.string.action_split), Icons.Filled.CallSplit) {
                Dialogs.show(DialogSpec.Split(File(single.path)))
            }
        }
    }
    if (entries.size == 2 && entries.none { it.isDirectory }) {
        actions += MenuAction(stringResource(R.string.action_compare), Icons.Filled.Compare) {
            pane.clearSelection()
            AppState.push(Screen.Diff(entries[0].path, entries[1].path))
        }
    }
    if (single != null) {
        if (single.isDirectory) {
            actions += MenuAction(stringResource(R.string.action_add_bookmark), Icons.Filled.Bookmark) {
                Prefs.addBookmark(single.path)
                AppState.toast(context.getString(R.string.msg_bookmark_added))
            }
            actions += MenuAction(stringResource(R.string.tool_analyzer), Icons.Filled.PieChart) {
                AppState.push(Screen.Analyzer(single.path))
            }
            actions += MenuAction(stringResource(R.string.tool_duplicates), Icons.Filled.FileCopy) {
                AppState.push(Screen.Duplicates(single.path))
            }
            actions += MenuAction(stringResource(R.string.action_home_shortcut), Icons.Filled.AddToHomeScreen) {
                Shortcuts.pinFolder(context, single.path)
            }
        } else {
            actions += MenuAction(stringResource(R.string.action_checksum), Icons.Filled.Fingerprint) {
                Dialogs.show(DialogSpec.Hash(File(single.path)))
            }
        }
        actions += MenuAction(stringResource(R.string.action_permissions), Icons.Filled.Lock) {
            Dialogs.show(DialogSpec.Permissions(pane, single))
        }
    }
    actions += MenuAction(sProps, Icons.Filled.Info) { Dialogs.show(DialogSpec.Properties(pane, entries)) }

    MenuDialog(
        title = single?.name ?: stringResource(R.string.n_selected, entries.size),
        subtitle = single?.let {
            if (it.isDirectory) Format.date(it.lastModified) else Format.size(it.size) + "  " + Format.date(it.lastModified)
        },
        actions = actions,
        onDismiss = onDismiss,
        leading = leadingIcon
    )
}

