package io.github.ricox.boostyvp.core.auth.pairing

import android.util.Log
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.EOFException
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.ArrayBlockingQueue
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

internal data class HttpRequest(
    val method: String,
    /** Path with any query string removed. */
    val path: String,
    val body: String,
) {
    /** Parses an `application/x-www-form-urlencoded` body. */
    fun formField(name: String): String? = body
        .split('&')
        .asSequence()
        .mapNotNull { pair ->
            val index = pair.indexOf('=')
            if (index <= 0) return@mapNotNull null
            val key = decode(pair.substring(0, index))
            if (key != name) null else decode(pair.substring(index + 1))
        }
        .firstOrNull()
        ?.takeIf { it.isNotBlank() }

    private fun decode(value: String): String =
        runCatching { URLDecoder.decode(value.replace('+', ' '), "UTF-8") }.getOrDefault(value)
}

internal data class HttpResponse(
    val status: Int,
    val body: String,
    val contentType: String = "text/plain; charset=utf-8",
) {
    companion object {
        fun html(body: String) = HttpResponse(200, body, "text/html; charset=utf-8")
        fun ok(body: String = "ok") = HttpResponse(200, body)
        fun badRequest(body: String = "rejected") = HttpResponse(400, body)
        fun notFound() = HttpResponse(404, "Not found")
    }
}

/**
 * A deliberately tiny HTTP/1.1 server.
 *
 * This replaced an embedded Ktor server. Ktor is a JVM server framework -- it
 * pulls in slf4j and engine machinery, is not officially supported on Android,
 * and when any of that fails to class-load the result is a
 * NoClassDefFoundError: an Error, not an Exception, so ordinary `catch` blocks
 * miss it and the calling coroutine dies silently. That presented as a pairing
 * screen stuck on its spinner forever, with nothing to debug.
 *
 * Serving two routes to one device on a LAN does not justify that surface. This
 * handles exactly what is needed -- a request line, headers, an optional
 * Content-Length body -- with nothing but java.net.
 */
