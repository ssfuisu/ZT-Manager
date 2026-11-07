package com.ztmanager.fs

import com.ztmanager.data.Prefs
import com.ztmanager.task.ConflictAction
import com.ztmanager.task.TaskContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

data class FolderStats(val size: Long, val files: Int, val dirs: Int)

object FileOps {

    fun list(path: String, countChildren: Boolean): List<FileEntry> {
        val dir = File(path)
        val names = dir.listFiles()
        if (names == null) {
            if (Prefs.rootMode && RootShell.isAvailable()) {
                return RootShell.list(path, countChildren) ?: throw IOException("Permission denied")
            }
            if (!dir.exists()) throw IOException("Folder does not exist")
            throw IOException("Permission denied")
        }
        return names.map { FileEntry.fromFile(it, countChildren) }
    }

    fun stats(files: List<File>, cancel: (() -> Boolean)? = null): FolderStats {
        var size = 0L
        var count = 0
        var dirs = 0
        val stack = ArrayDeque<File>()
        files.forEach { stack.addLast(it) }
        while (stack.isNotEmpty()) {
            if (cancel?.invoke() == true) break
            val f = stack.removeLast()
            if (f.isDirectory && !isSymlink(f)) {
                dirs++
                f.listFiles()?.forEach { stack.addLast(it) }
            } else {
                count++
                size += f.length()
            }
        }
        return FolderStats(size, count, dirs)
    }

    fun isSymlink(f: File): Boolean = try {
        (android.system.Os.lstat(f.absolutePath).st_mode and 0xF000) == 0xA000
    } catch (e: Exception) {
        false
    }

    fun uniqueFile(dir: File, name: String): File {
        var f = File(dir, name)
        if (!f.exists()) return f
        val dot = name.lastIndexOf('.')
        val hasExt = dot > 0 && !File(dir, name).isDirectory
        val base = if (hasExt) name.substring(0, dot) else name
        val ext = if (hasExt) name.substring(dot) else ""
        var i = 1
        while (f.exists()) {
            f = File(dir, "$base ($i)$ext")
            i++
        }
        return f
    }

    fun isInside(child: File, parent: File): Boolean {
        val c = child.canonicalPath
        val p = parent.canonicalPath
        return c == p || c.startsWith("$p/")
    }

    fun mkdir(parent: File, name: String): File {
        val f = File(parent, name)
        if (f.exists()) throw IOException("Already exists")
        if (!f.mkdirs()) {
            if (Prefs.rootMode && RootShell.mkdir(f.absolutePath).ok) return f
            throw IOException("Cannot create folder")
        }
        return f
    }

    fun createFile(parent: File, name: String): File {
        val f = File(parent, name)
        if (f.exists()) throw IOException("Already exists")
        val ok = try {
            f.createNewFile()
        } catch (e: IOException) {
            false
        }
        if (!ok) {
            if (Prefs.rootMode && RootShell.touch(f.absolutePath).ok) return f
            throw IOException("Cannot create file")
        }
        return f
    }

    fun rename(file: File, newName: String): File {
        val target = File(file.parentFile, newName)
        if (target.exists() && !target.absolutePath.equals(file.absolutePath, ignoreCase = true)) {
            throw IOException("Already exists")
        }
        if (!file.renameTo(target)) {
            if (Prefs.rootMode && RootShell.move(file.absolutePath, target.absolutePath).ok) return target
            throw IOException("Rename failed")
        }
        return target
    }

