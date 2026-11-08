package com.jarvisx.app.brain

import com.jarvisx.app.AppGraph
import com.jarvisx.core.lang.LanguageDetector
import com.jarvisx.core.research.SearchResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Role { USER, ASSISTANT, NOTICE }

data class ChatMessage(
    val id: Long,
    val role: Role,
    val text: String,
    /** Where the answer came from: a tool id or a model name. */
    val source: String? = null,
    val stats: String? = null,
    val isError: Boolean = false,
    val streaming: Boolean = false,
    val sources: List<SearchResult> = emptyList(),
)

/**
 * The current conversation, shared by the chat screen and the "Hey Jarvis" service, so a
 * question asked hands-free appears in the chat. Lives as long as the app process.
 */
class ChatSession(private val graph: AppGraph) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private var job: Job? = null
    private var nextId = 1L

    /**
     * Sends one user message. [viaVoice] answers are also spoken. [onFinished] runs when the
     * answer is complete (the wake-word service uses it to resume listening).
     */
    fun send(text: String, viaVoice: Boolean = false, onFinished: (() -> Unit)? = null): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _busy.value) return false
        add(ChatMessage(nextId++, Role.USER, trimmed))
        val answerId = nextId++
        _busy.value = true
        val speak = viaVoice
        if (speak) graph.voiceOutput.begin(LanguageDetector.detect(trimmed))
        job = scope.launch {
            val buffer = StringBuilder()
            var source: String? = null
            var sources: List<SearchResult> = emptyList()
            try {
                graph.assistant.respond(trimmed).collect { e ->
                    when (e) {
                        is AssistantEvent.ToolAnswer -> {
                            if (speak) graph.voiceOutput.finish(e.text)
                            source = toolLabel(e.toolId)
                            upsert(ChatMessage(answerId, Role.ASSISTANT, e.text, source = source))
                        }
                        is AssistantEvent.Searching -> {
                            source = "Searching the web for \"${e.query}\"…"
                            upsert(ChatMessage(answerId, Role.ASSISTANT, "", source = source, streaming = true))
                        }
                        is AssistantEvent.Sources -> sources = e.results
                        AssistantEvent.Restart -> {
                            buffer.clear()
                            if (speak) graph.voiceOutput.begin(LanguageDetector.detect(trimmed))
                            upsert(ChatMessage(answerId, Role.ASSISTANT, "", source = source, streaming = true, sources = sources))
                        }
                        is AssistantEvent.ModelStarted -> {
                            source = e.modelName
                            upsert(ChatMessage(answerId, Role.ASSISTANT, "", source = source, streaming = true, sources = sources))
                        }
                        is AssistantEvent.Token -> {
                            buffer.append(e.piece)
                            if (speak) graph.voiceOutput.feed(buffer.toString())
                            upsert(ChatMessage(answerId, Role.ASSISTANT, buffer.toString(), source = source, streaming = true, sources = sources))
                        }
                        is AssistantEvent.Notice -> add(ChatMessage(nextId++, Role.NOTICE, e.text))
                        is AssistantEvent.Error -> {
                            add(ChatMessage(nextId++, Role.NOTICE, e.message, isError = true))
                            if (speak) graph.voiceOutput.finish(e.message)
                        }
                        is AssistantEvent.Done -> {
                            val m = e.metrics
                            val stats = m?.let {
                                val stopped = if (it.stoppedByUser) " · stopped" else ""
                                "%.1f tok/s · %d tokens · first token %.1f s%s".format(
                                    it.tokensPerSecond, it.generatedTokens, it.firstTokenMs / 1000.0, stopped,
                                )
                            }
                            if (speak && m != null) graph.voiceOutput.finish(buffer.toString())
                            if (m != null) {
                                upsert(ChatMessage(answerId, Role.ASSISTANT, buffer.toString().ifBlank { "(no answer)" }, source = source, stats = stats, sources = sources))
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                add(ChatMessage(nextId++, Role.NOTICE, "Something went wrong: ${e.message ?: e.javaClass.simpleName}", isError = true))
            } finally {
                // If generation ended without Done (stop/cancel), make sure nothing stays "streaming".
                _messages.update { list -> list.map { if (it.streaming) it.copy(streaming = false) else it } }
                _busy.value = false
                onFinished?.invoke()
            }
        }
        return true
    }

    fun stop() {
        graph.brain.stop()
        graph.voiceOutput.stop()
    }

    fun newChat() {
        if (_busy.value) return
        _messages.value = emptyList()
        scope.launch { runCatching { graph.brain.newConversation() } }
    }

    private fun toolLabel(id: String) = when (id) {
        "calculator" -> "Calculator"
        "datetime" -> "Phone clock"
        else -> id
    }

    private fun add(m: ChatMessage) = _messages.update { it + m }

    private fun upsert(m: ChatMessage) = _messages.update { list ->
        val idx = list.indexOfFirst { it.id == m.id }
        if (idx < 0) list + m else list.toMutableList().also { it[idx] = m }
    }
}
