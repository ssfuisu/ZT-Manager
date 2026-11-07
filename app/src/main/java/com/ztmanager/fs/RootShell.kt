package com.ztmanager.fs

import java.io.File
import java.util.concurrent.TimeUnit

/** Minimal helper for running commands through `su`. */
object RootShell {

    data class Result(val code: Int, val out: String, val err: String) {
        val ok get() = code == 0
    }

    @Volatile
    private var cachedAvailable: Boolean? = null

    fun isAvailable(force: Boolean = false): Boolean {
        if (!force) cachedAvailable?.let { return it }
        val ok = try {
            val r = exec("id", timeoutSec = 15)
            r.ok && r.out.contains("uid=0")
        } catch (e: Exception) {
            false
        }
        cachedAvailable = ok
        return ok
    }

    fun quote(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    fun exec(command: String, timeoutSec: Long = 120): Result {
        val p = ProcessBuilder("su").redirectErrorStream(false).start()
        p.outputStream.bufferedWriter().use { w ->
            w.write(command)
            w.write("\nexit\n")
            w.flush()
        }
        val outReader = StreamCollector(p.inputStream)
        val errReader = StreamCollector(p.errorStream)
        outReader.start()
        errReader.start()
        if (!p.waitFor(timeoutSec, TimeUnit.SECONDS)) {
            p.destroy()
            return Result(-1, outReader.text(), "timeout")
        }
        outReader.join(2000)
        errReader.join(2000)
        return Result(p.exitValue(), outReader.text(), errReader.text())
    }

    private class StreamCollector(private val input: java.io.InputStream) : Thread() {
        private val sb = StringBuilder()
        override fun run() {
            try {
                input.bufferedReader().use { r ->
                    val buf = CharArray(8192)
                    while (true) {
                        val n = r.read(buf)
                        if (n < 0) break
                        synchronized(sb) { sb.append(buf, 0, n) }
                    }
                }
            } catch (_: Exception) {
            }
        }

        fun text(): String = synchronized(sb) { sb.toString() }
    }

    /** Lists a directory with root. Each line: type|size|mtime|mode|path */
    fun list(dir: String, countChildren: Boolean): List<FileEntry>? {
        val cmd = "find ${quote(dir)} -mindepth 1 -maxdepth 1 -exec stat -L -c '%F|%s|%Y|%A|%n' {} + 2>/dev/null"
        val r = exec(cmd)
        if (r.out.isBlank() && !r.ok) return null
        val list = ArrayList<FileEntry>()
        for (line in r.out.lineSequence()) {
            if (line.isBlank()) continue
            val parts = line.split('|', limit = 5)
            if (parts.size < 5) continue
            val isDir = parts[0].contains("directory")
            val path = parts[4]
            val name = path.substringAfterLast('/')
            list.add(
                FileEntry(
                    name = name,
                    path = path,
                    isDirectory = isDir,
                    size = if (isDir) 0 else parts[1].toLongOrNull() ?: 0,
                    lastModified = (parts[2].toLongOrNull() ?: 0) * 1000,
                    permissions = parts[3],
                    isSymlink = false
                )
            )
        }
        return list
    }

    fun copy(src: String, destDir: String): Result = exec("cp -a ${quote(src)} ${quote(destDir)}/")
    fun move(src: String, dest: String): Result = exec("mv -f ${quote(src)} ${quote(dest)}")
    fun delete(path: String): Result = exec("rm -rf ${quote(path)}")
    fun mkdir(path: String): Result = exec("mkdir -p ${quote(path)}")
    fun touch(path: String): Result = exec("touch ${quote(path)}")
    fun chmod(path: String, mode: String, recursive: Boolean): Result =
        exec("chmod ${if (recursive) "-R " else ""}$mode ${quote(path)}")

    /** Copies a root-only file into [dest] so that it can be read by the app. */
    fun readToCache(path: String, dest: File): Boolean {
        dest.parentFile?.mkdirs()
        val r = exec("cat ${quote(path)} > ${quote(dest.absolutePath)} && chmod 666 ${quote(dest.absolutePath)}")
        return r.ok && dest.exists()
    }

    fun writeFromCache(src: File, path: String): Boolean =
        exec("cat ${quote(src.absolutePath)} > ${quote(path)}").ok
}
