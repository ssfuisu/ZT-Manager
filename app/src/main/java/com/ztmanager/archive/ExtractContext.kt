package com.ztmanager.archive

import com.ztmanager.task.ConflictAction
import com.ztmanager.task.Prompter
import com.ztmanager.task.TaskContext
import java.io.File
import java.io.IOException

/** Thin adapter so archive code can run with or without a visible task. */
class ExtractContext(val tc: TaskContext?) {
    val cancelled: Boolean get() = tc?.handle?.cancelled == true

    fun checkCancelled() {
        tc?.checkCancelled()
    }

    fun addBytes(n: Long) {
        tc?.addBytes(n)
    }

    fun setTotal(bytes: Long) {
        tc?.setTotalBytes(bytes)
    }

    fun setMessage(msg: String) {
        tc?.setMessage(msg)
    }

    fun setItems(done: Int, total: Int) {
        tc?.setItems(done, total)
    }

    suspend fun resolveConflict(f: File): ConflictAction = tc?.resolveConflict(f) ?: ConflictAction.OVERWRITE

    suspend fun askPassword(target: String, wrong: Boolean): String? = Prompter.askPassword(target, wrong)

    companion object {
        /** Prevents "zip slip": entries must stay inside the destination directory. */
        fun safeTarget(destDir: File, relative: String): File {
            val target = File(destDir, relative)
            val destPath = destDir.canonicalPath
            val tPath = target.canonicalPath
            if (tPath != destPath && !tPath.startsWith("$destPath/")) {
                throw IOException("Blocked unsafe entry: $relative")
            }
            return target
        }

        fun relative(path: String, stripPrefix: String): String =
            if (stripPrefix.isEmpty()) path
            else if (path.startsWith("$stripPrefix/")) path.substring(stripPrefix.length + 1)
            else if (path == stripPrefix) "" else path
    }
}
