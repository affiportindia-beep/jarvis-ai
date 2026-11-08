package com.jarvisx.core

import com.jarvisx.core.voice.SpeechText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechTextTest {
    @Test fun stripsMarkdownCodeAndCitations() {
        val md = "**Tokyo** is the capital [1].\n\n1. First point\n- bullet `code`\n```kotlin\nfun x() = 1\n```\nSee https://example.com"
        val t = SpeechText.clean(md)
        assertEquals("Tokyo is the capital . First point bullet code I've shown the code on screen. See", t)
        assertFalse(t.contains("**"))
    }

    @Test fun hidesReasoning() {
        assertEquals("Answer.", SpeechText.clean("<think>long reasoning</think>Answer."))
    }

    @Test fun splitsEnglishAndHindiSentences() {
        val (done, rest) = SpeechText.takeSentences("Bharat ki rajdhani New Delhi hai. यह एक बड़ा शहर है। Aur bhi")
        assertEquals(listOf("Bharat ki rajdhani New Delhi hai.", "यह एक बड़ा शहर है।"), done)
        assertEquals("Aur bhi", rest)
    }

    @Test fun mergesTinyFragments() {
        val (done, rest) = SpeechText.takeSentences("Yes. Ok. This is a longer sentence here. tail")
        assertEquals(listOf("Yes. Ok. This is a longer sentence here."), done)
        assertEquals("tail", rest)
    }

    @Test fun noBoundaryMeansNothingFinished() {
        val (done, rest) = SpeechText.takeSentences("still writing")
        assertTrue(done.isEmpty())
        assertEquals("still writing", rest)
    }
}
