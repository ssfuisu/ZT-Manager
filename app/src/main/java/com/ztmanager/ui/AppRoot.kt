package com.ztmanager.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ztmanager.R
import com.ztmanager.ui.apk.ApkInfoScreen
import com.ztmanager.ui.apps.AppManagerScreen
import com.ztmanager.ui.browser.BrowserScreen
import com.ztmanager.ui.browser.DialogHost
import com.ztmanager.ui.editor.TextEditorScreen
import com.ztmanager.ui.hex.HexEditorScreen
import com.ztmanager.ui.settings.AboutScreen
import com.ztmanager.ui.settings.SettingsScreen
import com.ztmanager.ui.theme.ZtTheme
import com.ztmanager.ui.tools.AnalyzerScreen
import com.ztmanager.ui.tools.DiffScreen
import com.ztmanager.ui.tools.SearchScreen
import com.ztmanager.ui.tools.TerminalScreen
import com.ztmanager.ui.tools.TextViewScreen
import com.ztmanager.ui.viewer.ImageViewerScreen

@Composable
fun AppRoot(onRequestPermission: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (!AppState.permissionGranted) {
            PermissionScreen(onRequestPermission)
            return@Box
        }
        val screen = AppState.current
        if (screen != Screen.Browser) {
            BackHandler { AppState.pop() }
        }
        when (screen) {
            Screen.Browser -> BrowserScreen()
            is Screen.Editor -> TextEditorScreen(screen.path, screen.readOnly)
            is Screen.Hex -> HexEditorScreen(screen.path)
            is Screen.Images -> ImageViewerScreen(screen.path)
            is Screen.ApkInfo -> ApkInfoScreen(screen.path)
            Screen.Apps -> AppManagerScreen()
            Screen.Terminal -> TerminalScreen()
            is Screen.Search -> SearchScreen(screen.dir)
            is Screen.Analyzer -> AnalyzerScreen(screen.dir)
            is Screen.Diff -> DiffScreen(screen.a, screen.b)
            is Screen.TextView -> TextViewScreen(screen.title, screen.text)
            Screen.Settings -> SettingsScreen()
            Screen.About -> AboutScreen()
        }
        DialogHost()
    }
}

@Composable
private fun PermissionScreen(onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.Folder, null, tint = ZtTheme.colors.folder, modifier = Modifier.size(96.dp))
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.permission_message),
            textAlign = TextAlign.Center,
            color = ZtTheme.colors.secondaryText
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRequest) { Text(stringResource(R.string.grant_permission)) }
    }
}
