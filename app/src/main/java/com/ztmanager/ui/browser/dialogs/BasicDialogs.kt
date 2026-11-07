package com.ztmanager.ui.browser.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.GetApp
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Subject
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.ztmanager.R
import com.ztmanager.data.Prefs
import com.ztmanager.fs.FileEntry
import com.ztmanager.fs.FileOps
import com.ztmanager.fs.Format
import com.ztmanager.ui.AppState
import com.ztmanager.ui.Screen
import com.ztmanager.ui.browser.Actions
import com.ztmanager.ui.browser.DialogSpec
import com.ztmanager.ui.browser.Dialogs
import com.ztmanager.ui.browser.OpenAs
import com.ztmanager.ui.browser.Opener
import com.ztmanager.ui.common.FileIcon
import com.ztmanager.ui.common.Intents
import java.io.File

private fun entryOf(f: File) = FileEntry.fromFile(f, false)

@Composable
fun ArchiveTapDialog(spec: DialogSpec.ArchiveTap, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val f = spec.file
    val folder = Actions.folderNameFor(f)
    val other = AppState.panes[1 - spec.pane.index].currentDir
    val pick = stringResource(R.string.title_select_folder)
    val bundle = com.ztmanager.apk.SplitInstaller.isBundle(f)
    val installLabel = stringResource(R.string.action_install_bundle)
    val actions = listOfNotNull(
        if (bundle) MenuAction(installLabel, Icons.Filled.GetApp) { Actions.installBundle(context, f) } else null,
        MenuAction(stringResource(R.string.action_browse_archive), Icons.Filled.Archive) { spec.pane.openArchive(f) },
        MenuAction(stringResource(R.string.action_extract_here), Icons.Filled.Unarchive) {
            Actions.extract(context, f, null, "", f.parentFile!!)
        },
        MenuAction(stringResource(R.string.action_extract_to_folder, "$folder/"), Icons.Filled.Unarchive) {
            Actions.extract(context, f, null, "", File(f.parentFile, folder))
        },
        MenuAction(stringResource(R.string.action_extract_to), Icons.Filled.Unarchive) {
            Dialogs.show(DialogSpec.FolderPicker(pick, other?.path ?: f.parent ?: "/") { dest ->
                Actions.extract(context, f, null, "", dest)
            })
        },
        MenuAction(stringResource(R.string.action_open_as), Icons.Filled.Code) {
            Dialogs.show(DialogSpec.OpenAsChooser(f, spec.pane, false))
        }
    )
    MenuDialog(
        title = f.name,
        subtitle = Format.size(f.length()),
        actions = actions,
        onDismiss = onDismiss,
        leading = { FileIcon(entryOf(f), size = 36.dp) }
    )
}

@Composable
fun ApkTapDialog(spec: DialogSpec.ApkTap, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val f = spec.file
    val actions = listOf(
        MenuAction(stringResource(R.string.action_install), Icons.Filled.GetApp) { Intents.install(context, f) },
        MenuAction(stringResource(R.string.action_apk_info), Icons.Filled.Android) { AppState.push(Screen.ApkInfo(f.absolutePath)) },
        MenuAction(stringResource(R.string.action_browse_archive), Icons.Filled.Archive) { spec.pane.openArchive(f) },
        MenuAction(stringResource(R.string.action_open_as), Icons.Filled.Code) {
            Dialogs.show(DialogSpec.OpenAsChooser(f, spec.pane, spec.fromArchive))
        }
    )
    MenuDialog(
        title = f.name,
        subtitle = Format.size(f.length()),
        actions = actions,
        onDismiss = onDismiss,
        leading = { FileIcon(entryOf(f), size = 36.dp) }
    )
}

@Composable
fun OpenAsDialog(spec: DialogSpec.OpenAsChooser, onDismiss: () -> Unit) {
    val context = LocalContext.current
    fun go(kind: OpenAs) = Opener.openAs(context, spec.pane, spec.file, kind, spec.readOnly)
    val actions = listOf(
        MenuAction(stringResource(R.string.open_as_text), Icons.Filled.Subject) { go(OpenAs.TEXT) },
        MenuAction(stringResource(R.string.open_as_hex), Icons.Filled.Memory) { go(OpenAs.HEX) },
        MenuAction(stringResource(R.string.open_as_image), Icons.Filled.Image) { go(OpenAs.IMAGE) },
        MenuAction(stringResource(R.string.open_as_archive), Icons.Filled.Archive) { go(OpenAs.ARCHIVE) },
        MenuAction(stringResource(R.string.open_as_apk), Icons.Filled.Android) { go(OpenAs.APK) },
        MenuAction(stringResource(R.string.open_as_audio), Icons.Filled.MusicNote) { go(OpenAs.AUDIO) },
        MenuAction(stringResource(R.string.open_as_video), Icons.Filled.Movie) { go(OpenAs.VIDEO) },
        MenuAction(stringResource(R.string.open_as_pdf), Icons.Filled.PictureAsPdf) { go(OpenAs.PDF) },
        MenuAction(stringResource(R.string.open_as_sqlite), Icons.Filled.Storage) { go(OpenAs.SQLITE) },
        MenuAction(stringResource(R.string.open_as_external), Icons.Filled.OpenInNew) { go(OpenAs.EXTERNAL) }
    )
    MenuDialog(stringResource(R.string.action_open_as), spec.file.name, actions, onDismiss)
}

