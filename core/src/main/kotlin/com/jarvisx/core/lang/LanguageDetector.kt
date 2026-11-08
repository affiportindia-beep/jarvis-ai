package com.jarvisx.core.lang

/** The language and script JARVIS X should answer in. */
enum class ReplyLanguage(val label: String, val instruction: String) {
    ENGLISH("English", "Reply in English."),
    HINDI("Hindi", "Reply in Hindi using Devanagari script."),
    HINGLISH("Hinglish", "Reply in Hinglish: Hindi words written in Roman (English) letters, mixed with English as the user does."),
}

/**
 * Decides whether a message is English, Hindi (Devanagari) or Hinglish (Hindi in Roman letters).
 *
 * Rule-based on purpose: it is instant, needs no model and is easy to test. Hinglish is detected
 * from common Hindi function words that are not also English words ("kya", "hai", "nahi"...).
 * Ambiguous words such as "main", "to", "do", "hi" are deliberately not counted.
 */
object LanguageDetector {

    private val strongHinglish = setOf(
        "kya", "kyaa", "hai", "hain", "nahi", "nahin", "nhi", "mujhe", "mujhko", "kaise", "kaisa",
        "kaisi", "kyun", "kyon", "kyu", "aap", "aapko", "aapka", "aapki", "tum", "tumhe", "tumhara",
        "mera", "meri", "mere", "humein", "hamein", "batao", "bataiye", "bataye", "bata", "samjhao",
        "samjha", "samajh", "karo", "kariye", "karna", "karta", "karti", "karte", "raha", "rahi",
        "rahe", "kaun", "kab", "kahan", "kahaan", "kitna", "kitni", "kitne", "kuch", "kuchh",
        "accha", "acha", "achha", "haan", "haa", "chahiye", "sakta", "sakti", "sakte",
        "wala", "wali", "wale", "lekin", "magar", "aur", "bhi", "sirf", "abhi", "yaar", "bhai",
        "hoga", "hogi", "hota", "hoti", "tha", "thi", "kar", "dijiye", "chalo", "matlab", "isliye",
        "kyunki", "agar", "toh", "bahut", "zyada", "jaldi", "aaj", "kal", "ji", "kijiye", "batana",
        "hu", "hoon", "hun", "apna", "apni", "unka", "uska", "iska", "yeh", "ye", "woh", "wo",
        "ke", "liye", "kaunsi", "kaunsa", "konsa", "mein", "ko", "banane", "banao", "baare", "hamara",
    )

    fun detect(text: String): ReplyLanguage {
        var devanagari = 0
        var latin = 0
        for (ch in text) {
            when {
                ch in 'ऀ'..'ॿ' -> devanagari++
                ch.isLetter() && ch.code < 0x250 -> latin++
            }
        }
        val letters = devanagari + latin
        if (letters == 0) return ReplyLanguage.ENGLISH
        if (devanagari.toDouble() / letters >= 0.3) return ReplyLanguage.HINDI

        val words = text.lowercase()
            .split(Regex("[^a-z']+"))
            .filter { it.isNotEmpty() }
        if (words.isEmpty()) return ReplyLanguage.ENGLISH
        val hits = words.count { it in strongHinglish }
        return when {
            hits >= 2 -> ReplyLanguage.HINGLISH
            hits == 1 && words.size <= 5 -> ReplyLanguage.HINGLISH
            hits.toDouble() / words.size >= 0.2 -> ReplyLanguage.HINGLISH
            else -> ReplyLanguage.ENGLISH
        }
    }
}
