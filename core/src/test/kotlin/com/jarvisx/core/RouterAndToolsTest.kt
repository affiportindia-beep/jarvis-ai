package com.jarvisx.core

import com.jarvisx.core.lang.ReplyLanguage
import com.jarvisx.core.router.Route
import com.jarvisx.core.router.Router
import com.jarvisx.core.tools.CalculatorTool
import com.jarvisx.core.tools.DateTimeTool
import com.jarvisx.core.tools.Permission
import com.jarvisx.core.tools.Tool
import com.jarvisx.core.tools.ToolExecutor
import com.jarvisx.core.tools.ToolRegistry
import com.jarvisx.core.tools.ToolRequest
import com.jarvisx.core.tools.ToolResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class RouterAndToolsTest {
    // 8 Nov 2026, 10:30 India time
    private val clock = Clock.fixed(Instant.parse("2026-11-08T05:00:00Z"), ZoneId.of("Asia/Kolkata"))
    private val router = Router(ToolRegistry(listOf(CalculatorTool(), DateTimeTool(clock))))

    @Test fun mathGoesToCalculator() {
        val r = router.route("what is 12 * 12")
        assertTrue(r is Route.UseTool)
        assertEquals("calculator", (r as Route.UseTool).tool.id)
    }

    @Test fun clockQuestionsGoToClock() {
        for (q in listOf("what time is it", "time kya hua", "aaj ki date kya hai", "आज कौन सा दिन है")) {
            val r = router.route(q)
            assertTrue("'$q' should use the clock", r is Route.UseTool && r.tool.id == "datetime")
        }
    }

    @Test fun clockAnswersUseThePhoneClock() {
        val tool = DateTimeTool(clock)
        val en = ToolExecutor.run(tool, ToolRequest("what time is it", ReplyLanguage.ENGLISH)) as ToolResult.Answer
        assertTrue(en.text, en.text.contains("10:30"))
        val date = ToolExecutor.run(tool, ToolRequest("what's the date", ReplyLanguage.ENGLISH)) as ToolResult.Answer
        assertTrue(date.text, date.text.contains("8 November 2026"))
        assertTrue(date.text, date.text.contains("Sunday"))
    }

    @Test fun everythingElseGoesToTheModelWithLanguageHint() {
        val r = router.route("Mujhe startup idea ke baare mein batao")
        assertTrue(r is Route.UseModel)
        r as Route.UseModel
        assertEquals(ReplyLanguage.HINGLISH, r.language)
        assertTrue(r.prompt.endsWith("(${ReplyLanguage.HINGLISH.instruction})"))
    }

    @Test fun emptyInputIsIgnored() {
        assertTrue(router.route("   ") is Route.Ignore)
    }

    @Test fun slowToolIsStoppedByItsTimeLimit() {
        val slow = object : Tool {
            override val id = "slow"
            override val purpose = "test"
            override val permission = Permission.NONE
            override val timeoutMs = 50L
            override fun confidence(request: ToolRequest) = 1.0
            override fun execute(request: ToolRequest): ToolResult { Thread.sleep(2000); return ToolResult.Answer(id, "late", false) }
            override fun verify(request: ToolRequest, result: ToolResult.Answer) = true
        }
        val r = ToolExecutor.run(slow, ToolRequest("x", ReplyLanguage.ENGLISH))
        assertTrue(r is ToolResult.Failure)
    }

    @Test fun failingVerificationHidesTheAnswer() {
        val liar = object : Tool {
            override val id = "liar"
            override val purpose = "test"
            override val permission = Permission.NONE
            override val timeoutMs = 500L
            override fun confidence(request: ToolRequest) = 1.0
            override fun execute(request: ToolRequest) = ToolResult.Answer(id, "2+2=5", false)
            override fun verify(request: ToolRequest, result: ToolResult.Answer) = false
        }
        assertTrue(ToolExecutor.run(liar, ToolRequest("x", ReplyLanguage.ENGLISH)) is ToolResult.Failure)
    }

    @Test(expected = IllegalArgumentException::class)
    fun duplicateToolIdsRejected() {
        ToolRegistry(listOf(CalculatorTool(), CalculatorTool()))
    }
}
