package com.ztmanager.apk

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Decodes Android binary XML (for example AndroidManifest.xml inside an APK) into
 * readable text.
 */
object AxmlDecoder {
    private const val RES_STRING_POOL_TYPE = 0x0001
    private const val RES_XML_TYPE = 0x0003
    private const val RES_XML_START_NAMESPACE = 0x0100
    private const val RES_XML_END_NAMESPACE = 0x0101
    private const val RES_XML_START_ELEMENT = 0x0102
    private const val RES_XML_END_ELEMENT = 0x0103
    private const val RES_XML_CDATA = 0x0104
    private const val RES_XML_RESOURCE_MAP = 0x0180

    private const val TYPE_REFERENCE = 0x01
    private const val TYPE_ATTRIBUTE = 0x02
    private const val TYPE_STRING = 0x03
    private const val TYPE_FLOAT = 0x04
    private const val TYPE_DIMENSION = 0x05
    private const val TYPE_FRACTION = 0x06
    private const val TYPE_INT_DEC = 0x10
    private const val TYPE_INT_HEX = 0x11
    private const val TYPE_INT_BOOLEAN = 0x12
    private const val TYPE_INT_COLOR_ARGB8 = 0x1c
    private const val TYPE_INT_COLOR_RGB8 = 0x1d
    private const val TYPE_INT_COLOR_ARGB4 = 0x1e
    private const val TYPE_INT_COLOR_RGB4 = 0x1f

    // Attribute names for well-known android resource ids, used when the string pool is obfuscated.
    private val knownAttrs = mapOf(
        0x01010003 to "name", 0x01010001 to "label", 0x01010002 to "icon", 0x0101021b to "versionCode",
        0x0101021c to "versionName", 0x0101020c to "minSdkVersion", 0x01010270 to "targetSdkVersion",
        0x01010010 to "exported", 0x0101000e to "enabled", 0x01010006 to "permission", 0x01010000 to "theme",
        0x01010018 to "authorities", 0x0101001d to "screenOrientation", 0x0101001f to "configChanges",
        0x0101022b to "windowSoftInputMode", 0x01010202 to "debuggable", 0x01010280 to "allowBackup",
        0x0101000f to "process", 0x01010011 to "launchMode", 0x01010024 to "value", 0x01010025 to "resource",
        0x01010026 to "mimeType", 0x01010027 to "scheme", 0x01010028 to "host", 0x0101002a to "path",
        0x01010272 to "roundIcon", 0x01010527 to "extractNativeLibs", 0x0101057d to "compileSdkVersion",
        0x010104ea to "usesCleartextTraffic", 0x0101052c to "requestLegacyExternalStorage"
    )

    fun decode(data: ByteArray): String {
        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val type = buf.short.toInt() and 0xFFFF
        if (type != RES_XML_TYPE) throw IllegalArgumentException("Not a binary XML file")
        buf.short // header size
        buf.int // total size
        var strings: List<String> = emptyList()
        var resIds = IntArray(0)
        val namespaces = LinkedHashMap<String, String>() // uri -> prefix
        val pendingNs = ArrayList<Pair<String, String>>()
        val sb = StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
        var depth = 0
        var lastWasStart = false

        fun str(i: Int) = if (i >= 0 && i < strings.size) strings[i] else ""

        while (buf.remaining() >= 8) {
            val chunkStart = buf.position()
            val chunkType = buf.short.toInt() and 0xFFFF
            val headerSize = buf.short.toInt() and 0xFFFF
            val chunkSize = buf.int
            if (chunkSize < 8 || chunkStart + chunkSize > data.size) break
            when (chunkType) {
                RES_STRING_POOL_TYPE -> strings = readStringPool(buf, chunkStart, headerSize)
                RES_XML_RESOURCE_MAP -> {
                    val count = (chunkSize - headerSize) / 4
                    resIds = IntArray(count) { buf.int }
                }
                RES_XML_START_NAMESPACE -> {
                    buf.position(chunkStart + headerSize)
                    val prefix = str(buf.int)
                    val uri = str(buf.int)
                    namespaces[uri] = prefix
                    pendingNs.add(prefix to uri)
                }
                RES_XML_END_NAMESPACE -> {}
                RES_XML_START_ELEMENT -> {
                    buf.position(chunkStart + headerSize)
                    buf.int // ns
                    val name = str(buf.int)
                    buf.short // attribute start
                    buf.short // attribute size
                    val attrCount = buf.short.toInt() and 0xFFFF
                    buf.short; buf.short; buf.short
                    if (lastWasStart) sb.append(">\n")
                    sb.append("    ".repeat(depth)).append('<').append(name)
                    for ((p, u) in pendingNs) sb.append(" xmlns:").append(p).append("=\"").append(u).append('"')
                    pendingNs.clear()
                    for (a in 0 until attrCount) {
                        val nsIdx = buf.int
                        val nameIdx = buf.int
                        val rawIdx = buf.int
                        buf.short // size
                        buf.get() // res0
                        val dataType = buf.get().toInt() and 0xFF
                        val value = buf.int
                        var attrName = str(nameIdx)
                        if (attrName.isEmpty() && nameIdx < resIds.size) attrName = knownAttrs[resIds[nameIdx]] ?: String.format("attr_%08x", resIds[nameIdx])
                        val prefix = if (nsIdx >= 0) namespaces[str(nsIdx)] else null
                        val v = if (rawIdx >= 0) str(rawIdx) else formatValue(dataType, value) { str(it) }
                        sb.append("\n").append("    ".repeat(depth + 1))
                        if (!prefix.isNullOrEmpty()) sb.append(prefix).append(':')
                        sb.append(attrName).append("=\"").append(escape(v)).append('"')
                    }
                    depth++
                    lastWasStart = true
                }
                RES_XML_END_ELEMENT -> {
                    buf.position(chunkStart + headerSize)
                    buf.int
                    val name = str(buf.int)
                    depth--
                    if (lastWasStart) sb.append(" />\n")
                    else sb.append("    ".repeat(depth.coerceAtLeast(0))).append("</").append(name).append(">\n")
                    lastWasStart = false
                }
                RES_XML_CDATA -> {
                    buf.position(chunkStart + headerSize)
                    val text = str(buf.int)
                    if (lastWasStart) {
                        sb.append(">\n")
                        lastWasStart = false
                    }
                    sb.append("    ".repeat(depth)).append(escape(text)).append('\n')
                }
            }
            buf.position(chunkStart + chunkSize)
        }
        return sb.toString()
    }

