package com.ztmanager.ui.tools

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ztmanager.R
import com.ztmanager.ui.AppState
import com.ztmanager.ui.browser.DialogSpec
import com.ztmanager.ui.browser.Dialogs
import com.ztmanager.ui.common.Intents
import com.ztmanager.ui.theme.ZtTheme
import java.io.File

/** Read-only text viewer used for generated content such as decoded manifests. */
@Composable
fun TextViewScreen(title: String, text: String) {
    val context = LocalContext.current
    val saveTitle = stringResource(R.string.action_save_as)
    val pathHint = stringResource(R.string.path)
    val saved = stringResource(R.string.msg_saved)
    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ZtTheme.colors.toolbar),
                navigationIcon = { IconButton(onClick = { AppState.pop() }) { Icon(Icons.Filled.ArrowBack, null) } },
                title = { Text(title) },
                actions = {
                    IconButton(onClick = { Intents.copyText(context, text) }) { Icon(Icons.Filled.ContentCopy, null) }
                    IconButton(onClick = {
                        val start = (AppState.active.currentDir?.path ?: "/sdcard") + "/" + title
                        Dialogs.show(DialogSpec.Input(saveTitle, start, pathHint) { p ->
                            try {
                                File(p.trim()).writeText(text)
                                AppState.toast(saved)
                                AppState.refreshAll()
                            } catch (e: Exception) {
                                AppState.toast(e.message)
                            }
                        })
                    }) { Icon(Icons.Filled.Save, null) }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        SelectionContainer(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState())
        ) {
            Text(text, fontFamily = FontFamily.Monospace, fontSize = 12.sp, softWrap = false, modifier = Modifier.padding(10.dp))
        }
    }
}
