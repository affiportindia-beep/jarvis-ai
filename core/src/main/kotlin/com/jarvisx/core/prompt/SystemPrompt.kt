package com.jarvisx.core.prompt

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The fixed instructions given to the local model at the start of every conversation.
 *
 * The text lives in `core/src/main/resources/jarvisx/system_prompt.txt` so the Android app and
 * the desktop evaluation workflow (`.github/workflows/model-eval.yml`) use exactly the same words.
 * Kept short on purpose: on a phone CPU every system-prompt token costs prefill time.
 */
object SystemPrompt {
    private const val RESOURCE = "/jarvisx/system_prompt.txt"

    val template: String by lazy {
        SystemPrompt::class.java.getResourceAsStream(RESOURCE)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            ?: error("Missing resource $RESOURCE")
    }

    fun build(modelName: String, today: LocalDate = LocalDate.now()): String {
        val date = today.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH))
        return template.replace("{MODEL}", modelName).replace("{DATE}", date).trim()
    }
}
