package com.ztmanager.apk

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import com.ztmanager.R
import com.ztmanager.fs.FileType
import com.ztmanager.task.TaskContext
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile

/** Installs split APK bundles (.apks, .xapk, .apkm) through a PackageInstaller session. */
object SplitInstaller {
    private val bundleExt = setOf("apks", "xapk", "apkm")

    fun isBundle(f: File) = FileType.extensionOf(f.name) in bundleExt

    fun ensurePermission(context: Context): Boolean {
        if (context.packageManager.canRequestPackageInstalls()) return true
        try {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + context.packageName))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
        }
        Toast.makeText(context, R.string.msg_allow_install, Toast.LENGTH_LONG).show()
        return false
    }

    fun install(tc: TaskContext, context: Context, bundle: File) {
        val installer = context.packageManager.packageInstaller
        ZipFile(bundle).use { zip ->
            val apks = zip.entries().toList().filter { !it.isDirectory && it.name.lowercase().endsWith(".apk") }
            if (apks.isEmpty()) throw IOException("No APK files inside ${bundle.name}")
            tc.setTotalBytes(apks.sumOf { it.size.coerceAtLeast(0) })
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            val sessionId = installer.createSession(params)
            val session = installer.openSession(sessionId)
            try {
                apks.forEachIndexed { i, entry ->
                    tc.checkCancelled()
                    tc.setItems(i, apks.size)
                    val name = entry.name.substringAfterLast('/')
                    tc.setMessage(name)
                    session.openWrite("$i-$name", 0, entry.size.takeIf { it > 0 } ?: -1).use { out ->
                        zip.getInputStream(entry).use { input ->
                            val buf = ByteArray(256 * 1024)
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                tc.addBytes(n.toLong())
                            }
                        }
                        session.fsync(out)
                    }
                }
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                val pi = PendingIntent.getBroadcast(
                    context, sessionId,
                    Intent(context, InstallResultReceiver::class.java).setPackage(context.packageName),
                    flags
                )
                session.commit(pi.intentSender)
                session.close()
            } catch (e: Throwable) {
                session.abandon()
                throw e
            }
        }
        tc.flush()
    }
}

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm: Intent? = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                else intent.getParcelableExtra(Intent.EXTRA_INTENT)
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        context.startActivity(confirm)
                    } catch (_: Exception) {
                    }
                }
            }
            PackageInstaller.STATUS_SUCCESS -> Toast.makeText(context, R.string.msg_installed, Toast.LENGTH_SHORT).show()
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Error $status"
                Toast.makeText(context, context.getString(R.string.msg_install_failed) + ": " + msg, Toast.LENGTH_LONG).show()
            }
        }
    }
}
