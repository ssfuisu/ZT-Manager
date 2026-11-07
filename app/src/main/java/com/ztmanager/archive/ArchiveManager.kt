package com.ztmanager.archive

import com.ztmanager.ZtApp
import com.ztmanager.data.Prefs
import com.ztmanager.fs.FileType
import com.ztmanager.task.PasswordCache
import com.ztmanager.task.Prompter
import kotlinx.coroutines.CancellationException
import net.sf.sevenzipjbinding.ArchiveFormat
import java.io.File
import java.nio.charset.Charset

object ArchiveManager {

    private val zipExt = setOf("zip", "jar", "apk", "aar", "apks", "xapk", "apkm")
    private val compressorFormats = setOf(ArchiveFormat.GZIP, ArchiveFormat.BZIP2, ArchiveFormat.LZMA, ArchiveFormat.Z)

    fun charset(): Charset = try {
        Charset.forName(Prefs.archiveCharset)
    } catch (e: Exception) {
        Charsets.UTF_8
    }

    /** Opens an archive. Throws [PasswordRequiredException] if the header is encrypted. */
    fun open(file: File, password: String? = PasswordCache.get(file.absolutePath)): ArchiveHandle {
        val ext = FileType.extensionOf(file.name)
        if (ext in zipExt) {
            try {
                return ZipHandle(file, charset())
            } catch (e: Exception) {
                // fall through to the 7-Zip engine
            }
        }
        val handle = SevenZipHandle(file, file, password)
        return unwrapTarball(handle) ?: handle
    }

    /** Opens an archive asking the user for a password when needed. */
    suspend fun openInteractive(file: File): ArchiveHandle {
        var pw: String? = PasswordCache.get(file.absolutePath)
        var wrong = false
        while (true) {
            try {
                return open(file, pw)
            } catch (e: PasswordRequiredException) {
                if (pw != null) wrong = true
                pw = Prompter.askPassword(file.name, wrong) ?: throw CancellationException()
            }
        }
    }

    /**
     * Compressed tarballs (tar.gz, tgz, tar.xz ...) appear as a single inner .tar file.
     * Unpack it into the cache so the user can browse the real content directly.
     */
    private fun unwrapTarball(handle: SevenZipHandle): ArchiveHandle? {
        val fmt = handle.archiveFormat
        val isCompressor = fmt in compressorFormats || fmt?.name == "XZ"
        if (!isCompressor || handle.items.size != 1) return null
        val inner = handle.items[0]
        val lowerName = handle.file.name.lowercase()
        val looksTar = inner.path.lowercase().endsWith(".tar") ||
            lowerName.endsWith(".tgz") || lowerName.endsWith(".tbz") || lowerName.endsWith(".tbz2") ||
            lowerName.endsWith(".txz") || lowerName.contains(".tar.")
        if (!looksTar) return null
        val dir = File(ZtApp.instance.cacheDir, "tarballs").apply { mkdirs() }
        val key = (handle.file.absolutePath + handle.file.length() + handle.file.lastModified()).hashCode().toUInt().toString(16)
        val tar = File(dir, "$key.tar")
        try {
            if (!tar.exists() || tar.length() == 0L) {
                val tmpDir = File(dir, "$key.tmp").apply { deleteRecursively(); mkdirs() }
                kotlinx.coroutines.runBlocking {
                    handle.extract(listOf(inner), tmpDir, "", ExtractContext(null))
                }
                val out = tmpDir.walkTopDown().firstOrNull { it.isFile } ?: return null
                out.renameTo(tar)
                tmpDir.deleteRecursively()
            }
            val tarHandle = SevenZipHandle(tar, handle.file, null)
            handle.close()
            return tarHandle
        } catch (e: Exception) {
            tar.delete()
            return null
        }
    }

    /** Extracts one item into the cache so it can be opened by a viewer. */
    suspend fun extractToCache(handle: ArchiveHandle, item: ArchiveItem): File {
        val key = handle.file.absolutePath.hashCode().toUInt().toString(16)
        val dir = File(ZtApp.instance.cacheDir, "archive_open/$key")
        val parent = item.path.substringBeforeLast('/', "")
        val target = File(dir, item.path)
        if (target.exists()) target.delete()
        handle.extract(listOf(item), dir, "", ExtractContext(null))
        if (!target.exists()) {
            // single-stream formats may write with a different name
            val alt = File(dir, parent).listFiles()?.firstOrNull { it.isFile }
            if (alt != null) return alt
        }
        return target
    }

    fun clearCache() {
        File(ZtApp.instance.cacheDir, "archive_open").deleteRecursively()
        File(ZtApp.instance.cacheDir, "tarballs").deleteRecursively()
        File(ZtApp.instance.cacheDir, "incoming").deleteRecursively()
        File(ZtApp.instance.cacheDir, "root").deleteRecursively()
    }
}
