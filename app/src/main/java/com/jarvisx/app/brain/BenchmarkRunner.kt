package com.jarvisx.app.brain

import android.content.Context
import android.os.SystemClock
import com.jarvisx.app.BuildConfig
import com.jarvisx.app.device.HardwareProfiler
import com.jarvisx.app.models.ModelStore
import com.jarvisx.core.bench.BenchmarkReport
import com.jarvisx.core.bench.BenchmarkSample
import com.jarvisx.core.lang.LanguageDetector
import com.jarvisx.core.model.ModelSpec
import com.jarvisx.core.router.Router
import com.jarvisx.core.tools.ToolRegistry
import kotlinx.coroutines.flow.fold
import java.io.File
import java.time.OffsetDateTime

/**
 * Test D from the JARVIS X brief: load time, first-token latency, tokens/second, RAM, heat and
 * battery, measured on this phone with no network involved. The questions cover English, Hindi,
 * Hinglish and a short reasoning task.
 */
class BenchmarkRunner(
    private val context: Context,
    private val brain: LocalBrain,
    private val profiler: HardwareProfiler,
    private val store: ModelStore,
) {
    data class Question(val label: String, val text: String)

    val questions = listOf(
        Question("english-fact", "In two sentences, what is photosynthesis?"),
        Question("hindi", "भारत की राजधानी क्या है और वहाँ घूमने की दो प्रसिद्ध जगहें कौन सी हैं?"),
        Question("hinglish", "Mujhe Android app banana seekhna hai, shuru kaise karun? 3 steps mein batao."),
        Question("reasoning", "A shop sells pens at 3 for Rs 10. How much do 12 pens cost? Explain briefly."),
    )

    private val hinter = Router(ToolRegistry(emptyList()))

    suspend fun run(spec: ModelSpec, onStep: (String) -> Unit): Result<Pair<BenchmarkReport, File>> = runCatching {
        val before = profiler.profile()
        val tempBefore = profiler.batteryTempC()
        val batteryBefore = profiler.batteryPercent()

        onStep("Unloading any loaded model to measure a cold load…")
        brain.unload()
        onStep("Loading ${spec.displayName}…")
        val t0 = SystemClock.elapsedRealtime()
        brain.forceLoad(spec)?.let { error(it) }
        val loadMs = SystemClock.elapsedRealtime() - t0
        val ready = brain.status.value as? BrainStatus.Ready

        onStep("Running llama.cpp speed test (128 prompt + 32 generated tokens)…")
        val native = runCatching { brain.bench(128, 32) }.getOrElse { "native bench failed: ${it.message}" }
        // bench() leaves an empty context; start a clean conversation for the real questions.
        brain.newConversation()

        val samples = mutableListOf<BenchmarkSample>()
        for ((i, q) in questions.withIndex()) {
            onStep("Question ${i + 1}/${questions.size}: ${q.label}…")
            brain.newConversation()
            val prompt = hinter.withLanguageHint(q.text, LanguageDetector.detect(q.text))
            val answer = brain.generate(prompt, maxTokens = 200).fold(StringBuilder()) { sb, piece -> sb.append(piece) }.toString()
            val m = brain.lastMetrics
            samples += BenchmarkSample(
                label = q.label, prompt = q.text, answer = answer.trim(),
                promptTokens = m.promptTokens, generatedTokens = m.generatedTokens,
                prefillMs = m.prefillMs, firstTokenMs = m.firstTokenMs, generationMs = m.generationMs,
            )
        }
        brain.newConversation()

        val report = BenchmarkReport(
            appVersion = BuildConfig.VERSION_NAME,
            timestampIso = OffsetDateTime.now().toString(),
            device = before,
            modelId = spec.id,
            modelFileMb = store.sizeOnDiskMb(spec),
            threads = ready?.threads ?: 0,
            contextSize = ready?.contextSize ?: 0,
            loadMs = loadMs,
            nativeBench = native,
            samples = samples,
            ramBeforeLoadMb = before.availableRamMb,
            appPssAfterMb = profiler.appPssMb(),
            thermalStatusAfter = profiler.thermalStatus(),
            batteryTempBeforeC = tempBefore,
            batteryTempAfterC = profiler.batteryTempC(),
            batteryPercentBefore = batteryBefore,
            batteryPercentAfter = profiler.batteryPercent(),
        )
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        val file = File(dir, "jarvisx-benchmark-${System.currentTimeMillis()}.json")
        file.writeText(report.toJson())
        onStep("Done. Report saved.")
        report to file
    }
}
