package com.jarvisx.core.tools

import com.jarvisx.core.lang.ReplyLanguage
import java.time.Clock
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Answers "what time / date / day is it" from the phone's clock, never from the model. */
class DateTimeTool(private val clock: Clock = Clock.systemDefaultZone()) : Tool {
    override val id = "datetime"
    override val purpose = "Reads the current time, date and weekday from the phone clock."
    override val permission = Permission.NONE
    override val timeoutMs = 200L

    private enum class Kind { TIME, DATE, DAY }

    private val timePatterns = listOf(
        Regex("""\bwhat(?:'s| is)? the time\b"""), Regex("""\bwhat time is it\b"""),
        Regex("""\btime (?:kya|kitna) (?:hua|hai|ho gaya)\b"""), Regex("""\bkitne baje\b"""),
        Regex("""\bcurrent time\b"""), Regex("""\btime batao\b"""), Regex("""क्या समय"""), Regex("""कितने बजे"""),
        Regex("""\bsamay kya\b"""),
    )
    private val datePatterns = listOf(
        Regex("""\bwhat(?:'s| is)? (?:the |today's )?date\b"""), Regex("""\btoday's date\b"""),
        Regex("""\baaj (?:ki )?(?:date|tarikh|tareekh)\b"""), Regex("""\bdate kya hai\b"""),
        Regex("""आज (?:की )?तारीख"""), Regex("""तारीख क्या"""),
    )
    private val dayPatterns = listOf(
        Regex("""\bwhat day is (?:it|today)\b"""), Regex("""\bwhich day is today\b"""),
        Regex("""\baaj (?:kaun ?sa|konsa|kaunsa) (?:din|day)\b"""), Regex("""आज कौन सा दिन"""),
    )

    private fun kind(text: String): Kind? {
        val t = text.lowercase()
        if (t.length > 80) return null // long messages are real questions, not clock lookups
        return when {
            timePatterns.any { it.containsMatchIn(t) } -> Kind.TIME
            datePatterns.any { it.containsMatchIn(t) } -> Kind.DATE
            dayPatterns.any { it.containsMatchIn(t) } -> Kind.DAY
            else -> null
        }
    }

    override fun confidence(request: ToolRequest): Double = if (kind(request.text) != null) 0.95 else 0.0

    override fun execute(request: ToolRequest): ToolResult {
        val k = kind(request.text) ?: return ToolResult.Failure(id, "Not a time or date question.")
        val now = ZonedDateTime.now(clock)
        val locale = if (request.language == ReplyLanguage.HINDI) Locale.forLanguageTag("hi-IN") else Locale.forLanguageTag("en-IN")
        val time = now.format(DateTimeFormatter.ofPattern("h:mm a", locale))
        val date = now.format(DateTimeFormatter.ofPattern("d MMMM yyyy", locale))
        val day = now.format(DateTimeFormatter.ofPattern("EEEE", locale))
        val text = when (request.language) {
            ReplyLanguage.ENGLISH -> when (k) {
                Kind.TIME -> "It's $time."
                Kind.DATE -> "Today is $day, $date."
                Kind.DAY -> "Today is $day."
            }
            ReplyLanguage.HINGLISH -> when (k) {
                Kind.TIME -> "Abhi $time ho rahe hain."
                Kind.DATE -> "Aaj $day, $date hai."
                Kind.DAY -> "Aaj $day hai."
            }
            ReplyLanguage.HINDI -> when (k) {
                Kind.TIME -> "अभी $time बजे हैं।"
                Kind.DATE -> "आज $day, $date है।"
                Kind.DAY -> "आज $day है।"
            }
        }
        return ToolResult.Answer(id, text, verified = false)
    }

    override fun verify(request: ToolRequest, result: ToolResult.Answer): Boolean = result.text.isNotBlank()
}
