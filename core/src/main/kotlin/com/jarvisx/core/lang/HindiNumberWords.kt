package com.jarvisx.core.lang

import java.text.Normalizer

/**
 * Hindi fraction words that small models misread (the eval showed every Qwen3.5 size reading
 * "ढाई" = 2.5 as 1.5 or 0.5). Before a question reaches the model, each such word gets its value
 * in brackets: "ढाई किलो" -> "ढाई (2.5) किलो". The user's words are kept; only a hint is added.
 */
object HindiNumberWords {

    private data class Word(val forms: List<String>, val value: String)

    private val words = listOf(
        Word(listOf("ढाई", "dhai", "dhaai", "dhaee"), "2.5"),
        Word(listOf("\u0921\u0947\u095D", "\u0921\u0947\u0922\u093C", "डेढ", "dedh", "derh", "dedhh"), "1.5"),
        Word(listOf("साढ़े तीन", "sadhe teen", "saadhe teen", "sade teen"), "3.5"),
        Word(listOf("साढ़े चार", "sadhe char", "saadhe chaar", "sade char"), "4.5"),
        Word(listOf("आधा", "आधी", "aadha", "adha", "aadhi", "adhi"), "0.5"),
        Word(listOf("पाव", "paav", "pav"), "0.25"),
        Word(listOf("पौना", "पौने", "pauna", "paune"), "0.75"),
    )

    // Devanagari letters and Latin letters both count as "part of a word".
    private const val L = """[\p{L}\p{M}]"""

    private fun nfc(s: String) = Normalizer.normalize(s, Normalizer.Form.NFC)

    fun annotate(text: String): String {
        // NFC on both sides: "ढ़" can be typed as one code point or as "ढ" + nukta.
        var out = nfc(text)
        for (w in words) {
            for (form in w.forms.map { nfc(it) }.distinct().sortedByDescending { it.length }) {
                val re = Regex("""(?<!$L)${Regex.escape(form)}(?!$L)(?!\s*\()""", RegexOption.IGNORE_CASE)
                out = re.replace(out) { "${it.value} (${w.value})" }
            }
        }
        return out
    }
}
