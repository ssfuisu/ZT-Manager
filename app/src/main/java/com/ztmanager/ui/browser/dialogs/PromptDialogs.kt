package com.ztmanager.ui.browser.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.ztmanager.R
import com.ztmanager.fs.Format
import com.ztmanager.task.ConflictAction
import com.ztmanager.task.ConflictAnswer
import com.ztmanager.task.Prompt
import com.ztmanager.task.TaskManager
import com.ztmanager.ui.theme.ZtTheme

@Composable
fun PasswordDialog(p: Prompt.Password) {
    var pw by remember(p) { mutableStateOf("") }
    var show by remember(p) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { p.result.complete(null) },
        properties = DialogProperties(dismissOnClickOutside = false),
        icon = { Icon(Icons.Filled.VpnKey, null) },
        title = { Text(stringResource(R.string.password_required)) },
        text = {
            Column {
                Text(p.target, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (p.wrongPassword) {
                    Text(stringResource(R.string.wrong_password), color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = pw,
                    onValueChange = { pw = it },
                    label = { Text(stringResource(R.string.password)) },
                    singleLine = true,
                    visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { p.result.complete(pw) }),
                    trailingIcon = {
                        IconButton(onClick = { show = !show }) {
                            Icon(if (show) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = { TextButton(onClick = { p.result.complete(pw) }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = { p.result.complete(null) }) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
fun ConflictDialog(p: Prompt.Conflict) {
    var all by remember(p) { mutableStateOf(false) }
    fun answer(a: ConflictAction) = p.result.complete(ConflictAnswer(a, all))
    AlertDialog(
        onDismissRequest = { answer(ConflictAction.CANCEL) },
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text(stringResource(R.string.conflict_title)) },
        text = {
            Column {
                Text(stringResource(R.string.conflict_message, p.name))
                Spacer(Modifier.height(8.dp))
                CheckRow(stringResource(R.string.apply_to_all), all) { all = it }
                Spacer(Modifier.height(8.dp))
                Button(onClick = { answer(ConflictAction.OVERWRITE) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.overwrite))
                }
                OutlinedButton(onClick = { answer(ConflictAction.RENAME) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.keep_both))
                }
                OutlinedButton(onClick = { answer(ConflictAction.SKIP) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.skip))
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { answer(ConflictAction.CANCEL) }) { Text(stringResource(R.string.cancel)) } }
    )
}

/** ZArchiver style progress dialog for running tasks, with "Background" and "Cancel". */
@Composable
fun TaskProgressDialogs() {
    val task = TaskManager.tasks.firstOrNull { !it.background } ?: return
    AlertDialog(
        onDismissRequest = { task.background = true },
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text(task.title) },
        text = {
            Column {
                Text(task.message, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                if (task.progress < 0f) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(progress = { task.progress }, modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(6.dp))
                Row {
                    if (task.itemsTotal > 0) {
                        Text("${task.itemsDone} / ${task.itemsTotal}", style = MaterialTheme.typography.bodySmall, color = ZtTheme.colors.secondaryText)
                        Spacer(Modifier.width(12.dp))
                    }
                    if (task.bytesTotal > 0) {
                        Text(
                            Format.size(task.bytesDone) + " / " + Format.size(task.bytesTotal),
                            style = MaterialTheme.typography.bodySmall,
                            color = ZtTheme.colors.secondaryText
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (task.progress >= 0f) {
                        Text("${(task.progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { task.background = true }) { Text(stringResource(R.string.background)) } },
        dismissButton = { TextButton(onClick = { task.cancel() }) { Text(stringResource(R.string.cancel)) } }
    )
}
