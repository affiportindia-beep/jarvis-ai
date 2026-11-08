package com.jarvisx.app.brain

import com.arm.aichat.GenerationMetrics
import com.jarvisx.app.Settings
import com.jarvisx.app.research.WebSearch
import com.jarvisx.core.research.SearchResult
import com.jarvisx.core.research.WebPrompt
import com.jarvisx.core.lang.ReplyLanguage
import com.jarvisx.core.router.Route
import com.jarvisx.core.router.Router
import com.jarvisx.core.tools.ToolExecutor
import com.jarvisx.core.tools.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** What the chat screen receives while an answer is produced. */
sealed class AssistantEvent {
    data class ToolAnswer(val text: String, val toolId: String) : AssistantEvent()
    data class ModelStarted(val modelName: String) : AssistantEvent()
    data class Searching(val query: String) : AssistantEvent()
    data class Sources(val results: List<SearchResult>) : AssistantEvent()
    data class Token(val piece: String) : AssistantEvent()
    data class Notice(val text: String) : AssistantEvent()
    data class Done(val metrics: GenerationMetrics?, val source: String) : AssistantEvent()
    data class Error(val message: String) : AssistantEvent()
}

/**
 * The orchestrator: router first, deterministic tools second, local model last.
 * The only brain is [LocalBrain]. The network is used only for OPTIONAL search ([WebSearch]),
 * when the user switched it on; the results are read by the local model, never by a cloud AI.
 */
class Assistant(
    private val router: Router,
    private val brain: LocalBrain,
    private val web: WebSearch,
    private val settings: Settings,
) {

    fun respond(text: String): Flow<AssistantEvent> = flow {
        when (val route = router.route(text, online = settings.onlineSearch.value)) {
            is Route.Ignore -> return@flow
            is Route.UseTool -> {
                val result = withContext(Dispatchers.Default) { ToolExecutor.run(route.tool, route.request) }
                when (result) {
                    is ToolResult.Answer -> emit(AssistantEvent.ToolAnswer(result.text, result.toolId))
                    is ToolResult.Failure -> emit(AssistantEvent.Error(result.reason))
                }
                emit(AssistantEvent.Done(null, route.tool.id))
            }
            is Route.UseWeb -> {
                emit(AssistantEvent.Searching(route.query))
                val results = try {
                    web.search(route.query, route.language)
                } catch (e: Exception) {
                    emit(AssistantEvent.Notice(searchFailedNotice(route.language, e.message)))
                    null
                }
                val prompt = if (results.isNullOrEmpty()) {
                    if (results != null) emit(AssistantEvent.Notice(nothingFoundNotice(route.language)))
                    router.withLanguageHint(route.question, route.language)
                } else {
                    emit(AssistantEvent.Sources(results))
                    WebPrompt.build(route.question, results, route.language, LocalDate.now())
                }
                answerWithModel(prompt, route.language, if (results.isNullOrEmpty()) "" else " + web (${results.size} sources)")
            }
            is Route.UseModel -> answerWithModel(route.prompt, route.language, "")
        }
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<AssistantEvent>.answerWithModel(
        prompt: String,
        language: ReplyLanguage,
        sourceSuffix: String,
    ) {
        when (val outcome = brain.ensureLoaded()) {
            is LoadOutcome.Failure -> emit(AssistantEvent.Error(outcome.message))
            is LoadOutcome.Success -> {
                outcome.note?.let { emit(AssistantEvent.Notice(it)) }
                if (brain.contextNearlyFull(prompt.length / 3)) {
                    brain.newConversation()
                    emit(AssistantEvent.Notice(contextResetNotice(language)))
                }
                val think = settings.thinkHarder.value
                brain.setThinking(think)
                val name = outcome.model.displayName + sourceSuffix + if (think) " · thinking" else ""
                emit(AssistantEvent.ModelStarted(name))
                brain.generate(prompt, maxTokens = if (think) 2048 else 1024).collect { emit(AssistantEvent.Token(it)) }
                emit(AssistantEvent.Done(brain.lastMetrics, name))
            }
        }
    }

    private fun searchFailedNotice(language: ReplyLanguage, reason: String?) = when (language) {
        ReplyLanguage.ENGLISH -> "Online search failed (${reason ?: "unknown error"}). Answering from offline knowledge, which may be out of date."
        ReplyLanguage.HINGLISH -> "Online search nahi ho paya (${reason ?: "error"}). Offline knowledge se jawab de raha hoon, jo purana ho sakta hai."
        ReplyLanguage.HINDI -> "ऑनलाइन खोज नहीं हो पाई (${reason ?: "त्रुटि"})। ऑफ़लाइन जानकारी से जवाब दे रहा हूँ, जो पुरानी हो सकती है।"
    }

    private fun nothingFoundNotice(language: ReplyLanguage) = when (language) {
        ReplyLanguage.ENGLISH -> "The search found nothing. Answering from offline knowledge, which may be out of date."
        ReplyLanguage.HINGLISH -> "Search mein kuch nahi mila. Offline knowledge se jawab de raha hoon, jo purana ho sakta hai."
        ReplyLanguage.HINDI -> "खोज में कुछ नहीं मिला। ऑफ़लाइन जानकारी से जवाब दे रहा हूँ, जो पुरानी हो सकती है।"
    }

    private fun contextResetNotice(language: ReplyLanguage) = when (language) {
        ReplyLanguage.ENGLISH -> "The model's short-term memory was full, so it started a fresh context. Earlier messages stay on screen but the model no longer sees them."
        ReplyLanguage.HINGLISH -> "Model ki short-term memory full ho gayi thi, isliye naya context shuru kiya. Purane messages screen par hain, par model unhe ab nahi dekh raha."
        ReplyLanguage.HINDI -> "मॉडल की अल्पकालिक मेमोरी भर गई थी, इसलिए नया संदर्भ शुरू किया गया। पुराने संदेश स्क्रीन पर हैं, पर मॉडल उन्हें अब नहीं देखता।"
    }
}