@Composable
fun NameField(value: TextFieldValue, onChange: (TextFieldValue) -> Unit, label: String, onDone: () -> Unit) {
    val focus = remember { FocusRequester() }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onDone() }),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focus)
    )
    LaunchedEffect(Unit) {
        try {
            focus.requestFocus()
        } catch (_: Exception) {
        }
    }
}

@Composable
fun NewItemDialog(spec: DialogSpec.NewItem, onDismiss: () -> Unit) {
    val dir = spec.pane.currentDir ?: return onDismiss()
    var folder by remember { mutableStateOf(true) }
    var name by remember { mutableStateOf(TextFieldValue("")) }
    var error by remember { mutableStateOf<String?>(null) }
    val defFolder = stringResource(R.string.new_folder_default)
    val defFile = stringResource(R.string.new_file_default)
    fun create() {
        val n = name.text.trim().ifEmpty { if (folder) defFolder else defFile }
        try {
            if (folder) FileOps.mkdir(dir, n) else FileOps.createFile(dir, n)
            onDismiss()
            spec.pane.refresh()
        } catch (e: Exception) {
            error = e.message
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_new)) },
        text = {
            Column {
                Row {
                    FilterChip(selected = folder, onClick = { folder = true }, label = { Text(stringResource(R.string.folder)) })
                    Spacer(Modifier.padding(4.dp))
                    FilterChip(selected = !folder, onClick = { folder = false }, label = { Text(stringResource(R.string.file)) })
                }
                Spacer(Modifier.height(8.dp))
                NameField(name, { name = it; error = null }, stringResource(R.string.name)) { create() }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onClick = { create() }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
fun RenameDialog(spec: DialogSpec.Rename, onDismiss: () -> Unit) {
    val entry = spec.entry
    val dot = entry.name.lastIndexOf('.')
    val end = if (!entry.isDirectory && dot > 0) dot else entry.name.length
    var name by remember { mutableStateOf(TextFieldValue(entry.name, TextRange(0, end))) }
    var error by remember { mutableStateOf<String?>(null) }
    fun apply() {
        val n = name.text.trim()
        if (n.isEmpty() || n == entry.name) {
            onDismiss()
            return
        }
        if (n.contains('/')) {
            error = "/"
            return
        }
        try {
            if (entry.inArchive) {
                val session = spec.pane.archive ?: return
                session.handle.rename(entry.path, n)
                session.reload()
            } else {
                FileOps.rename(File(entry.path), n)
            }
            onDismiss()
            AppState.refreshAll()
        } catch (e: Exception) {
            error = e.message
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_rename)) },
        text = {
            Column {
                NameField(name, { name = it; error = null }, stringResource(R.string.name)) { apply() }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onClick = { apply() }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
fun DeleteDialog(spec: DialogSpec.Delete, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val entries = spec.entries
    val inArchive = entries.firstOrNull()?.inArchive == true
    val trashPossible = !inArchive && entries.all { com.ztmanager.fs.Trash.supports(File(it.path)) }
    var permanent by remember { mutableStateOf(!Prefs.useTrash || !trashPossible) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_delete)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    if (entries.size == 1) stringResource(R.string.confirm_delete_one, entries[0].name)
                    else stringResource(R.string.confirm_delete_many, entries.size)
                )
                if (entries.size in 2..8) {
                    Spacer(Modifier.height(8.dp))
                    entries.forEach { Text("- " + it.name, style = MaterialTheme.typography.bodySmall) }
                }
                if (trashPossible) {
                    Spacer(Modifier.height(8.dp))
                    CheckRow(stringResource(R.string.delete_permanently), permanent) { permanent = it }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                Actions.delete(context, spec.pane, entries, permanent)
            }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
fun SortDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_sort)) },
        text = {
            Column {
                RadioRow(stringResource(R.string.sort_name), Prefs.sortBy == Prefs.SORT_NAME) { Prefs.sortBy = Prefs.SORT_NAME }
                RadioRow(stringResource(R.string.sort_size), Prefs.sortBy == Prefs.SORT_SIZE) { Prefs.sortBy = Prefs.SORT_SIZE }
                RadioRow(stringResource(R.string.sort_date), Prefs.sortBy == Prefs.SORT_DATE) { Prefs.sortBy = Prefs.SORT_DATE }
                RadioRow(stringResource(R.string.sort_type), Prefs.sortBy == Prefs.SORT_TYPE) { Prefs.sortBy = Prefs.SORT_TYPE }
                Spacer(Modifier.height(6.dp))
                CheckRow(stringResource(R.string.sort_descending), Prefs.sortDesc) { Prefs.sortDesc = it }
                CheckRow(stringResource(R.string.sort_folders_first), Prefs.foldersFirst) { Prefs.foldersFirst = it }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } }
    )
}

@Composable
fun InputDialog(spec: DialogSpec.Input, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf(TextFieldValue(spec.initial, TextRange(spec.initial.length))) }
    fun ok() {
        onDismiss()
        spec.onOk(value.text)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(spec.title) },
        text = { NameField(value, { value = it }, spec.hint) { ok() } },
        confirmButton = { TextButton(onClick = { ok() }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
fun ConfirmDialog(spec: DialogSpec.Confirm, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(spec.title) },
        text = { Text(spec.message) },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                spec.onOk()
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
