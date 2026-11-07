package com.ztmanager.fs

import java.io.File

/**
 * A single row shown in a browser pane. For entries inside an archive, [path] is the
 * normalized inner path (no leading slash, "/" separated).
 */
data class FileEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val isHidden: Boolean = name.startsWith("."),
    val isSymlink: Boolean = false,
    val childCount: Int = -1,
    val permissions: String = "",
    val packedSize: Long = -1,
    val encrypted: Boolean = false,
    val inArchive: Boolean = false
) {
    val type: FileType = FileType.of(name, isDirectory)
    val extension: String get() = FileType.extensionOf(name)
    val file: File get() = File(path)

    companion object {
        fun fromFile(f: File, countChildren: Boolean): FileEntry {
            val isDir = f.isDirectory
            var perms = ""
            var isLink = false
            try {
                val st = android.system.Os.lstat(f.absolutePath)
                perms = modeToString(st.st_mode)
                isLink = (st.st_mode and 0xF000) == 0xA000
            } catch (e: Exception) {
                perms = (if (isDir) "d" else "-") + (if (f.canRead()) "r" else "-") + (if (f.canWrite()) "w" else "-") + "-------"
            }
            return FileEntry(
                name = f.name,
                path = f.absolutePath,
                isDirectory = isDir,
                size = if (isDir) 0 else f.length(),
                lastModified = f.lastModified(),
                isSymlink = isLink,
                childCount = if (isDir && countChildren) (f.list()?.size ?: -1) else -1,
                permissions = perms
            )
        }

        fun permString(f: File, isDir: Boolean): String {
            return try {
                val st = android.system.Os.lstat(f.absolutePath)
                modeToString(st.st_mode)
            } catch (e: Exception) {
                (if (isDir) "d" else "-") + (if (f.canRead()) "r" else "-") + (if (f.canWrite()) "w" else "-") + "-------"
            }
        }

        fun modeToString(mode: Int): String {
            val sb = StringBuilder()
            val fmt = mode and 0xF000
            sb.append(
                when (fmt) {
                    0x4000 -> 'd'
                    0xA000 -> 'l'
                    0x2000 -> 'c'
                    0x6000 -> 'b'
                    0x1000 -> 'p'
                    0xC000 -> 's'
                    else -> '-'
                }
            )
            val chars = "rwxrwxrwx"
            for (i in 0 until 9) {
                val bit = 1 shl (8 - i)
                sb.append(if (mode and bit != 0) chars[i] else '-')
            }
            return sb.toString()
        }
    }
}
