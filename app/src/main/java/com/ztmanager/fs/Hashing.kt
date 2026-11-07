package com.ztmanager.fs

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.zip.CRC32

object Hashing {
    val algorithms = listOf("MD5", "SHA-1", "SHA-256", "SHA-512", "CRC32")

    /** Computes all supported hashes in a single pass. */
    fun compute(file: File, progress: (Float) -> Unit, cancelled: () -> Boolean): Map<String, String> {
        val digests = listOf("MD5", "SHA-1", "SHA-256", "SHA-512").associateWith { MessageDigest.getInstance(it) }
        val crc = CRC32()
        val total = file.length().coerceAtLeast(1)
        var done = 0L
        var last = 0L
        FileInputStream(file).use { input ->
            val buf = ByteArray(512 * 1024)
            while (true) {
                if (cancelled()) return emptyMap()
                val n = input.read(buf)
                if (n < 0) break
                digests.values.forEach { it.update(buf, 0, n) }
                crc.update(buf, 0, n)
                done += n
                val now = System.currentTimeMillis()
                if (now - last > 100) {
                    last = now
                    progress(done.toFloat() / total)
                }
            }
        }
        val result = LinkedHashMap<String, String>()
        digests.forEach { (k, v) -> result[k] = Format.hex(v.digest()) }
        result["CRC32"] = String.format("%08x", crc.value)
        return result
    }
}
