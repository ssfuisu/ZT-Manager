package com.ztmanager.fs

import com.ztmanager.task.TaskContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/** Creates and verifies checksum lists in the common "hash  filename" format. */
object ChecksumFiles {
    val algorithms = linkedMapOf("MD5" to "md5", "SHA-1" to "sha1", "SHA-256" to "sha256", "SHA-512" to "sha512")
    private val extToAlgo = algorithms.entries.associate { it.value to it.key }

    fun isChecksumFile(f: File) = FileType.extensionOf(f.name) in extToAlgo

    private fun hash(tc: TaskContext, f: File, algo: String): String {
        val md = MessageDigest.getInstance(algo)
        FileInputStream(f).use { input ->
            val buf = ByteArray(512 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
                tc.addBytes(n.toLong())
            }
        }
        return Format.hex(md.digest())
    }

    fun create(tc: TaskContext, files: List<File>, algo: String): File {
        val ext = algorithms[algo] ?: "sha256"
        val dir = files.first().parentFile!!
        val name = if (files.size == 1) files[0].name + "." + ext else "checksums.$ext"
        val out = FileOps.uniqueFile(dir, name)
        tc.setTotalBytes(files.sumOf { it.length() })
        val sb = StringBuilder()
        files.forEachIndexed { i, f ->
            tc.checkCancelled()
            tc.setItems(i, files.size)
            tc.setMessage(f.name)
            sb.append(hash(tc, f, algo)).append("  ").append(f.name).append('\n')
        }
        out.writeText(sb.toString())
        tc.flush()
        return out
    }

    data class VerifyResult(val ok: List<String>, val failed: List<String>, val missing: List<String>)

    fun verify(tc: TaskContext, listFile: File): VerifyResult {
        val algo = extToAlgo[FileType.extensionOf(listFile.name)] ?: "SHA-256"
        val entries = listFile.readLines().mapNotNull { line ->
            val t = line.trim()
            if (t.isEmpty() || t.startsWith("#")) return@mapNotNull null
            val m = Regex("^([0-9a-fA-F]+)\\s+\\*?(.+)$").find(t) ?: return@mapNotNull null
            m.groupValues[1].lowercase() to m.groupValues[2].trim()
        }
        val dir = listFile.parentFile!!
        tc.setTotalBytes(entries.sumOf { File(dir, it.second).let { f -> if (f.isFile) f.length() else 0L } })
        val ok = ArrayList<String>()
        val failed = ArrayList<String>()
        val missing = ArrayList<String>()
        entries.forEachIndexed { i, (expected, name) ->
            tc.checkCancelled()
            tc.setItems(i, entries.size)
            tc.setMessage(name)
            val f = File(name).let { if (it.isAbsolute) it else File(dir, name) }
            if (!f.isFile) missing.add(name)
            else if (hash(tc, f, algo) == expected) ok.add(name) else failed.add(name)
        }
        tc.flush()
        return VerifyResult(ok, failed, missing)
    }
}
