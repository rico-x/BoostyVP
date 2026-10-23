package io.github.ricox.boostyvp.core.auth.pairing

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.net.BindException
import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/** Reopening the pairing screen restarts the server on its fixed port; Android must let it bind at once. */
@RunWith(AndroidJUnit4::class)
class PairingRestartDeviceTest {
    @Test(timeout = 60_000)
    fun restartOnTheSamePortBindsStraightAfterStop() {
        val port = LanAddress.freePort()
        // Busy cores, as on a CI runner compiling in parallel, delay the accept
        // thread's exit and widen the window.
        val load = busyCores()
        try {
            repeat(RESTARTS) { round ->
                val server = MiniHttpServer(port) { HttpResponse.ok("round $round") }
                try {
                    server.start()
                } catch (e: BindException) {
                    fail("Restart $round of $RESTARTS: ${e.message}")
                }
                Thread.sleep(5)
                server.stop()
            }
        } finally {
            load.forEach { it.interrupt() }
        }
        // The last restart still serves requests.
        val server = MiniHttpServer(port) { HttpResponse.ok("served") }
        try {
            server.start()
            val connection = URL("http://127.0.0.1:$port/link").openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 2_000
                connection.readTimeout = 2_000
                assertEquals(200, connection.responseCode)
                assertEquals("served", connection.inputStream.bufferedReader().use { it.readText() })
            } finally {
                connection.disconnect()
            }
        } finally {
            server.stop()
        }
    }

    private fun busyCores(): List<Thread> = List(Runtime.getRuntime().availableProcessors() * 2) {
        Thread { while (!Thread.currentThread().isInterrupted) Unit }.apply { isDaemon = true; start() }
    }

    private companion object {
        const val RESTARTS = 200
    }
}
