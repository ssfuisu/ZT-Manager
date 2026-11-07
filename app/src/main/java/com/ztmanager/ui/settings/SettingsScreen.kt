package com.ztmanager.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.ztmanager.ui.theme.ACCENTS
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import com.ztmanager.R
import com.ztmanager.archive.ArchiveManager
import com.ztmanager.data.AppLock
import com.ztmanager.data.Prefs
import com.ztmanager.fs.RootShell
import com.ztmanager.ui.AppState
import com.ztmanager.ui.browser.dialogs.RadioRow
import com.ztmanager.ui.theme.ZtTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val LANGUAGES = listOf("" to R.string.language_system, "en" to R.string.language_en, "tr" to R.string.language_tr)
private val ZIP_CHARSETS = listOf("UTF-8", "IBM437", "IBM866", "windows-1251", "windows-1252", "windows-1254", "GBK", "Big5", "Shift_JIS", "EUC-KR", "ISO-8859-1")

@Composable
fun SettingsScreen() {
    var dialog by remember { mutableStateOf<String?>(null) }
    val rootUnavailable = stringResource(R.string.msg_root_unavailable)
    val cacheCleared = stringResource(R.string.msg_cache_cleared)
    val currentLang = remember(dialog) { AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore('-') }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ZtTheme.colors.toolbar),
                navigationIcon = { IconButton(onClick = { AppState.pop() }) { Icon(Icons.Filled.ArrowBack, null) } },
                title = { Text(stringResource(R.string.settings)) }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Header(stringResource(R.string.section_appearance))
            val themeNames = listOf(
                stringResource(R.string.theme_system), stringResource(R.string.theme_light),
                stringResource(R.string.theme_dark), stringResource(R.string.theme_black)
            )
            Item(stringResource(R.string.theme), themeNames[Prefs.themeMode.coerceIn(0, 3)]) { dialog = "theme" }
            Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                Text(stringResource(R.string.accent_color), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                Row {
                    val dark = ZtTheme.colors.isDark
                    ACCENTS.forEachIndexed { i, pair ->
                        val c = if (dark) pair.first else pair.second
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .padding(end = 10.dp)
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(c)
                                .clickable { Prefs.accent = i }
                        ) {
                            if (Prefs.accent == i) Icon(Icons.Filled.Check, null, tint = Color.White)
                        }
                    }
                }
            }
            val langLabel = LANGUAGES.firstOrNull { it.first == currentLang }?.second ?: R.string.language_system
            Item(stringResource(R.string.language), stringResource(langLabel)) { dialog = "language" }

            Header(stringResource(R.string.section_browser))
            Toggle(stringResource(R.string.show_hidden), null, Prefs.showHidden) { Prefs.showHidden = it }
            Toggle(stringResource(R.string.sort_folders_first), null, Prefs.foldersFirst) { Prefs.foldersFirst = it }
            Toggle(stringResource(R.string.show_thumbnails), null, Prefs.showThumbnails) { Prefs.showThumbnails = it }
            Toggle(stringResource(R.string.show_folder_count), null, Prefs.showFolderCount) {
                Prefs.showFolderCount = it
                AppState.refreshAll()
            }
            Toggle(stringResource(R.string.confirm_delete), null, Prefs.confirmDelete) { Prefs.confirmDelete = it }
            Toggle(stringResource(R.string.use_trash), stringResource(R.string.use_trash_summary), Prefs.useTrash) { Prefs.useTrash = it }
            Toggle(stringResource(R.string.builtin_viewers), stringResource(R.string.builtin_viewers_summary), Prefs.builtInViewers) { Prefs.builtInViewers = it }
            Toggle(stringResource(R.string.single_pane), stringResource(R.string.single_pane_summary), Prefs.singlePane) { Prefs.singlePane = it }
            Toggle(stringResource(R.string.grid_view), null, Prefs.gridView) { Prefs.gridView = it }
            Toggle(stringResource(R.string.root_mode), stringResource(R.string.root_mode_summary), Prefs.rootMode) { on ->
                if (!on) {
                    Prefs.rootMode = false
                } else {
                    AppState.scope.launch {
                        val ok = withContext(Dispatchers.IO) { RootShell.isAvailable(force = true) }
                        if (ok) {
                            Prefs.rootMode = true
                            AppState.refreshAll()
                        } else AppState.toast(rootUnavailable)
                    }
                }
            }

            Header(stringResource(R.string.section_security))
            Item(
                stringResource(R.string.app_lock),
                stringResource(if (AppLock.enabled) R.string.app_lock_on else R.string.app_lock_off)
            ) { dialog = "pin" }

            Header(stringResource(R.string.section_editor))
            Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                Text(stringResource(R.string.font_size, Prefs.editorFontSize), style = MaterialTheme.typography.bodyLarge)
                Slider(
                    value = Prefs.editorFontSize.toFloat(),
                    onValueChange = { Prefs.editorFontSize = it.toInt() },
                    valueRange = 8f..32f,
                    steps = 23
                )
            }
            Toggle(stringResource(R.string.word_wrap), null, Prefs.editorWordWrap) { Prefs.editorWordWrap = it }
            Toggle(stringResource(R.string.line_numbers), null, Prefs.editorLineNumbers) { Prefs.editorLineNumbers = it }
            Toggle(stringResource(R.string.syntax_highlight), null, Prefs.editorHighlight) { Prefs.editorHighlight = it }
            Toggle(stringResource(R.string.auto_indent), null, Prefs.editorAutoIndent) { Prefs.editorAutoIndent = it }

            Header(stringResource(R.string.section_archives))
            Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                Text(stringResource(R.string.compression_level, Prefs.compressionLevel), style = MaterialTheme.typography.bodyLarge)
                Slider(
                    value = Prefs.compressionLevel.toFloat(),
                    onValueChange = { Prefs.compressionLevel = it.toInt() },
                    valueRange = 0f..9f,
                    steps = 8
                )
            }
            Item(stringResource(R.string.zip_encoding), Prefs.archiveCharset) { dialog = "charset" }
            Item(stringResource(R.string.clear_recent), null) {
                Prefs.clearRecent()
                AppState.toast(cacheCleared)
            }
            Item(stringResource(R.string.clear_cache), stringResource(R.string.clear_cache_summary)) {
                AppState.scope.launch {
                    withContext(Dispatchers.IO) { ArchiveManager.clearCache() }
                    AppState.toast(cacheCleared)
                }
            }
            Spacer(Modifier.padding(16.dp))
        }
    }

    if (dialog == "pin") PinDialog { dialog = null }

    when (dialog) {
        "theme" -> ChoiceDialog(
            stringResource(R.string.theme),
            listOf(
                stringResource(R.string.theme_system), stringResource(R.string.theme_light),
                stringResource(R.string.theme_dark), stringResource(R.string.theme_black)
            ),
            Prefs.themeMode,
            onPick = { Prefs.themeMode = it },
            onDismiss = { dialog = null }
        )
        "language" -> ChoiceDialog(
            stringResource(R.string.language),
            LANGUAGES.map { stringResource(it.second) },
            LANGUAGES.indexOfFirst { it.first == currentLang }.coerceAtLeast(0),
            onPick = { i ->
                val tag = LANGUAGES[i].first
                Prefs.language = tag
                AppCompatDelegate.setApplicationLocales(
                    if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag)
                )
            },
            onDismiss = { dialog = null }
        )
        "charset" -> ChoiceDialog(
            stringResource(R.string.zip_encoding),
            ZIP_CHARSETS,
            ZIP_CHARSETS.indexOf(Prefs.archiveCharset).coerceAtLeast(0),
            onPick = { Prefs.archiveCharset = ZIP_CHARSETS[it] },
            onDismiss = { dialog = null }
        )
    }
}