internal class MiniHttpServer(
    private val port: Int,
    private val onRequest: (HttpRequest) -> HttpResponse,
) {
    @Volatile
    private var running = false
    private var serverSocket: ServerSocket? = null
    @Volatile
    private var acceptThread: Thread? = null
    private val clients = Collections.newSetFromMap(ConcurrentHashMap<Socket, Boolean>())
    private val workers = ThreadPoolExecutor(
        WORKER_THREADS, WORKER_THREADS, 0, TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(MAX_QUEUED_CONNECTIONS),
    )

    /** @throws java.io.IOException if the port cannot be bound. */
    fun start(): Int {
        // Bound here rather than on the accept thread so a bind failure is
        // thrown to the caller instead of vanishing into a background thread.
        val socket = ServerSocket(port)
        socket.reuseAddress = true
        serverSocket = socket
        running = true

        acceptThread = thread(isDaemon = true, name = "boosty-pairing-http") {
            while (running) {
                val client = try {
                    socket.accept()
                } catch (t: Throwable) {
                    if (running) Log.w(TAG, "Accept failed", t)
                    break
                }
                synchronized(this) {
                    if (!running) {
                        runCatching { client.close() }
                    } else {
                        clients.add(client)
                        try {
                            workers.execute { handle(client) }
                        } catch (_: RejectedExecutionException) {
                            clients.remove(client)
                            runCatching { client.close() }
                        }
                    }
                }
            }
        }
        return socket.localPort
    }

    /**
     * Returns once the port is free again. Closing the socket is not enough: the
     * accept thread is blocked in the system call and keeps the listening socket
     * alive until it leaves it, so an immediate restart on the same port --
     * reopening the pairing screen -- failed with "Address already in use".
     */
    fun stop() {
        val accepting = synchronized(this) {
            running = false
            runCatching { serverSocket?.close() }
            serverSocket = null
            clients.forEach { runCatching { it.close() } }
            workers.shutdownNow()
            clients.clear()
            acceptThread.also { acceptThread = null }
        }
        // Outside the lock: the accept loop takes it to register a client.
        if (accepting != null && accepting !== Thread.currentThread()) {
            try {
                accepting.join(ACCEPT_EXIT_TIMEOUT_MILLIS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }

    private fun handle(client: Socket) {
        try {
            client.soTimeout = SOCKET_TIMEOUT_MILLIS
            val request = when (val read = readRequest(client.getInputStream())) {
                RequestRead.Incomplete -> return
                RequestRead.BadLength -> {
                    write(client, HttpResponse.badRequest())
                    return
                }
                is RequestRead.Complete -> read.request
            }

            val response = runCatching { onRequest(request) }
                .getOrElse {
                    Log.w(TAG, "Handler threw", it)
                    HttpResponse(500, "error")
                }

            write(client, response)
        } catch (t: Throwable) {
            Log.d(TAG, "Connection dropped", t)
        } finally {
            clients.remove(client)
            runCatching { client.close() }
        }
    }

    private fun write(client: Socket, response: HttpResponse) {
        val payload = response.body.toByteArray(Charsets.UTF_8)
        val head = buildString {
            append("HTTP/1.1 ").append(response.status).append(' ')
                .append(reason(response.status)).append("\r\n")
            append("Content-Type: ").append(response.contentType).append("\r\n")
            append("Content-Length: ").append(payload.size).append("\r\n")
            // No keep-alive: one request per connection keeps this simple and
            // lets the browser see the response land immediately.
            append("Connection: close\r\n")
            append("Cache-Control: no-store\r\n")
            append("\r\n")
        }

        BufferedOutputStream(client.getOutputStream()).apply {
            write(head.toByteArray(Charsets.US_ASCII))
            write(payload)
            flush()
        }
    }

    private fun reason(status: Int) = when (status) {
        200 -> "OK"
        400 -> "Bad Request"
        404 -> "Not Found"
        else -> "Internal Server Error"
    }

    private companion object {
        const val TAG = "MiniHttpServer"
        const val WORKER_THREADS = 2
        const val MAX_QUEUED_CONNECTIONS = 8
        const val SOCKET_TIMEOUT_MILLIS = 15_000
        /** stop() may run on the main thread (ViewModel.onCleared); the thread exits within milliseconds. */
        const val ACCEPT_EXIT_TIMEOUT_MILLIS = 500L
    }
}

/** One request read from a connection, or why the handler is not called. */
internal sealed interface RequestRead {
    data class Complete(val request: HttpRequest) : RequestRead
    /** Content-Length is not a number, negative or above the body limit: answered with 400. */
    data object BadLength : RequestRead
    /** No complete request head before the connection ended: dropped without an answer. */
    data object Incomplete : RequestRead
}

/**
 * Reads one HTTP/1.1 request -- a request line, headers and an optional
 * Content-Length body. Everything a device on the LAN sends passes through
 * here, so it touches nothing but [input] and is fuzzed on its own.
 *
 * @throws EOFException when the body ends before Content-Length bytes.
 */
internal fun readRequest(input: InputStream): RequestRead {
    val head = readHead(input) ?: return RequestRead.Incomplete
    val lines = head.split("\r\n")
    val requestLine = lines.firstOrNull()?.split(' ') ?: return RequestRead.Incomplete
    if (requestLine.size < 2) return RequestRead.Incomplete

    val method = requestLine[0].uppercase()
    val path = requestLine[1].substringBefore('?')

    val lengthHeader = lines
        .firstOrNull { it.startsWith("content-length:", ignoreCase = true) }
        ?.substringAfter(':')
        ?.trim()
    val contentLength = if (lengthHeader == null) 0 else lengthHeader.toIntOrNull()
    if (contentLength == null || contentLength !in 0..MAX_BODY_BYTES) return RequestRead.BadLength

    val body = if (contentLength > 0) readBody(input, contentLength) else ""
    return RequestRead.Complete(HttpRequest(method, path, body))
}

/** Reads bytes up to the blank line that ends the request head. */
private fun readHead(input: InputStream): String? {
    val buffer = ByteArrayOutputStream()
    var matched = 0
    while (buffer.size() < MAX_HEAD_BYTES) {
        val byte = input.read()
        if (byte == -1) return null
        buffer.write(byte)
        // Track the \r\n\r\n terminator without re-scanning the buffer.
        matched = when {
            byte == TERMINATOR[matched].code -> matched + 1
            byte == TERMINATOR[0].code -> 1
            else -> 0
        }
        if (matched == TERMINATOR.length) {
            return String(buffer.toByteArray(), Charsets.UTF_8)
        }
    }
    return null
}

private fun readBody(input: InputStream, length: Int): String {
    val bytes = ByteArray(length)
    var read = 0
    while (read < length) {
        // A single read() is not guaranteed to return everything; looping
        // here is what makes a large pasted token arrive intact.
        val count = input.read(bytes, read, length - read)
        if (count == -1) throw EOFException("Incomplete HTTP body")
        read += count
    }
    return String(bytes, 0, read, Charsets.UTF_8)
}

private const val TERMINATOR = "\r\n\r\n"
private const val MAX_HEAD_BYTES = 16 * 1024
internal const val MAX_BODY_BYTES = 256 * 1024
