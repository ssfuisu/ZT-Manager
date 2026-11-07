package com.ztmanager.fs

import com.ztmanager.task.TaskContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/** Splits a file into numbered parts (name.001, name.002 ...) and joins them again. */
object SplitTools {

    fun isFirstPart(f: File) = f.name.endsWith(".001")

    fun split(tc: TaskContext, src: File, partSize: Long): List<File> {
        if (partSize <= 0) throw IOException("Invalid part size")
        val parts = ArrayList<File>()
        tc.setTotalBytes(src.length())
        val count = ((src.length() + partSize - 1) / partSize).toInt().coerceAtLeast(1)
        FileInputStream(src).use { input ->
            val buf = ByteArray(256 * 1024)
            for (i in 1..count) {
                tc.checkCancelled()
                tc.setItems(i - 1, count)
                val part = File(src.parentFile, src.name + "." + i.toString().padStart(3, '0'))
                tc.setMessage(part.name)
                BufferedOutputStream(FileOutputStream(part)).use { o ->
                    var left = partSize
                    while (left > 0) {
                        val n = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                        if (n < 0) break
                        o.write(buf, 0, n)
                        left -= n
                        tc.addBytes(n.toLong())
                    }
                }
                parts.add(part)
            }
        }
        tc.setItems(count, count)
        tc.flush()
        return parts
    }

    fun partsOf(first: File): List<File> {
        val base = first.name.removeSuffix(".001")
        val out = ArrayList<File>()
        var i = 1
        while (true) {
            val f = File(first.parentFile, base + "." + i.toString().padStart(3, '0'))
            if (!f.exists()) break
            out.add(f)
            i++
        }
        return out
    }

    fun merge(tc: TaskContext, first: File): File {
        val parts = partsOf(first)
        if (parts.isEmpty()) throw IOException("No parts found")
        val target = FileOps.uniqueFile(first.parentFile!!, first.name.removeSuffix(".001"))
        tc.setTotalBytes(parts.sumOf { it.length() })
        try {
            BufferedOutputStream(FileOutputStream(target)).use { o ->
                parts.forEachIndexed { i, p ->
                    tc.setItems(i, parts.size)
                    tc.setMessage(p.name)
                    FileInputStream(p).use { FileOps.pipe(tc, it, o) }
                }
            }
        } catch (e: Throwable) {
            target.delete()
            throw e
        }
        tc.setItems(parts.size, parts.size)
        tc.flush()
        return target
    }
}
