package com.jarvisx.core

import com.jarvisx.core.lang.LanguageDetector
import com.jarvisx.core.tools.CalculatorTool
import com.jarvisx.core.tools.ToolExecutor
import com.jarvisx.core.tools.ToolRequest
import com.jarvisx.core.tools.ToolResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculatorToolTest {
    private val calc = CalculatorTool()

    private fun req(text: String) = ToolRequest(text, LanguageDetector.detect(text))

    private fun answer(text: String): String {
        val r = ToolExecutor.run(calc, req(text))
        assertTrue("expected an answer for '$text' but got $r", r is ToolResult.Answer)
        r as ToolResult.Answer
        assertTrue("answer must be verified", r.verified)
        return Regex("""\*\*(.+?)\*\*""").find(r.text)!!.groupValues[1]
    }

    @Test fun basicArithmetic() {
        assertEquals("100", answer("25*4"))
        assertEquals("14", answer("2 + 3 * 4"))
        assertEquals("20", answer("(2 + 3) * 4"))
        assertEquals("1024", answer("2^10"))
        assertEquals("-3", answer("-5 + 2"))
        assertEquals("2.5", answer("5 / 2"))
        assertEquals("0.3333333333", answer("1/3"))
    }

    @Test fun naturalLanguageEnglish() {
        assertEquals("100", answer("what is 25 times 4?"))
        assertEquals("300", answer("15% of 2000"))
        assertEquals("12", answer("square root of 144"))
        assertEquals("5", answer("calculate 20 divided by 4"))
        assertEquals("1", answer("10 mod 3"))
    }

    @Test fun hinglishAndHindi() {
        assertEquals("100", answer("25 into 4 kitna hota hai"))
        assertEquals("30", answer("5 guna 6"))
        assertEquals("100", answer("aaj 25 into 4 kitna"))
        assertEquals("300", answer("2000 ka 15%"))
        assertEquals("12", answer("144 ka square root"))
        assertEquals("15", answer("७ + ८"))
        assertEquals("Indian digit grouping", "100000", answer("1,00,000 * 1"))
    }

    @Test fun notACalculation() {
        assertNull(calc.extractExpression("How do I cook 2 eggs?"))
        assertNull(calc.extractExpression("Tell me about World War 2"))
        assertNull(calc.extractExpression("42"))
        assertEquals(0.0, calc.confidence(req("Meeting on 12/10/2025")), 0.0)
        assertEquals(0.0, calc.confidence(req("What is AI?")), 0.0)
    }

    @Test fun divisionByZeroIsAFailureNotACrash() {
        val r = ToolExecutor.run(calc, req("5/0"))
        assertTrue(r is ToolResult.Failure)
    }

    @Test fun answerExplainsItWasNotTheModel() {
        val r = ToolExecutor.run(calc, req("25 into 4 kitna hota hai")) as ToolResult.Answer
        assertTrue(r.text.contains("calculator"))
    }
}
