package com.ztmanager.ui.browser

import android.os.Environment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ztmanager.BuildConfig
import com.ztmanager.R
import com.ztmanager.data.Prefs
import com.ztmanager.fs.Format
import com.ztmanager.fs.StorageUtil
import com.ztmanager.ui.AppState
import com.ztmanager.ui.Screen
import com.ztmanager.ui.theme.ZtTheme
import java.io.File

@Composable
fun AppDrawer(onClose: () -> Unit) {
    val context = LocalContext.current
    val volumes = remember { StorageUtil.volumes(context) }
    fun go(path: String) {
        AppState.active.navigate(path)
        onClose()
    }
    ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Column(Modifier.padding(start = 20.dp, top = 20.dp, bottom = 12.dp)) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("v" + BuildConfig.VERSION_NAME, style = MaterialTheme.typography.bodySmall, color = ZtTheme.colors.secondaryText)
            }
            Section(stringResource(R.string.section_storage))
            volumes.forEach { v ->
                val used = (v.total - v.free).coerceAtLeast(0)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { go(v.path) }
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (v.removable) Icons.Filled.SdCard else Icons.Filled.PhoneAndroid, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(v.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                stringResource(R.string.storage_free, Format.size(v.free), Format.size(v.total)),
                                style = MaterialTheme.typography.bodySmall,
                                color = ZtTheme.colors.secondaryText
                            )
                        }
                    }
                    if (v.total > 0) {
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { used.toFloat() / v.total },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 40.dp)
                        )
                    }
                }
            }
            DrawerItem(Icons.Filled.Folder, stringResource(R.string.root_directory)) { go("/") }
            val root = StorageUtil.internalRoot
            listOf(
                Triple(Icons.Filled.Download, Environment.DIRECTORY_DOWNLOADS, R.string.dir_downloads),
                Triple(Icons.Filled.Image, Environment.DIRECTORY_DCIM, R.string.dir_dcim),
                Triple(Icons.Filled.Image, Environment.DIRECTORY_PICTURES, R.string.dir_pictures),
                Triple(Icons.Filled.MusicNote, Environment.DIRECTORY_MUSIC, R.string.dir_music),
                Triple(Icons.Filled.Description, Environment.DIRECTORY_DOCUMENTS, R.string.dir_documents)
            ).forEach { (icon, dir, label) ->
                val f = File(root, dir)
                if (f.isDirectory) DrawerItem(icon, stringResource(label)) { go(f.absolutePath) }
            }

            val bookmarks = Prefs.bookmarks
            if (bookmarks.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 6.dp), color = ZtTheme.colors.divider)
                Section(stringResource(R.string.section_bookmarks))
                bookmarks.forEach { b ->
                    DrawerItem(
                        Icons.Filled.Bookmark,
                        File(b).name.ifEmpty { b },
                        subtitle = b,
                        trailing = {
                            IconButton(onClick = { Prefs.removeBookmark(b) }) {
                                Icon(Icons.Filled.Close, null, modifier = Modifier.size(18.dp), tint = ZtTheme.colors.secondaryText)
                            }
                        }
                    ) { go(b) }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 6.dp), color = ZtTheme.colors.divider)
            Section(stringResource(R.string.section_tools))
            val dir = AppState.active.currentDir?.path ?: root
            DrawerItem(Icons.Filled.Apps, stringResource(R.string.tool_apps)) { onClose(); AppState.push(Screen.Apps) }
            DrawerItem(Icons.Filled.Search, stringResource(R.string.tool_search)) { onClose(); AppState.push(Screen.Search(dir)) }
            DrawerItem(Icons.Filled.PieChart, stringResource(R.string.tool_analyzer)) { onClose(); AppState.push(Screen.Analyzer(dir)) }
            DrawerItem(Icons.Filled.Code, stringResource(R.string.tool_terminal)) { onClose(); AppState.push(Screen.Terminal) }
            DrawerItem(Icons.Filled.Memory, stringResource(R.string.tool_text_editor)) {
                onClose()
                AppState.push(Screen.Editor(""))
            }
            HorizontalDivider(Modifier.padding(vertical = 6.dp), color = ZtTheme.colors.divider)
            DrawerItem(Icons.Filled.Settings, stringResource(R.string.settings)) { onClose(); AppState.push(Screen.Settings) }
            DrawerItem(Icons.Filled.Info, stringResource(R.string.about)) { onClose(); AppState.push(Screen.About) }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun DrawerItem(
    icon: ImageVector,
    label: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp)
    ) {
        Icon(icon, null, tint = ZtTheme.colors.secondaryText)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = ZtTheme.colors.secondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing?.invoke()
    }
}
