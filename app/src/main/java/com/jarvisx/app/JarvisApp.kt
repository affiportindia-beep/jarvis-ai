package com.jarvisx.app

import android.app.Application
import com.jarvisx.app.brain.Assistant
import com.jarvisx.app.brain.BenchmarkRunner
import com.jarvisx.app.brain.LocalBrain
import com.jarvisx.app.device.HardwareProfiler
import com.jarvisx.app.models.ModelStore
import com.jarvisx.app.research.WebSearch
import com.jarvisx.app.voice.VoiceInput
import com.jarvisx.app.voice.VoiceOutput
import com.jarvisx.core.router.Router
import com.jarvisx.core.tools.CalculatorTool
import com.jarvisx.core.tools.DateTimeTool
import com.jarvisx.core.tools.ToolRegistry

class JarvisApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
    }
}

/** Plain constructor wiring, no DI framework: every dependency is visible here. */
class AppGraph(app: Application) {
    val profiler = HardwareProfiler(app)
    val modelStore = ModelStore(app)
    val settings = Settings(app)
    val webSearch = WebSearch(app)
    val tools = ToolRegistry(listOf(CalculatorTool(), DateTimeTool()))
    val router = Router(tools)
    val brain = LocalBrain(app, profiler, modelStore)
    val assistant = Assistant(router, brain, webSearch, settings)
    val benchmark = BenchmarkRunner(app, brain, profiler, modelStore)
    val voiceInput = VoiceInput(app)
    val voiceOutput = VoiceOutput(app)
    val session = com.jarvisx.app.brain.ChatSession(this)
}
