package com.jarvisx.core.voice

/**
 * Turns a chat answer into text that sounds right when spoken: no Markdown symbols, no code
 * blocks, no source markers, and split into sentences so speech can start before the model
 * has finished writing.
 */
object SpeechText {

    /** Plain speakable text. Code blocks are replaced by a short spoken note. */
    fun clean(markdown: String, codeNote: String = "I've shown the code on screen."): String {
        var t = markdown.replace(Regex("(?s)<think>.*?</think>"), " ")
        t = t.replace(Regex("(?s)```.*?```"), " $codeNote ")
        t = t.replace(Regex("(?s)```.*$"), " ") // unfinished code block while streaming
        t = t.replace(Regex("`([^`]*)`"), "$1")
        t = t.replace(Regex("\\[(\\d+)]"), "") // [1] citations
        t = t.replace(Regex("\\*\\*|__|\\*|#+ |^\\s*[-•]\\s+", RegexOption.MULTILINE), "")
        t = t.replace(Regex("(?m)^\\s*\\d+\\.\\s+"), "")
        t = t.replace(Regex("https?://\\S+"), "")
        t = t.replace(Regex("[_~|>]"), " ")
        return t.replace(Regex("\\s+"), " ").trim()
    }

    private val boundary = Regex("(?<=[.!?।॥])\\s+|\\n{2,}")

    /**
     * Splits [text] into finished sentences plus the unfinished remainder, so speech can start
     * as soon as each sentence is complete. Very short fragments are merged with the next one.
     */
    fun takeSentences(text: String): Pair<List<String>, String> {
        val parts = text.split(boundary)
        if (parts.size <= 1) return emptyList<String>() to text
        val done = parts.dropLast(1).map { it.trim() }.filter { it.isNotEmpty() }
        val merged = mutableListOf<String>()
        var carry = ""
        for (p in done) {
            val s = if (carry.isEmpty()) p else "$carry $p"
            if (s.length < 12) carry = s else { merged += s; carry = "" }
        }
        val rest = if (carry.isEmpty()) parts.last() else "$carry ${parts.last()}"
        return merged to rest
    }
}
