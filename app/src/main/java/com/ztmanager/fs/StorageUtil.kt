package com.ztmanager.fs

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import java.io.File

data class StorageVolumeInfo(
    val name: String,
    val path: String,
    val total: Long,
    val free: Long,
    val removable: Boolean
)

object StorageUtil {

    val internalRoot: String get() = Environment.getExternalStorageDirectory().absolutePath

    fun volumes(context: Context): List<StorageVolumeInfo> {
        val result = ArrayList<StorageVolumeInfo>()
        val seen = HashSet<String>()
        val sm = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        try {
            for (v in sm.storageVolumes) {
                val dir: File? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) v.directory else pathFromReflection(v)
                val path = dir?.absolutePath ?: continue
                if (!seen.add(path)) continue
                val stat = statOf(path)
                result.add(
                    StorageVolumeInfo(
                        name = v.getDescription(context) ?: File(path).name,
                        path = path,
                        total = stat.first,
                        free = stat.second,
                        removable = v.isRemovable
                    )
                )
            }
        } catch (_: Exception) {
        }
        if (result.isEmpty()) {
            val p = internalRoot
            val st = statOf(p)
            result.add(StorageVolumeInfo("Internal storage", p, st.first, st.second, false))
        }
        // Fallback for SD cards that are not reported as storage volumes.
        context.getExternalFilesDirs(null).filterNotNull().forEach { f ->
            val idx = f.absolutePath.indexOf("/Android/data")
            if (idx > 0) {
                val root = f.absolutePath.substring(0, idx)
                if (seen.add(root) && result.none { it.path == root }) {
                    val st = statOf(root)
                    result.add(StorageVolumeInfo(File(root).name, root, st.first, st.second, true))
                }
            }
        }
        return result
    }

    private fun pathFromReflection(v: android.os.storage.StorageVolume): File? = try {
        val m = v.javaClass.getMethod("getPath")
        File(m.invoke(v) as String)
    } catch (e: Exception) {
        null
    }

    fun statOf(path: String): Pair<Long, Long> = try {
        val s = StatFs(path)
        Pair(s.totalBytes, s.availableBytes)
    } catch (e: Exception) {
        Pair(0L, 0L)
    }

    fun volumeFor(context: Context, path: String): StorageVolumeInfo? =
        volumes(context).filter { path.startsWith(it.path) }.maxByOrNull { it.path.length }
}
