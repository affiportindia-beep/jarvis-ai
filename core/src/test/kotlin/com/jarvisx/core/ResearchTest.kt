package com.jarvisx.core

import com.jarvisx.core.lang.ReplyLanguage
import com.jarvisx.core.research.GoogleNewsRss
import com.jarvisx.core.research.ResearchPolicy
import com.jarvisx.core.research.WebPrompt
import com.jarvisx.core.research.WikipediaSearch
import com.jarvisx.core.router.Route
import com.jarvisx.core.router.Router
import com.jarvisx.core.tools.CalculatorTool
import com.jarvisx.core.tools.ToolRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class ResearchTest {
    private fun res(name: String) = javaClass.getResource("/research/$name")!!.readText()
    private val router = Router(ToolRegistry(listOf(CalculatorTool())))

    @Test fun currentQuestionsNeedTheWeb() {
        assertTrue(ResearchPolicy.needsWeb("show me in recent episode of kapil sharma show on netlix who are the guest"))
        assertTrue(ResearchPolicy.needsWeb("aaj ka gold rate kya hai"))
        assertTrue(ResearchPolicy.needsWeb("आज की ताज़ा खबर"))
        assertTrue(ResearchPolicy.needsWeb("/web Qwen 3.6 release"))
        assertFalse(ResearchPolicy.needsWeb("Explain photosynthesis"))
        assertFalse(ResearchPolicy.needsWeb("Write a Kotlin function to reverse a string"))
    }

    @Test fun onlineRoutingOnlyWhenSwitchedOn() {
        val q = "latest news about ISRO"
        assertTrue(router.route(q, online = false) is Route.UseModel)
        val r = router.route(q, online = true)
        assertTrue(r is Route.UseWeb)
        assertEquals("latest news about ISRO", (r as Route.UseWeb).query)
        // Tools still win: maths never goes to the web.
        assertTrue(router.route("aaj 25 into 4 kitna", online = true) is Route.UseTool)
        // General knowledge stays local even when online.
        assertTrue(router.route("Explain photosynthesis", online = true) is Route.UseModel)
    }

    @Test fun onlyAllowedHosts() {
        assertTrue(ResearchPolicy.isAllowed(ResearchPolicy.googleNewsUrl("x", ReplyLanguage.ENGLISH)))
        assertTrue(ResearchPolicy.isAllowed(ResearchPolicy.wikipediaSearchUrl("x", ReplyLanguage.HINDI)))
        assertFalse(ResearchPolicy.isAllowed("https://example.com/v1/chat"))
        assertFalse(ResearchPolicy.isAllowed("http://news.google.com/rss"))
        assertFalse(ResearchPolicy.isAllowed("https://news.google.com.evil.com/rss"))
    }

    @Test fun parsesGoogleNews() {
        val r = GoogleNewsRss.parse(res("google_news.xml"))
        assertEquals(2, r.size)
        assertEquals("Kapil Sharma show: Ranbir Kapoor and Alia Bhatt join this week", r[0].title)
        assertEquals("Hindustan Times", r[0].source)
        assertEquals("7 Nov 2026", r[0].date)
    }

    @Test fun rejectsXmlEntitiesAttack() {
        val evil = """<?xml version="1.0"?><!DOCTYPE r [<!ENTITY x SYSTEM "file:///etc/passwd">]><rss><channel><item><title>&x;</title><link>https://a</link></item></channel></rss>"""
        val ok = runCatching { GoogleNewsRss.parse(evil) }.getOrNull()
        assertTrue(ok == null || ok.none { it.title.contains("root:") })
    }

    @Test fun parsesWikipedia() {
        val r = WikipediaSearch.parse(res("wikipedia.json"), "en.wikipedia.org")
        assertEquals(2, r.size)
        assertEquals("https://en.wikipedia.org/wiki/The_Great_Indian_Kapil_Show", r[0].url)
        assertTrue(r[0].snippet.startsWith("Indian comedy talk show — The Great Indian Kapil Show"))
        assertFalse(r[0].snippet.contains("<span"))
    }

    @Test fun promptForcesCitationsAndHonesty() {
        val r = GoogleNewsRss.parse(res("google_news.xml"))
        val p = WebPrompt.build("Who were the guests?", r, ReplyLanguage.HINGLISH, LocalDate.of(2026, 11, 8))
        assertTrue(p.contains("[1] Kapil Sharma show"))
        assertTrue(p.contains("did not find it"))
        assertTrue(p.endsWith(ReplyLanguage.HINGLISH.instruction))
    }

    /** Parses REAL responses fetched by CI (skipped locally). Catches format changes early. */
    @Test fun liveResponsesStillParse() {
        val dir = System.getenv("RESEARCH_FIXTURE_DIR")?.let { File(it) }
        assumeTrue("set RESEARCH_FIXTURE_DIR to run", dir != null && dir.isDirectory)
        val news = GoogleNewsRss.parse(File(dir, "news.xml").readText())
        assertTrue("Google News returned no parsable items", news.isNotEmpty())
        val wiki = WikipediaSearch.parse(File(dir, "wiki.json").readText(), "en.wikipedia.org")
        assertTrue("Wikipedia returned no parsable pages", wiki.isNotEmpty())
        println("LIVE: ${news.size} news items, first: ${news.first().title} (${news.first().date}); wiki: ${wiki.first().title}")
    }
}
