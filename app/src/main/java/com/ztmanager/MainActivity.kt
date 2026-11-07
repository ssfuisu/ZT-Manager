package com.ztmanager

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.SystemClock
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.ztmanager.data.AppLock
import com.ztmanager.fs.FileType
import com.ztmanager.ui.AppRoot
import com.ztmanager.ui.AppState
import com.ztmanager.ui.Screen
import com.ztmanager.ui.browser.Opener
import com.ztmanager.ui.common.Shortcuts
import com.ztmanager.ui.theme.ZtTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {

    private val legacyPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        updatePermission()
    }

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        updatePermission()
        if (AppLock.enabled && !AppState.unlockedOnce) AppState.locked = true
        setContent {
            ZtTheme {
                AppRoot(onRequestPermission = { requestStorage() })
            }
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (savedInstanceState == null) handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        val away = AppState.backgroundAt
        if (AppLock.enabled && away > 0 && SystemClock.elapsedRealtime() - away > AppLock.RELOCK_AFTER_MS) {
            AppState.locked = true
        }
        AppState.backgroundAt = 0
    }

    override fun onStop() {
        super.onStop()
        AppState.backgroundAt = SystemClock.elapsedRealtime()
    }

    override fun onResume() {
        super.onResume()
        val before = AppState.permissionGranted
        updatePermission()
        if (!before && AppState.permissionGranted) AppState.refreshAll()
    }

    private fun updatePermission() {
        AppState.permissionGranted = hasStoragePermission()
    }

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestStorage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName"))
                )
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            legacyPermission.launch(
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            )
        }
    }

    /** Opens files sent from other apps (archives, texts, APKs). */
    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Shortcuts.ACTION_OPEN_DIR) {
            val path = intent.getStringExtra(Shortcuts.EXTRA_PATH) ?: return
            AppState.stack.retainAll { it == Screen.Browser }
            if (File(path).isDirectory) AppState.active.navigate(path)
            return
        }
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        lifecycleScope.launch {
            val file = withContext(Dispatchers.IO) { resolve(uri) } ?: return@launch
            val pane = AppState.active
            AppState.stack.retainAll { it == Screen.Browser }
            if (FileType.of(file.name, false) == FileType.ARCHIVE || FileType.extensionOf(file.name) == "apk") {
                file.parent?.let { if (file.canWrite()) pane.navigate(it) }
            }
            Opener.openFile(this@MainActivity, pane, file, fromArchive = false)
        }
    }

    private fun resolve(uri: Uri): File? {
        if (uri.scheme == "file") return uri.path?.let { File(it) }
        if (uri.scheme != "content") return null
        // Try to map well known providers to a real path first.
        uri.path?.let { p ->
            val idx = p.indexOf("/storage/")
            if (idx >= 0) {
                val f = File(p.substring(idx))
                if (f.canRead()) return f
            }
            if (p.startsWith("/root/")) {
                val f = File(p.removePrefix("/root"))
                if (f.canRead()) return f
            }
        }
        var name = "file"
        try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0)?.let { name = it }
            }
        } catch (_: Exception) {
        }
        val dest = File(cacheDir, "incoming/" + name.replace('/', '_'))
        dest.parentFile?.mkdirs()
        return try {
            contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { input.copyTo(it) }
            } ?: return null
            dest
        } catch (e: Exception) {
            null
        }
    }
}
