package com.ztmanager.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.ztmanager.BuildConfig
import com.ztmanager.R
import com.ztmanager.fs.FileType
import com.ztmanager.ui.AppState
import java.io.File

object Intents {

    fun uri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".provider", file)

    fun openExternal(context: Context, file: File, mime: String = FileType.mimeOf(file.name)) {
        try {
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri(context, file), mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.action_open_with)))
        } catch (e: ActivityNotFoundException) {
            AppState.toast(context.getString(R.string.msg_no_app))
        } catch (e: Exception) {
            AppState.toast(e.message)
        }
    }

    /** "Set as" chooser (wallpaper, contact photo ...). */
    fun setAs(context: Context, file: File) {
        try {
            val intent = Intent(Intent.ACTION_ATTACH_DATA)
                .setDataAndType(uri(context, file), FileType.mimeOf(file.name))
                .putExtra("mimeType", FileType.mimeOf(file.name))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.action_set_as)))
        } catch (e: Exception) {
            AppState.toast(e.message)
        }
    }

    fun share(context: Context, files: List<File>) {
        if (files.isEmpty()) return
        try {
            val intent = if (files.size == 1) {
                Intent(Intent.ACTION_SEND)
                    .setType(FileType.mimeOf(files[0].name))
                    .putExtra(Intent.EXTRA_STREAM, uri(context, files[0]))
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE)
                    .setType("*/*")
                    .putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(files.map { uri(context, it) }))
            }
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.action_share)))
        } catch (e: Exception) {
            AppState.toast(e.message)
        }
    }

    fun install(context: Context, file: File) {
        val pm = context.packageManager
        if (!pm.canRequestPackageInstalls()) {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + context.packageName))
                )
                AppState.toast(context.getString(R.string.msg_allow_install))
            } catch (_: Exception) {
            }
            return
        }
        try {
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri(context, file), "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            AppState.toast(e.message)
        }
    }

    fun appDetails(context: Context, pkg: String) {
        try {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))
        } catch (_: Exception) {
        }
    }

    fun uninstall(context: Context, pkg: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")))
        } catch (_: Exception) {
        }
    }

    fun launch(context: Context, pkg: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(pkg)
        if (intent == null) {
            AppState.toast(context.getString(R.string.msg_no_launcher))
            return
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            AppState.toast(e.message)
        }
    }

    fun copyText(context: Context, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("text", text))
        AppState.toast(context.getString(R.string.msg_copied))
    }
}
