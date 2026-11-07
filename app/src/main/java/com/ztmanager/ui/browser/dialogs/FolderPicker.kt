package com.ztmanager.ui.browser.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ztmanager.R
import com.ztmanager.data.Prefs
import com.ztmanager.fs.FileOps
import com.ztmanager.fs.StorageUtil
import com.ztmanager.ui.AppState
import com.ztmanager.ui.browser.DialogSpec
import com.ztmanager.ui.browser.Dialogs
import com.ztmanager.ui.browser.NaturalOrder
import com.ztmanager.ui.theme.ZtTheme
import java.io.File

@Composable
fun FolderPickerDialog(spec: DialogSpec.FolderPicker, onDismiss: () -> Unit) {
    var path by remember { mutableStateOf(File(spec.start).takeIf { it.isDirectory }?.absolutePath ?: StorageUtil.internalRoot) }
    var version by remember { mutableStateOf(0) }
    val dirs = remember(path, version) {
        (File(path).listFiles()?.filter { it.isDirectory && (Prefs.showHidden || !it.name.startsWith(".")) } ?: emptyList())
            .sortedWith { a, b -> NaturalOrder.compare(a.name, b.name) }
    }
    val newFolderTitle = stringResource(R.string.action_new_folder)
    val nameHint = stringResource(R.string.name)
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Text(
                    spec.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 20.dp, top = 18.dp, end = 20.dp)
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
                    IconButton(onClick = { File(path).parent?.let { path = it } }) { Icon(Icons.Filled.ArrowUpward, null) }
                    IconButton(onClick = { path = StorageUtil.internalRoot }) { Icon(Icons.Filled.Home, null) }
                    Text(
                        path,
                        style = MaterialTheme.typography.bodySmall,
                        color = ZtTheme.colors.secondaryText,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        Dialogs.show(DialogSpec.Input(newFolderTitle, "", nameHint) { n ->
                            if (n.isNotBlank()) {
                                try {
                                    val f = FileOps.mkdir(File(path), n.trim())
                                    path = f.absolutePath
                                } catch (e: Exception) {
                                    AppState.toast(e.message)
                                }
                                version++
                            }
                        })
                    }) { Icon(Icons.Filled.CreateNewFolder, null) }
                }
                HorizontalDivider(color = ZtTheme.colors.divider)
                LazyColumn(Modifier.height(360.dp)) {
                    items(dirs, key = { it.absolutePath }) { d ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { path = d.absolutePath }
                                .padding(horizontal = 20.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.Filled.Folder, null, tint = ZtTheme.colors.folder, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.width(14.dp))
                            Text(d.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                HorizontalDivider(color = ZtTheme.colors.divider)
                Row(Modifier.fillMaxWidth().padding(8.dp)) {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    TextButton(onClick = {
                        onDismiss()
                        spec.onPick(File(path))
                    }) { Text(stringResource(R.string.select_here)) }
                }
            }
        }
    }
}
