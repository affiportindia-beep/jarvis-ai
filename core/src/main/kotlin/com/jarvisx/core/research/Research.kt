package com.jarvisx.core.research

import com.jarvisx.core.lang.ReplyLanguage
import java.net.URLEncoder
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One search hit shown to the model and, as a numbered source, to the user. */
data class SearchResult(
    val title: String,
    val url: String,
    val source: String,
    /** Publication date as shown to the user ("8 Nov 2026"), or null if unknown. */
    val date: String?,
    val snippet: String,
)

/**
 * Online search is OPTIONAL. It runs only when the user has switched it on, and only these
 * hosts are ever contacted. No AI service, no API key: the answer is still written by the
 * local model on the phone, from the results below.
 */
object ResearchPolicy {
    val ALLOWED_HOSTS = setOf("news.google.com", "en.wikipedia.org", "hi.wikipedia.org")

    private val currentWords = listOf(
        "latest", "recent", "recently", "today", "today's", "tonight", "yesterday", "this week",
        "this month", "this year", "current", "currently", "right now", "news", "headline",
        "price", "rate today", "score", "match", "result", "won", "winner", "election",
        "released", "release date", "launch", "launched", "episode", "season", "trailer",
        "box office", "weather", "stock", "share price", "update", "new version", "who is the ceo",
        "who is the president", "who is the prime minister", "2025", "2026", "2027",
        "search", "google", "online", "internet", "look up",
        "aaj", "abhi", "taaza", "khabar", "dhundo", "khojo", "naya", "nayi", "kal ka", "kal ki",
        "आज", "अभी", "ताज़ा", "ताजा", "खबर", "समाचार", "कीमत", "भाव", "नवीनतम", "हाल ही",
    )

    /** True if the question is about something that changes over time or asks to search. */
    fun needsWeb(text: String): Boolean {
        val t = " " + text.lowercase(Locale.ROOT) + " "
        if (t.trimStart().startsWith("/web")) return true
        return currentWords.any { w ->
            if (w.first().code > 0x7F) t.contains(w)
            else Regex("(?<![a-z])${Regex.escape(w)}(?![a-z])").containsMatchIn(t)
        }
    }

    /** Strips the optional "/web" prefix and filler so the search engine gets the topic. */
    fun searchQuery(text: String): String {
        var q = text.trim().removePrefix("/web").trim()
        q = q.replace(Regex("(?i)\\b(please|can you|could you|tell me|show me|search( for)?|google|look up|batao|bataiye|dhundo|khojo)\\b"), " ")
        q = q.replace(Regex("[?!।]"), " ").replace(Regex("\\s+"), " ").trim()
        return q.take(200)
    }

    fun googleNewsUrl(query: String, language: ReplyLanguage): String {
        val q = URLEncoder.encode(query, "UTF-8")
        return if (language == ReplyLanguage.HINDI) "https://news.google.com/rss/search?q=$q&hl=hi&gl=IN&ceid=IN:hi"
        else "https://news.google.com/rss/search?q=$q&hl=en-IN&gl=IN&ceid=IN:en"
    }

    fun wikipediaSearchUrl(query: String, language: ReplyLanguage): String {
        val host = if (language == ReplyLanguage.HINDI) "hi.wikipedia.org" else "en.wikipedia.org"
        return "https://$host/w/rest.php/v1/search/page?q=${URLEncoder.encode(query, "UTF-8")}&limit=3"
    }

    fun hostOf(url: String): String? = Regex("^https://([^/:?#]+)").find(url)?.groupValues?.get(1)?.lowercase(Locale.ROOT)

    fun isAllowed(url: String): Boolean = hostOf(url) in ALLOWED_HOSTS
}

/** Parses Google News RSS (an XML feed of headlines; no key needed). */
object GoogleNewsRss {
    private val rfc1123 = DateTimeFormatter.RFC_1123_DATE_TIME
    private val shown = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

    fun parse(xml: String, limit: Int = 6): List<SearchResult> {
        val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply {
            // Untrusted input: no DTDs, no external entities.
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            isExpandEntityReferences = false
        }
        val doc = factory.newDocumentBuilder().parse(org.xml.sax.InputSource(java.io.StringReader(xml)))
        val items = doc.getElementsByTagName("item")
        val out = mutableListOf<SearchResult>()
        for (i in 0 until items.length) {
            val item = items.item(i) as org.w3c.dom.Element
            fun text(tag: String) = item.getElementsByTagName(tag).item(0)?.textContent?.trim().orEmpty()
            val source = text("source")
            var title = text("title")
            if (source.isNotEmpty() && title.endsWith(" - $source")) title = title.removeSuffix(" - $source")
            val date = runCatching { ZonedDateTime.parse(text("pubDate"), rfc1123).format(shown) }.getOrNull()
            val link = text("link")
            if (title.isNotEmpty() && link.startsWith("https://")) {
                out += SearchResult(title = title, url = link, source = source.ifEmpty { "Google News" }, date = date, snippet = "")
            }
            if (out.size >= limit) break
        }
        return out
    }
}

/** Parses the Wikipedia REST search response (JSON). */
object WikipediaSearch {
    fun parse(json: String, host: String, limit: Int = 3): List<SearchResult> {
        val pages = org.json.JSONObject(json).optJSONArray("pages") ?: return emptyList()
        val out = mutableListOf<SearchResult>()
        for (i in 0 until minOf(pages.length(), limit)) {
            val p = pages.getJSONObject(i)
            val title = p.optString("title")
            val key = p.optString("key").ifEmpty { title.replace(' ', '_') }
            val excerpt = p.optString("excerpt").replace(Regex("<[^>]+>"), "").replace("&quot;", "\"").replace("&amp;", "&")
            val description = if (p.isNull("description")) "" else p.optString("description")
            if (title.isEmpty()) continue
            out += SearchResult(
                title = title,
                url = "https://$host/wiki/" + java.net.URLEncoder.encode(key, "UTF-8").replace("+", "_"),
                source = "Wikipedia",
                date = null,
                snippet = listOf(description, excerpt).filter { it.isNotBlank() }.joinToString(" — ").take(300),
            )
        }
        return out
    }
}

/** Builds the user turn that makes the local model answer only from the search results. */
object WebPrompt {
    fun build(question: String, results: List<SearchResult>, language: ReplyLanguage, today: LocalDate): String = buildString {
        val d = today.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH))
        appendLine("Web search results (searched $d; headlines and short extracts only, may be incomplete):")
        results.forEachIndexed { i, r ->
            append("[${i + 1}] ${r.title} — ${r.source}")
            r.date?.let { append(", $it") }
            appendLine()
            if (r.snippet.isNotBlank()) appendLine("    ${r.snippet}")
        }
        appendLine()
        appendLine("Question: $question")
        appendLine()
        append("Answer using only these results. Cite the sources you use like [1] or [2]. ")
        append("If the results do not contain the answer, say clearly that the search did not find it; do not guess. ")
        append(language.instruction)
    }
}
