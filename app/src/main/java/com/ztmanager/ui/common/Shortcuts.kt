package com.ztmanager.ui.common

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.ztmanager.MainActivity
import com.ztmanager.R
import com.ztmanager.ui.AppState
import java.io.File

object Shortcuts {
    const val ACTION_OPEN_DIR = "com.ztmanager.action.OPEN_DIR"
    const val EXTRA_PATH = "path"

    fun pinFolder(context: Context, path: String) {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            AppState.toast(context.getString(R.string.msg_not_supported))
            return
        }
        val intent = Intent(context, MainActivity::class.java)
            .setAction(ACTION_OPEN_DIR)
            .putExtra(EXTRA_PATH, path)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val name = File(path).name.ifEmpty { path }
        val info = ShortcutInfoCompat.Builder(context, "dir:" + path.hashCode())
            .setShortLabel(name)
            .setLongLabel(path)
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_folder))
            .setIntent(intent)
            .build()
        ShortcutManagerCompat.requestPinShortcut(context, info, null)
    }
}
