package com.ztmanager.fs

import com.ztmanager.task.TaskContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Password based file encryption.
 *
 * Format: "ZTE1" | salt(16) | iv(16) | AES-256-CTR ciphertext | HMAC-SHA256(32)
 * The plaintext starts with the original file name (2 byte length + UTF-8).
 * Keys are derived with PBKDF2-HMAC-SHA256; the MAC covers header and ciphertext and is
 * checked before anything is decrypted.
 */
object FileCrypto {
    const val EXTENSION = "zte"
    private val MAGIC = "ZTE1".toByteArray()
    private const val ITERATIONS = 120_000
    private const val MAC_LEN = 32

    class WrongPasswordException : IOException("Wrong password or damaged file")

    private fun keys(password: CharArray, salt: ByteArray): Pair<SecretKeySpec, SecretKeySpec> {
        val spec = PBEKeySpec(password, salt, ITERATIONS, 512)
        val raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(raw.copyOfRange(0, 32), "AES") to SecretKeySpec(raw.copyOfRange(32, 64), "HmacSHA256")
    }

    fun isEncrypted(f: File): Boolean = try {
        FileInputStream(f).use { val b = ByteArray(4); it.read(b) == 4 && b.contentEquals(MAGIC) }
    } catch (e: Exception) {
        false
    }

    fun encrypt(tc: TaskContext, src: File, password: String, deleteSource: Boolean): File {
        val out = FileOps.uniqueFile(src.parentFile!!, src.name + "." + EXTENSION)
        val rnd = SecureRandom()
        val salt = ByteArray(16).also { rnd.nextBytes(it) }
        val iv = ByteArray(16).also { rnd.nextBytes(it) }
        val (encKey, macKey) = keys(password.toCharArray(), salt)
        val cipher = Cipher.getInstance("AES/CTR/NoPadding").apply { init(Cipher.ENCRYPT_MODE, encKey, IvParameterSpec(iv)) }
        val mac = Mac.getInstance("HmacSHA256").apply { init(macKey) }
        tc.setTotalBytes(src.length())
        tc.setMessage(src.name)
        try {
            BufferedOutputStream(FileOutputStream(out)).use { o ->
                fun writeAll(b: ByteArray) {
                    o.write(b)
                    mac.update(b)
                }
                writeAll(MAGIC)
                writeAll(salt)
                writeAll(iv)
                val name = src.name.toByteArray(Charsets.UTF_8)
                val header = byteArrayOf((name.size shr 8).toByte(), name.size.toByte()) + name
                writeAll(cipher.update(header))
                FileInputStream(src).use { input ->
                    val buf = ByteArray(256 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        cipher.update(buf, 0, n)?.let { writeAll(it) }
                        tc.addBytes(n.toLong())
                    }
                }
                cipher.doFinal()?.let { if (it.isNotEmpty()) writeAll(it) }
                o.write(mac.doFinal())
            }
        } catch (e: Throwable) {
            out.delete()
            throw e
        }
        tc.flush()
        if (deleteSource) src.delete()
        return out
    }

    fun decrypt(tc: TaskContext, src: File, password: String, deleteSource: Boolean): File {
        val total = src.length()
        if (total < 4 + 16 + 16 + 2 + MAC_LEN) throw IOException("Not an encrypted file")
        val salt = ByteArray(16)
        val iv = ByteArray(16)
        DataInputStream(FileInputStream(src)).use { d ->
            val m = ByteArray(4)
            d.readFully(m)
            if (!m.contentEquals(MAGIC)) throw IOException("Not an encrypted file")
            d.readFully(salt)
            d.readFully(iv)
        }
        val (encKey, macKey) = keys(password.toCharArray(), salt)
        val bodyEnd = total - MAC_LEN
        tc.setTotalBytes(bodyEnd * 2)
        tc.setMessage(src.name)

        // 1. verify the MAC over everything before the tag
        val mac = Mac.getInstance("HmacSHA256").apply { init(macKey) }
        FileInputStream(src).use { input -> copyLimited(tc, input, bodyEnd) { b, n -> mac.update(b, 0, n) } }
        val expected = ByteArray(MAC_LEN)
        RandomAccessFile(src, "r").use { r ->
            r.seek(bodyEnd)
            r.readFully(expected)
        }
        if (!MessageDigest.isEqual(mac.doFinal(), expected)) throw WrongPasswordException()

        // 2. decrypt
        val cipher = Cipher.getInstance("AES/CTR/NoPadding").apply { init(Cipher.DECRYPT_MODE, encKey, IvParameterSpec(iv)) }
        val tmp = File(src.parentFile, src.name + ".part")
        var outName = src.name.removeSuffix("." + EXTENSION)
        try {
            BufferedInputStream(FileInputStream(src)).use { input ->
                var toSkip = 36L
                while (toSkip > 0) {
                    val skipped = input.skip(toSkip)
                    if (skipped > 0) toSkip -= skipped
                    else if (input.read() >= 0) toSkip-- else break
                }
                var headerBuf = ByteArray(0)
                var nameLen = -1
                var out: BufferedOutputStream? = null
                try {
                    copyLimited(tc, input, bodyEnd - 36) { b, n ->
                        var plain = cipher.update(b, 0, n) ?: return@copyLimited
                        if (out == null) {
                            headerBuf += plain
                            if (nameLen < 0 && headerBuf.size >= 2) {
                                nameLen = ((headerBuf[0].toInt() and 0xFF) shl 8) or (headerBuf[1].toInt() and 0xFF)
                            }
                            if (nameLen >= 0 && headerBuf.size >= 2 + nameLen) {
                                outName = String(headerBuf, 2, nameLen, Charsets.UTF_8).replace('/', '_').ifEmpty { outName }
                                plain = headerBuf.copyOfRange(2 + nameLen, headerBuf.size)
                                out = BufferedOutputStream(FileOutputStream(tmp))
                            } else return@copyLimited
                        }
                        out?.write(plain)
                    }
                    cipher.doFinal()?.let { out?.write(it) }
                } finally {
                    out?.close()
                }
            }
            val target = FileOps.uniqueFile(src.parentFile!!, outName)
            if (!tmp.renameTo(target)) throw IOException("Cannot write $outName")
            tc.flush()
            if (deleteSource) src.delete()
            return target
        } catch (e: Throwable) {
            tmp.delete()
            throw e
        }
    }

    private inline fun copyLimited(tc: TaskContext, input: InputStream, limit: Long, sink: (ByteArray, Int) -> Unit) {
        val buf = ByteArray(256 * 1024)
        var left = limit
        while (left > 0) {
            val n = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
            if (n < 0) break
            sink(buf, n)
            left -= n
            tc.addBytes(n.toLong())
        }
    }
}
