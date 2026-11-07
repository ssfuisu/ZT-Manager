package com.ztmanager.ui.viewer

import android.net.Uri
import android.widget.FrameLayout
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import com.ztmanager.R
import com.ztmanager.ui.AppState
import com.ztmanager.ui.common.Intents
import java.io.File

@Composable
fun VideoPlayerScreen(path: String) {
    val context = LocalContext.current
    val file = remember { File(path) }
    var view by remember { mutableStateOf<VideoView?>(null) }
    val failed = context.getString(R.string.msg_cannot_play)
    DisposableEffect(Unit) {
        onDispose { view?.stopPlayback() }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                FrameLayout(ctx).apply {
                    val v = VideoView(ctx)
                    addView(
                        v,
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                            android.view.Gravity.CENTER
                        )
                    )
                    val controller = MediaController(ctx)
                    controller.setAnchorView(v)
                    v.setMediaController(controller)
                    v.setVideoURI(Uri.fromFile(file))
                    v.setOnPreparedListener { mp ->
                        mp.isLooping = false
                        v.start()
                        controller.show(3000)
                    }
                    v.setOnErrorListener { _, _, _ ->
                        AppState.toast(failed)
                        true
                    }
                    view = v
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.Center)
        )
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Black.copy(alpha = 0.5f),
                titleContentColor = Color.White,
                navigationIconContentColor = Color.White,
                actionIconContentColor = Color.White
            ),
            navigationIcon = { IconButton(onClick = { AppState.pop() }) { Icon(Icons.Filled.ArrowBack, null) } },
            title = { Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            actions = {
                IconButton(onClick = { Intents.share(context, listOf(file)) }) { Icon(Icons.Filled.Share, null) }
                IconButton(onClick = { Intents.openExternal(context, file) }) { Icon(Icons.Filled.OpenInNew, null) }
            }
        )
    }
}
