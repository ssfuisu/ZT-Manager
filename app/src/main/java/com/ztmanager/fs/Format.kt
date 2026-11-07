package com.ztmanager.fs

import java.text.DateFormat
import java.util.Date
import java.util.Locale

object Format {
    fun size(bytes: Long): String {
        if (bytes < 0) return "-"
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB", "PB")
        var value = bytes.toDouble() / 1024.0
        var i = 0
        while (value >= 1024 && i < units.size - 1) {
            value /= 1024.0
            i++
        }
        return if (value >= 100) String.format(Locale.US, "%.0f %s", value, units[i])
        else String.format(Locale.US, "%.1f %s", value, units[i])
    }

    fun date(millis: Long): String {
        if (millis <= 0) return ""
        val df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        return df.format(Date(millis))
    }

    fun dateLong(millis: Long): String {
        if (millis <= 0) return "-"
        val df = DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.MEDIUM)
        return df.format(Date(millis))
    }

    fun hex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            sb.append(HEX[v ushr 4]).append(HEX[v and 0x0F])
        }
        return sb.toString()
    }

    private val HEX = "0123456789abcdef".toCharArray()

    fun percent(done: Long, total: Long): Float =
        if (total <= 0) 0f else (done.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f)
}
