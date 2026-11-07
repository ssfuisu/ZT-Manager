package com.ztmanager.fs

import com.ztmanager.ZtApp
import com.ztmanager.task.TaskContext
import java.io.File
import java.io.IOException

/**
 * Recycle bin. Deleted items are moved into a hidden ".ZTTrash" folder on the root of
 * the storage volume they live on, next to a small ".ztinfo" file that records the
 * original location.
 */
object Trash {
    const val DIR_NAME = ".ZTTrash"
    private const val INFO_EXT = ".ztinfo"

    data class Item(
        val id: String,
        val trashDir: File,
        val name: String,
        val originalPath: String,
        val deletedAt: Long,
        val isDirectory: Boolean,
        val size: Long
    ) {
        val file: File get() = File(trashDir, id)
        val info: File get() = File(trashDir, id + INFO_EXT)
    }

    @Volatile
    private var cachedRoots: Pair<Long, List<String>>? = null

    private fun roots(): List<String> {
        val now = System.currentTimeMillis()
        cachedRoots?.let { if (now - it.first < 10_000) return it.second }
        val vols = StorageUtil.volumes(ZtApp.instance).map { it.path }
        val list = (vols + StorageUtil.internalRoot).distinct()
        cachedRoots = now to list
        return list
    }

    private fun trashDirFor(file: File): File? {
        val path = file.absolutePath
        val root = roots().filter { path == it || path.startsWith("$it/") }.maxByOrNull { it.length } ?: return null
        return File(root, DIR_NAME)
    }

    fun isInTrash(file: File): Boolean = file.absolutePath.split('/').contains(DIR_NAME)

    /** Returns true when [file] can be moved to the recycle bin instead of deleted. */
    fun supports(file: File): Boolean = !isInTrash(file) && trashDirFor(file) != null

    fun moveToTrash(tc: TaskContext, files: List<File>) {
        files.forEachIndexed { i, f ->
            tc.checkCancelled()
            tc.setItems(i, files.size)
            tc.setMessage(f.name)
            val dir = trashDirFor(f) ?: throw IOException("No recycle bin for ${f.path}")
            if (!dir.exists()) {
                dir.mkdirs()
                File(dir, ".nomedia").createNewFile()
            }
            val id = System.currentTimeMillis().toString(36) + "_" + i + "_" + f.name.replace('/', '_')
            val target = File(dir, id)
            val isDir = f.isDirectory
            val size = if (isDir) FileOps.stats(listOf(f)).size else f.length()
            if (!f.renameTo(target)) throw IOException("Cannot move ${f.name} to the recycle bin")
            File(dir, id + INFO_EXT).writeText(
                listOf(f.absolutePath, System.currentTimeMillis().toString(), if (isDir) "d" else "f", size.toString()).joinToString("\n")
            )
        }
        tc.setItems(files.size, files.size)
    }

    fun list(): List<Item> {
        val out = ArrayList<Item>()
        for (root in roots()) {
            val dir = File(root, DIR_NAME)
            val infos = dir.listFiles { f -> f.name.endsWith(INFO_EXT) } ?: continue
            for (info in infos) {
                val id = info.name.removeSuffix(INFO_EXT)
                val data = File(dir, id)
                if (!data.exists()) {
                    info.delete()
                    continue
                }
                val lines = try {
                    info.readLines()
                } catch (e: Exception) {
                    emptyList()
                }
                val original = lines.getOrNull(0) ?: continue
                out.add(
                    Item(
                        id = id,
                        trashDir = dir,
                        name = File(original).name,
                        originalPath = original,
                        deletedAt = lines.getOrNull(1)?.toLongOrNull() ?: data.lastModified(),
                        isDirectory = lines.getOrNull(2) == "d" || data.isDirectory,
                        size = lines.getOrNull(3)?.toLongOrNull() ?: data.length()
                    )
                )
            }
        }
        return out.sortedByDescending { it.deletedAt }
    }

    fun restore(tc: TaskContext, items: List<Item>) {
        items.forEachIndexed { i, item ->
            tc.checkCancelled()
            tc.setItems(i, items.size)
            tc.setMessage(item.name)
            val original = File(item.originalPath)
            original.parentFile?.mkdirs()
            val target = if (original.exists()) FileOps.uniqueFile(original.parentFile!!, original.name) else original
            if (!item.file.renameTo(target)) throw IOException("Cannot restore ${item.name}")
            item.info.delete()
        }
        tc.setItems(items.size, items.size)
    }

    fun delete(tc: TaskContext, items: List<Item>) {
        items.forEachIndexed { i, item ->
            tc.checkCancelled()
            tc.setItems(i, items.size)
            tc.setMessage(item.name)
            FileOps.deleteRecursive(item.file, tc)
            item.info.delete()
        }
        tc.setItems(items.size, items.size)
    }
}
