package com.moto.files.tools

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import com.moto.files.data.files.TreeRepository
import kotlinx.coroutines.runBlocking
import java.io.BufferedInputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean

/** Explicit, read-only, session-scoped LAN transfer. Do not expose on untrusted Wi-Fi. */
class WifiShare(private val context: Context, private val folder: Uri) : AutoCloseable {
    private val random = SecureRandom()
    private val token = ByteArray(16).also(random::nextBytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    private val active = AtomicBoolean(true)
    private val server: ServerSocket
    val address: String
    init {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val caps = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
        require(caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) { "Connect to Wi-Fi first" }
        val host = connectivity.getLinkProperties(connectivity.activeNetwork)?.linkAddresses
            ?.map { it.address }?.filterIsInstance<java.net.Inet4Address>()
            ?.firstOrNull { it.isSiteLocalAddress } ?: error("No Wi-Fi IPv4 address found")
        server = ServerSocket(0, 8, host)
        address = "http://${host.hostAddress}:${server.localPort}/?key=$token"
        Thread({ accept() }, "MotoFiles-LAN").apply { isDaemon = true; start() }
    }
    private fun accept() {
        while (active.get()) {
            try { server.accept().use { socket -> handle(socket) } }
            catch (_: Exception) { if (!active.get()) break }
        }
    }
    private fun handle(socket: Socket) {
        socket.soTimeout = 15_000
        val input = BufferedInputStream(socket.getInputStream())
        fun line(): String {
            val bytes = java.io.ByteArrayOutputStream()
            while (bytes.size() < 4096) {
                val b = input.read()
                if (b < 0 || b == 10) break
                if (b != 13) bytes.write(b)
            }
            return bytes.toString("UTF-8")
        }
        val request = line().split(' ')
        var headerCount = 0
        while (headerCount++ < 50 && line().isNotEmpty()) Unit
        val output = socket.getOutputStream()
        fun response(code: Int, type: String, bytes: ByteArray) {
            output.write("HTTP/1.1 $code ${if (code == 200) "OK" else "Forbidden"}\r\nContent-Type: $type\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\nX-Content-Type-Options: nosniff\r\nCache-Control: no-store\r\n\r\n".toByteArray())
            output.write(bytes)
        }
        if (request.size < 2 || request[0] != "GET") { response(403, "text/plain", "GET only".toByteArray()); return }
        val url = Uri.parse(request[1])
        if (url.getQueryParameter("key") != token) { response(403, "text/plain", "Not authorized".toByteArray()); return }
        val files = runBlocking { TreeRepository(context).list(folder).getOrThrow() }.filter { !it.isDirectory }
        if (url.path == "/file") {
            val id = url.getQueryParameter("id")
            val file = files.find { it.uri.toString() == id } ?: run { response(403, "text/plain", "Unavailable".toByteArray()); return }
            // Streaming only; no request path is ever resolved as a filesystem path.
            val length = file.length
            if (length <= 0) { response(403, "text/plain", "Size unavailable".toByteArray()); return }
            output.write("HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nContent-Disposition: attachment; filename=\"download\"\r\nContent-Length: $length\r\nConnection: close\r\nX-Content-Type-Options: nosniff\r\n\r\n".toByteArray())
            context.contentResolver.openInputStream(file.uri)?.use { it.copyTo(output) }
        } else {
            fun esc(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
            val html = buildString {
                append("<!doctype html><meta name=viewport content='width=device-width'><title>Moto Files</title><h1>Moto Files</h1><p>Read-only local transfer</p><ul>")
                files.forEach { file ->
                    append("<li><a href='/file?key=$token&id=${URLEncoder.encode(file.uri.toString(), "UTF-8")}' rel='noreferrer'>${esc(file.name)}</a></li>")
                }
                append("</ul>")
            }
            response(200, "text/html; charset=utf-8", html.toByteArray())
        }
    }
    override fun close() { active.set(false); server.close() }
}
