package com.jarvisx.app

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** User settings, stored only on this phone. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _onlineSearch = MutableStateFlow(prefs.getBoolean(KEY_ONLINE, false))
    /** Optional online search. OFF by default; JARVIS X is fully usable without it. */
    val onlineSearch: StateFlow<Boolean> = _onlineSearch.asStateFlow()

    fun setOnlineSearch(on: Boolean) {
        prefs.edit().putBoolean(KEY_ONLINE, on).apply()
        _onlineSearch.value = on
    }

    private val _thinkHarder = MutableStateFlow(prefs.getBoolean(KEY_THINK, false))
    /** Step-by-step reasoning before answering: better on tricky questions, 2-4x slower. */
    val thinkHarder: StateFlow<Boolean> = _thinkHarder.asStateFlow()

    fun setThinkHarder(on: Boolean) {
        prefs.edit().putBoolean(KEY_THINK, on).apply()
        _thinkHarder.value = on
    }

    private val _voiceLanguage = MutableStateFlow(prefs.getString(KEY_VOICE_LANG, "hi-IN") ?: "hi-IN")
    /** Language the offline recogniser listens for: "hi-IN" (Hindi/Hinglish) or "en-IN". */
    val voiceLanguage: StateFlow<String> = _voiceLanguage.asStateFlow()

    fun setVoiceLanguage(tag: String) {
        prefs.edit().putString(KEY_VOICE_LANG, tag).apply()
        _voiceLanguage.value = tag
    }

    private val _wakeWord = MutableStateFlow(prefs.getBoolean(KEY_WAKE, false))
    /** "Hey Jarvis" listening in a visible foreground service. OFF by default (uses battery). */
    val wakeWord: StateFlow<Boolean> = _wakeWord.asStateFlow()

    fun setWakeWord(on: Boolean) {
        prefs.edit().putBoolean(KEY_WAKE, on).apply()
        _wakeWord.value = on
    }

    var onlineSearchExplained: Boolean
        get() = prefs.getBoolean(KEY_EXPLAINED, false)
        set(v) { prefs.edit().putBoolean(KEY_EXPLAINED, v).apply() }

    private companion object {
        const val KEY_ONLINE = "online_search"
        const val KEY_EXPLAINED = "online_search_explained"
        const val KEY_THINK = "think_harder"
        const val KEY_VOICE_LANG = "voice_language"
        const val KEY_WAKE = "wake_word"
    }
}
