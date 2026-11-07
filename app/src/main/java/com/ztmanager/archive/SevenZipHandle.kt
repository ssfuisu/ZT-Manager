package com.ztmanager.archive

import com.ztmanager.fs.FileOps
import com.ztmanager.task.CancelledByUserException
import com.ztmanager.task.ConflictAction
import com.ztmanager.task.PasswordCache
import kotlinx.coroutines.CancellationException
import net.sf.sevenzipjbinding.ArchiveFormat
import net.sf.sevenzipjbinding.ExtractAskMode
import net.sf.sevenzipjbinding.ExtractOperationResult
import net.sf.sevenzipjbinding.IArchiveExtractCallback
import net.sf.sevenzipjbinding.IArchiveOpenCallback
import net.sf.sevenzipjbinding.IArchiveOpenVolumeCallback
import net.sf.sevenzipjbinding.ICryptoGetTextPassword
import net.sf.sevenzipjbinding.IInArchive
import net.sf.sevenzipjbinding.IInStream
import net.sf.sevenzipjbinding.ISequentialOutStream
import net.sf.sevenzipjbinding.PropID
import net.sf.sevenzipjbinding.SevenZip
import net.sf.sevenzipjbinding.SevenZipException
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.util.Date

/**
 * Archive handle backed by the native 7-Zip engine. Supports 7z, RAR, RAR5, TAR, GZ,
 * BZ2, XZ, ISO, CAB, ARJ, LZH, WIM, CPIO, RPM and more.
 *
 * @param source the file actually opened (may be a temporary unpacked tar)
 * @param file the file shown to the user
 */
