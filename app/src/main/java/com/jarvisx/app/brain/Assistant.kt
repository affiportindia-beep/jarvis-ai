package com.jarvisx.app.brain

import com.arm.aichat.GenerationMetrics
import com.jarvisx.core.lang.ReplyLanguage
import com.jarvisx.core.router.Route
import com.jarvisx.core.router.Router
import com.jarvisx.core.tools.ToolExecutor
import com.jarvisx.core.tools.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

/** What the chat screen receives while an answer is produced. */
sealed class AssistantEvent {
    data class ToolAnswer(val text: String, val toolId: String) : AssistantEvent()
    data class ModelStarted(val modelName: String) : AssistantEvent()
    data class Token(val piece: String) : AssistantEvent()
    data class Notice(val text: String) : AssistantEvent()
    data class Done(val metrics: GenerationMetrics?, val source: String) : AssistantEvent()
    data class Error(val message: String) : AssistantEvent()
}

/**
 * The orchestrator: router first, deterministic tools second, local model last.
 * Nothing here can reach the network; the only brain is [LocalBrain].
 */
class Assistant(private val router: Router, private val brain: LocalBrain) {

    fun respond(text: String): Flow<AssistantEvent> = flow {
        when (val route = router.route(text)) {
            is Route.Ignore -> return@flow
            is Route.UseTool -> {
                val result = withContext(Dispatchers.Default) { ToolExecutor.run(route.tool, route.request) }
                when (result) {
                    is ToolResult.Answer -> emit(AssistantEvent.ToolAnswer(result.text, result.toolId))
                    is ToolResult.Failure -> emit(AssistantEvent.Error(result.reason))
                }
                emit(AssistantEvent.Done(null, route.tool.id))
            }
            is Route.UseModel -> {
                when (val outcome = brain.ensureLoaded()) {
                    is LoadOutcome.Failure -> {
                        emit(AssistantEvent.Error(outcome.message))
                        return@flow
                    }
                    is LoadOutcome.Success -> {
                        outcome.note?.let { emit(AssistantEvent.Notice(it)) }
                        if (brain.contextNearlyFull()) {
                            brain.newConversation()
                            emit(AssistantEvent.Notice(contextResetNotice(route.language)))
                        }
                        emit(AssistantEvent.ModelStarted(outcome.model.displayName))
                        brain.generate(route.prompt).collect { emit(AssistantEvent.Token(it)) }
                        emit(AssistantEvent.Done(brain.lastMetrics, outcome.model.displayName))
                    }
                }
            }
        }
    }

    private fun contextResetNotice(language: ReplyLanguage) = when (language) {
        ReplyLanguage.ENGLISH -> "The model's short-term memory was full, so it started a fresh context. Earlier messages stay on screen but the model no longer sees them."
        ReplyLanguage.HINGLISH -> "Model ki short-term memory full ho gayi thi, isliye naya context shuru kiya. Purane messages screen par hain, par model unhe ab nahi dekh raha."
        ReplyLanguage.HINDI -> "मॉडल की अल्पकालिक मेमोरी भर गई थी, इसलिए नया संदर्भ शुरू किया गया। पुराने संदेश स्क्रीन पर हैं, पर मॉडल उन्हें अब नहीं देखता।"
    }
}
