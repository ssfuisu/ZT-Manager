package com.ztmanager.ui.viewer

import android.graphics.Typeface
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ztmanager.R
import com.ztmanager.fs.Format
import com.ztmanager.ui.AppState
import com.ztmanager.ui.common.Intents
import com.ztmanager.ui.theme.ZtTheme
import java.io.File

@Composable
fun FontViewerScreen(path: String) {
    val context = LocalContext.current
    val file = remember { File(path) }
    val family = remember {
        try {
            FontFamily(Typeface.createFromFile(file))
        } catch (e: Exception) {
            null
        }
    }
    var sample by remember { mutableStateOf("The quick brown fox jumps over the lazy dog") }
    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ZtTheme.colors.toolbar),
                navigationIcon = { IconButton(onClick = { AppState.pop() }) { Icon(Icons.Filled.ArrowBack, null) } },
                title = {
                    Column {
                        Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                        Text(Format.size(file.length()), style = MaterialTheme.typography.bodySmall, color = ZtTheme.colors.secondaryText)
                    }
                },
                actions = { IconButton(onClick = { Intents.share(context, listOf(file)) }) { Icon(Icons.Filled.Share, null) } }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            if (family == null) {
                Text(stringResource(R.string.msg_font_invalid), color = MaterialTheme.colorScheme.error)
                return@Column
            }
            OutlinedTextField(value = sample, onValueChange = { sample = it }, label = { Text(stringResource(R.string.font_sample)) }, modifier = Modifier.fillMaxWidth())
            Text("ABCDEFGHIJKLMNOPQRSTUVWXYZ\nabcdefghijklmnopqrstuvwxyz\n0123456789 !?.,;:()[]{}@#&*+-=/", fontFamily = family, fontSize = 20.sp, modifier = Modifier.padding(vertical = 12.dp))
            Text("ÇĞİÖŞÜ çğıöşü  ÄÖÜß  ÀÉÎÕÛ  АБВГД  αβγδ", fontFamily = family, fontSize = 18.sp)
            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = ZtTheme.colors.divider)
            listOf(12, 16, 20, 28, 36, 48, 64).forEach { size ->
                Text("$size", style = MaterialTheme.typography.labelSmall, color = ZtTheme.colors.secondaryText)
                Text(sample, fontFamily = family, fontSize = size.sp, modifier = Modifier.padding(bottom = 10.dp))
            }
        }
    }
}
