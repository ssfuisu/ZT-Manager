package com.ztmanager.archive

import com.ztmanager.task.TaskContext
import net.lingala.zip4j.io.outputstream.ZipOutputStream
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionLevel
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.apache.commons.compress.archivers.sevenz.SevenZMethod
import org.apache.commons.compress.archivers.sevenz.SevenZMethodConfiguration
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipParameters
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream
import org.tukaani.xz.LZMA2Options
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

enum class CreateFormat(val ext: String, val label: String, val multiFile: Boolean, val supportsPassword: Boolean) {
    ZIP("zip", "zip", true, true),
    SEVEN_Z("7z", "7z", true, false),
    TAR("tar", "tar", true, false),
    TAR_GZ("tar.gz", "tar.gz", true, false),
    TAR_BZ2("tar.bz2", "tar.bz2", true, false),
    TAR_XZ("tar.xz", "tar.xz", true, false),
    GZ("gz", "gz", false, false),
    BZ2("bz2", "bz2", false, false),
    XZ("xz", "xz", false, false)
}

object ArchiveCreator {

    private data class Source(val file: File, val name: String)

    private fun collect(sources: List<File>): List<Source> {
        val out = ArrayList<Source>()
        fun walk(f: File, name: String) {
            out.add(Source(f, name))
            if (f.isDirectory) f.listFiles()?.sortedBy { it.name }?.forEach { walk(it, name + "/" + it.name) }
        }
        sources.forEach { walk(it, it.name) }
        return out
    }

    fun create(
        tc: TaskContext,
        sources: List<File>,
        output: File,
        format: CreateFormat,
        level: Int,
        password: String?
    ) {
        val entries = collect(sources)
        tc.setTotalBytes(entries.sumOf { if (it.file.isFile) it.file.length() else 0L })
        val tmp = File(output.parentFile, output.name + ".part")
        try {
            when (format) {
                CreateFormat.ZIP -> createZip(tc, entries, tmp, level, password)
                CreateFormat.SEVEN_Z -> create7z(tc, entries, tmp, level)
                CreateFormat.TAR -> createTar(tc, entries, BufferedOutputStream(FileOutputStream(tmp)))
                CreateFormat.TAR_GZ -> createTar(tc, entries, gzip(FileOutputStream(tmp), level))
                CreateFormat.TAR_BZ2 -> createTar(tc, entries, BZip2CompressorOutputStream(BufferedOutputStream(FileOutputStream(tmp)), level.coerceIn(1, 9)))
                CreateFormat.TAR_XZ -> createTar(tc, entries, XZCompressorOutputStream(BufferedOutputStream(FileOutputStream(tmp)), xzPreset(level)))
                CreateFormat.GZ -> single(tc, sources, gzip(FileOutputStream(tmp), level))
                CreateFormat.BZ2 -> single(tc, sources, BZip2CompressorOutputStream(BufferedOutputStream(FileOutputStream(tmp)), level.coerceIn(1, 9)))
                CreateFormat.XZ -> single(tc, sources, XZCompressorOutputStream(BufferedOutputStream(FileOutputStream(tmp)), xzPreset(level)))
            }
            if (output.exists()) output.delete()
            if (!tmp.renameTo(output)) throw IOException("Cannot write ${output.name}")
        } catch (e: Throwable) {
            tmp.delete()
            throw e
        }
        tc.flush()
    }

    // LZMA2 presets above 6 need hundreds of MB of RAM, which phones do not have.
    private fun xzPreset(level: Int) = level.coerceIn(0, 6)

    private fun gzip(out: OutputStream, level: Int): OutputStream {
        val p = GzipParameters()
        p.compressionLevel = level.coerceIn(0, 9)
        return GzipCompressorOutputStream(BufferedOutputStream(out), p)
    }

    private fun copyInto(tc: TaskContext, f: File, out: OutputStream) {
        FileInputStream(f).use { input ->
            val buf = ByteArray(128 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                tc.addBytes(n.toLong())
            }
        }
    }

    private fun createZip(tc: TaskContext, entries: List<Source>, out: File, level: Int, password: String?) {
        val encrypt = !password.isNullOrEmpty()
        val zos = if (encrypt) ZipOutputStream(FileOutputStream(out), password!!.toCharArray())
        else ZipOutputStream(FileOutputStream(out))
        zos.use { z ->
            entries.forEachIndexed { i, src ->
                tc.checkCancelled()
                tc.setItems(i, entries.size)
                tc.setMessage(src.file.name)
                val params = ZipParameters()
                params.lastModifiedFileTime = src.file.lastModified()
                if (src.file.isDirectory) {
                    params.fileNameInZip = src.name + "/"
                    params.compressionMethod = CompressionMethod.STORE
                    z.putNextEntry(params)
                    z.closeEntry()
                } else {
                    params.fileNameInZip = src.name
                    params.entrySize = src.file.length()
                    if (level <= 0) {
                        params.compressionMethod = CompressionMethod.STORE
                    } else {
                        params.compressionMethod = CompressionMethod.DEFLATE
                        params.compressionLevel = CompressionLevel.values().firstOrNull { it.level == level.coerceIn(1, 9) }
                            ?: CompressionLevel.NORMAL
                    }
                    if (encrypt) {
                        params.isEncryptFiles = true
                        params.encryptionMethod = EncryptionMethod.AES
                        params.aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
                    }
                    z.putNextEntry(params)
                    copyInto(tc, src.file, z)
                    z.closeEntry()
                }
            }
        }
    }

    private fun create7z(tc: TaskContext, entries: List<Source>, out: File, level: Int) {
        SevenZOutputFile(out).use { sz ->
            if (level <= 0) {
                sz.setContentCompression(SevenZMethod.COPY)
            } else {
                sz.setContentMethods(listOf(SevenZMethodConfiguration(SevenZMethod.LZMA2, LZMA2Options(xzPreset(level)))))
            }
            entries.forEachIndexed { i, src ->
                tc.checkCancelled()
                tc.setItems(i, entries.size)
                tc.setMessage(src.file.name)
                val entry = sz.createArchiveEntry(src.file, src.name)
                sz.putArchiveEntry(entry)
                if (src.file.isFile) {
                    FileInputStream(src.file).use { input ->
                        val buf = ByteArray(128 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            sz.write(buf, 0, n)
                            tc.addBytes(n.toLong())
                        }
                    }
                }
                sz.closeArchiveEntry()
            }
            sz.finish()
        }
    }

    private fun createTar(tc: TaskContext, entries: List<Source>, stream: OutputStream) {
        TarArchiveOutputStream(stream).use { tar ->
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
            tar.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX)
            entries.forEachIndexed { i, src ->
                tc.checkCancelled()
                tc.setItems(i, entries.size)
                tc.setMessage(src.file.name)
                val entry = TarArchiveEntry(src.file, src.name)
                tar.putArchiveEntry(entry)
                if (src.file.isFile) copyInto(tc, src.file, tar)
                tar.closeArchiveEntry()
            }
            tar.finish()
        }
    }

    private fun single(tc: TaskContext, sources: List<File>, stream: OutputStream) {
        val f = sources.singleOrNull()?.takeIf { it.isFile }
            ?: throw IOException("This format can only compress a single file")
        stream.use {
            tc.setMessage(f.name)
            copyInto(tc, f, it)
        }
    }
}
