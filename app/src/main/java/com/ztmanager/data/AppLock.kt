package com.ztmanager.data

import com.ztmanager.fs.Format
import java.security.MessageDigest
import java.security.SecureRandom

/** Optional PIN that protects the app when it is opened or resumed. */
object AppLock {
    const val RELOCK_AFTER_MS = 30_000L

    val enabled: Boolean get() = Prefs.pinHash.isNotEmpty()

    private fun hash(pin: String, salt: String): String =
        Format.hex(MessageDigest.getInstance("SHA-256").digest((salt + ":" + pin).toByteArray()))

    fun set(pin: String) {
        val salt = Format.hex(ByteArray(16).also { SecureRandom().nextBytes(it) })
        Prefs.pinSalt = salt
        Prefs.pinHash = hash(pin, salt)
    }

    fun clear() {
        Prefs.pinHash = ""
        Prefs.pinSalt = ""
    }

    fun check(pin: String): Boolean = enabled && MessageDigest.isEqual(
        hash(pin, Prefs.pinSalt).toByteArray(), Prefs.pinHash.toByteArray()
    )
}
