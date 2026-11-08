package com.jarvisx.app.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.jarvisx.core.lang.ReplyLanguage
import com.jarvisx.core.voice.SpeechText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * Spoken replies with Android's text-to-speech, using OFFLINE voices only (voices that need a
 * network connection are never selected). Speaks sentence by sentence while the answer is still
 * being written, so speech starts quickly.
 */
class VoiceOutput(context: Context) {

    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

    @Volatile private var ready = false
    private val pending = AtomicInteger(0)
    private var pendingText = StringBuilder()
    private var currentLanguage = ReplyLanguage.ENGLISH
    /** How many characters of the cleaned answer have already been queued for speech. */
    private var spokenChars = 0

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            setLanguage(currentLanguage)
            if (pendingText.isNotEmpty()) { enqueue(pendingText.toString()); pendingText = StringBuilder() }
        }
    }.apply {
        setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { _speaking.value = true }
            override fun onDone(utteranceId: String?) { if (pending.decrementAndGet() <= 0) _speaking.value = false }
            @Deprecated("deprecated") override fun onError(utteranceId: String?) { if (pending.decrementAndGet() <= 0) _speaking.value = false }
            override fun onStop(utteranceId: String?, interrupted: Boolean) { pending.set(0); _speaking.value = false }
        })
        setSpeechRate(1.0f)
        setPitch(0.95f) // a slightly lower, calmer voice: JARVIS X's own identity
    }

    /** Hindi answers use a Hindi voice; English and Hinglish use Indian English, which reads Roman Hindi well. */
    private fun localeFor(language: ReplyLanguage): Locale =
        if (language == ReplyLanguage.HINDI) Locale("hi", "IN") else Locale("en", "IN")

    private fun setLanguage(language: ReplyLanguage) {
        currentLanguage = language
        if (!ready) return
        val locale = localeFor(language)
        val offline: Voice? = runCatching {
            tts.voices?.filter { it.locale.language == locale.language && !it.isNetworkConnectionRequired }
                ?.sortedWith(compareByDescending<Voice> { it.locale.country == locale.country }.thenByDescending { it.quality })
                ?.firstOrNull()
        }.getOrNull()
        if (offline != null) tts.voice = offline else tts.language = locale
    }

    /** True if an offline voice exists for [language]. */
    fun hasOfflineVoice(language: ReplyLanguage): Boolean = ready && runCatching {
        val l = localeFor(language)
        tts.voices?.any { it.locale.language == l.language && !it.isNetworkConnectionRequired } == true
    }.getOrDefault(false)

    /** Starts a new spoken answer. */
    fun begin(language: ReplyLanguage) {
        stop()
        spokenChars = 0
        setLanguage(language)
    }

    /** Feed the answer as it streams; finished sentences are spoken immediately. */
    fun feed(fullTextSoFar: String) {
        val clean = SpeechText.clean(fullTextSoFar)
        if (clean.length <= spokenChars) return
        val unseen = clean.substring(spokenChars)
        val (done, rest) = SpeechText.takeSentences(unseen)
        if (done.isEmpty()) return
        done.forEach { enqueue(it) }
        spokenChars = clean.length - rest.length
    }

    /** Speaks whatever is left when the answer is complete. */
    fun finish(fullText: String) {
        val clean = SpeechText.clean(fullText)
        if (clean.length > spokenChars) enqueue(clean.substring(spokenChars).trim())
        spokenChars = clean.length
    }

    fun speak(text: String, language: ReplyLanguage) {
        begin(language)
        finish(text)
    }

    private fun enqueue(sentence: String) {
        if (sentence.isBlank()) return
        if (!ready) { pendingText.append(' ').append(sentence); return }
        pending.incrementAndGet()
        _speaking.value = true
        tts.speak(sentence, TextToSpeech.QUEUE_ADD, Bundle(), "jx-${System.nanoTime()}")
    }

    fun stop() {
        pendingText = StringBuilder()
        pending.set(0)
        if (ready) tts.stop()
        _speaking.value = false
    }

    fun shutdown() = tts.shutdown()
}
