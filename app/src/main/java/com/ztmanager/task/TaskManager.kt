package com.ztmanager.task

import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.ztmanager.ZtApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class CancelledByUserException : CancellationException("Cancelled")

class TaskHandle(val id: Int, val title: String) {
    var message by mutableStateOf("")
    var progress by mutableStateOf(-1f)
    var itemsDone by mutableStateOf(0)
    var itemsTotal by mutableStateOf(0)
    var bytesDone by mutableStateOf(0L)
    var bytesTotal by mutableStateOf(0L)
    var background by mutableStateOf(false)
    var finished by mutableStateOf(false)
    internal var job: Job? = null
    @Volatile var cancelled = false
        private set

    fun cancel() {
        cancelled = true
        job?.cancel(CancelledByUserException())
    }
}

/** Passed to every background task; reports progress and handles user questions. */
class TaskContext(val handle: TaskHandle) {
    private var lastUpdate = 0L
    private var conflictAll: ConflictAction? = null

    fun checkCancelled() {
        if (handle.cancelled) throw CancelledByUserException()
    }

    fun setMessage(msg: String) {
        handle.message = msg
    }

    fun setItems(done: Int, total: Int) {
        handle.itemsDone = done
        handle.itemsTotal = total
        if (handle.bytesTotal <= 0 && total > 0) handle.progress = done.toFloat() / total
    }

    fun setTotalBytes(total: Long) {
        handle.bytesTotal = total
    }

    private var bytes = 0L

    fun addBytes(n: Long) {
        checkCancelled()
        bytes += n
        val now = System.currentTimeMillis()
        if (now - lastUpdate > 150) {
            lastUpdate = now
            flush()
        }
    }

    fun flush() {
        handle.bytesDone = bytes
        if (handle.bytesTotal > 0) handle.progress = (bytes.toDouble() / handle.bytesTotal).toFloat().coerceIn(0f, 1f)
    }

    suspend fun resolveConflict(target: File): ConflictAction {
        conflictAll?.let { return it }
        val ans = Prompter.askConflict(target.name, target.isDirectory)
        if (ans.applyToAll) conflictAll = ans.action
        if (ans.action == ConflictAction.CANCEL) throw CancelledByUserException()
        return ans.action
    }

    suspend fun askPassword(target: String, wrong: Boolean): String? = Prompter.askPassword(target, wrong)
}

object TaskManager {
    private val ids = AtomicInteger(1)
    val tasks = mutableStateListOf<TaskHandle>()

    /**
     * Runs [block] on the IO dispatcher. [onDone] is called on the main thread with
     * null on success or an error message.
     */
    fun start(
        title: String,
        successMessage: String? = null,
        onDone: ((String?) -> Unit)? = null,
        block: suspend TaskContext.() -> Unit
    ): TaskHandle {
        val app = ZtApp.instance
        val handle = TaskHandle(ids.getAndIncrement(), title)
        tasks.add(handle)
        startService()
        handle.job = app.appScope.launch(Dispatchers.Main) {
            var error: String? = null
            try {
                withContext(Dispatchers.IO) { TaskContext(handle).block() }
            } catch (e: CancellationException) {
                error = CANCELLED
            } catch (e: Throwable) {
                error = e.message ?: e.javaClass.simpleName
            }
            handle.finished = true
            tasks.remove(handle)
            when {
                error == null && successMessage != null ->
                    Toast.makeText(app, successMessage, Toast.LENGTH_SHORT).show()
                error != null && error != CANCELLED ->
                    Toast.makeText(app, error, Toast.LENGTH_LONG).show()
            }
            onDone?.invoke(error)
        }
        return handle
    }

    const val CANCELLED = "__cancelled__"

    private fun startService() {
        val app = ZtApp.instance
        try {
            ContextCompat.startForegroundService(app, Intent(app, TaskService::class.java))
        } catch (_: Exception) {
            // Background start restrictions: the task still runs in the app process.
        }
    }
}
