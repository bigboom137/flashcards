package com.flashcards.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL

/** Drives the real server over the loopback: discovery, the code check, GET and POST. */
class SyncServerTest {
    private fun get(port: Int, code: String): Pair<Int, String> {
        val c = URL(SyncProtocol.url("127.0.0.1", port, code)).openConnection() as HttpURLConnection
        c.connectTimeout = 3000
        c.readTimeout = 3000
        return try {
            val status = c.responseCode
            val body = (if (status == 200) c.inputStream else c.errorStream)?.readBytes()?.decodeToString().orEmpty()
            status to body
        } finally {
            c.disconnect()
        }
    }

    private fun post(port: Int, code: String, body: String): Int {
        val c = URL(SyncProtocol.url("127.0.0.1", port, code)).openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.doOutput = true
        c.connectTimeout = 3000
        c.readTimeout = 3000
        return try {
            c.outputStream.use { it.write(body.toByteArray()) }
            c.responseCode
        } finally {
            c.disconnect()
        }
    }

    @Test
    fun servesAndAcceptsALibrary() {
        var library = """{"version":1,"decks":[],"cards":[],"logs":[]}"""
        val server = SyncServer(readLibrary = { library }, writeLibrary = { library = it })
        assertTrue(server.start())
        try {
            val (status, body) = get(server.port, server.code)
            assertEquals(200, status)
            assertTrue(body.contains("\"decks\""))

            val updated = """{"version":1,"decks":[{"id":1,"name":"Latin","createdAt":1}],"cards":[],"logs":[]}"""
            assertEquals(200, post(server.port, server.code, updated))
            assertEquals(updated, library)
        } finally {
            server.stop()
        }
    }

    @Test
    fun wrongCodeIsRefused() {
        val server = SyncServer(readLibrary = { "{}" }, writeLibrary = { error("must not be called") })
        assertTrue(server.start())
        try {
            val wrong = if (server.code == "000000") "111111" else "000000"
            assertEquals(403, get(server.port, wrong).first)
            assertEquals(403, post(server.port, wrong, "{}"))
        } finally {
            server.stop()
        }
    }

    @Test
    fun answersDiscoveryBroadcasts() {
        val server = SyncServer(readLibrary = { "{}" }, writeLibrary = {})
        assertTrue(server.start())
        try {
            DatagramSocket().use { socket ->
                socket.soTimeout = 3000
                val request = SyncProtocol.DISCOVER_REQUEST.toByteArray()
                socket.send(
                    DatagramPacket(request, request.size, InetAddress.getByName("127.0.0.1"), SyncProtocol.DISCOVERY_PORT),
                )
                val buffer = ByteArray(1024)
                val packet = DatagramPacket(buffer, buffer.size)
                socket.receive(packet)
                val info = SyncProtocol.HostInfo.fromJson(String(packet.data, 0, packet.length), "127.0.0.1")
                assertNotNull(info)
                assertEquals(server.port, info!!.port)
                assertTrue(info.name.isNotBlank())
            }
        } finally {
            server.stop()
        }
    }

    @Test
    fun portIsReleasedOnStop() {
        val server = SyncServer(readLibrary = { "{}" }, writeLibrary = {})
        assertTrue(server.start())
        val port = server.port
        server.stop()

        val again = SyncServer(readLibrary = { "{}" }, writeLibrary = {})
        assertTrue(again.start())
        try {
            assertEquals(port, again.port)
        } finally {
            again.stop()
        }
    }
}