    suspend fun copy(tc: TaskContext, sources: List<File>, destDir: File, move: Boolean) {
        if (!destDir.exists() && !destDir.mkdirs()) throw IOException("Cannot create ${destDir.path}")
        for (src in sources) {
            if (src.isDirectory && isInside(destDir, src)) {
                throw IOException("Cannot copy a folder into itself: ${src.name}")
            }
        }
        tc.setMessage("...")
        val st = stats(sources) { tc.handle.cancelled }
        tc.setTotalBytes(st.size)
        var done = 0
        val total = sources.size
        for (src in sources) {
            tc.checkCancelled()
            tc.setItems(done, total)
            var target = File(destDir, src.name)
            val sameDir = src.parentFile?.canonicalPath == destDir.canonicalPath
            if (sameDir) {
                if (move) {
                    done++
                    continue
                }
                target = uniqueFile(destDir, src.name)
            }
            if (move && !target.exists() && src.renameTo(target)) {
                tc.addBytes(if (src.isDirectory) 0 else target.length())
                done++
                continue
            }
            if (!src.canRead() && Prefs.rootMode && RootShell.isAvailable()) {
                val r = if (move) RootShell.move(src.absolutePath, target.absolutePath)
                else RootShell.copy(src.absolutePath, destDir.absolutePath)
                if (!r.ok) throw IOException(r.err.ifBlank { "Root operation failed" })
                done++
                continue
            }
            val ok = copyRecursive(tc, src, target)
            if (move && ok) deleteRecursive(src)
            done++
        }
        tc.setItems(total, total)
        tc.flush()
    }

    /** Returns true when the source was fully copied (nothing skipped). */
    private suspend fun copyRecursive(tc: TaskContext, src: File, target: File): Boolean {
        tc.checkCancelled()
        if (src.isDirectory && !isSymlink(src)) {
            if (target.exists() && !target.isDirectory) {
                when (tc.resolveConflict(target)) {
                    ConflictAction.SKIP -> return false
                    ConflictAction.RENAME -> return copyRecursive(tc, src, uniqueFile(target.parentFile!!, target.name))
                    else -> target.delete()
                }
            }
            if (!target.exists() && !target.mkdirs()) throw IOException("Cannot create ${target.path}")
            var all = true
            src.listFiles()?.forEach { child ->
                if (!copyRecursive(tc, child, File(target, child.name))) all = false
            }
            target.setLastModified(src.lastModified())
            return all
        }
        var dest = target
        if (dest.exists()) {
            when (tc.resolveConflict(dest)) {
                ConflictAction.SKIP -> {
                    tc.addBytes(src.length())
                    return false
                }
                ConflictAction.RENAME -> dest = uniqueFile(dest.parentFile!!, dest.name)
                else -> if (dest.isDirectory) deleteRecursive(dest)
            }
        }
        tc.setMessage(src.name)
        FileInputStream(src).use { input ->
            FileOutputStream(dest).use { output -> pipe(tc, input, output) }
        }
        dest.setLastModified(src.lastModified())
        return true
    }

    fun pipe(tc: TaskContext?, input: InputStream, output: OutputStream) {
        val buf = ByteArray(256 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            output.write(buf, 0, n)
            tc?.addBytes(n.toLong())
        }
    }

    suspend fun delete(tc: TaskContext, files: List<File>) {
        val total = files.size
        files.forEachIndexed { i, f ->
            tc.checkCancelled()
            tc.setItems(i, total)
            tc.setMessage(f.name)
            if (!deleteRecursive(f, tc)) {
                if (Prefs.rootMode && RootShell.isAvailable()) {
                    val r = RootShell.delete(f.absolutePath)
                    if (!r.ok) throw IOException(r.err.ifBlank { "Cannot delete ${f.name}" })
                } else {
                    throw IOException("Cannot delete ${f.name}")
                }
            }
        }
        tc.setItems(total, total)
    }

    fun deleteRecursive(f: File, tc: TaskContext? = null): Boolean {
        tc?.checkCancelled()
        if (f.isDirectory && !isSymlink(f)) {
            f.listFiles()?.forEach { deleteRecursive(it, tc) }
        }
        return f.delete() || !f.exists()
    }

    fun chmod(path: String, mode: Int, recursive: Boolean): Boolean {
        val octal = Integer.toOctalString(mode)
        if (!recursive) {
            try {
                android.system.Os.chmod(path, mode)
                return true
            } catch (_: Exception) {
            }
        }
        if (Prefs.rootMode && RootShell.isAvailable()) return RootShell.chmod(path, octal, recursive).ok
        if (recursive) {
            return try {
                File(path).walkTopDown().forEach { android.system.Os.chmod(it.absolutePath, mode) }
                true
            } catch (e: Exception) {
                false
            }
        }
        return false
    }

    fun readMode(path: String): Int = try {
        android.system.Os.stat(path).st_mode and 0xFFF
    } catch (e: Exception) {
        0
    }
}
