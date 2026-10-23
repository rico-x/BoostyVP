package io.github.ricox.boostyvp.core.auth.pairing

import android.util.Log
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger

class MiniHttpServerTest {
    @Before fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any(), any(), any<Throwable>()) } returns 0
        every { Log.w(any(), any(), any<Throwable>()) } returns 0
    }
    @After fun tearDown() { unmockkStatic(Log::class) }

    @Test fun `automatic port allocation binds the actual advertised port`() {
        val server = MiniHttpServer(0) { HttpResponse.ok("received") }
        try {
            val port = server.start()
            assertTrue(port > 0)
            Socket("127.0.0.1", port).use { socket ->
                socket.soTimeout = 2_000
                socket.getOutputStream().write("GET /link HTTP/1.1\r\nHost: localhost\r\n\r\n".toByteArray())
                assertTrue(socket.getInputStream().bufferedReader().readText().endsWith("received"))
            }
        } finally { server.stop() }
    }

    @Test fun `oversized or incomplete HTTP bodies never reach the pairing handler`() {
        val calls = AtomicInteger()
        val server = MiniHttpServer(0) { calls.incrementAndGet(); HttpResponse.ok() }
        try {
            val port = server.start()
            for (length in listOf("-1", "262145", "invalid", "100")) {
                Socket("127.0.0.1", port).use { socket ->
                    socket.soTimeout = 2_000
                    socket.getOutputStream().write(
                        "POST /pair HTTP/1.1\r\nContent-Length: $length\r\n\r\n{}".toByteArray())
                    socket.shutdownOutput()
                    val response = socket.getInputStream().bufferedReader().readText()
                    if (length != "100") assertTrue(response.startsWith("HTTP/1.1 400"))
                }
            }
            assertEquals(0, calls.get())
        } finally { server.stop() }
    }

    @Test fun `stop closes active and queued clients without waiting for their timeouts`() {
        val server = MiniHttpServer(0) { error("No complete request was sent") }
        val clients = mutableListOf<Socket>()
        try {
            val port = server.start()
            repeat(10) { clients += Socket("127.0.0.1", port).apply { soTimeout = 2_000 } }
            server.stop()
            clients.forEach { socket ->
                val closed = runCatching { socket.getInputStream().read() == -1 }
                    .getOrElse { it is java.net.SocketException }
                assertTrue("Client remained open after shutdown", closed)
            }
        } finally {
            server.stop()
            clients.forEach { it.close() }
        }
    }

    @Test(timeout = 30_000) fun `a restart on the same port binds straight after stop`() {
        // stop() runs while the accept thread is blocked in the system call; the
        // port used to stay taken until that thread left it, so reopening the
        // pairing screen on its fixed port could fail with "Address already in use".
        val port = LanAddress.freePort()
        // Busy cores, as on a CI runner compiling in parallel, delay the accept
        // thread's exit and widen the window.
        val load = busyCores()
        try {
            repeat(RESTARTS) { round ->
                val server = MiniHttpServer(port) { HttpResponse.ok() }
                try {
                    server.start()
                } catch (e: java.net.BindException) {
                    fail("Restart $round of $RESTARTS: ${e.message}")
                }
                Thread.sleep(5)
                server.stop()
            }
        } finally {
            load.forEach { it.interrupt() }
        }
    }

    private fun busyCores(): List<Thread> = List(Runtime.getRuntime().availableProcessors() * 2) {
        Thread { while (!Thread.currentThread().isInterrupted) Unit }.apply { isDaemon = true; start() }
    }

    private companion object {
        const val RESTARTS = 200
    }
}
