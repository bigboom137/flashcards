package com.flashcards.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URL

/** The phone's half of Wi-Fi sync: find desktops on the network and swap libraries. */
object SyncClient {
    private const val CONNECT_TIMEOUT_MS = 6000
    private const val READ_TIMEOUT_MS = 20000

    /**
     * Broadcasts a discovery request and collects the desktops that answer within
     * [timeoutMs]. Returns an empty list when nothing answers, which is normal on
     * networks that block broadcasts — the address can still be typed in by hand.
     */
    suspend fun discover(timeoutMs: Long = 2500): List<SyncProtocol.HostInfo> = withContext(Dispatchers.IO) {
        val found = LinkedHashMap<String, SyncProtocol.HostInfo>()
        runCatching {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.soTimeout = 400
                val request = SyncProtocol.DISCOVER_REQUEST.toByteArray()
                for (target in broadcastAddresses()) {
                    runCatching {
                        socket.send(DatagramPacket(request, request.size, target, SyncProtocol.DISCOVERY_PORT))
                    }
                }
                // A plain deadline, not withTimeout: receive() blocks without ever
                // suspending, so a coroutine timeout would have nothing to cancel at.
                val deadline = System.currentTimeMillis() + timeoutMs
                val buffer = ByteArray(1024)
                while (System.currentTimeMillis() < deadline) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                    } catch (e: SocketTimeoutException) {
                        continue
                    }
                    val address = packet.address.hostAddress ?: continue
                    val body = String(packet.data, 0, packet.length)
                    SyncProtocol.HostInfo.fromJson(body, address)?.let { found[address] = it }
                }
            }
        }
        found.values.toList()
    }

    private fun broadcastAddresses(): List<InetAddress> {
        val addresses = mutableListOf<InetAddress>()
        runCatching {
            java.net.NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.interfaceAddresses }
                .mapNotNull { it.broadcast }
                .forEach { addresses += it }
        }
        runCatching { addresses += InetAddress.getByName("255.255.255.255") }
        return addresses.distinct()
    }

    /** Downloads the desktop's library as backup JSON. */
    suspend fun fetch(host: String, port: Int, code: String): String = withContext(Dispatchers.IO) {
        val connection = open(host, port, code)
        connection.requestMethod = "GET"
        connection.read()
    }

    /** Uploads the merged library, replacing what the desktop had. */
    suspend fun push(host: String, port: Int, code: String, json: String) = withContext(Dispatchers.IO) {
        val connection = open(host, port, code)
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        connection.outputStream.use { it.write(json.toByteArray()) }
        connection.read()
        Unit
    }

    private fun open(host: String, port: Int, code: String): HttpURLConnection =
        (URL(SyncProtocol.url(host, port, code)).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
        }

    private fun HttpURLConnection.read(): String = try {
        when (responseCode) {
            200 -> inputStream.use { it.readBytes().decodeToString() }
            403 -> throw IOException("That code did not match the one on the computer")
            else -> throw IOException("The computer answered $responseCode")
        }
    } finally {
        disconnect()
    }
}
