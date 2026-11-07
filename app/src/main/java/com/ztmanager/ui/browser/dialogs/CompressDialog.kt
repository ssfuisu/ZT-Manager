package com.ztmanager.ui.browser.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.ztmanager.R
import com.ztmanager.archive.CreateFormat
import com.ztmanager.data.Prefs
import com.ztmanager.ui.browser.Actions
import com.ztmanager.ui.browser.DialogSpec
import com.ztmanager.ui.browser.Dialogs
import com.ztmanager.ui.theme.ZtTheme
import java.io.File

@Composable
fun CompressDialog(spec: DialogSpec.Compress, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val files = spec.files
    val singleFile = files.size == 1 && files[0].isFile
    val baseName = remember {
        if (files.size == 1) files[0].name.let { n ->
            if (files[0].isFile && n.lastIndexOf('.') > 0) n.substring(0, n.lastIndexOf('.')) else n
        } else (files[0].parentFile?.name ?: "archive")
    }
    var name by remember { mutableStateOf(baseName) }
    var format by remember { mutableStateOf(CreateFormat.ZIP) }
    var level by remember { mutableFloatStateOf(Prefs.compressionLevel.toFloat()) }
    var password by remember { mutableStateOf("") }
    var showPw by remember { mutableStateOf(false) }
    var deleteSource by remember { mutableStateOf(false) }
    var dest by remember { mutableStateOf(spec.destDir) }
    var each by remember { mutableStateOf(false) }
    val pickTitle = stringResource(R.string.title_select_folder)

    val formats = CreateFormat.values().filter { it.multiFile || singleFile || (each && files.all { f -> f.isFile }) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_compress)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.archive_name)) },
                    suffix = { Text("." + format.ext) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.archive_format), style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    formats.forEach { f ->
                        FilterChip(
                            selected = f == format,
                            onClick = { format = f },
                            label = { Text(f.label) },
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.compression_level, level.toInt()) + "  " + levelName(level.toInt()),
                    style = MaterialTheme.typography.labelLarge
                )
                Slider(value = level, onValueChange = { level = it }, valueRange = 0f..9f, steps = 8)
                if (format.supportsPassword) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.password_optional)) },
                        singleLine = true,
                        visualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { showPw = !showPw }) {
                                Icon(if (showPw) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (password.isNotEmpty()) {
                        Text(stringResource(R.string.aes_note), style = MaterialTheme.typography.bodySmall, color = ZtTheme.colors.secondaryText)
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            Dialogs.show(DialogSpec.FolderPicker(pickTitle, dest.path) { dest = it })
                        }
                        .padding(vertical = 6.dp)
                ) {
                    Icon(Icons.Filled.FolderOpen, null, tint = ZtTheme.colors.folder)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(stringResource(R.string.destination), style = MaterialTheme.typography.labelMedium, color = ZtTheme.colors.secondaryText)
                        Text(dest.path, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (files.size > 1) CheckRow(stringResource(R.string.compress_each), each) { each = it }
                CheckRow(stringResource(R.string.delete_after_compress), deleteSource) { deleteSource = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val n = name.trim().ifEmpty { "archive" }
                val out = File(dest, n + "." + format.ext)
                Prefs.compressionLevel = level.toInt()
                onDismiss()
                spec.pane.clearSelection()
                if (each && files.size > 1) {
                    val fmt = if (format.multiFile || files.all { it.isFile }) format else CreateFormat.ZIP
                    Actions.compressEach(context, files, dest, fmt, level.toInt(), password.ifEmpty { null }, deleteSource)
                } else {
                    Actions.compress(context, files, out, format, level.toInt(), password.ifEmpty { null }, deleteSource)
                }
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun levelName(level: Int): String = when (level) {
    0 -> stringResource(R.string.level_store)
    in 1..2 -> stringResource(R.string.level_fastest)
    in 3..4 -> stringResource(R.string.level_fast)
    5 -> stringResource(R.string.level_normal)
    in 6..7 -> stringResource(R.string.level_high)
    else -> stringResource(R.string.level_ultra)
}
