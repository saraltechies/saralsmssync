package com.saraltechies.saralsmssync.sync

import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Passes encrypted text between phones through a public ntfy server, which keeps it for a few hours in
 * case a phone is offline. The server only ever sees the encrypted text and a random topic.
 */
object Relay {
    private const val SERVER = "https://ntfy.sh"

    /** Sends one encrypted message. Throws IOException if it wasn't accepted, so it can be tried again. */
    fun publish(topic: String, cipherText: String) {
        val connection = URL("$SERVER/$topic").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "text/plain")
            connection.outputStream.use { it.write(cipherText.toByteArray()) }
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("Relay answered $code")
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Listens until the connection drops, calling [onEvent] with each message's relay id and encrypted
     * text. [since] is a relay id or a Unix time. [onConnection] gets the connection so it can be closed
     * from another thread, and [onOpen] runs once the relay has answered.
     */
    fun listen(
        topic: String,
        since: String,
        onConnection: (HttpURLConnection) -> Unit,
        onOpen: () -> Unit,
        onEvent: (id: String, cipherText: String) -> Unit,
    ) {
        val connection = URL("$SERVER/$topic/json?since=$since").openConnection() as HttpURLConnection
        onConnection(connection)
        try {
            connection.connectTimeout = 15_000
            // The relay sends a keepalive at least every minute or so; silence longer than this is a dead link.
            connection.readTimeout = 120_000
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("Relay answered $code")
            onOpen()
            connection.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line -> parse(line)?.let { (id, text) -> onEvent(id, text) } }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(line: String): Pair<String, String>? = try {
        val json = JSONObject(line)
        if (json.optString("event") != "message") null
        else json.getString("id") to json.getString("message")
    } catch (_: JSONException) {
        null
    }
}
