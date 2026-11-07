package com.ztmanager.ui.browser

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import com.ztmanager.fs.FileEntry
import com.ztmanager.task.Prompt
import com.ztmanager.task.Prompter
import com.ztmanager.ui.browser.dialogs.ApkTapDialog
import com.ztmanager.ui.browser.dialogs.ArchiveTapDialog
import com.ztmanager.ui.browser.dialogs.BatchRenameDialog
import com.ztmanager.ui.browser.dialogs.CompressDialog
import com.ztmanager.ui.browser.dialogs.ConfirmDialog
import com.ztmanager.ui.browser.dialogs.ConflictDialog
import com.ztmanager.ui.browser.dialogs.CryptoDialog
import com.ztmanager.ui.browser.dialogs.MessageDialog
import com.ztmanager.ui.browser.dialogs.SplitDialog
import com.ztmanager.ui.browser.dialogs.ImageConvertDialog
import com.ztmanager.ui.browser.dialogs.ChecksumDialog
import com.ztmanager.ui.browser.dialogs.ContextMenuDialog
import com.ztmanager.ui.browser.dialogs.DeleteDialog
import com.ztmanager.ui.browser.dialogs.FolderPickerDialog
import com.ztmanager.ui.browser.dialogs.HashDialog
import com.ztmanager.ui.browser.dialogs.InputDialog
import com.ztmanager.ui.browser.dialogs.NewItemDialog
import com.ztmanager.ui.browser.dialogs.OpenAsDialog
import com.ztmanager.ui.browser.dialogs.PasswordDialog
import com.ztmanager.ui.browser.dialogs.PermissionsDialog
import com.ztmanager.ui.browser.dialogs.PropertiesDialog
import com.ztmanager.ui.browser.dialogs.RenameDialog
import com.ztmanager.ui.browser.dialogs.SortDialog
import com.ztmanager.ui.browser.dialogs.TaskProgressDialogs
import java.io.File

sealed class DialogSpec {
    class ContextMenu(val pane: PaneState, val entries: List<FileEntry>) : DialogSpec()
    class ArchiveTap(val file: File, val pane: PaneState) : DialogSpec()
    class ApkTap(val file: File, val pane: PaneState, val fromArchive: Boolean) : DialogSpec()
    class OpenAsChooser(val file: File, val pane: PaneState, val readOnly: Boolean) : DialogSpec()
    class NewItem(val pane: PaneState) : DialogSpec()
    class Rename(val pane: PaneState, val entry: FileEntry) : DialogSpec()
    class BatchRename(val pane: PaneState, val entries: List<FileEntry>) : DialogSpec()
    class Delete(val pane: PaneState, val entries: List<FileEntry>) : DialogSpec()
    class Compress(val pane: PaneState, val files: List<File>, val destDir: File) : DialogSpec()
    class FolderPicker(val title: String, val start: String, val onPick: (File) -> Unit) : DialogSpec()
    class Properties(val pane: PaneState, val entries: List<FileEntry>) : DialogSpec()
    class Hash(val file: File) : DialogSpec()
    class Permissions(val pane: PaneState, val entry: FileEntry) : DialogSpec()
    data object Sort : DialogSpec()
    class Input(val title: String, val initial: String, val hint: String = "", val onOk: (String) -> Unit) : DialogSpec()
    class Confirm(val title: String, val message: String, val onOk: () -> Unit) : DialogSpec()
    class Message(val title: String, val message: String) : DialogSpec()
    class Crypto(val pane: PaneState, val files: List<File>, val decrypt: Boolean) : DialogSpec()
    class Split(val file: File) : DialogSpec()
    class ImageConvert(val pane: PaneState, val files: List<File>) : DialogSpec()
    class Checksum(val pane: PaneState, val files: List<File>) : DialogSpec()
}

object Dialogs {
    val stack = mutableStateListOf<DialogSpec>()

    fun show(spec: DialogSpec) {
        stack.add(spec)
    }

    fun dismiss(spec: DialogSpec) {
        stack.remove(spec)
    }
}

@Composable
fun DialogHost() {
    for (spec in Dialogs.stack.toList()) {
        key(spec) {
        val close = { Dialogs.dismiss(spec) }
        when (spec) {
            is DialogSpec.ContextMenu -> ContextMenuDialog(spec, close)
            is DialogSpec.ArchiveTap -> ArchiveTapDialog(spec, close)
            is DialogSpec.ApkTap -> ApkTapDialog(spec, close)
            is DialogSpec.OpenAsChooser -> OpenAsDialog(spec, close)
            is DialogSpec.NewItem -> NewItemDialog(spec, close)
            is DialogSpec.Rename -> RenameDialog(spec, close)
            is DialogSpec.BatchRename -> BatchRenameDialog(spec, close)
            is DialogSpec.Delete -> DeleteDialog(spec, close)
            is DialogSpec.Compress -> CompressDialog(spec, close)
            is DialogSpec.FolderPicker -> FolderPickerDialog(spec, close)
            is DialogSpec.Properties -> PropertiesDialog(spec, close)
            is DialogSpec.Hash -> HashDialog(spec, close)
            is DialogSpec.Permissions -> PermissionsDialog(spec, close)
            is DialogSpec.Sort -> SortDialog(close)
            is DialogSpec.Input -> InputDialog(spec, close)
            is DialogSpec.Confirm -> ConfirmDialog(spec, close)
            is DialogSpec.Message -> MessageDialog(spec, close)
            is DialogSpec.Crypto -> CryptoDialog(spec, close)
            is DialogSpec.Split -> SplitDialog(spec, close)
            is DialogSpec.ImageConvert -> ImageConvertDialog(spec, close)
            is DialogSpec.Checksum -> ChecksumDialog(spec, close)
        }
        }
    }
    when (val p = Prompter.current) {
        is Prompt.Password -> PasswordDialog(p)
        is Prompt.Conflict -> ConflictDialog(p)
        null -> {}
    }
    TaskProgressDialogs()
}
