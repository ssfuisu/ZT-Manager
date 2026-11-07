package com.ztmanager.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Preferences backed by SharedPreferences and exposed as Compose state so the UI
 * recomposes automatically when a value changes.
 */
object Prefs {
    private lateinit var sp: SharedPreferences

    fun init(context: Context) {
        sp = context.getSharedPreferences("zt_prefs", Context.MODE_PRIVATE)
    }

    // Appearance
    var themeMode by IntPref("theme_mode", THEME_SYSTEM)
    var language by StringPref("language", "")

    // Browser
    var showHidden by BoolPref("show_hidden", false)
    var sortBy by IntPref("sort_by", SORT_NAME)
    var sortDesc by BoolPref("sort_desc", false)
    var foldersFirst by BoolPref("folders_first", true)
    var gridView by BoolPref("grid_view", false)
    var showThumbnails by BoolPref("show_thumbnails", true)
    var showFolderCount by BoolPref("show_folder_count", true)
    var confirmDelete by BoolPref("confirm_delete", true)
    var rootMode by BoolPref("root_mode", false)
    var singlePane by BoolPref("single_pane", false)
    var leftPath by StringPref("left_path", "")
    var rightPath by StringPref("right_path", "")
    var bookmarksRaw by StringPref("bookmarks", "")
    var recentRaw by StringPref("recent_dirs", "")
    var useTrash by BoolPref("use_trash", true)
    var builtInViewers by BoolPref("builtin_viewers", true)
    var accent by IntPref("accent", 0)
    var pinHash by StringPref("pin_hash", "")
    var savedPasswordsRaw by StringPref("saved_passwords", "")
    var showFolderSizes by BoolPref("show_folder_sizes", false)
    var pinSalt by StringPref("pin_salt", "")

    // Editor
    var editorFontSize by IntPref("editor_font_size", 14)
    var editorWordWrap by BoolPref("editor_word_wrap", false)
    var editorLineNumbers by BoolPref("editor_line_numbers", true)
    var editorHighlight by BoolPref("editor_highlight", true)
    var editorAutoIndent by BoolPref("editor_auto_indent", true)

    // Archives
    var compressionLevel by IntPref("compression_level", 5)
    var archiveCharset by StringPref("archive_charset", "UTF-8")

    val bookmarks: List<String>
        get() = bookmarksRaw.split('\n').filter { it.isNotBlank() }

    fun addBookmark(path: String) {
        if (path !in bookmarks) bookmarksRaw = (bookmarks + path).joinToString("\n")
    }

    val recentDirs: List<String>
        get() = recentRaw.split('\n').filter { it.isNotBlank() }

    fun addRecent(path: String) {
        recentRaw = (listOf(path) + recentDirs.filter { it != path }).take(10).joinToString("\n")
    }

    val savedPasswords: List<String>
        get() = savedPasswordsRaw.split('\n').filter { it.isNotEmpty() }

    fun savePassword(pw: String) {
        if (pw.isEmpty() || pw.contains('\n')) return
        savedPasswordsRaw = (listOf(pw) + savedPasswords.filter { it != pw }).take(30).joinToString("\n")
    }

    fun clearRecent() {
        recentRaw = ""
    }

    fun removeBookmark(path: String) {
        bookmarksRaw = bookmarks.filter { it != path }.joinToString("\n")
    }

    const val THEME_SYSTEM = 0
    const val THEME_LIGHT = 1
    const val THEME_DARK = 2
    const val THEME_BLACK = 3

    const val SORT_NAME = 0
    const val SORT_SIZE = 1
    const val SORT_DATE = 2
    const val SORT_TYPE = 3

    private abstract class Pref<T>(val key: String, val def: T) : ReadWriteProperty<Any?, T> {
        private var state: MutableState<T>? = null
        abstract fun read(): T
        abstract fun write(value: T)
        private fun s(): MutableState<T> = state ?: mutableStateOf(read()).also { state = it }
        override fun getValue(thisRef: Any?, property: KProperty<*>): T = s().value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            s().value = value
            write(value)
        }
    }

    private class IntPref(key: String, def: Int) : Pref<Int>(key, def) {
        override fun read() = sp.getInt(key, def)
        override fun write(value: Int) = sp.edit().putInt(key, value).apply()
    }

    private class BoolPref(key: String, def: Boolean) : Pref<Boolean>(key, def) {
        override fun read() = sp.getBoolean(key, def)
        override fun write(value: Boolean) = sp.edit().putBoolean(key, value).apply()
    }

    private class StringPref(key: String, def: String) : Pref<String>(key, def) {
        override fun read() = sp.getString(key, def) ?: def
        override fun write(value: String) = sp.edit().putString(key, value).apply()
    }
}
