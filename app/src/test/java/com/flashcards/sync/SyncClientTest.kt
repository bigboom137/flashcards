package com.flashcards.sync

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

/**
 * Runs the phone's client against a stand-in for the desktop server. Android's unit
 * test classpath has no HTTP server, so this speaks just enough HTTP/1.1 by hand.
 */
class SyncClientTest {
    private class Fake(private val body: String = """{"decks":[]}""", private val status: Int = 200) {
        private val socket = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val port: Int get() = socket.localPort

        @Volatile var requestLine: String? = null
        @Volatile var received: String? = null

        fun start() = thread(isDaemon = true) {
            runCatching {
                socket.accept().use { client ->
                    val input = BufferedReader(InputStreamReader(client.getInputStream()))
                    requestLine = input.readLine()
                    var length = 0
                    while (true) {
                        val header = input.readLine() ?: break
                        if (header.isEmpty()) break
                        if (header.startsWith("Content-Length:", ignoreCase = true)) {
                            length = header.substringAfter(':').trim().toInt()
                        }
                    }
                    if (length > 0) {
                        val buffer = CharArray(length)
                        var read = 0
                        while (read < length) {
                            val n = input.read(buffer, read, length - read)
                            if (n < 0) break
                            read += n
                        }
                        received = String(buffer, 0, read)
                    }
                    val bytes = body.toByteArray()
                    client.getOutputStream().apply {
                        write("HTTP/1.1 $status OK\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        write(bytes)
                        flush()
                    }
                }
            }
            socket.close()
        }

        fun query(): String? = requestLine?.substringAfter('?', "")?.substringBefore(' ')
        fun method(): String? = requestLine?.substringBefore(' ')
    }

    @Test
    fun fetchSendsTheCodeAndReturnsTheBody() = runBlocking {
        val fake = Fake(body = """{"decks":[{"name":"Latin"}]}""")
        fake.start()
        val result = SyncClient.fetch("127.0.0.1", fake.port, "123456")
        assertEquals("""{"decks":[{"name":"Latin"}]}""", result)
        assertEquals("GET", fake.method())
        assertEquals("code=123456", fake.query())
    }

    @Test
    fun pushUploadsTheMergedLibrary() = runBlocking {
        val fake = Fake()
        fake.start()
        SyncClient.push("127.0.0.1", fake.port, "654321", """{"cards":[1]}""")
        assertEquals("""{"cards":[1]}""", fake.received)
        assertEquals("POST", fake.method())
        assertEquals("code=654321", fake.query())
    }

    @Test
    fun aRejectedCodeExplainsItself() = runBlocking {
        val fake = Fake(body = "no", status = 403)
        fake.start()
        try {
            SyncClient.fetch("127.0.0.1", fake.port, "000000")
            fail("expected the wrong code to be reported")
        } catch (e: IOException) {
            assertTrue(e.message!!, e.message!!.contains("code"))
        }
    }

    @Test
    fun discoveryReturnsEmptyWhenNothingAnswers() = runBlocking {
        // Nothing is sharing: this must come back empty rather than hang or throw.
        assertTrue(SyncClient.discover(timeoutMs = 600).isEmpty())
    }
}
