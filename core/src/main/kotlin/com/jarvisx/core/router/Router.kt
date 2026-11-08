package com.jarvisx.core.router

import com.jarvisx.core.lang.HindiNumberWords
import com.jarvisx.core.lang.LanguageDetector
import com.jarvisx.core.lang.ReplyLanguage
import com.jarvisx.core.research.ResearchPolicy
import com.jarvisx.core.tools.Tool
import com.jarvisx.core.tools.ToolRegistry
import com.jarvisx.core.tools.ToolRequest

/** What the assistant should do with one message. */
sealed class Route {
    abstract val language: ReplyLanguage

    /** A deterministic tool can answer exactly. */
    data class UseTool(val tool: Tool, val request: ToolRequest) : Route() {
        override val language get() = request.language
    }

    /** Needs language understanding: send to the local model. [prompt] includes the language hint. */
    data class UseModel(val prompt: String, override val language: ReplyLanguage) : Route()

    /**
     * Online search is switched on and the question is about something current: search the
     * allowed sources, then let the local model answer from the results.
     */
    data class UseWeb(val question: String, val query: String, override val language: ReplyLanguage) : Route()

    /** Nothing to do (empty input). */
    data class Ignore(override val language: ReplyLanguage = ReplyLanguage.ENGLISH) : Route()
}

/**
 * Tools first, model second. The model is only used when no tool is confident, which keeps
 * answers that have one correct value (maths, the clock) exact and fast.
 */
class Router(private val registry: ToolRegistry) {

    /** @param online true only when the user has switched online search on. */
    fun route(rawText: String, online: Boolean = false): Route {
        val text = rawText.trim()
        if (text.isEmpty()) return Route.Ignore()
        val language = LanguageDetector.detect(text)
        val request = ToolRequest(text, language)
        registry.best(request)?.let { return Route.UseTool(it, request) }
        if (online && ResearchPolicy.needsWeb(text)) {
            val query = ResearchPolicy.searchQuery(text)
            if (query.isNotBlank()) return Route.UseWeb(text.removePrefix("/web").trim(), query, language)
        }
        return Route.UseModel(withLanguageHint(text.removePrefix("/web").trim(), language), language)
    }

    /**
     * Small models drift into English. A short, explicit hint at the end of the user turn keeps
     * the reply in the user's language and script without changing the system prompt (which
     * would force the whole context to be re-encoded).
     */
    fun withLanguageHint(text: String, language: ReplyLanguage): String =
        "${HindiNumberWords.annotate(text)}\n\n(${language.instruction})"
}
