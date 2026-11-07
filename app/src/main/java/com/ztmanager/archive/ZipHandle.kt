package com.ztmanager.archive

import com.ztmanager.fs.FileOps
import com.ztmanager.task.ConflictAction
import com.ztmanager.task.PasswordCache
import kotlinx.coroutines.CancellationException
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.exception.ZipException
import net.lingala.zip4j.model.FileHeader
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.CompressionLevel
import net.lingala.zip4j.model.enums.CompressionMethod
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.Charset

class ZipHandle(override val file: File, charset: Charset) : ArchiveHandle {

    private val zip = ZipFile(file)
    override var password: String? = PasswordCache.get(file.absolutePath)
        private set
    override val formatName: String = "ZIP"
    override var items: List<ArchiveItem> = emptyList()
        private set
    private var headers: List<FileHeader> = emptyList()

    init {
        zip.charset = charset
        password?.let { zip.setPassword(it.toCharArray()) }
        if (!zip.isValidZipFile) throw ZipException("Not a valid zip file")
        reload()
    }

    private fun reload() {
        headers = zip.fileHeaders
        items = headers.mapIndexed { i, h ->
            ArchiveItem(
                path = normalizePath(h.fileName),
                isDir = h.isDirectory,
                size = h.uncompressedSize,
                packedSize = h.compressedSize,
                time = h.lastModifiedTimeEpoch,
                encrypted = h.isEncrypted,
                index = i
            )
        }
    }

    override val canModify: Boolean
        get() = file.canWrite() && !zip.isSplitArchive

    private fun setPassword(pw: String) {
        password = pw
        zip.setPassword(pw.toCharArray())
        PasswordCache.put(file.absolutePath, pw)
    }

    override suspend fun extract(items: List<ArchiveItem>, destDir: File, stripPrefix: String, ctx: ExtractContext) {
        ctx.setTotal(items.sumOf { if (it.isDir) 0L else it.size.coerceAtLeast(0) })
        var done = 0
        for (item in items) {
            ctx.checkCancelled()
            ctx.setItems(done++, items.size)
            val rel = ExtractContext.relative(item.path, stripPrefix)
            if (rel.isEmpty()) continue
            var target = ExtractContext.safeTarget(destDir, rel)
            if (item.isDir) {
                target.mkdirs()
                continue
            }
            if (target.exists()) {
                when (ctx.resolveConflict(target)) {
                    ConflictAction.SKIP -> continue
                    ConflictAction.RENAME -> target = FileOps.uniqueFile(target.parentFile!!, target.name)
                    else -> {}
                }
            }
            target.parentFile?.mkdirs()
            val header = headers[item.index]
            ctx.setMessage(item.name)
            if (header.isEncrypted && password == null) {
                val pw = ctx.askPassword(file.name, false) ?: throw CancellationException()
                setPassword(pw)
            }
            while (true) {
                try {
                    zip.getInputStream(header).use { input ->
                        FileOutputStream(target).use { out -> FileOps.pipe(ctx.tc, input, out) }
                    }
                    if (item.time > 0) target.setLastModified(item.time)
                    break
                } catch (e: ZipException) {
                    target.delete()
                    if (e.type == ZipException.Type.WRONG_PASSWORD || (header.isEncrypted && e.message?.contains("password", true) == true)) {
                        val pw = ctx.askPassword(file.name, true) ?: throw CancellationException()
                        setPassword(pw)
                    } else {
                        throw e
                    }
                }
            }
        }
        ctx.setItems(items.size, items.size)
    }

    override suspend fun test(ctx: ExtractContext): List<String> {
        val bad = ArrayList<String>()
        ctx.setTotal(items.sumOf { if (it.isDir) 0L else it.size.coerceAtLeast(0) })
        val buf = ByteArray(128 * 1024)
        items.forEachIndexed { i, item ->
            ctx.checkCancelled()
            ctx.setItems(i, items.size)
            if (item.isDir) return@forEachIndexed
            ctx.setMessage(item.name)
            val header = headers[item.index]
            if (header.isEncrypted && password == null) {
                val pw = ctx.askPassword(file.name, false) ?: throw CancellationException()
                setPassword(pw)
            }
            try {
                // zip4j verifies the CRC when the entry stream reaches its end
                zip.getInputStream(header).use { input ->
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        ctx.addBytes(n.toLong())
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                bad.add(item.path)
            }
        }
        return bad
    }

    override fun addFiles(files: List<File>, innerDir: String, ctx: ExtractContext) {
        val params = ZipParameters().apply {
            if (innerDir.isNotEmpty()) rootFolderNameInZip = innerDir
            compressionMethod = CompressionMethod.DEFLATE
            compressionLevel = CompressionLevel.NORMAL
        }
        for (f in files) {
            ctx.checkCancelled()
            ctx.setMessage(f.name)
            if (f.isDirectory) zip.addFolder(f, params) else zip.addFile(f, params)
        }
        reload()
    }

    override fun rename(path: String, newName: String) {
        val parent = path.substringBeforeLast('/', "")
        val target = if (parent.isEmpty()) newName else "$parent/$newName"
        val map = LinkedHashMap<String, String>()
        for (h in headers) {
            val n = normalizePath(h.fileName)
            val suffix = if (h.fileName.endsWith("/")) "/" else ""
            when {
                n == path -> map[h.fileName] = target + suffix
                n.startsWith("$path/") -> map[h.fileName] = target + n.substring(path.length) + suffix
            }
        }
        if (map.isEmpty()) throw java.io.IOException("Entry not found")
        zip.renameFiles(map)
        reload()
    }

    override fun delete(items: List<ArchiveItem>) {
        val names = items.map { headers[it.index].fileName }
        if (names.isNotEmpty()) zip.removeFiles(names)
        reload()
    }

    override fun close() {
        try {
            zip.close()
        } catch (_: Exception) {
        }
    }
}
