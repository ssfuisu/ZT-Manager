package com.ztmanager.task

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class ConflictAction { OVERWRITE, SKIP, RENAME, CANCEL }

data class ConflictAnswer(val action: ConflictAction, val applyToAll: Boolean)

sealed class Prompt {
    class Password(val target: String, val wrongPassword: Boolean) : Prompt() {
        val result = CompletableDeferred<String?>()
    }

    class Conflict(val name: String, val isDirectory: Boolean) : Prompt() {
        val result = CompletableDeferred<ConflictAnswer>()
    }
}

/** Shows questions from background work (passwords, file conflicts) to the user. */
object Prompter {
    var current by mutableStateOf<Prompt?>(null)
        private set

    private val mutex = Mutex()

    suspend fun askPassword(target: String, wrong: Boolean): String? = mutex.withLock {
        val p = Prompt.Password(target, wrong)
        current = p
        try {
            p.result.await()
        } finally {
            current = null
        }
    }

    suspend fun askConflict(name: String, isDir: Boolean): ConflictAnswer = mutex.withLock {
        val p = Prompt.Conflict(name, isDir)
        current = p
        try {
            p.result.await()
        } finally {
            current = null
        }
    }
}

/** Remembers archive passwords for the lifetime of the process. */
object PasswordCache {
    private val map = HashMap<String, String>()
    @Synchronized fun get(path: String): String? = map[path]
    @Synchronized fun put(path: String, password: String) { map[path] = password }
}
