package com.ztmanager.ui.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ztmanager.R
import com.ztmanager.fs.StorageUtil
import com.ztmanager.share.ShareServer
import com.ztmanager.share.WebServer
import com.ztmanager.ui.AppState
import com.ztmanager.ui.browser.DialogSpec
import com.ztmanager.ui.browser.Dialogs
import com.ztmanager.ui.common.Intents
import com.ztmanager.ui.theme.ZtTheme
import java.io.File

@Composable
fun ShareScreen() {
    val context = LocalContext.current
    val running = ShareServer.server != null
    var root by remember { mutableStateOf(if (running) ShareServer.rootPath else (AppState.active.currentDir?.path ?: StorageUtil.internalRoot)) }
    var port by remember { mutableStateOf((ShareServer.server?.port ?: 8080).toString()) }
    var upload by remember { mutableStateOf(true) }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val pick = stringResource(R.string.title_select_folder)
    val addresses = remember(running) { WebServer.localAddresses() }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ZtTheme.colors.toolbar),
                navigationIcon = { IconButton(onClick = { AppState.pop() }) { Icon(Icons.Filled.ArrowBack, null) } },
                title = { Text(stringResource(R.string.tool_share)) }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        if (running) Icons.Filled.Wifi else Icons.Filled.WifiOff, null,
                        tint = if (running) MaterialTheme.colorScheme.primary else ZtTheme.colors.secondaryText,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(if (running) R.string.share_running else R.string.share_stopped),
                        fontWeight = FontWeight.SemiBold
                    )
                    if (running) {
                        Spacer(Modifier.height(8.dp))
                        if (addresses.isEmpty()) Text(stringResource(R.string.share_no_network), color = MaterialTheme.colorScheme.error)
                        addresses.forEach { ip ->
                            val url = "http://$ip:${ShareServer.server?.port}/"
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SelectionContainer {
                                    Text(url, fontFamily = FontFamily.Monospace, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                                }
                                IconButton(onClick = { Intents.copyText(context, url) }) { Icon(Icons.Filled.ContentCopy, null, Modifier.size(18.dp)) }
                            }
                        }
                        addresses.firstOrNull()?.let { ip ->
                            val url = "http://$ip:${ShareServer.server?.port}/"
                            val qr = remember(url) { com.ztmanager.ui.common.Qr.bitmap(url, 512)?.asImageBitmap() }
                            if (qr != null) {
                                androidx.compose.foundation.Image(
                                    qr, null,
                                    modifier = Modifier
                                        .padding(vertical = 8.dp)
                                        .size(180.dp)
                                )
                            }
                        }
                        Text(stringResource(R.string.share_hint), style = MaterialTheme.typography.bodySmall, color = ZtTheme.colors.secondaryText)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !running) {
                        Dialogs.show(DialogSpec.FolderPicker(pick, root) { root = it.absolutePath })
                    }
                    .padding(vertical = 8.dp)
            ) {
                Icon(Icons.Filled.FolderOpen, null, tint = ZtTheme.colors.folder)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.share_folder), style = MaterialTheme.typography.labelMedium, color = ZtTheme.colors.secondaryText)
                    Text(root, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (!running) {
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it.filter { c -> c.isDigit() }.take(5) },
                    label = { Text(stringResource(R.string.share_port)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.password_optional)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                    Text(stringResource(R.string.share_allow_upload), modifier = Modifier.weight(1f))
                    Switch(checked = upload, onCheckedChange = { upload = it })
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = {
                    error = null
                    if (running) {
                        ShareServer.stop()
                    } else {
                        try {
                            val p = port.toIntOrNull()?.takeIf { it in 1024..65535 } ?: 8080
                            ShareServer.start(File(root), p, upload, password)
                        } catch (e: Exception) {
                            error = e.message ?: e.javaClass.simpleName
                        }
                    }
                },
                colors = if (running) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) { Text(stringResource(if (running) R.string.share_stop else R.string.share_start)) }
            if (running) {
                Text(stringResource(R.string.share_log), style = MaterialTheme.typography.labelLarge)
                LazyColumn(Modifier.fillMaxSize()) {
                    items(ShareServer.log) { line ->
                        Text(line, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = ZtTheme.colors.secondaryText)
                    }
                }
            }
        }
    }
}
