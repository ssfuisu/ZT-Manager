package com.ztmanager.share

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ztmanager.ui.AppState
import kotlinx.coroutines.launch
import java.io.File

/** Holds the running Wi-Fi share server so it keeps running while browsing. */
object ShareServer {
    var server by mutableStateOf<WebServer?>(null)
        private set
    var rootPath by mutableStateOf("")
        private set
    val log = mutableStateListOf<String>()

    val running: Boolean get() = server?.running == true

    fun start(root: File, port: Int, allowUpload: Boolean, password: String?) {
        stop()
        val s = WebServer(root, port, allowUpload, password?.takeIf { it.isNotEmpty() }) { line ->
            AppState.scope.launch {
                log.add(0, line)
                if (log.size > 200) log.removeAt(log.lastIndex)
            }
        }
        s.start()
        server = s
        rootPath = root.absolutePath
    }

    fun stop() {
        server?.stop()
        server = null
    }
}
