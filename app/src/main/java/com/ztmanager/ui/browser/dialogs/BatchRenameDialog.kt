package com.ztmanager.ui.browser.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ztmanager.R
import com.ztmanager.fs.FileEntry
import com.ztmanager.ui.AppState
import com.ztmanager.ui.browser.DialogSpec
import com.ztmanager.ui.theme.ZtTheme
import java.io.File

private fun splitName(e: FileEntry): Pair<String, String> {
    val dot = e.name.lastIndexOf('.')
    return if (!e.isDirectory && dot > 0) e.name.substring(0, dot) to e.name.substring(dot + 1) else e.name to ""
}

/**
 * Batch rename with either a pattern ({name}, {ext}, {n}) or find and replace
 * (optionally with regular expressions).
 */
@Composable
fun BatchRenameDialog(spec: DialogSpec.BatchRename, onDismiss: () -> Unit) {
    val entries = spec.entries
    var patternMode by remember { mutableStateOf(true) }
    var pattern by remember { mutableStateOf("{name}_{n}.{ext}") }
    var start by remember { mutableStateOf("1") }
    var find by remember { mutableStateOf("") }
    var replace by remember { mutableStateOf("") }
    var regex by remember { mutableStateOf(false) }
    var case by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    val failedFmt = stringResource(R.string.msg_rename_failed)

    fun rawName(e: FileEntry, i: Int): String {
        val (base, ext) = splitName(e)
        return if (patternMode) {
            val n = (start.toIntOrNull() ?: 1) + i
            val width = entries.size.toString().length
            val date = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date(e.lastModified))
            val time = java.text.SimpleDateFormat("HHmmss", java.util.Locale.US).format(java.util.Date(e.lastModified))
            var out = pattern.replace("{name}", base).replace("{ext}", ext)
                .replace("{n}", n.toString().padStart(width, '0'))
                .replace("{date}", date).replace("{time}", time)
            if (ext.isEmpty()) out = out.removeSuffix(".")
            out
        } else {
            if (find.isEmpty()) e.name
            else if (regex) runCatching { e.name.replace(Regex(find), replace) }.getOrDefault(e.name)
            else e.name.replace(find, replace)
        }
    }

    fun newName(e: FileEntry, i: Int): String {
        val raw = rawName(e, i)
        return when (case) {
            1 -> raw.lowercase()
            2 -> raw.uppercase()
            else -> raw
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_batch_rename)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row {
                    FilterChip(selected = patternMode, onClick = { patternMode = true }, label = { Text(stringResource(R.string.rename_pattern)) })
                    Spacer(Modifier.padding(4.dp))
                    FilterChip(selected = !patternMode, onClick = { patternMode = false }, label = { Text(stringResource(R.string.rename_replace)) })
                }
                if (patternMode) {
                    OutlinedTextField(pattern, { pattern = it }, label = { Text(stringResource(R.string.rename_pattern)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(start, { start = it.filter { c -> c.isDigit() } }, label = { Text(stringResource(R.string.rename_start)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.rename_pattern_help), style = MaterialTheme.typography.bodySmall, color = ZtTheme.colors.secondaryText)
                } else {
                    OutlinedTextField(find, { find = it }, label = { Text(stringResource(R.string.find)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(replace, { replace = it }, label = { Text(stringResource(R.string.replace_with)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    CheckRow(stringResource(R.string.regex), regex) { regex = it }
                }
                Row(Modifier.padding(top = 6.dp)) {
                    listOf(R.string.case_keep, R.string.case_lower, R.string.case_upper).forEachIndexed { i, label ->
                        FilterChip(selected = case == i, onClick = { case = i }, label = { Text(stringResource(label)) }, modifier = Modifier.padding(end = 6.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.preview), style = MaterialTheme.typography.labelLarge)
                entries.take(6).forEachIndexed { i, e ->
                    Text(
                        e.name + "  ->  " + newName(e, i),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (entries.size > 6) Text("...", style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                var failed = 0
                entries.forEachIndexed { i, e ->
                    val n = newName(e, i)
                    if (n.isNotBlank() && n != e.name && !n.contains('/')) {
                        val src = File(e.path)
                        val dst = File(src.parentFile, n)
                        if (dst.exists() || !src.renameTo(dst)) failed++
                    }
                }
                onDismiss()
                spec.pane.clearSelection()
                AppState.refreshAll()
                if (failed > 0) AppState.toast(String.format(failedFmt, failed))
            }) { Text(stringResource(R.string.apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
