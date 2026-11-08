package com.jarvisx.core

import com.jarvisx.core.lang.ReplyLanguage
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The desktop eval must ask the model exactly what the app asks it. */
class EvalSyncTest {
    @Test fun evalScriptUsesTheAppsLanguageHints() {
        val script = listOf(File("../eval/run_eval.py"), File("eval/run_eval.py")).first { it.isFile }.readText()
        for (lang in ReplyLanguage.values()) {
            assertTrue("eval/run_eval.py is missing the hint for $lang", script.contains("\"${lang.name}\": \"${lang.instruction}\""))
        }
    }
}
