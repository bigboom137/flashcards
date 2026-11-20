package com.flashcards.sync

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.util.concurrent.Executors
import kotlin.concurrent.thread

/**
 * Lets the phone find this computer on the same Wi-Fi and swap libraries with it.
 *
 * Runs two things while sharing is on: a UDP responder that answers the phone's
 * discovery broadcasts, and a small HTTP server that serves this library and accepts
 * a merged one back. Both stop when [stop] is called.
 */
class SyncServer(
    private val readLibrary: () -> String,
    private val writeLibrary: (String) -> Unit,
    private val onActivity: (String) -> Unit = {},
) {
    @Volatile var code: String = SyncProtocol.newCode()
        private set
    @Volatile var port: Int = SyncProtocol.DEFAULT_HTTP_PORT
        private set
    @Volatile var running: Boolean = false
        private set

    private var http: HttpServer? = null
    private var discovery: DatagramSocket? = null

    val hostName: String = runCatching { InetAddress.getLocalHost().hostName }.getOrNull()
        ?: System.getenv("COMPUTERNAME") ?: "This computer"

    /** This machine's address on the local network, for typing in by hand. */
    fun localAddress(): String = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual }
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { !it.isLoopbackAddress && it.address.size == 4 && it.isSiteLocalAddress }
            ?.hostAddress
    }.getOrNull() ?: "unknown"

    @Synchronized
    fun start(): Boolean {
        if (running) return true
        code = SyncProtocol.newCode()
        val server = bindHttp() ?: return false
        server.createContext(SyncProtocol.PATH) { handle(it) }
        server.executor = Executors.newFixedThreadPool(2)
        server.start()
        http = server
        startDiscoveryResponder()
        running = true
        return true
    }

    /** Takes the default port, or the next few if something else already has it. */
    private fun bindHttp(): HttpServer? {
        for (candidate in SyncProtocol.DEFAULT_HTTP_PORT until SyncProtocol.DEFAULT_HTTP_PORT + 10) {
            runCatching { HttpServer.create(InetSocketAddress(candidate), 0) }
                .onSuccess { port = candidate; return it }
        }
        onActivity("Could not open a network port")
        return null
    }

    private fun startDiscoveryResponder() {
        val socket = runCatching {
            DatagramSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(SyncProtocol.DISCOVERY_PORT))
            }
        }.getOrElse {
            // Discovery is a convenience; the phone can still connect by typing the address.
            onActivity("Automatic discovery unavailable (port in use)")
            return
        }
        discovery = socket
        thread(isDaemon = true, name = "flashcards-discovery") {
            val buffer = ByteArray(256)
            while (!socket.isClosed) {
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                } catch (e: IOException) {
                    break // closed by stop()
                }
                val message = String(packet.data, 0, packet.length).trim()
                if (message != SyncProtocol.DISCOVER_REQUEST) continue
                val reply = SyncProtocol.HostInfo(hostName, localAddress(), port).toJson().toByteArray()
                runCatching { socket.send(DatagramPacket(reply, reply.size, packet.address, packet.port)) }
            }
        }
    }

    @Synchronized
    fun stop() {
        running = false
        http?.stop(0)
        http = null
        discovery?.close()
        discovery = null
    }

    private fun handle(exchange: HttpExchange) {
        try {
            val given = exchange.requestURI.query
                ?.split("&")
                ?.firstOrNull { it.startsWith("${SyncProtocol.CODE_PARAM}=") }
                ?.substringAfter('=')
            if (given != code) {
                onActivity("Refused a connection with the wrong code")
                respond(exchange, 403, "Wrong code")
                return
            }
            when (exchange.requestMethod) {
                "GET" -> {
                    respond(exchange, 200, readLibrary())
                    onActivity("Sent this library to ${exchange.remoteAddress.address.hostAddress}")
                }
                "POST" -> {
                    val body = exchange.requestBody.readBytes().decodeToString()
                    writeLibrary(body)
                    respond(exchange, 200, "ok")
                    onActivity("Updated from ${exchange.remoteAddress.address.hostAddress}")
                }
                else -> respond(exchange, 405, "Unsupported")
            }
        } catch (e: Exception) {
            onActivity("Sync failed: ${e.message}")
            runCatching { respond(exchange, 500, e.message ?: "error") }
        } finally {
            exchange.close()
        }
    }

    private fun respond(exchange: HttpExchange, status: Int, body: String) {
        val bytes = body.toByteArray()
        exchange.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }
}
