package com.jarvisx.app.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jarvisx.app.JarvisApp
import com.jarvisx.app.brain.AssistantEvent
import com.jarvisx.app.brain.BrainStatus
import kotlinx.coroutines.Job
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
)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val busy: Boolean = false,
    val brain: BrainStatus = BrainStatus.NoModel,
)

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = (app as JarvisApp).graph
    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()
    private var job: Job? = null
    private var nextId = 1L

    init {
        viewModelScope.launch { graph.brain.status.collect { s -> _state.update { it.copy(brain = s) } } }
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _state.value.busy) return
        add(ChatMessage(nextId++, Role.USER, trimmed))
        val answerId = nextId++
        _state.update { it.copy(busy = true) }
        job = viewModelScope.launch {
            val buffer = StringBuilder()
            var source: String? = null
            try {
                graph.assistant.respond(trimmed).collect { e ->
                    when (e) {
                        is AssistantEvent.ToolAnswer -> {
                            source = toolLabel(e.toolId)
                            upsert(ChatMessage(answerId, Role.ASSISTANT, e.text, source = source))
                        }
                        is AssistantEvent.ModelStarted -> {
                            source = e.modelName
                            upsert(ChatMessage(answerId, Role.ASSISTANT, "", source = source, streaming = true))
                        }
                        is AssistantEvent.Token -> {
                            buffer.append(e.piece)
                            upsert(ChatMessage(answerId, Role.ASSISTANT, buffer.toString(), source = source, streaming = true))
                        }
                        is AssistantEvent.Notice -> add(ChatMessage(nextId++, Role.NOTICE, e.text))
                        is AssistantEvent.Error -> add(ChatMessage(nextId++, Role.NOTICE, e.message, isError = true))
                        is AssistantEvent.Done -> {
                            val m = e.metrics
                            val stats = m?.let {
                                val stopped = if (it.stoppedByUser) " · stopped" else ""
                                "%.1f tok/s · %d tokens · first token %.1f s%s".format(
                                    it.tokensPerSecond, it.generatedTokens, it.firstTokenMs / 1000.0, stopped,
                                )
                            }
                            if (m != null) {
                                upsert(ChatMessage(answerId, Role.ASSISTANT, buffer.toString().ifBlank { "(no answer)" }, source = source, stats = stats))
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                add(ChatMessage(nextId++, Role.NOTICE, "Something went wrong: ${e.message ?: e.javaClass.simpleName}", isError = true))
            } finally {
                // If generation ended without Done (stop/cancel), make sure nothing stays "streaming".
                _state.update { st ->
                    st.copy(busy = false, messages = st.messages.map { if (it.streaming) it.copy(streaming = false) else it })
                }
            }
        }
    }

    fun stop() = graph.brain.stop()

    fun newChat() {
        if (_state.value.busy) return
        _state.update { it.copy(messages = emptyList()) }
        viewModelScope.launch { runCatching { graph.brain.newConversation() } }
    }

    private fun toolLabel(id: String) = when (id) {
        "calculator" -> "Calculator"
        "datetime" -> "Phone clock"
        else -> id
    }

    private fun add(m: ChatMessage) = _state.update { it.copy(messages = it.messages + m) }

    private fun upsert(m: ChatMessage) = _state.update { st ->
        val idx = st.messages.indexOfFirst { it.id == m.id }
        st.copy(messages = if (idx < 0) st.messages + m else st.messages.toMutableList().also { it[idx] = m })
    }
}
