package com.ztmanager.ui.apk

import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.ztmanager.R
import com.ztmanager.apk.ApkInfo
import com.ztmanager.apk.ApkParser
import com.ztmanager.fs.Format
import com.ztmanager.ui.AppState
import com.ztmanager.ui.Screen
import com.ztmanager.ui.browser.dialogs.InfoRow
import com.ztmanager.ui.common.Intents
import com.ztmanager.ui.theme.ZtTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ApkInfoScreen(path: String) {
    val context = LocalContext.current
    val state by produceState<Result<ApkInfo>?>(null, path) {
        value = withContext(Dispatchers.IO) { runCatching { ApkParser.parse(context, path) } }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ZtTheme.colors.toolbar),
                navigationIcon = { IconButton(onClick = { AppState.pop() }) { Icon(Icons.Filled.ArrowBack, null) } },
                title = { Text(stringResource(R.string.action_apk_info)) }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        val r = state
        when {
            r == null -> Column(Modifier.padding(padding).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(48.dp))
                CircularProgressIndicator()
            }
            r.isFailure -> Text(
                r.exceptionOrNull()?.message ?: "Error",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(padding).padding(16.dp)
            )
            else -> ApkDetails(r.getOrThrow(), path, Modifier.padding(padding))
        }
    }
}

@Composable
private fun ApkDetails(info: ApkInfo, path: String, modifier: Modifier) {
    val context = LocalContext.current
    val manifestTitle = "AndroidManifest.xml"
    LazyColumn(modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                val bmp = remember(info) { info.icon?.toBitmap(144, 144)?.asImageBitmap() }
                if (bmp != null) Image(bmp, null, Modifier.size(64.dp))
                else Icon(Icons.Filled.Android, null, Modifier.size(64.dp))
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(info.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(info.packageName, style = MaterialTheme.typography.bodyMedium, color = ZtTheme.colors.secondaryText)
                    Text("${info.versionName} (${info.versionCode})", style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Button(onClick = { Intents.install(context, File(path)) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_install))
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = {
                    AppState.pop()
                    AppState.active.openArchive(File(path))
                }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_browse_archive)) }
            }
            OutlinedButton(onClick = {
                AppState.scope.launch {
                    val text = withContext(Dispatchers.IO) { runCatching { ApkParser.readManifest(path) } }
                    text.onSuccess { AppState.push(Screen.TextView(manifestTitle, it)) }
                        .onFailure { AppState.toast(it.message) }
                }
            }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.view_manifest)) }
            Spacer(Modifier.height(8.dp))
        }
        item {
            InfoCard {
                InfoRow(stringResource(R.string.size), Format.size(info.size))
                InfoRow(stringResource(R.string.min_sdk), ApkParser.sdkName(info.minSdk))
                InfoRow(stringResource(R.string.target_sdk), ApkParser.sdkName(info.targetSdk))
                InfoRow(stringResource(R.string.abis), info.abis.ifEmpty { listOf(stringResource(R.string.none)) }.joinToString(", "))
                InfoRow(stringResource(R.string.dex_files), info.dexCount.toString())
                InfoRow(
                    stringResource(R.string.installed_version),
                    info.installedVersion ?: stringResource(R.string.not_installed)
                )
            }
        }
        item {
            InfoCard {
                Text(stringResource(R.string.signature), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (info.certs.isEmpty()) Text(stringResource(R.string.none))
                info.certs.forEach { c ->
                    InfoRow(stringResource(R.string.cert_subject), c.subject)
                    InfoRow(stringResource(R.string.cert_issuer), c.issuer)
                    InfoRow(stringResource(R.string.cert_validity), Format.date(c.validFrom) + " - " + Format.date(c.validTo))
                    InfoRow(stringResource(R.string.cert_algorithm), c.algorithm)
                    InfoRow("MD5", c.md5)
                    InfoRow("SHA-1", c.sha1)
                    InfoRow("SHA-256", c.sha256)
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                }
            }
        }
        item { Expandable(stringResource(R.string.permissions_list), info.permissions) }
        item { Expandable(stringResource(R.string.activities), info.activities) }
        item { Expandable(stringResource(R.string.services), info.services) }
        item { Expandable(stringResource(R.string.receivers), info.receivers) }
        item { Expandable(stringResource(R.string.providers), info.providers) }
    }
}

@Composable
private fun InfoCard(content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(Modifier.padding(14.dp)) { content() }
    }
}

@Composable
private fun Expandable(title: String, items: List<String>) {
    var open by remember { mutableStateOf(false) }
    InfoCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { open = !open }
        ) {
            Text("$title (${items.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
        }
        if (open) {
            items.forEach {
                Text(it, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp))
            }
        }
    }
}

