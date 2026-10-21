package com.blackcat.remote

import java.io.InputStream
import java.io.OutputStream
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.HttpsURLConnection

/** Independent of OpenAI and its credentials. TLS uses Android's normal certificate/hostname checks. */
internal class UpdateHttps(private val connect: (URL) -> HttpsURLConnection = { it.openConnection() as HttpsURLConnection }) {
    private val cancelled = AtomicBoolean(false)
    @Volatile private var active: HttpsURLConnection? = null
    fun cancel() { cancelled.set(true); active?.disconnect() }
    fun ensureActive() { check(!cancelled.get()) { "Update cancelled" } }

    fun fetch(url: String, hosts: Set<String>, max: Long, output: OutputStream): Long {
        require(max in 1..SignedUpdateMetadata.MAX_APK)
        val deadline = System.nanoTime() + 90_000_000_000L
        var current = UpdateUrls.validate(url, hosts)
        val visited = mutableSetOf<String>()
        repeat(3) { hop ->
            ensureActive()
            check(System.nanoTime() < deadline && visited.add(current.toString())) { "Update redirect loop or deadline" }
            val c = connect(current.toURL())
            active = c
            try {
                ensureActive()
                c.instanceFollowRedirects = false
                c.connectTimeout = 5000
                c.readTimeout = 10000
                c.useCaches = false
                c.setRequestProperty("Accept-Encoding", "identity")
                val status = c.responseCode
                if (status in setOf(301, 302, 303, 307, 308)) {
                    check(hop < 2) { "Too many update redirects" }
                    val location = c.getHeaderField("Location") ?: error("Missing redirect location")
                    check(location.length <= 2048)
                    current = UpdateUrls.validate(current.resolve(location).toString(), hosts)
                } else {
                    check(status == 200) { "Update server did not return a complete response" }
                    val encoding = c.getHeaderField("Content-Encoding")
                    check(encoding == null || encoding.equals("identity", true)) { "Encoded update response rejected" }
                    val length = c.getHeaderField("Content-Length")?.let {
                        check(it.matches(Regex("[0-9]{1,12}")))
                        it.toLong()
                    }
                    check(length == null || length in 1..max) { "Update response too large" }
                    val count = c.inputStream.use { copy(it, output, max, deadline) }
                    check(count > 0 && (length == null || count == length)) { "Incomplete update download" }
                    ensureActive()
                    return count
                }
            } finally { c.disconnect(); active = null }
        }
        error("Update redirects exhausted")
    }

    private fun copy(input: InputStream, output: OutputStream, max: Long, deadline: Long): Long {
        val buffer = ByteArray(16384)
        var count = 0L
        while (true) {
            ensureActive()
            check(System.nanoTime() < deadline) { "Update download deadline exceeded" }
            val n = input.read(buffer)
            if (n == -1) break
            count += n
            check(count <= max) { "Update response too large" }
            output.write(buffer, 0, n)
        }
        return count
    }
}
