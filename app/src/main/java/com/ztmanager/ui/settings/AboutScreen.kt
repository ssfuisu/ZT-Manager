package com.ztmanager.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ztmanager.BuildConfig
import com.ztmanager.R
import com.ztmanager.ui.AppState
import com.ztmanager.ui.theme.ZtTheme

@Composable
fun AboutScreen() {
    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ZtTheme.colors.toolbar),
                navigationIcon = { IconButton(onClick = { AppState.pop() }) { Icon(Icons.Filled.ArrowBack, null) } },
                title = { Text(stringResource(R.string.about)) }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Filled.Folder, null, tint = ZtTheme.colors.folder, modifier = Modifier.size(88.dp))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("v" + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")", color = ZtTheme.colors.secondaryText)
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.about_text), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.licenses_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                "7-Zip-JBinding-4Android (LGPL 2.1)\n" +
                    "7-Zip by Igor Pavlov (LGPL 2.1 + unRAR restriction)\n" +
                    "zip4j (Apache 2.0)\n" +
                    "Apache Commons Compress (Apache 2.0)\n" +
                    "XZ for Java (Public Domain)\n" +
                    "AndroidX and Jetpack Compose (Apache 2.0)",
                style = MaterialTheme.typography.bodySmall,
                color = ZtTheme.colors.secondaryText
            )
        }
    }
}
