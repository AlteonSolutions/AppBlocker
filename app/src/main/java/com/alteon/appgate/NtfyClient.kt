package com.alteon.appgate

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Talks to ntfy over plain HTTPS with java.net, so remote control adds no dependency (the app has
 * none, by decision). Every call blocks: callers run it off the main thread.
 *
 * Every failure is an IOException the caller catches. Remote control is a secondary path; the PIN
 * still works with no network.
 */
object NtfyClient {

    /** ntfy sends a keepalive every 45 s; silence well past that means the connection is dead. */
    private const val STREAM_READ_TIMEOUT_MS = 90_000
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val POLL_READ_TIMEOUT_MS = 20_000

    fun publish(topic: String, body: String) {
        post("${RemoteProtocol.SERVER}/$topic", body, "text/plain; charset=utf-8")
    }

    /** ntfy's JSON publish endpoint, needed for notification action buttons. */
    fun publishJson(json: String) {
        post(RemoteProtocol.SERVER, json, "application/json")
    }

    /**
     * Holds a streaming subscription open, calling [onLine] for each JSON line, until the server
     * closes it, the read times out, or [keepGoing] returns false. [since] is a message id, a unix
     * time, or a duration like "10m".
     */
    fun stream(topic: String, since: String, keepGoing: () -> Boolean, onLine: (String) -> Unit) {
        val conn = open("${RemoteProtocol.SERVER}/$topic/json?since=${enc(since)}", STREAM_READ_TIMEOUT_MS)
        try {
            BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { reader ->
                while (keepGoing()) {
                    val line = reader.readLine() ?: break
                    if (line.isNotBlank()) onLine(line)
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    /** Messages cached on [topic] since [since], without waiting for new ones. */
    fun poll(topic: String, since: String): List<String> {
        val conn = open("${RemoteProtocol.SERVER}/$topic/json?poll=1&since=${enc(since)}", POLL_READ_TIMEOUT_MS)
        try {
            return BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { r ->
                r.readLines().filter { it.isNotBlank() }
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun post(url: String, body: String, contentType: String) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = POLL_READ_TIMEOUT_MS
            setRequestProperty("Content-Type", contentType)
        }
        try {
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code !in 200..299) throw java.io.IOException("ntfy answered HTTP $code")
        } finally {
            conn.disconnect()
        }
    }

    private fun open(url: String, readTimeoutMs: Int): HttpURLConnection {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = readTimeoutMs
        }
        val code = conn.responseCode
        if (code !in 200..299) {
            conn.disconnect()
            throw java.io.IOException("ntfy answered HTTP $code")
        }
        return conn
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}
