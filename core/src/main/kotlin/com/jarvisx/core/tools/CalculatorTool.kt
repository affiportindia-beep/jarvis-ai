package com.jarvisx.core.tools

import com.jarvisx.core.lang.ReplyLanguage
import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Exact arithmetic, so the language model never guesses at maths.
 *
 * Understands digits and operators plus common English, Hinglish and Hindi phrasings:
 * "25 into 4", "15% of 2000", "2000 ka 15%", "144 ka square root", "5 guna 6", "७ + ८" is not
 * supported (Devanagari digits are converted first). Trigonometry uses degrees.
 */
class CalculatorTool : Tool {
    override val id = "calculator"
    override val purpose = "Evaluates arithmetic exactly instead of asking the language model."
    override val permission = Permission.NONE
    override val timeoutMs = 500L

    override fun confidence(request: ToolRequest): Double {
        if (looksLikeDate(request.text)) return 0.0
        val expr = extractExpression(request.text) ?: return 0.0
        return if (runCatching { Evaluator(expr).evaluate() }.isSuccess) 0.97 else 0.0
    }

    override fun execute(request: ToolRequest): ToolResult {
        val expr = extractExpression(request.text)
            ?: return ToolResult.Failure(id, "No calculation found in the message.")
        val value = try {
            Evaluator(expr).evaluate()
        } catch (e: ArithmeticException) {
            return ToolResult.Failure(id, e.message ?: "That calculation is not defined.")
        } catch (e: IllegalArgumentException) {
            return ToolResult.Failure(id, "I could not read that calculation: ${e.message}")
        }
        if (value.isNaN() || value.isInfinite()) {
            return ToolResult.Failure(id, "That calculation has no finite answer.")
        }
        val shown = pretty(expr)
        val result = format(value)
        val note = when (request.language) {
            ReplyLanguage.ENGLISH -> "Calculated exactly by the calculator, not by the AI model."
            ReplyLanguage.HINGLISH -> "Ye calculator se exact nikala hai, AI model se andaza nahi."
            ReplyLanguage.HINDI -> "यह कैलकुलेटर से सटीक निकाला गया है, AI मॉडल से अंदाज़ा नहीं।"
        }
        return ToolResult.Answer(id, "$shown = **$result**\n\n_${note}_", verified = false)
    }

    override fun verify(request: ToolRequest, result: ToolResult.Answer): Boolean {
        val expr = extractExpression(request.text) ?: return false
        val first = Evaluator(expr).evaluate()
        val second = Evaluator(expr).evaluate()
        return first.isFinite() && first == second && result.text.contains(format(first))
    }

    // ---------------------------------------------------------------- parsing helpers

    private fun looksLikeDate(text: String): Boolean =
        Regex("""\b\d{1,2}[/\-.]\d{1,2}[/\-.]\d{2,4}\b""").containsMatchIn(text)

    /** Returns a clean arithmetic expression, or null if the text is not a calculation. */
    fun extractExpression(text: String): String? {
        var s = text.lowercase()
        // Devanagari digits -> ASCII
        s = buildString { for (c in s) append(if (c in '०'..'९') ('0' + (c - '०')) else c) }
        s = s.replace('×', '*').replace('÷', '/').replace('−', '-').replace('–', '-')
        s = s.replace(Regex("""(?<=\d),(?=\d)"""), "")

        val num = """(\d+(?:\.\d+)?)"""
        val pct = """(?:%|percent|per cent|pratishat|प्रतिशत)"""
        val of = """(?:of|ka|ki|ke|का|की|के)"""
        s = s.replace(Regex("""$num\s*$pct\s*$of\s*$num""")) { "(${it.groupValues[1]}/100*${it.groupValues[2]})" }
        s = s.replace(Regex("""$num\s*$of\s*$num\s*$pct""")) { "(${it.groupValues[2]}/100*${it.groupValues[1]})" }
        s = s.replace(Regex("""$num\s*$of\s*(?:square root|sqrt|root|vargmool|वर्गमूल)""")) { "sqrt(${it.groupValues[1]})" }
        s = s.replace(Regex("""(?:square root of|square root|sqrt of|root of|वर्गमूल)\s*$num""")) { "sqrt(${it.groupValues[1]})" }
        s = s.replace(Regex("""$num\s*(?:squared|$of\s*square|का वर्ग)""")) { "(${it.groupValues[1]}^2)" }
        s = s.replace(Regex("""$num\s*(?:cubed|$of\s*cube|का घन)""")) { "(${it.groupValues[1]}^3)" }

        val words = listOf(
            "to the power of" to "^", "raised to" to "^", "ki power" to "^", "power" to "^",
            "multiplied by" to "*", "multiply by" to "*", "divided by" to "/", "divide by" to "/",
            "plus" to "+", "jama" to "+", "jod" to "+", "जोड़" to "+", "प्लस" to "+",
            "minus" to "-", "ghatao" to "-", "ghata" to "-", "घटा" to "-", "माइनस" to "-",
            "times" to "*", "into" to "*", "guna" to "*", "गुणा" to "*",
            "bhaag" to "/", "bhag" to "/", "over" to "/", "भाग" to "/", "mod" to " mod ",
        )
        for ((w, op) in words) s = s.replace(Regex("(?<![a-z])${Regex.escape(w)}(?![a-z])"), op)
        s = s.replace(Regex("""(?<=\d)\s*x\s*(?=\d)"""), "*")
        s = s.replace(Regex("""(?<=\d)\s*by\s*(?=\d)"""), "/")

        val filler = setOf(
            "what", "whats", "what's", "is", "the", "value", "of", "calculate", "calc", "compute",
            "solve", "please", "pls", "equals", "equal", "to", "kitna", "kitne", "kitni", "hota",
            "hoti", "hote", "hai", "hain", "hoga", "hogi", "kya", "batao", "bataiye", "bata", "tell",
            "me", "answer", "jarvis", "hey", "ok", "okay", "find", "result", "क्या", "है", "कितना",
            "होता", "होगा", "बताओ", "बताइए", "जार्विस", "can", "you", "and", "ji", "yaar", "bhai",
        )
        s = s.replace(Regex("""[=?!]"""), " ")
        val kept = s.split(Regex("""\s+""")).filter { it.isNotEmpty() && it !in filler }
        val expr = kept.joinToString(" ").trim()
        if (expr.isEmpty()) return null
        if (!Regex("""^[0-9.+\-*/^%() a-z]*$""").matches(expr)) return null
        if (!expr.any { it.isDigit() }) return null
        val hasOperator = Regex("""[+\-*/^%]|sqrt|sin|cos|tan|log|ln|abs| mod """).containsMatchIn(" $expr ")
        if (!hasOperator) return null
        val letters = Regex("[a-z]+").findAll(expr).map { it.value }.toList()
        if (letters.any { it !in Evaluator.identifiers }) return null
        return expr
    }