@Composable
private fun ChoiceDialog(title: String, options: List<String>, selected: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEachIndexed { i, o ->
                    RadioRow(o, i == selected) {
                        onDismiss()
                        onPick(i)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } }
    )
}

@Composable
private fun Header(text: String) {
    HorizontalDivider(color = ZtTheme.colors.divider)
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun Item(title: String, summary: String?, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (summary != null) Text(summary, style = MaterialTheme.typography.bodySmall, color = ZtTheme.colors.secondaryText)
    }
}

@Composable
private fun Toggle(title: String, summary: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (summary != null) Text(summary, style = MaterialTheme.typography.bodySmall, color = ZtTheme.colors.secondaryText)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun PinDialog(onDismiss: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    val valid = pin.length >= 4 && pin == repeat
    val setMsg = stringResource(R.string.app_lock_set)
    val removedMsg = stringResource(R.string.app_lock_removed)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.app_lock)) },
        text = {
            Column {
                Text(stringResource(R.string.app_lock_help), style = MaterialTheme.typography.bodySmall, color = ZtTheme.colors.secondaryText)
                androidx.compose.material3.OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter { c -> c.isDigit() }.take(12) },
                    label = { Text(stringResource(R.string.lock_new_pin)) },
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
                androidx.compose.material3.OutlinedTextField(
                    value = repeat,
                    onValueChange = { repeat = it.filter { c -> c.isDigit() }.take(12) },
                    label = { Text(stringResource(R.string.lock_repeat_pin)) },
                    singleLine = true,
                    isError = repeat.isNotEmpty() && repeat != pin,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                AppLock.set(pin)
                AppState.unlockedOnce = true
                AppState.toast(setMsg)
                onDismiss()
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            Row {
                if (AppLock.enabled) {
                    TextButton(onClick = {
                        AppLock.clear()
                        AppState.toast(removedMsg)
                        onDismiss()
                    }) { Text(stringResource(R.string.app_lock_remove), color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        }
    )
}
