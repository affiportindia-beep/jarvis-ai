package com.jarvisx.app.research

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.jarvisx.core.lang.ReplyLanguage
import com.jarvisx.core.research.GoogleNewsRss
import com.jarvisx.core.research.ResearchPolicy
import com.jarvisx.core.research.SearchResult
import com.jarvisx.core.research.WikipediaSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * OPTIONAL online search. One of only two classes allowed to use the network
 * (the other is ModelDownloader); scripts/check_no_cloud.sh enforces this.
 *
 * Contacts only [ResearchPolicy.ALLOWED_HOSTS] over HTTPS, sends only the search words, and
 * never sends conversation history. No AI service and no API key are involved.
 */
class WebSearch(private val context: Context) {

    class SearchException(message: String) : IOException(message)

    fun isOnline(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /** News headlines + Wikipedia pages, de-duplicated, at most [limit]. */
    suspend fun search(query: String, language: ReplyLanguage, limit: Int = 6): List<SearchResult> = coroutineScope {
        if (!isOnline()) throw SearchException("No internet connection.")
        val news = async(Dispatchers.IO) { runCatching { GoogleNewsRss.parse(get(ResearchPolicy.googleNewsUrl(query, language))) } }
        val wikiUrl = ResearchPolicy.wikipediaSearchUrl(query, language)
        val wiki = async(Dispatchers.IO) {
            runCatching { WikipediaSearch.parse(get(wikiUrl), ResearchPolicy.hostOf(wikiUrl) ?: "en.wikipedia.org") }
        }
        val n = news.await()
        val w = wiki.await()
        if (n.isFailure && w.isFailure) {
            throw SearchException("Search failed: ${n.exceptionOrNull()?.message ?: w.exceptionOrNull()?.message}")
        }
        // Mostly news (current), plus up to 2 Wikipedia pages for background.
        val merged = (n.getOrDefault(emptyList()).take(limit - 2) + w.getOrDefault(emptyList()).take(2))
        merged.distinctBy { it.title.lowercase() }.take(limit)
    }

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        require(ResearchPolicy.isAllowed(url)) { "Blocked: $url is not an allowed search source" }
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            instanceFollowRedirects = false // a redirect could lead outside the allowed hosts
            setRequestProperty("User-Agent", "JarvisX/0.2 (Android; offline-first assistant; optional search)")
            setRequestProperty("Accept-Language", "en-IN,hi;q=0.8")
        }
        try {
            val code = conn.responseCode
            if (code != 200) throw SearchException("${ResearchPolicy.hostOf(url)} answered HTTP $code")
            conn.inputStream.use { input ->
                val bytes = input.readNBytesCompat(MAX_BYTES)
                String(bytes, Charsets.UTF_8)
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun java.io.InputStream.readNBytesCompat(max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (out.size() < max) {
            val n = read(buf, 0, minOf(buf.size, max - out.size()))
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    companion object {
        private const val MAX_BYTES = 1_000_000
    }
}
