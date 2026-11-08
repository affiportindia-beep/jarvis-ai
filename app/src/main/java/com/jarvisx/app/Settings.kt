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

    var onlineSearchExplained: Boolean
        get() = prefs.getBoolean(KEY_EXPLAINED, false)
        set(v) { prefs.edit().putBoolean(KEY_EXPLAINED, v).apply() }

    private companion object {
        const val KEY_ONLINE = "online_search"
        const val KEY_EXPLAINED = "online_search_explained"
        const val KEY_THINK = "think_harder"
    }
}
