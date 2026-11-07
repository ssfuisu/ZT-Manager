package com.ztmanager.share

import android.util.Base64
import com.ztmanager.fs.FileType
import com.ztmanager.fs.Format
import com.ztmanager.ui.browser.NaturalOrder
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.Executors
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Minimal HTTP/1.0 file server for sharing a folder over the local network.
 * Supports directory listing, downloads (with byte ranges), folder download as ZIP and
 * multipart uploads. Every connection is closed after one request.
 */
class WebServer(
    private val root: File,
    val port: Int,
    private val allowUpload: Boolean,
    private val password: String?,
    private val onLog: (String) -> Unit
) {
    private var server: ServerSocket? = null
    private val pool = Executors.newFixedThreadPool(8)
    @Volatile var running = false
        private set

    fun start() {
        val s = ServerSocket(port)
        server = s
        running = true
        Thread {
            while (running) {
                val client = try {
                    s.accept()
                } catch (e: Exception) {
                    break
                }
                pool.execute { handle(client) }
            }
        }.apply { isDaemon = true; start() }
    }

    fun stop() {
        running = false
        try {
            server?.close()
        } catch (_: Exception) {
        }
        pool.shutdownNow()
    }

    private class Request(val method: String, val path: String, val query: Map<String, String>, val headers: Map<String, String>)

    private fun readLine(input: InputStream): String? {
        val buf = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b < 0) return if (buf.size() == 0) null else buf.toString("ISO-8859-1")
            if (b == '\n'.code) break
            if (b != '\r'.code) buf.write(b)
            if (buf.size() > 16 * 1024) return null
        }
        return buf.toString("ISO-8859-1")
    }

    private fun parse(input: InputStream): Request? {
        val first = readLine(input) ?: return null
        val parts = first.split(' ')
        if (parts.size < 2) return null
        val headers = HashMap<String, String>()
        while (true) {
            val line = readLine(input) ?: break
            if (line.isEmpty()) break
            val i = line.indexOf(':')
            if (i > 0) headers[line.substring(0, i).trim().lowercase()] = line.substring(i + 1).trim()
        }
        val target = parts[1]
        val rawPath = target.substringBefore('?')
        val query = HashMap<String, String>()
        if (target.contains('?')) {
            target.substringAfter('?').split('&').forEach { kv ->
                val k = kv.substringBefore('=')
                if (k.isNotEmpty()) query[k] = decode(kv.substringAfter('=', ""))
            }
        }
        val path = decode(rawPath)
        return Request(parts[0].uppercase(), path, query, headers)
    }

    private fun decode(s: String): String = try {
        URLDecoder.decode(s.replace("+", "%2B"), "UTF-8")
    } catch (e: Exception) {
        s
    }

    private fun encodePath(p: String): String =
        p.split('/').joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }

    private fun html(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun resolve(path: String): File? {
        val f = File(root, path.trimStart('/'))
        val rootPath = root.canonicalPath
        val fp = f.canonicalPath
        return if (fp == rootPath || fp.startsWith("$rootPath/")) f else null
    }

    private fun handle(socket: Socket) {
        try {
            socket.soTimeout = 60_000
            val input = BufferedInputStream(socket.getInputStream(), 64 * 1024)
            val out = BufferedOutputStream(socket.getOutputStream(), 64 * 1024)
            val req = parse(input) ?: return
            onLog(req.method + " " + req.path + "  " + (socket.inetAddress?.hostAddress ?: ""))
            if (!password.isNullOrEmpty()) {
                val auth = req.headers["authorization"]
                val ok = auth != null && auth.startsWith("Basic ") && try {
                    String(Base64.decode(auth.removePrefix("Basic ").trim(), Base64.DEFAULT)).substringAfter(':') == password
                } catch (e: Exception) {
                    false
                }
                if (!ok) {
                    respond(out, 401, "text/plain", "Authentication required".toByteArray(), mapOf("WWW-Authenticate" to "Basic realm=\"ZT Manager\""))
                    return
                }
            }
            val file = resolve(req.path)
            if (file == null || !file.exists()) {
                respond(out, 404, "text/plain", "Not found".toByteArray())
                return
            }
            when {
                req.method == "POST" && file.isDirectory -> {
                    if (!allowUpload) {
                        respond(out, 403, "text/plain", "Uploads are disabled".toByteArray())
                        return
                    }
                    val saved = receiveUpload(req, input, file)
                    onLog("Uploaded: " + saved.joinToString(", "))
                    redirect(out, encodePath(req.path.ifEmpty { "/" }))
                }
                file.isDirectory && req.query["zip"] == "1" -> sendZip(out, file)
                file.isDirectory -> {
                    if (!req.path.endsWith("/")) {
                        redirect(out, encodePath(req.path) + "/")
                        return
                    }
                    respond(out, 200, "text/html; charset=utf-8", listing(req.path, file).toByteArray(Charsets.UTF_8))
                }
                req.method == "GET" || req.method == "HEAD" -> sendFile(out, req, file)
                else -> respond(out, 405, "text/plain", "Method not allowed".toByteArray())
            }
        } catch (e: Exception) {
            // client went away
        } finally {
            try {
                socket.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun writeHead(out: OutputStream, code: Int, headers: Map<String, String>) {
        val reason = when (code) {
            200 -> "OK"; 206 -> "Partial Content"; 302 -> "Found"; 401 -> "Unauthorized"
            403 -> "Forbidden"; 404 -> "Not Found"; 405 -> "Method Not Allowed"; 416 -> "Range Not Satisfiable"
            else -> "Error"
        }
        val sb = StringBuilder("HTTP/1.1 $code $reason\r\n")
        headers.forEach { (k, v) -> sb.append(k).append(": ").append(v).append("\r\n") }
        sb.append("Connection: close\r\nServer: ZT Manager\r\n\r\n")
        out.write(sb.toString().toByteArray(Charsets.UTF_8))
    }

    private fun respond(out: OutputStream, code: Int, type: String, body: ByteArray, extra: Map<String, String> = emptyMap()) {
        writeHead(out, code, mapOf("Content-Type" to type, "Content-Length" to body.size.toString()) + extra)
        out.write(body)
        out.flush()
    }

    private fun redirect(out: OutputStream, location: String) {
        writeHead(out, 302, mapOf("Location" to location, "Content-Length" to "0"))
        out.flush()
    }

    private fun sendFile(out: OutputStream, req: Request, file: File) {
        val size = file.length()
        var start = 0L
        var end = size - 1
        var code = 200
        req.headers["range"]?.let { r ->
            val m = Regex("bytes=(\\d*)-(\\d*)").find(r)
            if (m != null) {
                val a = m.groupValues[1]
                val b = m.groupValues[2]
                if (a.isEmpty() && b.isNotEmpty()) {
                    start = (size - b.toLong()).coerceAtLeast(0)
                } else {
                    start = a.toLongOrNull() ?: 0
                    if (b.isNotEmpty()) end = minOf(b.toLong(), size - 1)
                }
                if (start > end || start >= size) {
                    writeHead(out, 416, mapOf("Content-Range" to "bytes */$size", "Content-Length" to "0"))
                    out.flush()
                    return
                }
                code = 206
            }
        }
        val len = (end - start + 1).coerceAtLeast(0)
        val headers = LinkedHashMap<String, String>()
        headers["Content-Type"] = FileType.mimeOf(file.name).let { if (it == "*/*") "application/octet-stream" else it }
        headers["Content-Length"] = len.toString()
        headers["Accept-Ranges"] = "bytes"
        if (code == 206) headers["Content-Range"] = "bytes $start-$end/$size"
        if (req.query["dl"] == "1") {
            headers["Content-Disposition"] = "attachment; filename*=UTF-8''" + URLEncoder.encode(file.name, "UTF-8").replace("+", "%20")
        }
        writeHead(out, code, headers)
        if (req.method == "HEAD") {
            out.flush()
            return
        }
        FileInputStream(file).use { input ->
            input.channel.position(start)
            val buf = ByteArray(128 * 1024)
            var left = len
            while (left > 0) {
                val n = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                if (n < 0) break
                out.write(buf, 0, n)
                left -= n
            }
        }
        out.flush()
    }

    private fun sendZip(out: OutputStream, dir: File) {
        val name = (dir.name.ifEmpty { "files" }) + ".zip"
        writeHead(
            out, 200,
            mapOf(
                "Content-Type" to "application/zip",
                "Content-Disposition" to "attachment; filename*=UTF-8''" + URLEncoder.encode(name, "UTF-8").replace("+", "%20")
            )
        )
        val zos = ZipOutputStream(out)
        val base = dir.canonicalPath.length + 1
        dir.walkTopDown().forEach { f ->
            val rel = f.canonicalPath.let { if (it.length >= base) it.substring(base) else "" }
            if (rel.isEmpty()) return@forEach
            if (f.isDirectory) {
                zos.putNextEntry(ZipEntry("$rel/"))
                zos.closeEntry()
            } else if (f.canRead()) {
                zos.putNextEntry(ZipEntry(rel).apply { time = f.lastModified() })
                FileInputStream(f).use { it.copyTo(zos, 128 * 1024) }
                zos.closeEntry()
            }
        }
        zos.finish()
        out.flush()
    }

    private fun receiveUpload(req: Request, input: InputStream, dir: File): List<String> {
        val type = req.headers["content-type"] ?: return emptyList()
        val boundary = type.substringAfter("boundary=", "").trim('"').takeIf { it.isNotEmpty() } ?: return emptyList()
        val saved = ArrayList<String>()
        // the body starts with "--boundary\r\n"
        readLine(input) ?: return saved
        val delimiter = ("\r\n--" + boundary).toByteArray(Charsets.ISO_8859_1)
        while (true) {
            val partHeaders = HashMap<String, String>()
            while (true) {
                val line = readLine(input) ?: return saved
                if (line.isEmpty()) break
                val i = line.indexOf(':')
                if (i > 0) partHeaders[line.substring(0, i).trim().lowercase()] = line.substring(i + 1).trim()
            }
            val disposition = partHeaders["content-disposition"] ?: ""
            val rawName = Regex("filename=\"([^\"]*)\"").find(disposition)?.groupValues?.get(1)
            val fileName = rawName?.let { String(it.toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8) }
                ?.substringAfterLast('/')?.substringAfterLast('\\')?.takeIf { it.isNotBlank() && it != "." && it != ".." }
            var found = false
            if (fileName != null) {
                var target = File(dir, fileName)
                var i = 1
                while (target.exists()) {
                    val dot = fileName.lastIndexOf('.')
                    target = if (dot > 0) File(dir, fileName.substring(0, dot) + " ($i)" + fileName.substring(dot))
                    else File(dir, "$fileName ($i)")
                    i++
                }
                BufferedOutputStream(FileOutputStream(target), 128 * 1024).use { o -> found = copyUntil(input, delimiter, o) }
                if (found) saved.add(target.name) else target.delete()
            } else {
                found = copyUntil(input, delimiter, null)
            }
            if (!found) return saved
            val a = input.read()
            val b = input.read()
            if (a == '-'.code && b == '-'.code) return saved
            if (a < 0 || b < 0) return saved
        }
    }

    /** Copies bytes until [delim] is found (KMP matching). Returns false on end of stream. */
    private fun copyUntil(input: InputStream, delim: ByteArray, out: OutputStream?): Boolean {
        val fail = IntArray(delim.size)
        var k = 0
        for (i in 1 until delim.size) {
            while (k > 0 && delim[i] != delim[k]) k = fail[k - 1]
            if (delim[i] == delim[k]) k++
            fail[i] = k
        }
        var j = 0
        while (true) {
            val r = input.read()
            if (r < 0) return false
            val b = r.toByte()
            while (j > 0 && b != delim[j]) {
                val keep = fail[j - 1]
                out?.write(delim, 0, j - keep)
                j = keep
            }
            if (b == delim[j]) {
                j++
                if (j == delim.size) return true
            } else {
                out?.write(r)
            }
        }
    }

    private fun listing(path: String, dir: File): String {
        val files = (dir.listFiles() ?: emptyArray()).filter { !it.name.startsWith(".") }
            .sortedWith(compareBy<File> { !it.isDirectory }.then(Comparator { a, b -> NaturalOrder.compare(a.name, b.name) }))
        val sb = StringBuilder()
        sb.append("<!DOCTYPE html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
        sb.append("<title>ZT Manager - ").append(html(path)).append("</title><style>")
        sb.append(
            "body{font-family:system-ui,sans-serif;margin:0;background:#1c1f24;color:#e6e8eb}" +
                "header{background:#262a31;padding:14px 18px;position:sticky;top:0}h1{font-size:18px;margin:0}" +
                ".path{color:#9aa3ad;font-size:13px;margin-top:4px;word-break:break-all}" +
                "main{padding:8px 12px;max-width:960px;margin:auto}a{color:#4da3ff;text-decoration:none}" +
                "table{width:100%;border-collapse:collapse}td{padding:9px 6px;border-bottom:1px solid #30353d;font-size:14px}" +
                "td.s,td.d{color:#9aa3ad;white-space:nowrap;font-size:12px}td.n{word-break:break-all}" +
                ".dir{color:#ffc34d;font-weight:600}.bar{display:flex;gap:8px;flex-wrap:wrap;margin:10px 0}" +
                ".btn{background:#2c313a;border:1px solid #3a404a;color:#e6e8eb;padding:8px 12px;border-radius:8px;font-size:13px}" +
                "form{background:#23272e;padding:12px;border-radius:10px;margin:10px 0}input[type=submit]{background:#4da3ff;color:#00213f;border:0;padding:8px 14px;border-radius:8px;font-weight:600}"
        )
        sb.append("</style></head><body><header><h1>ZT Manager</h1><div class=\"path\">").append(html(path)).append("</div></header><main>")
        sb.append("<div class=\"bar\">")
        if (path != "/") sb.append("<a class=\"btn\" href=\"../\">Parent folder</a>")
        sb.append("<a class=\"btn\" href=\"?zip=1\">Download folder as ZIP</a></div>")
        if (allowUpload) {
            sb.append("<form method=\"post\" enctype=\"multipart/form-data\"><input type=\"file\" name=\"files\" multiple> <input type=\"submit\" value=\"Upload\"></form>")
        }
        sb.append("<table>")
        for (f in files) {
            val href = URLEncoder.encode(f.name, "UTF-8").replace("+", "%20") + if (f.isDirectory) "/" else ""
            sb.append("<tr><td class=\"n\">")
            if (f.isDirectory) sb.append("<a class=\"dir\" href=\"").append(href).append("\">").append(html(f.name)).append("/</a>")
            else sb.append("<a href=\"").append(href).append("\">").append(html(f.name)).append("</a>")
            sb.append("</td><td class=\"s\">")
            if (f.isFile) sb.append(Format.size(f.length())).append(" &middot; <a href=\"").append(href).append("?dl=1\">download</a>")
            sb.append("</td><td class=\"d\">").append(html(Format.date(f.lastModified()))).append("</td></tr>")
        }
        sb.append("</table></main></body></html>")
        return sb.toString()
    }

    companion object {
        /** IPv4 addresses of the device on local networks (Wi-Fi, hotspot, ethernet). */
        fun localAddresses(): List<String> = try {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { ni -> ni.inetAddresses.toList().filterIsInstance<Inet4Address>().map { it.hostAddress ?: "" } }
                .filter { it.isNotEmpty() }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
