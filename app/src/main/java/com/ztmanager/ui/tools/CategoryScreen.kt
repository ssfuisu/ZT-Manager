package com.ztmanager.ui.tools

import android.content.Context
import android.provider.MediaStore
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ztmanager.R
import com.ztmanager.fs.FileEntry

import com.ztmanager.fs.Format
import com.ztmanager.ui.AppState
import com.ztmanager.ui.browser.Opener
import com.ztmanager.ui.common.FileIcon
import com.ztmanager.ui.theme.ZtTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object Categories {
    const val IMAGES = 0
    const val VIDEOS = 1
    const val AUDIO = 2
    const val DOCUMENTS = 3
    const val ARCHIVES = 4
    const val APKS = 5
    const val RECENT = 6
    const val LARGE = 7

    val all = listOf(IMAGES, VIDEOS, AUDIO, DOCUMENTS, ARCHIVES, APKS, RECENT, LARGE)

    fun icon(kind: Int): ImageVector = when (kind) {
        IMAGES -> Icons.Filled.Image
        VIDEOS -> Icons.Filled.Movie
        AUDIO -> Icons.Filled.MusicNote
        DOCUMENTS -> Icons.Filled.Description
        ARCHIVES -> Icons.Filled.Archive
        APKS -> Icons.Filled.Android
        RECENT -> Icons.Filled.History
        else -> Icons.Filled.SdStorage
    }

    fun label(kind: Int): Int = when (kind) {
        IMAGES -> R.string.cat_images
        VIDEOS -> R.string.cat_videos
        AUDIO -> R.string.cat_audio
        DOCUMENTS -> R.string.cat_documents
        ARCHIVES -> R.string.cat_archives
        APKS -> R.string.cat_apks
        RECENT -> R.string.cat_recent
        else -> R.string.cat_large
    }

    private fun extSelection(exts: List<String>): String =
        exts.joinToString(" OR ") { "LOWER(" + MediaStore.Files.FileColumns.DATA + ") LIKE '%." + it + "'" }

    /** Queries the media store; works on all volumes and is much faster than walking folders. */
    fun query(context: Context, kind: Int): List<FileEntry> {
        val c = MediaStore.Files.FileColumns.MIME_TYPE
        val now = System.currentTimeMillis() / 1000
        val selection = when (kind) {
            IMAGES -> "$c LIKE 'image/%'"
            VIDEOS -> "$c LIKE 'video/%'"
            AUDIO -> "$c LIKE 'audio/%'"
            DOCUMENTS -> extSelection(listOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp", "rtf", "txt", "md", "epub", "csv"))
            ARCHIVES -> extSelection(listOf("zip", "7z", "rar", "tar", "gz", "tgz", "bz2", "xz", "iso", "cab", "jar", "apks", "xapk"))
            APKS -> extSelection(listOf("apk"))
            RECENT -> MediaStore.Files.FileColumns.DATE_MODIFIED + " > " + (now - 7 * 24 * 3600) + " AND " + c + " IS NOT NULL"
            else -> MediaStore.Files.FileColumns.SIZE + " > " + (100L * 1024 * 1024)
        }
        val order = when (kind) {
            LARGE -> MediaStore.Files.FileColumns.SIZE + " DESC"
            else -> MediaStore.Files.FileColumns.DATE_MODIFIED + " DESC"
        }
        val out = ArrayList<FileEntry>()
        val projection = arrayOf(
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED
        )
        context.contentResolver.query(MediaStore.Files.getContentUri("external"), projection, selection, null, order)?.use { cur ->
            while (cur.moveToNext() && out.size < 5000) {
                val path = cur.getString(0) ?: continue
                val f = File(path)
                if (f.isDirectory || f.name.startsWith(".")) continue
                if (!f.exists()) continue
                out.add(
                    FileEntry(
                        name = f.name,
                        path = path,
                        isDirectory = false,
                        size = cur.getLong(1),
                        lastModified = cur.getLong(2) * 1000
                    )
                )
            }
        }
        return out
    }
}

@Composable
fun CategoryScreen(kind: Int) {
    val context = LocalContext.current
    var current by remember { mutableStateOf(kind) }
    val result by produceState<List<FileEntry>?>(null, current) {
        value = null
        value = withContext(Dispatchers.IO) { runCatching { Categories.query(context, current) }.getOrDefault(emptyList()) }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ZtTheme.colors.toolbar),
                navigationIcon = { IconButton(onClick = { AppState.pop() }) { Icon(Icons.Filled.ArrowBack, null) } },
                title = {
                    Column {
                        Text(stringResource(Categories.label(current)))
                        result?.let {
                            Text(
                                stringResource(R.string.n_items, it.size) + "  " + Format.size(it.sumOf { e -> e.size }),
                                style = MaterialTheme.typography.bodySmall,
                                color = ZtTheme.colors.secondaryText
                            )
                        }
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(Modifier.horizontalScrollCompat().padding(horizontal = 8.dp, vertical = 4.dp)) {
                Categories.all.forEach { k ->
                    FilterChip(
                        selected = k == current,
                        onClick = { current = k },
                        leadingIcon = { Icon(Categories.icon(k), null) },
                        label = { Text(stringResource(Categories.label(k))) },
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
            }
            val list = result
            if (list == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(list, key = { it.path }) { e ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = { Opener.open(context, AppState.active, e) },
                                    onLongClick = {
                                        AppState.pop()
                                        File(e.path).parent?.let { AppState.active.navigate(it) }
                                    }
                                )
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                        ) {
                            FileIcon(e, 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(e.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    (File(e.path).parent ?: "") ,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ZtTheme.colors.secondaryText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(Format.size(e.size), style = MaterialTheme.typography.bodySmall)
                                Text(Format.date(e.lastModified), style = MaterialTheme.typography.labelSmall, color = ZtTheme.colors.secondaryText)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Modifier.horizontalScrollCompat(): Modifier =
    this.then(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()))

