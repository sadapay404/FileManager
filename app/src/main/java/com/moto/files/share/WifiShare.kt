package com.moto.files.share

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.Base64

/**
 * Cable-free local file sharing over Wi-Fi: a tiny HTTP server bound ONLY to the Wi-Fi
 * interface (it refuses to start — rather than bind 0.0.0.0 — when Wi-Fi is off),
 * requiring a per-session token on every request. Files are streamed, never slurped.
 */
class WifiShareServer(
    private val context: Context,
    private val root: File,
    private val port: Int
) {
    private var socket: ServerSocket? = null
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    val token: String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(ByteArray(24).also { SecureRandom().nextBytes(it) })

    @Volatile
    var running = false
        private set

    fun start(): String? {
        if (running) return address()
        val ip = wifiIp() ?: return null // never fall back to a wildcard bind
        return try {
            val s = ServerSocket(port, 50, ip)
            socket = s
            running = true
            job = scope.launch {
                while (running && !s.isClosed) {
                    try { scope.launch { serve(s.accept()) } }
                    catch (_: Exception) { break }
                }
            }
            address()
        } catch (e: Exception) { null }
    }

    fun stop() {
        running = false
        job?.cancel()
        socket?.close()
        socket = null
    }

    fun address(): String? {
        val ip = wifiIp() ?: return null
        return "http://$ip:$port/?token=$token"
    }

    private fun wifiIp(): InetAddress? {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val v = wm?.connectionInfo?.ipAddress ?: return null
        if (v == 0) return null
        return InetAddress.getByAddress(
            byteArrayOf(
                (v and 0xff).toByte(), (v shr 8 and 0xff).toByte(),
                (v shr 16 and 0xff).toByte(), (v shr 24 and 0xff).toByte()
            )
        )
    }

    private fun serve(client: Socket) {
        try {
            client.use { s ->
                s.soTimeout = 10_000
                val reader = BufferedReader(InputStreamReader(s.getInputStream()))
                val out = PrintWriter(s.getOutputStream())
                val line = reader.readLine() ?: return
                val target = line.split(" ").getOrNull(1) ?: "/"
                val path = target.substringBefore('?')
                val query = target.substringAfter('?', "")
                val tokenOk = query.split('&').any { it == "token=$token" }
                if (!tokenOk) {
                    out.println("HTTP/1.1 401 Unauthorized")
                    out.println(); out.println("token required"); out.flush()
                    return
                }
                if (!line.startsWith("GET")) {
                    out.println("HTTP/1.1 405 Method Not Allowed"); out.println(); out.flush()
                    return
                }
                respond(path, out, s)
            }
        } catch (_: Exception) { /* dropped client */ }
    }

    private fun respond(path: String, out: PrintWriter, s: Socket) {
        val f = File(root, path.removePrefix("/"))
        if (!f.canonicalPath.startsWith(root.canonicalPath)) {
            out.println("HTTP/1.1 403 Forbidden"); out.println(); out.flush(); return
        }
        if (f.isDirectory) {
            val base = if (path == "/") "" else path.removeSuffix("/") + "/"
            val items = f.listFiles()?.joinToString("") { e ->
                val href = "?token=$token&path=" + URLEncoder.encode(base + e.name, "UTF-8")
                "<li><a href=\"$href\">${esc(e.name)}${if (e.isDirectory) "/" else ""}</a></li>"
            } ?: ""
            val html = "<html><body><h2>${esc(f.name)}</h2><ul>$items</ul></body></html>"
            out.println("HTTP/1.1 200 OK")
            out.println("Content-Type: text/html; charset=utf-8")
            out.println("Content-Length: ${html.toByteArray().size}")
            out.println(); out.println(html); out.flush()
        } else if (f.isFile) {
            out.println("HTTP/1.1 200 OK")
            out.println("Content-Type: application/octet-stream")
            out.println("Content-Disposition: attachment; filename=\"${f.name.replace("\"", "")}\"")
            out.println("Content-Length: ${f.length()}")
            out.println(); out.flush()
            s.getOutputStream().use { o ->
                f.inputStream().use { i ->
                    val buf = ByteArray(64 * 1024)
                    var n: Int
                    while (i.read(buf).also { n = it } != -1) o.write(buf, 0, n)
                }
            }
        } else {
            out.println("HTTP/1.1 404 Not Found"); out.println(); out.flush()
        }
    }

    private fun esc(s: String) =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
