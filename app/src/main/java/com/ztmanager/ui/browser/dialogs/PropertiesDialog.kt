package com.ztmanager.ui.browser.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ztmanager.R
import com.ztmanager.fs.FileOps
import com.ztmanager.fs.FileType
import com.ztmanager.fs.FolderStats
import com.ztmanager.fs.Format
import com.ztmanager.fs.Hashing
import com.ztmanager.ui.browser.DialogSpec
import com.ztmanager.ui.browser.Dialogs
import com.ztmanager.ui.common.Intents
import com.ztmanager.ui.theme.ZtTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

@Composable
fun PropertiesDialog(spec: DialogSpec.Properties, onDismiss: () -> Unit) {
    val entries = spec.entries
    val single = entries.singleOrNull()
    var stats by remember { mutableStateOf<FolderStats?>(null) }
    var media by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    val labels = MediaLabels(
        stringResource(R.string.media_duration), stringResource(R.string.resolution),
        stringResource(R.string.media_bitrate), stringResource(R.string.media_title),
        stringResource(R.string.media_artist), stringResource(R.string.media_album)
    )
    val needsStats = !entries.first().inArchive && entries.any { it.isDirectory } || entries.size > 1
    LaunchedEffect(Unit) {
        val s1 = single
        if (s1 != null && !s1.inArchive && !s1.isDirectory) {
            media = withContext(Dispatchers.IO) { mediaInfo(File(s1.path), s1.type, labels) }
        }
        if (needsStats && !entries.first().inArchive) {
            stats = withContext(Dispatchers.IO) { FileOps.stats(entries.map { File(it.path) }) }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_properties)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (single != null) {
                    InfoRow(stringResource(R.string.name), single.name)
                    InfoRow(stringResource(R.string.path), single.path)
                    InfoRow(
                        stringResource(R.string.type),
                        if (single.isDirectory) stringResource(R.string.folder) else FileType.mimeOf(single.name)
                    )
                    if (single.isDirectory && !single.inArchive) {
                        val s = stats
                        InfoRow(
                            stringResource(R.string.size),
                            if (s == null) stringResource(R.string.calculating)
                            else Format.size(s.size) + " (" + s.size + " B)"
                        )
                        if (s != null) InfoRow(stringResource(R.string.contents), stringResource(R.string.files_folders, s.files, s.dirs))
                    } else if (!single.isDirectory) {
                        InfoRow(stringResource(R.string.size), Format.size(single.size) + " (" + single.size + " B)")
                        if (single.inArchive && single.packedSize >= 0) {
                            InfoRow(stringResource(R.string.packed_size), Format.size(single.packedSize))
                        }
                    }
                    InfoRow(stringResource(R.string.modified), Format.dateLong(single.lastModified))
                    media.forEach { (k, v) -> InfoRow(k, v) }
                    if (!single.inArchive) {
                        val f = File(single.path)
                        InfoRow(stringResource(R.string.permissions), single.permissions + "  (" + Integer.toOctalString(FileOps.readMode(single.path)) + ")")
                        InfoRow(
                            stringResource(R.string.access),
                            (if (f.canRead()) "R" else "-") + (if (f.canWrite()) "W" else "-") + (if (f.canExecute()) "X" else "-") +
                                if (single.isHidden) "  " + stringResource(R.string.hidden) else ""
                        )
                        if (single.isSymlink) {
                            InfoRow(stringResource(R.string.link_target), runCatching { f.canonicalPath }.getOrDefault("?"))
                        }
                    } else if (single.encrypted) {
                        InfoRow(stringResource(R.string.encryption), stringResource(R.string.encrypted))
                    }
                } else {
                    InfoRow(stringResource(R.string.selected), stringResource(R.string.n_items, entries.size))
                    val s = stats
                    val size = if (entries.first().inArchive) entries.sumOf { it.size } else s?.size
                    InfoRow(stringResource(R.string.size), size?.let { Format.size(it) } ?: stringResource(R.string.calculating))
                    if (s != null) InfoRow(stringResource(R.string.contents), stringResource(R.string.files_folders, s.files, s.dirs))
                    InfoRow(stringResource(R.string.path), File(entries.first().path).parent ?: "/")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        dismissButton = {
            if (single != null && !single.isDirectory && !single.inArchive) {
                TextButton(onClick = {
                    onDismiss()
                    Dialogs.show(DialogSpec.Hash(File(single.path)))
                }) { Text(stringResource(R.string.action_checksum)) }
            }
        }
    )
}

class MediaLabels(val duration: String, val resolution: String, val bitrate: String, val title: String, val artist: String, val album: String)

private fun mediaInfo(f: File, type: FileType, l: MediaLabels): List<Pair<String, String>> {
    val out = ArrayList<Pair<String, String>>()
    when (type) {
        FileType.IMAGE -> {
            val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(f.absolutePath, o)
            if (o.outWidth > 0) out.add(l.resolution to "${o.outWidth} x ${o.outHeight}")
        }
        FileType.AUDIO, FileType.VIDEO -> {
            val r = android.media.MediaMetadataRetriever()
            try {
                r.setDataSource(f.absolutePath)
                fun meta(k: Int) = r.extractMetadata(k)?.takeIf { it.isNotBlank() }
                meta(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let {
                    out.add(l.duration to com.ztmanager.ui.viewer.formatTime(it.toInt()))
                }
                val w = meta(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                val h = meta(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                if (w != null && h != null) out.add(l.resolution to "$w x $h")
                meta(android.media.MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()?.let {
                    out.add(l.bitrate to "${it / 1000} kbps")
                }
                meta(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE)?.let { out.add(l.title to it) }
                meta(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST)?.let { out.add(l.artist to it) }
                meta(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUM)?.let { out.add(l.album to it) }
            } catch (_: Exception) {
            } finally {
                try {
                    r.release()
                } catch (_: Exception) {
                }
            }
        }
        else -> {}
    }
    return out
}

@Composable
fun HashDialog(spec: DialogSpec.Hash, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var progress by remember { mutableFloatStateOf(0f) }
    var result by remember { mutableStateOf<Map<String, String>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var compare by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        try {
            val ctx = coroutineContext
            result = withContext(Dispatchers.IO) {
                Hashing.compute(spec.file, { progress = it }, { !ctx.isActive })
            }
        } catch (e: Exception) {
            error = e.message
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_checksum)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(spec.file.name, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                val r = result
                when {
                    error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
                    r == null -> {
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                        Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                    }
                    else -> {
                        r.forEach { (algo, value) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(algo, style = MaterialTheme.typography.labelMedium, color = ZtTheme.colors.secondaryText)
                                    SelectionContainer {
                                        Text(value, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                IconButton(onClick = { Intents.copyText(context, value) }) {
                                    Icon(Icons.Filled.ContentCopy, null, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = compare,
                            onValueChange = { compare = it.trim() },
                            label = { Text(stringResource(R.string.compare_hash)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (compare.isNotEmpty()) {
                            val match = r.entries.firstOrNull { it.value.equals(compare, ignoreCase = true) }
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                                Icon(
                                    if (match != null) Icons.Filled.CheckCircle else Icons.Filled.Error, null,
                                    tint = if (match != null) Color(0xFF43A047) else MaterialTheme.colorScheme.error
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (match != null) stringResource(R.string.hash_match, match.key)
                                    else stringResource(R.string.hash_mismatch)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } }
    )
}

@Composable
fun PermissionsDialog(spec: DialogSpec.Permissions, onDismiss: () -> Unit) {
    val entry = spec.entry
    var mode by remember { mutableIntStateOf(FileOps.readMode(entry.path)) }
    var recursive by remember { mutableStateOf(false) }
    val failed = stringResource(R.string.msg_failed)
    val labels = listOf(stringResource(R.string.perm_owner), stringResource(R.string.perm_group), stringResource(R.string.perm_others))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_permissions)) },
        text = {
            Column {
                Text(entry.name, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Row {
                    Spacer(Modifier.width(72.dp))
                    listOf("R", "W", "X").forEach {
                        Text(it, modifier = Modifier.width(48.dp), textAlign = TextAlign.Center)
                    }
                }
                for (row in 0 until 3) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(labels[row], modifier = Modifier.width(72.dp))
                        for (col in 0 until 3) {
                            val bit = 1 shl (8 - (row * 3 + col))
                            Checkbox(
                                checked = mode and bit != 0,
                                onCheckedChange = { c -> mode = if (c) mode or bit else mode and bit.inv() },
                                modifier = Modifier.width(48.dp)
                            )
                        }
                    }
                }
                Text(
                    stringResource(R.string.octal_value, String.format("%03o", mode and 0x1FF)),
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 6.dp)
                )
                if (entry.isDirectory) CheckRow(stringResource(R.string.recursive), recursive) { recursive = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                val ok = FileOps.chmod(entry.path, mode and 0xFFF, recursive)
                if (!ok) com.ztmanager.ui.AppState.toast(failed)
                spec.pane.refresh()
            }) { Text(stringResource(R.string.apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
