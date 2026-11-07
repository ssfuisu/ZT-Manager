package com.ztmanager.fs

import android.webkit.MimeTypeMap
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Subject
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import java.util.Locale

enum class FileType(val color: Color, val icon: ImageVector) {
    FOLDER(Color(0xFFFFC34D), Icons.Filled.Folder),
    ARCHIVE(Color(0xFFE0794A), Icons.Filled.Archive),
    APK(Color(0xFF7CB342), Icons.Filled.Android),
    IMAGE(Color(0xFF26A69A), Icons.Filled.Image),
    VIDEO(Color(0xFFEF5350), Icons.Filled.Movie),
    AUDIO(Color(0xFFAB47BC), Icons.Filled.MusicNote),
    TEXT(Color(0xFF78909C), Icons.Filled.Subject),
    CODE(Color(0xFF42A5F5), Icons.Filled.Code),
    DOCUMENT(Color(0xFF5C6BC0), Icons.Filled.Description),
    PDF(Color(0xFFE53935), Icons.Filled.PictureAsPdf),
    FONT(Color(0xFF8D6E63), Icons.Filled.FontDownload),
    DATABASE(Color(0xFF26C6DA), Icons.Filled.Storage),
    UNKNOWN(Color(0xFF90A4AE), Icons.Filled.InsertDriveFile);

    companion object {
        private val archiveExt = setOf(
            "zip", "7z", "rar", "tar", "gz", "tgz", "bz2", "tbz", "tbz2", "xz", "txz", "lzma",
            "iso", "cab", "arj", "lzh", "lha", "z", "cpio", "rpm", "deb", "wim", "xar", "jar",
            "aar", "apks", "xapk", "obb", "001", "zipx", "chm", "udf", "dmg", "img", "ar"
        )
        private val imageExt = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "heif", "ico", "svg", "avif")
        private val videoExt = setOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "3gp", "m4v", "ts", "mpg", "mpeg")
        private val audioExt = setOf("mp3", "wav", "ogg", "flac", "aac", "m4a", "wma", "opus", "amr", "mid", "midi")
        val textExt = setOf(
            "txt", "log", "md", "ini", "cfg", "conf", "prop", "properties", "csv", "tsv", "srt",
            "ass", "nfo", "rc", "lst", "env", "gitignore", "toml", "yml", "yaml", "lrc", "smali"
        )
        val codeExt = setOf(
            "java", "kt", "kts", "gradle", "xml", "html", "htm", "css", "js", "mjs", "ts", "tsx", "jsx",
            "json", "c", "h", "cpp", "hpp", "cc", "cs", "py", "rb", "go", "rs", "php", "sh", "bash",
            "bat", "cmd", "ps1", "lua", "sql", "swift", "dart", "pl", "r", "vue", "mk", "cmake", "pro"
        )
        private val docExt = setOf("doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp", "rtf", "epub")
        private val fontExt = setOf("ttf", "otf", "woff", "woff2")
        private val dbExt = setOf("db", "sqlite", "sqlite3", "realm")

        fun extensionOf(name: String): String {
            val i = name.lastIndexOf('.')
            return if (i > 0 && i < name.length - 1) name.substring(i + 1).lowercase(Locale.ROOT) else ""
        }

        fun of(name: String, isDir: Boolean): FileType {
            if (isDir) return FOLDER
            val lower = name.lowercase(Locale.ROOT)
            if (lower == "makefile" || lower == "dockerfile") return CODE
            return when (val ext = extensionOf(name)) {
                "apk" -> APK
                "pdf" -> PDF
                in archiveExt -> ARCHIVE
                in imageExt -> IMAGE
                in videoExt -> VIDEO
                in audioExt -> AUDIO
                in codeExt -> CODE
                in textExt -> TEXT
                in docExt -> DOCUMENT
                in fontExt -> FONT
                in dbExt -> DATABASE
                else -> if (ext.isEmpty() && lower.startsWith(".")) TEXT else UNKNOWN
            }
        }

        fun mimeOf(name: String): String {
            val ext = extensionOf(name)
            if (ext == "apk") return "application/vnd.android.package-archive"
            return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: when (of(name, false)) {
                TEXT, CODE -> "text/plain"
                IMAGE -> "image/*"
                VIDEO -> "video/*"
                AUDIO -> "audio/*"
                else -> "*/*"
            }
        }

        fun isArchive(name: String) = of(name, false) == ARCHIVE || extensionOf(name) == "apk"
        fun isTextLike(name: String) = of(name, false).let { it == TEXT || it == CODE }
    }
}
