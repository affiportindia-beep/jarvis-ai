package com.jarvisx.core

import com.jarvisx.core.lang.HindiNumberWords
import com.jarvisx.core.lang.ReplyLanguage
import com.jarvisx.core.router.Router
import com.jarvisx.core.tools.ToolRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.Normalizer

class HindiNumberWordsTest {
    private fun n(s: String) = Normalizer.normalize(s, Normalizer.Form.NFC)

    @Test fun bothEncodingsOfDedh() {
        assertEquals(n("\u0921\u0947\u095D (1.5) घंटा"), HindiNumberWords.annotate("\u0921\u0947\u0922\u093C घंटा"))
    }

    @Test fun devanagari() {
        assertEquals(n("ढाई (2.5) किलो कितने का होगा?"), HindiNumberWords.annotate("ढाई किलो कितने का होगा?"))
        assertEquals(n("डेढ़ (1.5) घंटा"), HindiNumberWords.annotate("डेढ़ घंटा"))
        assertEquals(n("साढ़े तीन (3.5) बजे"), HindiNumberWords.annotate("साढ़े तीन बजे"))
    }

    @Test fun roman() {
        assertEquals("dhai (2.5) kilo aloo", HindiNumberWords.annotate("dhai kilo aloo"))
        assertEquals("Dedh (1.5) litre doodh", HindiNumberWords.annotate("Dedh litre doodh"))
    }

    @Test fun leavesOtherWordsAlone() {
        // "pav" inside "pavbhaji" and "adhi" inside "adhikari" must not change.
        assertEquals("pavbhaji aur adhikari", HindiNumberWords.annotate("pavbhaji aur adhikari"))
        assertEquals("I need half a kilo", HindiNumberWords.annotate("I need half a kilo"))
    }

    @Test fun idempotent() {
        val once = HindiNumberWords.annotate("ढाई किलो")
        assertEquals(once, HindiNumberWords.annotate(once))
    }

    @Test fun routerAddsTheHintBeforeTheModel() {
        val p = Router(ToolRegistry(emptyList())).withLanguageHint("ढाई किलो सेब कितने का?", ReplyLanguage.HINDI)
        assertTrue(p, p.startsWith(n("ढाई (2.5) किलो")))
    }
}