    private fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun formatValue(type: Int, v: Int, str: (Int) -> String): String = when (type) {
        TYPE_REFERENCE -> if (v == 0) "@null" else String.format("@0x%08x", v)
        TYPE_ATTRIBUTE -> String.format("?0x%08x", v)
        TYPE_STRING -> str(v)
        TYPE_FLOAT -> java.lang.Float.intBitsToFloat(v).toString()
        TYPE_DIMENSION -> complex(v, arrayOf("px", "dp", "sp", "pt", "in", "mm"))
        TYPE_FRACTION -> complex(v, arrayOf("%", "%p"))
        TYPE_INT_DEC -> v.toString()
        TYPE_INT_HEX -> String.format("0x%08x", v)
        TYPE_INT_BOOLEAN -> if (v != 0) "true" else "false"
        TYPE_INT_COLOR_ARGB8, TYPE_INT_COLOR_RGB8, TYPE_INT_COLOR_ARGB4, TYPE_INT_COLOR_RGB4 -> String.format("#%08x", v)
        else -> String.format("0x%08x (type 0x%02x)", v, type)
    }

    private fun complex(v: Int, units: Array<String>): String {
        val mantissa = (v shr 8).toFloat()
        val real = mantissa * when ((v shr 4) and 0x3) {
            0 -> 1f
            1 -> 1f / (1 shl 7)
            2 -> 1f / (1 shl 15)
            else -> 1f / (1 shl 23)
        }
        val unit = units.getOrElse(v and 0xF) { "" }
        return (if (real == real.toInt().toFloat()) real.toInt().toString() else real.toString()) + unit
    }

    private fun readStringPool(buf: ByteBuffer, chunkStart: Int, headerSize: Int): List<String> {
        val count = buf.int
        buf.int // style count
        val flags = buf.int
        val stringsStart = buf.int
        buf.int // styles start
        val utf8 = flags and (1 shl 8) != 0
        buf.position(chunkStart + headerSize)
        val offsets = IntArray(count) { buf.int }
        val base = chunkStart + stringsStart
        val result = ArrayList<String>(count)
        val arr = buf.array()
        for (i in 0 until count) {
            var p = base + offsets[i]
            try {
                if (utf8) {
                    // skip utf16 length
                    p += if (arr[p].toInt() and 0x80 != 0) 2 else 1
                    var len = arr[p].toInt() and 0xFF
                    if (len and 0x80 != 0) {
                        len = ((len and 0x7F) shl 8) or (arr[p + 1].toInt() and 0xFF)
                        p += 2
                    } else p += 1
                    result.add(String(arr, p, len, Charsets.UTF_8))
                } else {
                    var len = (arr[p].toInt() and 0xFF) or ((arr[p + 1].toInt() and 0xFF) shl 8)
                    p += 2
                    if (len and 0x8000 != 0) {
                        val low = (arr[p].toInt() and 0xFF) or ((arr[p + 1].toInt() and 0xFF) shl 8)
                        len = ((len and 0x7FFF) shl 16) or low
                        p += 2
                    }
                    result.add(String(arr, p, len * 2, Charsets.UTF_16LE))
                }
            } catch (e: Exception) {
                result.add("")
            }
        }
        return result
    }
}
