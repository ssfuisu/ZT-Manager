package com.ztmanager.ui.apps

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.ztmanager.R
import com.ztmanager.apk.ApkParser
import com.ztmanager.archive.ArchiveCreator
import com.ztmanager.archive.CreateFormat
import com.ztmanager.fs.FileOps
import com.ztmanager.fs.Format
import com.ztmanager.fs.StorageUtil
import com.ztmanager.task.TaskManager
import com.ztmanager.ui.AppState
import com.ztmanager.ui.Screen
import com.ztmanager.ui.browser.dialogs.MenuAction
import com.ztmanager.ui.browser.dialogs.MenuDialog
import com.ztmanager.ui.common.Intents
import com.ztmanager.ui.theme.ZtTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class AppItem(
    val label: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val system: Boolean,
    val apkPath: String,
    val splits: List<String>,
    val size: Long,
    val info: ApplicationInfo,
    val installed: Long,
    val updated: Long
)

private fun loadApps(context: Context): List<AppItem> {
    val pm = context.packageManager
    @Suppress("DEPRECATION")
    val packages = pm.getInstalledPackages(PackageManager.GET_META_DATA)
    return packages.mapNotNull { p ->
        val ai = p.applicationInfo ?: return@mapNotNull null
        val splits = ai.splitSourceDirs?.toList() ?: emptyList()
        AppItem(
            label = ai.loadLabel(pm).toString(),
            packageName = p.packageName,
            versionName = p.versionName ?: "",
            versionCode = ApkParser.versionCodeOf(p),
            system = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
            apkPath = ai.sourceDir,
            splits = splits,
            size = File(ai.sourceDir).length() + splits.sumOf { File(it).length() },
            info = ai,
            installed = p.firstInstallTime,
            updated = p.lastUpdateTime
        )
    }.sortedBy { it.label.lowercase() }
}

private fun safeName(s: String) = s.replace(Regex("[^\\w.\\- ]"), "_")

fun backupApp(context: Context, app: AppItem) {
    val dir = File(StorageUtil.internalRoot, "ZTManager/Backup")
    TaskManager.start(
        title = context.getString(R.string.task_backup),
        successMessage = context.getString(R.string.msg_backup_done, dir.absolutePath)
    ) {
        dir.mkdirs()
        val base = safeName(app.label) + "_" + safeName(app.versionName)
        setMessage(app.label)
        if (app.splits.isEmpty()) {
            val src = File(app.apkPath)
            val out = File(dir, "$base.apk")
            setTotalBytes(src.length())
            src.inputStream().use { input -> out.outputStream().use { output -> FileOps.pipe(this, input, output) } }
            flush()
        } else {
            // split APKs are bundled into an .apks archive
            val tmp = File(context.cacheDir, "apks_" + app.packageName).apply { deleteRecursively(); mkdirs() }
            File(app.apkPath).copyTo(File(tmp, "base.apk"), overwrite = true)
            app.splits.forEach { s -> File(s).copyTo(File(tmp, File(s).name), overwrite = true) }
            ArchiveCreator.create(this, tmp.listFiles()!!.toList(), File(dir, "$base.apks"), CreateFormat.ZIP, 0, null)
            tmp.deleteRecursively()
        }
    }
}

@Composable
fun AppManagerScreen() {
    val context = LocalContext.current
    var showSystem by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<AppItem?>(null) }
    var sort by remember { mutableStateOf(0) }
    val apps by produceState<List<AppItem>?>(null) {
        value = withContext(Dispatchers.IO) { loadApps(context) }
    }
    val list = apps?.filter { (showSystem || !it.system) && (query.isBlank() || it.label.contains(query, true) || it.packageName.contains(query, true)) }
        ?.let { l ->
            when (sort) {
                1 -> l.sortedByDescending { it.size }
                2 -> l.sortedByDescending { it.installed }
                3 -> l.sortedByDescending { it.updated }
                else -> l
            }
        }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ZtTheme.colors.toolbar),
                navigationIcon = { IconButton(onClick = { AppState.pop() }) { Icon(Icons.Filled.ArrowBack, null) } },
                title = {
                    Column {
                        Text(stringResource(R.string.tool_apps))
                        if (list != null) Text(stringResource(R.string.n_items, list.size), style = MaterialTheme.typography.bodySmall, color = ZtTheme.colors.secondaryText)
                    }
                },
                actions = { IconButton(onClick = { searching = !searching }) { Icon(Icons.Filled.Search, null) } }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (searching) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.filter_hint)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
            Row(Modifier.padding(horizontal = 12.dp)) {
                FilterChip(selected = !showSystem, onClick = { showSystem = false }, label = { Text(stringResource(R.string.user_apps)) })
                Spacer(Modifier.width(8.dp))
                FilterChip(selected = showSystem, onClick = { showSystem = true }, label = { Text(stringResource(R.string.all_apps)) })
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
                listOf(R.string.sort_name, R.string.sort_size, R.string.sort_installed, R.string.sort_updated).forEachIndexed { i, label ->
                    FilterChip(selected = sort == i, onClick = { sort = i }, label = { Text(stringResource(label)) }, modifier = Modifier.padding(end = 6.dp))
                }
            }
            if (list == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(list, key = { it.packageName }) { app -> AppRow(app) { selected = app } }
                }
            }
        }
    }

    selected?.let { app ->
        MenuDialog(
            title = app.label,
            subtitle = app.packageName,
            actions = listOf(
                MenuAction(stringResource(R.string.action_launch), Icons.Filled.PlayArrow) { Intents.launch(context, app.packageName) },
                MenuAction(stringResource(R.string.action_apk_info), Icons.Filled.Info) { AppState.push(Screen.ApkInfo(app.apkPath)) },
                MenuAction(stringResource(R.string.action_backup_apk), Icons.Filled.Backup) { backupApp(context, app) },
                MenuAction(stringResource(R.string.action_share), Icons.Filled.Share) { Intents.share(context, listOf(File(app.apkPath))) },
                MenuAction(stringResource(R.string.action_app_settings), Icons.Filled.Settings) { Intents.appDetails(context, app.packageName) },
                MenuAction(stringResource(R.string.action_uninstall), Icons.Filled.Delete, enabled = !app.system) {
                    Intents.uninstall(context, app.packageName)
                }
            ),
            onDismiss = { selected = null },
            leading = { AppIcon(app, 40) }
        )
    }
}

@Composable
private fun AppIcon(app: AppItem, sizeDp: Int) {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(null, app.packageName) {
        value = withContext(Dispatchers.IO) {
            try {
                app.info.loadIcon(context.packageManager).toBitmap(128, 128).asImageBitmap()
            } catch (e: Throwable) {
                null
            }
        }
    }
    val i = icon
    if (i != null) Image(i, null, Modifier.size(sizeDp.dp))
    else Icon(Icons.Filled.Android, null, Modifier.size(sizeDp.dp), tint = ZtTheme.colors.secondaryText)
}

@Composable
private fun AppRow(app: AppItem, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        AppIcon(app, 42)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            Text(app.packageName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = ZtTheme.colors.secondaryText)
            Text(
                app.versionName + "  " + Format.size(app.size) + "  " + Format.date(app.updated) + (if (app.splits.isNotEmpty()) "  split" else ""),
                style = MaterialTheme.typography.bodySmall,
                color = ZtTheme.colors.secondaryText
            )
        }
    }
}