    private fun pretty(expr: String): String =
        expr.replace("*", " × ").replace("/", " ÷ ").replace(Regex("\\s+"), " ").trim()

    fun format(v: Double): String {
        val rounded = round(v)
        if (abs(v) < 1e15 && abs(v - rounded) < 1e-9 * maxOf(1.0, abs(v))) return rounded.toLong().toString()
        return BigDecimal(v).round(MathContext(10)).stripTrailingZeros().toPlainString()
    }
}

/** Small recursive-descent evaluator. Throws IllegalArgumentException on bad input. */
internal class Evaluator(private val src: String) {
    companion object {
        val identifiers = setOf("sqrt", "sin", "cos", "tan", "log", "ln", "abs", "pi", "e", "mod")
    }

    private var pos = 0

    fun evaluate(): Double {
        val v = expression()
        skipSpaces()
        if (pos < src.length) throw IllegalArgumentException("unexpected '${src[pos]}'")
        return v
    }

    private fun skipSpaces() { while (pos < src.length && src[pos] == ' ') pos++ }

    private fun peekWord(): String? {
        skipSpaces()
        var end = pos
        while (end < src.length && src[end].isLetter()) end++
        return if (end > pos) src.substring(pos, end) else null
    }

    private fun expression(): Double {
        var v = term()
        while (true) {
            skipSpaces()
            if (pos >= src.length) return v
            when (src[pos]) {
                '+' -> { pos++; v += term() }
                '-' -> { pos++; v -= term() }
                else -> return v
            }
        }
    }

    private fun term(): Double {
        var v = power()
        while (true) {
            skipSpaces()
            if (pos >= src.length) return v
            when {
                src[pos] == '*' -> { pos++; v *= power() }
                src[pos] == '/' -> {
                    pos++
                    val d = power()
                    if (d == 0.0) throw ArithmeticException("Division by zero is not defined.")
                    v /= d
                }
                peekWord() == "mod" -> {
                    pos += 3
                    val d = power()
                    if (d == 0.0) throw ArithmeticException("Modulo by zero is not defined.")
                    v %= d
                }
                else -> return v
            }
        }
    }

    private fun power(): Double {
        val base = unary()
        skipSpaces()
        if (pos < src.length && src[pos] == '^') {
            pos++
            return base.pow(power())
        }
        return base
    }

    private fun unary(): Double {
        skipSpaces()
        if (pos < src.length && src[pos] == '-') { pos++; return -unary() }
        if (pos < src.length && src[pos] == '+') { pos++; return unary() }
        var v = primary()
        while (true) {
            skipSpaces()
            if (pos < src.length && src[pos] == '%') { pos++; v /= 100.0 } else break
        }
        return v
    }

    private fun primary(): Double {
        skipSpaces()
        if (pos >= src.length) throw IllegalArgumentException("expression ends too early")
        val c = src[pos]
        if (c == '(') {
            pos++
            val v = expression()
            skipSpaces()
            if (pos >= src.length || src[pos] != ')') throw IllegalArgumentException("missing ')'")
            pos++
            return v
        }
        if (c.isDigit() || c == '.') {
            val start = pos
            while (pos < src.length && (src[pos].isDigit() || src[pos] == '.')) pos++
            return src.substring(start, pos).toDoubleOrNull()
                ?: throw IllegalArgumentException("bad number '${src.substring(start, pos)}'")
        }
        val word = peekWord() ?: throw IllegalArgumentException("unexpected '$c'")
        pos += word.length
        return when (word) {
            "pi" -> Math.PI
            "e" -> Math.E
            "sqrt" -> {
                val x = primary()
                if (x < 0) throw ArithmeticException("The square root of a negative number is not a real number.")
                sqrt(x)
            }
            "sin" -> sin(Math.toRadians(primary()))
            "cos" -> cos(Math.toRadians(primary()))
            "tan" -> tan(Math.toRadians(primary()))
            "log" -> {
                val x = primary()
                if (x <= 0) throw ArithmeticException("log is only defined for positive numbers.")
                log10(x)
            }
            "ln" -> {
                val x = primary()
                if (x <= 0) throw ArithmeticException("ln is only defined for positive numbers.")
                ln(x)
            }
            "abs" -> abs(primary())
            else -> throw IllegalArgumentException("unknown word '$word'")
        }
    }
}
