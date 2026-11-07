package com.ztmanager.ui.viewer

import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ztmanager.R
import com.ztmanager.fs.FileType
import com.ztmanager.ui.AppState
import com.ztmanager.ui.browser.NaturalOrder
import com.ztmanager.ui.common.Intents
import com.ztmanager.ui.theme.ZtTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

private data class TrackMeta(val title: String, val artist: String, val album: String, val art: ImageBitmap?)

private fun readMeta(f: File): TrackMeta {
    val r = MediaMetadataRetriever()
    return try {
        r.setDataSource(f.absolutePath)
        val art = r.embeddedPicture?.let { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
        TrackMeta(
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: f.nameWithoutExtension,
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "",
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: "",
            art
        )
    } catch (e: Exception) {
        TrackMeta(f.nameWithoutExtension, "", "", null)
    } finally {
        try {
            r.release()
        } catch (_: Exception) {
        }
    }
}

fun formatTime(ms: Int): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) String.format("%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60)
    else String.format("%d:%02d", s / 60, s % 60)
}

private const val REPEAT_OFF = 0
private const val REPEAT_ALL = 1
private const val REPEAT_ONE = 2

/** Simple music player that plays all audio files of the folder as a playlist. */
@Composable
fun AudioPlayerScreen(path: String) {
    val context = LocalContext.current
    val start = remember { File(path) }
    val playlist = remember {
        start.parentFile?.listFiles()
            ?.filter { it.isFile && FileType.of(it.name, false) == FileType.AUDIO }
            ?.sortedWith { a, b -> NaturalOrder.compare(a.name, b.name) }
            ?.takeIf { it.isNotEmpty() } ?: listOf(start)
    }
    var index by remember { mutableIntStateOf(playlist.indexOfFirst { it.absolutePath == start.absolutePath }.coerceAtLeast(0)) }
    var playing by remember { mutableStateOf(false) }
    var position by remember { mutableIntStateOf(0) }
    var duration by remember { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    var repeat by remember { mutableIntStateOf(REPEAT_ALL) }
    var shuffle by remember { mutableStateOf(false) }
    var showList by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val player = remember { MediaPlayer() }
    val current = playlist[index]

    fun next(auto: Boolean) {
        if (auto && repeat == REPEAT_ONE) {
            player.seekTo(0)
            player.start()
            return
        }
        val n = when {
            shuffle && playlist.size > 1 -> (playlist.indices - index).random()
            index + 1 < playlist.size -> index + 1
            repeat == REPEAT_ALL || !auto -> 0
            else -> -1
        }
        if (n >= 0) index = n else playing = false
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                player.release()
            } catch (_: Exception) {
            }
        }
    }

    LaunchedEffect(index) {
        error = null
        try {
            player.reset()
            player.setDataSource(current.absolutePath)
            player.setOnCompletionListener { next(true) }
            withContext(Dispatchers.IO) { player.prepare() }
            duration = player.duration
            player.start()
            playing = true
        } catch (e: Exception) {
            error = e.message ?: e.javaClass.simpleName
            playing = false
        }
    }

    LaunchedEffect(playing) {
        while (playing) {
            if (!dragging) position = try {
                player.currentPosition
            } catch (e: Exception) {
                0
            }
            delay(250)
        }
    }

    val meta by produceState(TrackMeta(current.nameWithoutExtension, "", "", null), current) {
        value = withContext(Dispatchers.IO) { readMeta(current) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ZtTheme.colors.toolbar),
                navigationIcon = { IconButton(onClick = { AppState.pop() }) { Icon(Icons.Filled.ArrowBack, null) } },
                title = { Text(stringResource(R.string.audio_player)) },
                actions = {
                    IconButton(onClick = { showList = !showList }) { Icon(Icons.Filled.QueueMusic, null) }
                    IconButton(onClick = { Intents.share(context, listOf(current)) }) { Icon(Icons.Filled.Share, null) }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (showList) {
            LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                itemsIndexed(playlist) { i, f ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (i == index) ZtTheme.colors.selection else Color.Transparent)
                            .clickable {
                                index = i
                                showList = false
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Icon(Icons.Filled.MusicNote, null, tint = if (i == index) MaterialTheme.colorScheme.primary else ZtTheme.colors.secondaryText)
                        Spacer(Modifier.width(14.dp))
                        Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            return@Scaffold
        }
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                Modifier
                    .fillMaxWidth(0.8f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                val art = meta.art
                if (art != null) Image(art, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Icon(Icons.Filled.MusicNote, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(120.dp))
            }
            Spacer(Modifier.height(24.dp))
            Text(meta.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            val sub = listOf(meta.artist, meta.album).filter { it.isNotBlank() }.joinToString("  -  ")
            if (sub.isNotEmpty()) Text(sub, color = ZtTheme.colors.secondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${index + 1} / ${playlist.size}", style = MaterialTheme.typography.bodySmall, color = ZtTheme.colors.secondaryText)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(16.dp))
            Slider(
                value = if (dragging) dragValue else if (duration > 0) position.toFloat() / duration else 0f,
                onValueChange = {
                    dragging = true
                    dragValue = it
                },
                onValueChangeFinished = {
                    val target = (dragValue * duration).toInt()
                    try {
                        player.seekTo(target)
                    } catch (_: Exception) {
                    }
                    position = target
                    dragging = false
                }
            )
            Row(Modifier.fillMaxWidth()) {
                Text(formatTime(if (dragging) (dragValue * duration).toInt() else position), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.weight(1f))
                Text(formatTime(duration), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = { shuffle = !shuffle }) {
                    Icon(Icons.Filled.Shuffle, null, tint = if (shuffle) MaterialTheme.colorScheme.primary else ZtTheme.colors.secondaryText)
                }
                IconButton(onClick = {
                    if (position > 3000) player.seekTo(0) else index = if (index > 0) index - 1 else playlist.lastIndex
                }) { Icon(Icons.Filled.SkipPrevious, null, Modifier.size(36.dp)) }
                FilledIconButton(onClick = {
                    try {
                        if (player.isPlaying) {
                            player.pause()
                            playing = false
                        } else {
                            player.start()
                            playing = true
                        }
                    } catch (_: Exception) {
                    }
                }, modifier = Modifier.size(72.dp)) {
                    Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, Modifier.size(40.dp))
                }
                IconButton(onClick = { next(false) }) { Icon(Icons.Filled.SkipNext, null, Modifier.size(36.dp)) }
                IconButton(onClick = { repeat = (repeat + 1) % 3 }) {
                    Icon(
                        if (repeat == REPEAT_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat, null,
                        tint = if (repeat == REPEAT_OFF) ZtTheme.colors.secondaryText else MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
