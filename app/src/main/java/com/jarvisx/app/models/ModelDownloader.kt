package com.jarvisx.app.models

import com.jarvisx.core.model.ModelSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/**
 * THE ONLY CLASS IN JARVIS X THAT OPENS A NETWORK CONNECTION.
 * CI (scripts/check_no_cloud.sh) fails the build if any other source file does.
 *
 * Downloads a model file over HTTPS, resumes partial downloads, and verifies SHA-256 before the
 * file is ever loaded. The expected hash is the pinned one from the catalog, or else the hash
 * Hugging Face publishes for the file (header X-Linked-Etag), read before downloading.
 */
class ModelDownloader(private val store: ModelStore) {

    sealed class Progress {
        object Checking : Progress()
        data class Downloading(val bytes: Long, val total: Long) : Progress()
        object Verifying : Progress()
    }

    class DownloadException(message: String) : IOException(message)

    private data class Remote(val sha256: String?, val size: Long)

    suspend fun download(spec: ModelSpec, onProgress: (Progress) -> Unit) = withContext(Dispatchers.IO) {
        onProgress(Progress.Checking)
        val remote = probe(spec.url)
        val pinned = spec.sha256
        val published = remote.sha256
        val expected = (pinned ?: published)?.lowercase()
            ?: throw DownloadException("The server did not publish a checksum for ${spec.fileName}, so it cannot be verified. Download refused.")
        if (pinned != null && published != null && pinned.lowercase() != published.lowercase()) {
            throw DownloadException("The file on the server has changed since this app version was built. Download refused for safety.")
        }

        val part = store.partFile(spec)
        var have = if (part.isFile) part.length() else 0L
        if (remote.size > 0 && have > remote.size) { part.delete(); have = 0 }
        val needed = (if (remote.size > 0) remote.size - have else spec.approxSizeMb * 1024L * 1024L)
        val freeBytes = store.dir.usableSpace
        if (freeBytes < needed + RESERVE_BYTES) {
            throw DownloadException(
                "Not enough storage: ${needed / MB} MB needed plus ${RESERVE_BYTES / MB} MB spare, ${freeBytes / MB} MB free."
            )
        }

        if (remote.size <= 0 || have < remote.size) {
            fetch(spec.url, part, have, remote.size, onProgress)
        }

        onProgress(Progress.Verifying)
        val actual = sha256(part)
        if (actual != expected) {
            part.delete()
            throw DownloadException("Checksum mismatch: the file is damaged or was altered. It has been deleted; please try again.")
        }
        val target = store.file(spec)
        target.delete()
        if (!part.renameTo(target)) throw DownloadException("Could not move the verified file into place.")
        store.hashFile(spec).writeText(actual)
    }

    /** Reads checksum and size without following the redirect (Hugging Face puts them on the 302). */
    private fun probe(url: String): Remote {
        val conn = open(url).apply {
            requestMethod = "HEAD"
            instanceFollowRedirects = false
        }
        try {
            val code = conn.responseCode
            if (code !in 200..399) throw DownloadException("Model server answered HTTP $code for ${URL(url).path}.")
            val etag = (conn.getHeaderField("X-Linked-Etag") ?: conn.getHeaderField("ETag"))
                ?.removePrefix("W/")?.trim('"', ' ')
            val sha = etag?.takeIf { Regex("^[0-9a-fA-F]{64}$").matches(it) }
            val size = (conn.getHeaderField("X-Linked-Size") ?: conn.getHeaderField("Content-Length"))?.toLongOrNull() ?: -1L
            return Remote(sha, size)
        } finally {
            conn.disconnect()
        }
    }

    private suspend fun fetch(url: String, part: File, startAt: Long, total: Long, onProgress: (Progress) -> Unit) {
        val conn = open(url).apply {
            instanceFollowRedirects = true
            if (startAt > 0) setRequestProperty("Range", "bytes=$startAt-")
        }
        try {
            val code = conn.responseCode
            val append = when (code) {
                HttpURLConnection.HTTP_PARTIAL -> true
                HttpURLConnection.HTTP_OK -> false
                416 -> return // already complete
                else -> throw DownloadException("Download failed: HTTP $code.")
            }
            var written = if (append) startAt else 0L
            val size = if (total > 0) total else conn.contentLengthLong.let { if (it > 0) written + it else -1L }
            conn.inputStream.use { input ->
                FileOutputStream(part, append).use { out ->
                    val buf = ByteArray(BUFFER)
                    var lastReport = 0L
                    while (true) {
                        coroutineContext.ensureActive() // Cancel button: stop promptly, keep .part for resume
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        written += n
                        if (written - lastReport >= REPORT_EVERY) {
                            lastReport = written
                            onProgress(Progress.Downloading(written, size))
                        }
                    }
                }
            }
            onProgress(Progress.Downloading(written, size))
            if (size > 0 && written < size) throw DownloadException("Connection closed early; tap Download again to resume.")
        } finally {
            conn.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection {
        require(url.startsWith("https://")) { "Only HTTPS downloads are allowed" }
        return (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            setRequestProperty("User-Agent", "JarvisX/0.1 (Android; model download)")
            setRequestProperty("Accept-Encoding", "identity")
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buf = ByteArray(BUFFER)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val MB = 1024L * 1024L
        private const val BUFFER = 1 shl 20
        private const val REPORT_EVERY = 4L * MB
        private const val RESERVE_BYTES = 300L * MB
    }
}
