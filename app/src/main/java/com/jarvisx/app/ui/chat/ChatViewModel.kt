package com.jarvisx.app.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jarvisx.app.JarvisApp
import com.jarvisx.app.brain.BrainStatus
import com.jarvisx.app.brain.ChatMessage
import com.jarvisx.app.voice.ListenState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val busy: Boolean = false,
    val brain: BrainStatus = BrainStatus.NoModel,
    val onlineSearch: Boolean = false,
    val thinkHarder: Boolean = false,
    val showOnlineExplainer: Boolean = false,
    val listen: ListenState = ListenState.Idle,
    val voiceLanguage: String = "hi-IN",
    val speaking: Boolean = false,
    val wakeWord: Boolean = false,
    val showWakeExplainer: Boolean = false,
)

/** UI state for the chat screen; the conversation itself lives in [com.jarvisx.app.brain.ChatSession]. */
class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = (app as JarvisApp).graph
    private val session = graph.session
    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { session.messages.collect { m -> _state.update { it.copy(messages = m) } } }
        viewModelScope.launch { session.busy.collect { b -> _state.update { it.copy(busy = b) } } }
        viewModelScope.launch { graph.brain.status.collect { s -> _state.update { it.copy(brain = s) } } }
        viewModelScope.launch { graph.settings.onlineSearch.collect { on -> _state.update { it.copy(onlineSearch = on) } } }
        viewModelScope.launch { graph.settings.thinkHarder.collect { on -> _state.update { it.copy(thinkHarder = on) } } }
        viewModelScope.launch { graph.voiceInput.state.collect { l -> _state.update { it.copy(listen = l) } } }
        viewModelScope.launch { graph.settings.voiceLanguage.collect { v -> _state.update { it.copy(voiceLanguage = v) } } }
        viewModelScope.launch { graph.voiceOutput.speaking.collect { sp -> _state.update { it.copy(speaking = sp) } } }
        viewModelScope.launch { graph.settings.wakeWord.collect { w -> _state.update { it.copy(wakeWord = w) } } }
    }

    fun send(text: String) { session.send(text) }
    fun stop() = session.stop()
    fun newChat() = session.newChat()

    // ---- voice ----
    fun startListening() {
        if (_state.value.busy) return
        graph.voiceOutput.stop()
        graph.voiceInput.start(_state.value.voiceLanguage) { text -> session.send(text, viaVoice = true) }
    }

    fun stopListening() = graph.voiceInput.stop()
    fun stopSpeaking() = graph.voiceOutput.stop()
    fun dismissVoiceMessage() = graph.voiceInput.clearMessage()
    fun downloadSpeechPack() = graph.voiceInput.downloadLanguagePack(_state.value.voiceLanguage)
    fun toggleVoiceLanguage() =
        graph.settings.setVoiceLanguage(if (_state.value.voiceLanguage == "hi-IN") "en-IN" else "hi-IN")

    /** "Hey Jarvis": first switch-on explains battery use and the notification. */
    fun toggleWake() {
        if (_state.value.wakeWord) {
            graph.settings.setWakeWord(false)
            com.jarvisx.app.voice.WakeWordService.stop(getApplication())
        } else {
            _state.update { it.copy(showWakeExplainer = true) }
        }
    }

    fun confirmWake(enable: Boolean) {
        _state.update { it.copy(showWakeExplainer = false) }
        if (enable) {
            graph.settings.setWakeWord(true)
            com.jarvisx.app.voice.WakeWordService.start(getApplication())
        }
    }

    fun toggleThink() = graph.settings.setThinkHarder(!_state.value.thinkHarder)

    /** First switch-on shows what leaves the phone; the user confirms before it is enabled. */
    fun toggleOnline() {
        val on = _state.value.onlineSearch
        if (!on && !graph.settings.onlineSearchExplained) {
            _state.update { it.copy(showOnlineExplainer = true) }
        } else {
            graph.settings.setOnlineSearch(!on)
        }
    }

    fun confirmOnline(enable: Boolean) {
        _state.update { it.copy(showOnlineExplainer = false) }
        if (enable) {
            graph.settings.onlineSearchExplained = true
            graph.settings.setOnlineSearch(true)
        }
    }
}
