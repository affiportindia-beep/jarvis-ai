package com.jarvisx.app.ui.diagnostics

import android.app.Application
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jarvisx.app.BuildConfig
import com.jarvisx.app.JarvisApp
import com.jarvisx.app.brain.BrainStatus
import com.jarvisx.app.ui.common.Card
import com.jarvisx.app.ui.common.KeyValue
import com.jarvisx.app.ui.common.SectionTitle
import com.jarvisx.app.ui.theme.Jx
import com.jarvisx.core.model.DeviceProfile
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DiagState(
    val profile: DeviceProfile? = null,
    val appPssMb: Long = 0,
    val context: Pair<Int, Int> = 0 to 0,
    val nativeInfo: String = "",
    val benchRunning: Boolean = false,
    val benchLog: List<String> = emptyList(),
    val reportSummary: String? = null,
    val reportJson: String? = null,
    val reportPath: String? = null,
)

class DiagnosticsViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = (app as JarvisApp).graph
    val brain = graph.brain.status
    private val _state = MutableStateFlow(DiagState())
    val state: StateFlow<DiagState> = _state.asStateFlow()

    private var loop: kotlinx.coroutines.Job? = null

    /** Refreshes live numbers every 2 s. Safe to call on every screen visit: one loop only. */
    fun refreshLoop() {
        if (loop?.isActive == true) return
        loop = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) { pollForever() }
    }

    private suspend fun pollForever() {
        while (true) {
            val ctx = runCatching { graph.brain.contextUsage() }.getOrDefault(0 to 0)
            _state.update { it.copy(profile = graph.profiler.profile(), appPssMb = graph.profiler.appPssMb(), context = ctx) }
            if (_state.value.nativeInfo.isEmpty()) {
                val info = runCatching { graph.brain.nativeSystemInfo() }.getOrDefault("")
                _state.update { it.copy(nativeInfo = info) }
            }
            delay(2000)
        }
    }

    fun runBenchmark() {
        if (_state.value.benchRunning) return
        val spec = graph.brain.preferredModel()
        _state.update { it.copy(benchRunning = true, benchLog = emptyList(), reportSummary = null, reportJson = null) }
        viewModelScope.launch {
            val result = graph.benchmark.run(spec) { step -> _state.update { it.copy(benchLog = it.benchLog + step) } }
            result.fold(
                onSuccess = { (report, file) ->
                    _state.update {
                        it.copy(benchRunning = false, reportSummary = report.summary(), reportJson = report.toJson(), reportPath = file.absolutePath)
                    }
                },
                onFailure = { e ->
                    _state.update { it.copy(benchRunning = false, benchLog = it.benchLog + "Failed: ${e.message}") }
                },
            )
        }
    }
}

@Composable
fun DiagnosticsScreen(vm: DiagnosticsViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val brain by vm.brain.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(Unit) { vm.refreshLoop() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Diagnostics", style = MaterialTheme.typography.headlineSmall, color = Jx.Text)

        Card {
            SectionTitle("Privacy status")
            KeyValue("AI inference", "on this phone (llama.cpp, CPU)")
            KeyValue("Cloud AI / API keys", "none — not present in the app")
            KeyValue("Network use", "model downloads only, when you tap Download")
            KeyValue("Microphone", "not used in this version")
        }

        state.profile?.let { p ->
            Card {
                SectionTitle("This phone")
                KeyValue("Device", "${p.manufacturer} ${p.model}")
                KeyValue("Chip", p.socModel)
                KeyValue("Android SDK", p.androidSdk.toString())
                KeyValue("RAM", "%.1f GB total · %.1f GB free".format(p.totalRamMb / 1024.0, p.availableRamMb / 1024.0))
                KeyValue("CPU", "${p.cpuCores} cores, ${p.performanceCores} performance")
                KeyValue("CPU features", listOfNotNull(
                    "dotprod".takeIf { p.hasDotProd }, "i8mm".takeIf { p.hasI8mm }, "sve".takeIf { "sve" in p.cpuFeatures },
                ).ifEmpty { listOf("basic NEON") }.joinToString(", "))
                KeyValue("Storage free", "%.1f GB".format(p.freeStorageMb / 1024.0))
                KeyValue("Heat", thermalLabel(p.thermalStatus) + (p.batteryTempC?.let { " · battery %.1f °C".format(it) } ?: ""))
                KeyValue("JARVIS X memory", "${state.appPssMb} MB")
            }
        }

        Card {
            SectionTitle("Local model")
            when (val b = brain) {
                BrainStatus.NoModel -> KeyValue("State", "not loaded (loads on your next question)")
                is BrainStatus.Loading -> KeyValue("State", "loading ${b.model.displayName}…")
                is BrainStatus.Ready -> {
                    KeyValue("Model", b.model.displayName)
                    if (b.threads > 0) KeyValue("Threads", b.threads.toString())
                    if (b.loadMs > 0) KeyValue("Load time", "${b.loadMs} ms")
                    KeyValue("Context", "${state.context.first} / ${state.context.second} tokens used")
                }
                is BrainStatus.Generating -> KeyValue("State", "${b.model.displayName} is answering")
                is BrainStatus.Error -> Text(b.message, color = Jx.Bad, style = MaterialTheme.typography.bodyMedium)
            }
            if (state.nativeInfo.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(state.nativeInfo, color = Jx.TextDim, style = MaterialTheme.typography.labelSmall)
            }
        }

        Card {
            SectionTitle("Benchmark (Test D)")
            Text(
                "Loads the model fresh, runs llama.cpp's speed test, then asks 4 questions (English, Hindi, " +
                    "Hinglish, reasoning). Takes 1–5 minutes. Keep the screen on and the phone unplugged for a fair battery reading.",
                color = Jx.TextDim, style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = vm::runBenchmark,
                enabled = !state.benchRunning,
                colors = ButtonDefaults.buttonColors(containerColor = Jx.Accent),
            ) { Text(if (state.benchRunning) "Running…" else "Run benchmark") }
            state.benchLog.forEach { Text("› $it", color = Jx.TextDim, style = MaterialTheme.typography.labelSmall) }
            state.reportSummary?.let { summary ->
                Spacer(Modifier.height(8.dp))
                SelectionContainer { Text(summary, color = Jx.Text, style = MaterialTheme.typography.bodyMedium) }
                state.reportPath?.let { Text("Saved: $it", color = Jx.TextDim, style = MaterialTheme.typography.labelSmall) }
                Spacer(Modifier.height(8.dp))
                Row {
                    OutlinedButton(onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "JARVIS X benchmark report")
                            putExtra(Intent.EXTRA_TEXT, state.reportJson ?: summary)
                        }
                        context.startActivity(Intent.createChooser(send, "Share benchmark report"))
                    }) { Text("Share report") }
                }
            }
        }

        Text("JARVIS X ${BuildConfig.VERSION_NAME}", color = Jx.TextDim, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(12.dp))
    }
}

private fun thermalLabel(s: Int) = when (s) {
    0 -> "normal"
    1 -> "light"
    2 -> "moderate (threads reduced)"
    3 -> "severe (threads halved)"
    4 -> "critical"
    5 -> "emergency"
    6 -> "shutdown"
    else -> "unknown"
}
