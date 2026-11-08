package com.jarvisx.app.models

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.jarvisx.core.model.ModelCatalog
import com.jarvisx.core.model.ModelSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/**
 * Installs a model file the user already has on the phone (for example in Downloads), so a
 * reinstall never forces a second multi-gigabyte download. No network is used.
 *
 * The file is accepted only if its SHA-256 equals a pinned hash in [ModelCatalog]; anything
 * else is rejected and the partial copy deleted.
 */
class ModelImporter(private val context: Context, private val store: ModelStore) {

    data class Progress(val bytes: Long, val total: Long)

    suspend fun import(uri: Uri, onProgress: (Progress) -> Unit): ModelSpec = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val (name, size) = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { c ->
                if (c.moveToFirst()) (c.getString(0) ?: "") to (if (c.isNull(1)) -1L else c.getLong(1)) else "" to -1L
            } ?: ("" to -1L)

        // Pick the expected model by file name or exact size first, so we fail fast on wrong files.
        val candidates = ModelCatalog.all.filter { it.sha256 != null }
        val guess = candidates.firstOrNull { it.fileName.equals(name, ignoreCase = true) }
        if (size > 0 && candidates.none { size in (it.approxSizeMb - 2) * MB..(it.approxSizeMb + 2) * MB }) {
            throw IOException("This file (${size / MB} MB) does not match any JARVIS X model. Expected one of: " +
                candidates.joinToString { "${it.fileName} (${it.approxSizeMb} MB)" })
        }
        if (store.dir.usableSpace < size + 200 * MB) {
            throw IOException("Not enough storage to copy ${size / MB} MB.")
        }

        val tmp = java.io.File(store.dir, "import.part")
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tmp).use { out ->
                    val buf = ByteArray(1 shl 20)
                    var done = 0L
                    var last = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        done += n
                        if (done - last >= 16 * MB) { last = done; onProgress(Progress(done, size)) }
                    }
                    onProgress(Progress(done, size))
                }
            } ?: throw IOException("Could not open the selected file.")

            val sha = digest.digest().joinToString("") { "%02x".format(it) }
            val spec = candidates.firstOrNull { it.sha256.equals(sha, ignoreCase = true) }
                ?: throw IOException(
                    "Checksum does not match any JARVIS X model" +
                        (guess?.let { ", including ${it.displayName}. The file may be incomplete or damaged." } ?: ".")
                )
            val target = store.file(spec)
            target.delete()
            if (!tmp.renameTo(target)) throw IOException("Could not move the verified file into place.")
            store.hashFile(spec).writeText(sha)
            spec
        } finally {
            tmp.delete()
        }
    }

    companion object {
        private const val MB = 1024L * 1024L
    }
}