class SevenZipHandle(
    private val source: File,
    override val file: File,
    pw: String?
) : ArchiveHandle {

    override var password: String? = pw
        private set
    private val openCallback = VolumeCallback(source, pw)
    private val archive: IInArchive
    override val formatName: String
    override val items: List<ArchiveItem>

    init {
        archive = try {
            val stream = openCallback.getStream(source.absolutePath)
                ?: throw IOException("Cannot open ${source.name}")
            SevenZip.openInArchive(null, stream, openCallback)
        } catch (e: SevenZipException) {
            openCallback.close()
            if (openCallback.passwordAsked) throw PasswordRequiredException(pw != null)
            throw IOException(e.message ?: "Unsupported archive", e)
        } catch (e: UnsatisfiedLinkError) {
            openCallback.close()
            throw IOException("Native archive engine is not available on this device", e)
        } catch (e: NoClassDefFoundError) {
            openCallback.close()
            throw IOException("Native archive engine is not available on this device", e)
        } catch (e: ExceptionInInitializerError) {
            openCallback.close()
            throw IOException("Native archive engine is not available on this device", e)
        }
        formatName = archive.archiveFormat?.methodName?.uppercase() ?: "ARCHIVE"
        val count = archive.numberOfItems
        val list = ArrayList<ArchiveItem>(count)
        val single = archive.archiveFormat?.supportMultipleFiles() == false
        for (i in 0 until count) {
            var path = archive.getStringProperty(i, PropID.PATH)?.let { normalizePath(it) } ?: ""
            if (path.isEmpty()) path = if (single) file.nameWithoutExtension.ifEmpty { "data" } else "item_$i"
            val isDir = archive.getProperty(i, PropID.IS_FOLDER) as? Boolean ?: false
            val size = (archive.getProperty(i, PropID.SIZE) as? Number)?.toLong() ?: -1L
            val packed = (archive.getProperty(i, PropID.PACKED_SIZE) as? Number)?.toLong() ?: -1L
            val time = (archive.getProperty(i, PropID.LAST_MODIFICATION_TIME) as? Date)?.time ?: 0L
            val enc = archive.getProperty(i, PropID.ENCRYPTED) as? Boolean ?: false
            list.add(ArchiveItem(path, isDir, size, packed, time, enc, i))
        }
        items = list
        if (pw != null) PasswordCache.put(file.absolutePath, pw)
    }

    val archiveFormat: ArchiveFormat? get() = archive.archiveFormat

    override suspend fun extract(items: List<ArchiveItem>, destDir: File, stripPrefix: String, ctx: ExtractContext) {
        val targets = HashMap<Int, File>()
        ctx.setTotal(items.sumOf { if (it.isDir) 0L else it.size.coerceAtLeast(0) })
        for (item in items) {
            ctx.checkCancelled()
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
            targets[item.index] = target
        }
        if (targets.isEmpty()) return
        val byIndex = items.associateBy { it.index }
        if (password == null && targets.keys.any { byIndex[it]?.encrypted == true }) {
            password = ctx.askPassword(file.name, false) ?: throw CancellationException()
        }
        var pending = targets
        var attempt = 0
        while (pending.isNotEmpty()) {
            val cb = ExtractCallback(pending, ctx, password)
            try {
                archive.extract(pending.keys.sorted().toIntArray(), false, cb)
            } catch (e: SevenZipException) {
                cb.closeCurrent()
                if (ctx.cancelled) throw CancelledByUserException()
                throw IOException(e.message ?: "Extraction failed", e)
            }
            if (ctx.cancelled) throw CancelledByUserException()
            if (cb.failed.isEmpty()) break
            val passwordProblem = cb.failed.values.any {
                it == ExtractOperationResult.WRONG_PASSWORD ||
                    ((it == ExtractOperationResult.DATAERROR || it == ExtractOperationResult.CRCERROR) &&
                        targets.keys.any { k -> byIndex[k]?.encrypted == true })
            }
            if (passwordProblem && attempt < 10) {
                attempt++
                password = ctx.askPassword(file.name, true) ?: throw CancellationException()
                pending = HashMap(cb.failed.keys.associateWith { targets[it]!! })
            } else {
                throw IOException("Extraction error: " + cb.failed.values.first().name)
            }
        }
        password?.let { PasswordCache.put(file.absolutePath, it) }
        items.forEach { item ->
            if (item.time > 0) targets[item.index]?.setLastModified(item.time)
        }
    }

    override suspend fun test(ctx: ExtractContext): List<String> {
        val files = items.filter { !it.isDir }
        ctx.setTotal(files.sumOf { it.size.coerceAtLeast(0) })
        if (password == null && files.any { it.encrypted }) {
            password = ctx.askPassword(file.name, false) ?: throw CancellationException()
        }
        val byIndex = items.associateBy { it.index }
        val cb = TestCallback(ctx, password)
        try {
            archive.extract(files.map { it.index }.sorted().toIntArray(), true, cb)
        } catch (e: SevenZipException) {
            if (ctx.cancelled) throw CancelledByUserException()
            throw IOException(e.message ?: "Test failed", e)
        }
        if (ctx.cancelled) throw CancelledByUserException()
        return cb.failed.mapNotNull { byIndex[it]?.path }
    }

    private class TestCallback(val ctx: ExtractContext, val password: String?) : IArchiveExtractCallback, ICryptoGetTextPassword {
        var currentIndex = -1
        val failed = ArrayList<Int>()

        override fun getStream(index: Int, extractAskMode: ExtractAskMode?): ISequentialOutStream? {
            currentIndex = index
            if (extractAskMode == ExtractAskMode.SKIP) return null
            return ISequentialOutStream { data ->
                if (ctx.cancelled) throw SevenZipException("Cancelled")
                ctx.addBytes(data.size.toLong())
                data.size
            }
        }

        override fun prepareOperation(extractAskMode: ExtractAskMode?) {}

        override fun setOperationResult(extractOperationResult: ExtractOperationResult?) {
            if (currentIndex >= 0 && extractOperationResult != ExtractOperationResult.OK) failed.add(currentIndex)
            currentIndex = -1
        }

        override fun setTotal(total: Long) {}
        override fun setCompleted(complete: Long) {}
        override fun cryptoGetTextPassword(): String = password ?: ""
    }

    private class ExtractCallback(
        val targets: Map<Int, File>,
        val ctx: ExtractContext,
        val password: String?
    ) : IArchiveExtractCallback, ICryptoGetTextPassword {
        var current: FileOutputStream? = null
        var currentIndex = -1
        val failed = HashMap<Int, ExtractOperationResult>()

        override fun getStream(index: Int, extractAskMode: ExtractAskMode?): ISequentialOutStream? {
            closeCurrent()
            if (extractAskMode != ExtractAskMode.EXTRACT) return null
            val target = targets[index] ?: return null
            target.parentFile?.mkdirs()
            val out = FileOutputStream(target)
            current = out
            currentIndex = index
            ctx.setMessage(target.name)
            return ISequentialOutStream { data ->
                if (ctx.cancelled) throw SevenZipException("Cancelled")
                out.write(data)
                ctx.addBytes(data.size.toLong())
                data.size
            }
        }

        override fun prepareOperation(extractAskMode: ExtractAskMode?) {}

        override fun setOperationResult(extractOperationResult: ExtractOperationResult?) {
            val idx = currentIndex
            closeCurrent()
            if (idx >= 0 && extractOperationResult != ExtractOperationResult.OK) {
                failed[idx] = extractOperationResult ?: ExtractOperationResult.UNKNOWN_OPERATION_RESULT
                targets[idx]?.delete()
            }
            currentIndex = -1
        }

        fun closeCurrent() {
            try {
                current?.close()
            } catch (_: Exception) {
            }
            current = null
        }

        override fun setTotal(total: Long) {}
        override fun setCompleted(complete: Long) {}

        override fun cryptoGetTextPassword(): String = password ?: ""
    }

    private class VolumeCallback(val first: File, val password: String?) :
        IArchiveOpenVolumeCallback, IArchiveOpenCallback, ICryptoGetTextPassword {
        private val opened = HashMap<String, RandomAccessFile>()
        private var name: String = first.absolutePath
        var passwordAsked = false

        override fun getProperty(propID: PropID?): Any? = if (propID == PropID.NAME) name else null

        override fun getStream(filename: String?): IInStream? {
            if (filename == null) return null
            val f = File(filename).let { if (it.isAbsolute) it else File(first.parentFile, filename) }
            return try {
                val raf = opened[f.absolutePath] ?: RandomAccessFile(f, "r").also { opened[f.absolutePath] = it }
                raf.seek(0)
                name = f.absolutePath
                RandomAccessFileInStream(raf)
            } catch (e: Exception) {
                null
            }
        }

        override fun setTotal(files: Long?, bytes: Long?) {}
        override fun setCompleted(files: Long?, bytes: Long?) {}

        override fun cryptoGetTextPassword(): String {
            passwordAsked = true
            return password ?: throw SevenZipException("Password required")
        }

        fun close() {
            opened.values.forEach {
                try {
                    it.close()
                } catch (_: Exception) {
                }
            }
            opened.clear()
        }
    }

    override fun close() {
        try {
            archive.close()
        } catch (_: Exception) {
        }
        openCallback.close()
    }
}
