package com.ztmanager.ui.tools

import android.app.ActivityManager
import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ztmanager.R
import com.ztmanager.fs.Format
import com.ztmanager.fs.StorageUtil
import com.ztmanager.ui.AppState
import com.ztmanager.ui.browser.dialogs.InfoRow
import com.ztmanager.ui.common.Intents
import com.ztmanager.ui.theme.ZtTheme
import java.io.File

private fun cpuName(): String = try {
    File("/proc/cpuinfo").readLines().firstOrNull { it.startsWith("Hardware") || it.startsWith("model name") }
        ?.substringAfter(':')?.trim() ?: Build.HARDWARE
} catch (e: Exception) {
    Build.HARDWARE
}

private fun maxFreq(): String = try {
    val cpus = File("/sys/devices/system/cpu").listFiles { f -> f.name.matches(Regex("cpu\\d+")) } ?: emptyArray()
    val max = cpus.mapNotNull { File(it, "cpufreq/cpuinfo_max_freq").takeIf { f -> f.canRead() }?.readText()?.trim()?.toLongOrNull() }.maxOrNull()
    if (max != null) String.format("%.2f GHz", max / 1_000_000.0) else "-"
} catch (e: Exception) {
    "-"
}

private fun collect(context: Context): List<Pair<String, List<Pair<String, String>>>> {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val mem = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
    val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    val dm = context.resources.displayMetrics
    val up = SystemClock.elapsedRealtime() / 1000
    fun g(id: Int, vararg args: Any): String = context.getString(id, *args)
    val device = listOf(
        g(R.string.di_model) to Build.MODEL,
        g(R.string.di_manufacturer) to Build.MANUFACTURER,
        g(R.string.di_brand) to Build.BRAND,
        g(R.string.di_codename) to Build.DEVICE,
        g(R.string.di_board) to Build.BOARD
    )
    val system = listOf(
        "Android" to Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")",
        g(R.string.di_security_patch) to Build.VERSION.SECURITY_PATCH,
        g(R.string.di_build) to Build.DISPLAY,
        g(R.string.di_kernel) to (System.getProperty("os.version") ?: "-"),
        "Java VM" to (System.getProperty("java.vm.version") ?: "-"),
        "Fingerprint" to Build.FINGERPRINT,
        g(R.string.di_uptime) to String.format("%dd %02dh %02dm", up / 86400, (up / 3600) % 24, (up / 60) % 60)
    )
    val hardware = listOf(
        "CPU" to cpuName(),
        g(R.string.di_cores) to Runtime.getRuntime().availableProcessors().toString(),
        g(R.string.di_max_freq) to maxFreq(),
        "ABIs" to Build.SUPPORTED_ABIS.joinToString(", "),
        "RAM" to g(R.string.storage_free, Format.size(mem.availMem), Format.size(mem.totalMem)),
        g(R.string.di_screen) to "${dm.widthPixels} x ${dm.heightPixels}  ${dm.densityDpi} dpi",
        g(R.string.di_battery) to bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).let { if (it > 0) "$it%" else "-" }
    )
    val storage = StorageUtil.volumes(context).map { v -> v.name to g(R.string.storage_free, Format.size(v.free), Format.size(v.total)) }
    return listOf(g(R.string.di_device) to device, g(R.string.di_system) to system, g(R.string.di_hardware) to hardware, g(R.string.section_storage) to storage)
}

@Composable
fun DeviceInfoScreen() {
    val context = LocalContext.current
    val data = remember { collect(context) }
    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ZtTheme.colors.toolbar),
                navigationIcon = { IconButton(onClick = { AppState.pop() }) { Icon(Icons.Filled.ArrowBack, null) } },
                title = { Text(stringResource(R.string.tool_device_info)) },
                actions = {
                    IconButton(onClick = {
                        Intents.copyText(context, data.joinToString("\n\n") { (t, rows) -> "[$t]\n" + rows.joinToString("\n") { "${it.first}: ${it.second}" } })
                    }) { Icon(Icons.Filled.ContentCopy, null) }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize().padding(horizontal = 12.dp)) {
            items(data) { (title, rows) ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        rows.forEach { (k, v) -> InfoRow(k, v) }
                    }
                }
            }
        }
    }
}
