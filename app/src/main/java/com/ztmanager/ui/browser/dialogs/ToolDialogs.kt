package com.ztmanager.ui.browser.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.ztmanager.R
import com.ztmanager.fs.Format
import com.ztmanager.ui.browser.Actions
import com.ztmanager.ui.browser.DialogSpec
import com.ztmanager.ui.theme.ZtTheme

@Composable
fun MessageDialog(spec: DialogSpec.Message, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(spec.title) },
        text = {
            SelectionContainer(Modifier.verticalScroll(rememberScrollState())) { Text(spec.message) }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } }
    )
}

@Composable
fun CryptoDialog(spec: DialogSpec.Crypto, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    var deleteSource by remember { mutableStateOf(false) }
    val mismatch = !spec.decrypt && confirm.isNotEmpty() && confirm != password
    val canRun = password.isNotEmpty() && (spec.decrypt || confirm == password)
    val transform = if (show) VisualTransformation.None else PasswordVisualTransformation()
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(if (spec.decrypt) Icons.Filled.LockOpen else Icons.Filled.Lock, null) },
        title = { Text(stringResource(if (spec.decrypt) R.string.action_decrypt else R.string.action_encrypt)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    if (spec.files.size == 1) spec.files[0].name else stringResource(R.string.n_items, spec.files.size),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.password)) },
                    singleLine = true,
                    visualTransformation = transform,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { show = !show }) {
                            Icon(if (show) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                if (!spec.decrypt) {
                    OutlinedTextField(
                        value = confirm,
                        onValueChange = { confirm = it },
                        label = { Text(stringResource(R.string.password_confirm)) },
                        singleLine = true,
                        isError = mismatch,
                        visualTransformation = transform,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (mismatch) Text(stringResource(R.string.password_mismatch), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.encrypt_note), style = MaterialTheme.typography.bodySmall, color = ZtTheme.colors.secondaryText)
                }
                CheckRow(stringResource(if (spec.decrypt) R.string.delete_after_decrypt else R.string.delete_after_encrypt), deleteSource) { deleteSource = it }
            }
        },
        confirmButton = {
            TextButton(enabled = canRun, onClick = {
                onDismiss()
                spec.pane.clearSelection()
                if (spec.decrypt) Actions.decrypt(context, spec.files, password, deleteSource)
                else Actions.encrypt(context, spec.files, password, deleteSource)
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
fun SplitDialog(spec: DialogSpec.Split, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var size by remember { mutableStateOf("100") }
    var unitMb by remember { mutableStateOf(true) }
    val bytes = (size.toLongOrNull() ?: 0L) * if (unitMb) 1024L * 1024 else 1024L
    val parts = if (bytes > 0) (spec.file.length() + bytes - 1) / bytes else 0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_split)) },
        text = {
            Column {
                Text(spec.file.name + "  (" + Format.size(spec.file.length()) + ")", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = size,
                    onValueChange = { size = it.filter { c -> c.isDigit() }.take(9) },
                    label = { Text(stringResource(R.string.part_size)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(Modifier.padding(top = 6.dp)) {
                    FilterChip(selected = unitMb, onClick = { unitMb = true }, label = { Text("MB") }, modifier = Modifier.padding(end = 6.dp))
                    FilterChip(selected = !unitMb, onClick = { unitMb = false }, label = { Text("KB") })
                }
                Row(Modifier.padding(top = 4.dp)) {
                    listOf(10L, 25L, 50L, 100L, 700L, 1024L).forEach { v ->
                        TextButton(onClick = { size = v.toString(); unitMb = true }) { Text(if (v >= 1024) "1G" else "${v}M") }
                    }
                }
                Text(stringResource(R.string.split_parts, parts), style = MaterialTheme.typography.bodySmall, color = ZtTheme.colors.secondaryText)
            }
        },
        confirmButton = {
            TextButton(enabled = bytes > 0 && parts in 1..9999, onClick = {
                onDismiss()
                Actions.split(context, spec.file, bytes)
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
