package com.ztmanager.archive

import com.ztmanager.fs.FileEntry
import java.io.Closeable
import java.io.File

data class ArchiveItem(
    val path: String,
    val isDir: Boolean,
    val size: Long,
    val packedSize: Long,
    val time: Long,
    val encrypted: Boolean,
    val index: Int
) {
    val name: String get() = path.substringAfterLast('/')
}

class PasswordRequiredException(val wrong: Boolean) : Exception("Password required")

interface ArchiveHandle : Closeable {
    val file: File
    val formatName: String
    val items: List<ArchiveItem>
    val canModify: Boolean get() = false
    val password: String?

    /**
     * Extracts [items] into [destDir]. The prefix [stripPrefix] (an inner directory) is
     * removed from every path before writing.
     */
    suspend fun extract(items: List<ArchiveItem>, destDir: File, stripPrefix: String, ctx: ExtractContext)

    fun addFiles(files: List<File>, innerDir: String, ctx: ExtractContext) {
        throw UnsupportedOperationException("Read-only archive")
    }

    fun delete(items: List<ArchiveItem>) {
        throw UnsupportedOperationException("Read-only archive")
    }
}

fun normalizePath(p: String): String =
    p.replace('\\', '/').trim('/').split('/').filter { it.isNotEmpty() && it != "." }.joinToString("/")

/** Builds a browsable directory tree out of the flat entry list of an archive. */
class ArchiveTree(items: List<ArchiveItem>) {
    private val children = HashMap<String, MutableMap<String, FileEntry>>()
    private val all = items

    init {
        children[""] = LinkedHashMap()
        for (item in items) {
            val path = item.path
            if (path.isEmpty()) continue
            val parts = path.split('/')
            var parent = ""
            for (i in parts.indices) {
                val cur = if (parent.isEmpty()) parts[i] else parent + "/" + parts[i]
                val last = i == parts.size - 1
                val map = children.getOrPut(parent) { LinkedHashMap() }
                if (last) {
                    val existing = map[parts[i]]
                    if (item.isDir) {
                        if (existing == null) map[parts[i]] = dirEntry(parts[i], cur, item.time)
                        children.getOrPut(cur) { LinkedHashMap() }
                    } else {
                        map[parts[i]] = FileEntry(
                            name = parts[i], path = cur, isDirectory = false, size = item.size,
                            lastModified = item.time, packedSize = item.packedSize,
                            encrypted = item.encrypted, inArchive = true
                        )
                    }
                } else {
                    if (map[parts[i]] == null) map[parts[i]] = dirEntry(parts[i], cur, 0)
                    children.getOrPut(cur) { LinkedHashMap() }
                }
                parent = cur
            }
        }
        // fill in child counts
        for ((dir, map) in children) {
            for ((k, v) in map) if (v.isDirectory) map[k] = v.copy(childCount = children[v.path]?.size ?: 0)
        }
    }

    private fun dirEntry(name: String, path: String, time: Long) =
        FileEntry(name = name, path = path, isDirectory = true, size = 0, lastModified = time, inArchive = true)

    fun list(dir: String): List<FileEntry> = children[dir]?.values?.toList() ?: emptyList()

    fun exists(dir: String) = children.containsKey(dir)

    /** All archive items that are at or below any of the given inner paths. */
    fun collect(paths: Collection<String>): List<ArchiveItem> {
        if (paths.any { it.isEmpty() }) return all
        return all.filter { item -> paths.any { p -> item.path == p || item.path.startsWith("$p/") } }
    }

    fun totalSize(items: List<ArchiveItem>) = items.sumOf { if (it.isDir) 0L else it.size.coerceAtLeast(0) }
}
