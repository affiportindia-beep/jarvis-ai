package com.jarvisx.core.bench

import com.jarvisx.core.model.DeviceProfile

/** One measured question in a benchmark run. */
data class BenchmarkSample(
    val label: String,
    val prompt: String,
    val answer: String,
    val promptTokens: Int,
    val generatedTokens: Int,
    val prefillMs: Long,
    val firstTokenMs: Long,
    val generationMs: Long,
) {
    val tokensPerSecond: Double get() = if (generationMs > 0) generatedTokens * 1000.0 / generationMs else 0.0
}

/**
 * Everything Test D asks for, in one file the user can send back.
 * Written as JSON by hand so `core` needs no dependencies.
 */
data class BenchmarkReport(
    val appVersion: String,
    val timestampIso: String,
    val device: DeviceProfile,
    val modelId: String,
    val modelFileMb: Long,
    val threads: Int,
    val contextSize: Int,
    val loadMs: Long,
    val nativeBench: String,
    val samples: List<BenchmarkSample>,
    val ramBeforeLoadMb: Long,
    val appPssAfterMb: Long,
    val thermalStatusAfter: Int,
    val batteryTempBeforeC: Float?,
    val batteryTempAfterC: Float?,
    val batteryPercentBefore: Int?,
    val batteryPercentAfter: Int?,
    val networkUsedForInference: Boolean = false,
) {
    val averageTokensPerSecond: Double
        get() = samples.filter { it.generatedTokens > 0 }.map { it.tokensPerSecond }.average().let { if (it.isNaN()) 0.0 else it }

    fun toJson(): String = Json.obj(
        "app_version" to appVersion,
        "timestamp" to timestampIso,
        "device" to Json.obj(
            "manufacturer" to device.manufacturer,
            "model" to device.model,
            "soc" to device.socModel,
            "android_sdk" to device.androidSdk,
            "total_ram_mb" to device.totalRamMb,
            "available_ram_mb" to device.availableRamMb,
            "cpu_cores" to device.cpuCores,
            "performance_cores" to device.performanceCores,
            "cpu_features" to device.cpuFeatures.sorted(),
            "free_storage_mb" to device.freeStorageMb,
            "thermal_status" to device.thermalStatus,
            "battery_temp_c" to device.batteryTempC,
        ),
        "model_id" to modelId,
        "model_file_mb" to modelFileMb,
        "threads" to threads,
        "context_size" to contextSize,
        "load_ms" to loadMs,
        "ram_before_load_mb" to ramBeforeLoadMb,
        "app_pss_after_mb" to appPssAfterMb,
        "thermal_status_after" to thermalStatusAfter,
        "battery_temp_before_c" to batteryTempBeforeC,
        "battery_temp_after_c" to batteryTempAfterC,
        "battery_percent_before" to batteryPercentBefore,
        "battery_percent_after" to batteryPercentAfter,
        "network_used_for_inference" to networkUsedForInference,
        "average_tokens_per_second" to averageTokensPerSecond,
        "native_bench" to nativeBench,
        "samples" to samples.map {
            Json.obj(
                "label" to it.label,
                "prompt" to it.prompt,
                "answer" to it.answer,
                "prompt_tokens" to it.promptTokens,
                "generated_tokens" to it.generatedTokens,
                "prefill_ms" to it.prefillMs,
                "first_token_ms" to it.firstTokenMs,
                "generation_ms" to it.generationMs,
                "tokens_per_second" to it.tokensPerSecond,
            )
        },
    ).json

    /** Human-readable summary for the Diagnostics screen. */
    fun summary(): String = buildString {
        appendLine("Model: $modelId (${modelFileMb} MB), $threads threads, context $contextSize")
        appendLine("Load time: ${loadMs} ms")
        appendLine("Average speed: ${"%.1f".format(averageTokensPerSecond)} tokens/s")
        for (s in samples) {
            appendLine("• ${s.label}: first token ${s.firstTokenMs} ms, ${"%.1f".format(s.tokensPerSecond)} tok/s, ${s.generatedTokens} tokens")
        }
        appendLine("App memory after run: ${appPssAfterMb} MB")
        append("Thermal status after: $thermalStatusAfter (0 = none, 3 = severe)")
    }
}

/** Minimal JSON writer: strings, numbers, booleans, null, lists and nested objects. */
object Json {
    class Raw(val json: String)

    fun obj(vararg pairs: Pair<String, Any?>): Raw =
        Raw(pairs.joinToString(",", "{", "}") { (k, v) -> "${str(k)}:${value(v)}" })

    fun value(v: Any?): String = when (v) {
        null -> "null"
        is Raw -> v.json
        is String -> str(v)
        is Boolean -> v.toString()
        is Float -> if (v.isFinite()) v.toString() else "null"
        is Double -> if (v.isFinite()) "%.3f".format(java.util.Locale.ROOT, v) else "null"
        is Number -> v.toString()
        is Iterable<*> -> v.joinToString(",", "[", "]") { value(it) }
        else -> str(v.toString())
    }

    fun str(s: String): String = buildString {
        append('"')
        for (c in s) when {
            c == '"' -> append("\\\"")
            c == '\\' -> append("\\\\")
            c == '\n' -> append("\\n")
            c == '\r' -> append("\\r")
            c == '\t' -> append("\\t")
            c < ' ' -> append("\\u%04x".format(c.code))
            else -> append(c)
        }
        append('"')
    }
}

