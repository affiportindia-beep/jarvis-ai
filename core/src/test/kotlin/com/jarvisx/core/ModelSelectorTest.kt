package com.jarvisx.core

import com.jarvisx.core.bench.BenchmarkReport
import com.jarvisx.core.bench.BenchmarkSample
import com.jarvisx.core.bench.Json
import com.jarvisx.core.model.DeviceProfile
import com.jarvisx.core.model.ModelCatalog
import com.jarvisx.core.model.ModelSelector
import com.jarvisx.core.prompt.SystemPrompt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ModelSelectorTest {

    private fun phone(totalGb: Double, availMb: Long, perfCores: Int = 4, thermal: Int = 0) = DeviceProfile(
        manufacturer = "samsung", model = "SM-N986B", socModel = "s5e9830", androidSdk = 33,
        totalRamMb = (totalGb * 1024).toLong(), availableRamMb = availMb, cpuCores = 8,
        performanceCores = perfCores, cpuFeatures = setOf("asimd", "asimddp"), freeStorageMb = 50_000,
        thermalStatus = thermal, batteryTempC = 31f,
    )

    @Test fun note20UltraGets4B() {
        // Galaxy Note 20 Ultra (India): 12 GB total, about 5.7 GB free as reported by the user.
        val p = phone(11.2, 5700)
        assertEquals("qwen3.5-4b-q4_k_m", ModelSelector.recommend(p).model.id)
        assertTrue(ModelSelector.canLoadNow(ModelCatalog.byId("qwen3.5-4b-q4_k_m")!!, p).ok)
        assertFalse("9B must not fit in 5.7 GB free", ModelSelector.canLoadNow(ModelCatalog.byId("qwen3.5-9b-q4_k_m")!!, p).ok)
        assertTrue(p.hasDotProd)
    }

    @Test fun tiersByRam() {
        assertEquals("qwen3.5-0.8b-q8_0", ModelSelector.recommend(phone(3.6, 1500)).model.id)
        assertEquals("qwen3.5-2b-q4_k_m", ModelSelector.recommend(phone(5.6, 2500)).model.id)
        assertEquals("qwen3.5-4b-q4_k_m", ModelSelector.recommend(phone(7.5, 4000)).model.id)
        assertEquals("smallest model when RAM is too low", "qwen3.5-0.8b-q8_0", ModelSelector.recommend(phone(2.0, 800)).model.id)
    }

    @Test fun experimentalModelsAreNeverRecommendedAutomatically() {
        assertFalse(ModelSelector.recommend(phone(24.0, 20_000)).model.experimental)
    }

    @Test fun fallsBackToASmallerDownloadedModelWhenRamIsShort() {
        val m4 = ModelCatalog.byId("qwen3.5-4b-q4_k_m")!!
        val m2 = ModelCatalog.byId("qwen3.5-2b-q4_k_m")!!
        assertEquals(m2, ModelSelector.bestLoadable(m4, listOf(m2, m4), phone(11.2, 3200)))
        assertEquals(m4, ModelSelector.bestLoadable(m4, listOf(m2, m4), phone(11.2, 5700)))
        assertEquals(null, ModelSelector.bestLoadable(m4, listOf(m4), phone(11.2, 1000)))
    }

    @Test fun threadsDropWhenHot() {
        assertEquals(4, ModelSelector.threads(phone(12.0, 5000, perfCores = 4, thermal = 0)))
        assertEquals(3, ModelSelector.threads(phone(12.0, 5000, perfCores = 4, thermal = 2)))
        assertEquals(2, ModelSelector.threads(phone(12.0, 5000, perfCores = 4, thermal = 3)))
        assertEquals(2, ModelSelector.threads(phone(12.0, 5000, perfCores = 1)))
    }

    @Test fun contextShrinksWhenRamIsTight() {
        val m4 = ModelCatalog.byId("qwen3.5-4b-q4_k_m")!!
        assertEquals(4096, ModelSelector.contextSize(m4, phone(12.0, 5700)))
        assertEquals(2048, ModelSelector.contextSize(m4, phone(12.0, 4300)))
    }

    @Test fun contextRestartRule() {
        assertFalse(ModelSelector.shouldRestartContext(1000, 4096))
        assertTrue(ModelSelector.shouldRestartContext(3000, 4096))
    }

    @Test fun catalogIsConsistent() {
        assertEquals(ModelCatalog.all.size, ModelCatalog.all.map { it.id }.toSet().size)
        for (m in ModelCatalog.all) {
            assertTrue(m.url.startsWith("https://huggingface.co/"))
            assertTrue(m.url.endsWith("/" + m.fileName))
            assertEquals("Apache-2.0", m.licence)
            assertTrue(m.ramRequiredMb > m.approxSizeMb)
        }
    }

    @Test fun systemPromptCarriesTheRules() {
        val p = SystemPrompt.build("Qwen3.5 4B", LocalDate.of(2026, 11, 8))
        assertTrue(p.contains("8 November 2026"))
        assertTrue(p.contains("no internet access"))
        assertTrue(p.contains("Never claim you opened an app"))
        assertTrue("system prompt should stay short for fast prefill", p.length < 1600)
    }

    @Test fun benchmarkReportIsValidJsonShape() {
        val report = BenchmarkReport(
            appVersion = "0.1.0", timestampIso = "2026-11-08T10:30:00+05:30", device = phone(12.0, 5700),
            modelId = "qwen3.5-4b-q4_k_m", modelFileMb = 2700, threads = 4, contextSize = 4096, loadMs = 4200,
            nativeBench = "| model | t/s |\n", samples = listOf(
                BenchmarkSample("hindi", "भारत \"की\" राजधानी?", "नई दिल्ली", 30, 10, 900, 1000, 2000),
            ),
            ramBeforeLoadMb = 5700, appPssAfterMb = 3300, thermalStatusAfter = 1,
            batteryTempBeforeC = 31f, batteryTempAfterC = 36.5f, batteryPercentBefore = 80, batteryPercentAfter = 79,
        )
        val json = report.toJson()
        assertTrue(json.startsWith("{") && json.endsWith("}"))
        assertTrue(json.contains("\"tokens_per_second\":5.000"))
        assertTrue(json.contains("\\\"की\\\""))
        assertTrue(json.contains("\"network_used_for_inference\":false"))
        assertEquals(5.0, report.averageTokensPerSecond, 0.001)
        assertEquals("\"a\\nb\"", Json.str("a\nb"))
    }
}
