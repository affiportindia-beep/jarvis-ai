package com.jarvisx.app.brain

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.arm.aichat.AiChat
import com.arm.aichat.GenerationMetrics
import com.arm.aichat.InferenceEngine
import com.jarvisx.app.device.HardwareProfiler
import com.jarvisx.app.models.ModelStore
import com.jarvisx.core.model.ModelCatalog
import com.jarvisx.core.model.ModelSelector
import com.jarvisx.core.model.ModelSpec
import com.jarvisx.core.prompt.SystemPrompt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed class BrainStatus {
    object NoModel : BrainStatus()
    data class Loading(val model: ModelSpec) : BrainStatus()
    data class Ready(val model: ModelSpec, val threads: Int, val contextSize: Int, val loadMs: Long) : BrainStatus()
    data class Generating(val model: ModelSpec) : BrainStatus()
    data class Error(val message: String) : BrainStatus()
}

/**
 * The local model: llama.cpp on the phone's CPU. There is no other brain and no cloud fallback.
 *
 * One model is loaded at a time. It is unloaded after [IDLE_UNLOAD_MS] without use so the
 * phone gets its RAM back, and loading always checks free RAM first.
 */
class LocalBrain(
    context: Context,
    private val profiler: HardwareProfiler,
    private val store: ModelStore,
) {
    private val engine: InferenceEngine = AiChat.getInferenceEngine(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Mutex()
    private var idleJob: Job? = null

    private val _status = MutableStateFlow<BrainStatus>(BrainStatus.NoModel)
    val status: StateFlow<BrainStatus> = _status.asStateFlow()

    var loaded: ModelSpec? = null
        private set

    val lastMetrics: GenerationMetrics get() = engine.lastMetrics

    /** The model the app should use: the user's choice, else the recommendation. */
    fun preferredModel(): ModelSpec {
        store.selectedId?.let { id -> ModelCatalog.byId(id)?.let { return it } }
        return ModelSelector.recommend(profiler.profile()).model
    }

    /**
     * Makes sure a model is loaded. Falls back to a smaller installed model when RAM is short,
     * and says so in the returned [LoadOutcome.note].
     */
    suspend fun ensureLoaded(): LoadOutcome = lock.withLock {
        cancelIdleTimer()
        waitForEngine()
        val installed = store.installed()
        if (installed.isEmpty()) {
            _status.value = BrainStatus.NoModel
            return LoadOutcome.Failure("No model is downloaded yet. Open the Models tab and download one (Wi-Fi recommended).")
        }
        val preferred = preferredModel()
        loaded?.let { current ->
            if (current == preferred || preferred !in installed) return LoadOutcome.Success(current, null)
        }
        val profile = profiler.profile()
        val target = ModelSelector.bestLoadable(preferred, installed, profile)
            ?: return LoadOutcome.Failure(ModelSelector.canLoadNow(installed.minBy { it.ramRequiredMb }, profile).reason)
                .also { _status.value = BrainStatus.Error(it.message) }
        val note = if (target != preferred && preferred in installed) {
            "Not enough free RAM for ${preferred.displayName} right now, so ${target.displayName} is answering."
        } else null
        load(target, profile)?.let { return LoadOutcome.Failure(it) }
        LoadOutcome.Success(target, note)
    }

    /** Loads [spec] now (used by the benchmark and the Models screen). Returns an error message or null. */
    suspend fun forceLoad(spec: ModelSpec): String? = lock.withLock {
        waitForEngine()
        if (!store.isInstalled(spec)) return "${spec.displayName} is not downloaded."
        val profile = profiler.profile()
        val check = ModelSelector.canLoadNow(spec, profile)
        if (loaded != spec && !check.ok) return check.reason
        load(spec, profile)
    }

    private suspend fun load(spec: ModelSpec, profile: com.jarvisx.core.model.DeviceProfile): String? {
        unloadLocked()
        val threads = ModelSelector.threads(profile)
        val ctx = ModelSelector.contextSize(spec, profile)
        _status.value = BrainStatus.Loading(spec)
        val start = SystemClock.elapsedRealtime()
        return try {
            engine.loadModel(store.file(spec).absolutePath, threads, ctx)
            engine.setSystemPrompt(SystemPrompt.build(spec.displayName))
            // Qwen3.5 recommended non-thinking sampling: temperature 0.7, top-p 0.8, top-k 20.
            engine.setSamplerConfig(temperature = 0.7f, topK = 20, topP = 0.8f, minP = 0.0f, repeatPenalty = 1.05f)
            loaded = spec
            _status.value = BrainStatus.Ready(spec, threads, ctx, SystemClock.elapsedRealtime() - start)
            null
        } catch (e: Exception) {
            Log.e(TAG, "load failed", e)
            runCatching { engine.cleanUp() }
            loaded = null
            val msg = "Could not load ${spec.displayName}: ${e.message ?: e.javaClass.simpleName}. " +
                "If this keeps happening, delete and re-download the model."
            _status.value = BrainStatus.Error(msg)
            msg
        }
    }

    /** Starts a fresh conversation (clears the model's short-term context). */
    suspend fun newConversation(): Unit = lock.withLock {
        val spec = loaded ?: return@withLock
        if (engine.state.value is InferenceEngine.State.ModelReady) {
            engine.setSystemPrompt(SystemPrompt.build(spec.displayName))
        }
        Unit
    }

    /** True if the context is nearly full; the caller then starts a new conversation. */
    suspend fun contextNearlyFull(): Boolean {
        val (used, size) = engine.contextUsage()
        return ModelSelector.shouldRestartContext(used, size)
    }

    suspend fun contextUsage(): Pair<Int, Int> = engine.contextUsage()

    fun generate(prompt: String, maxTokens: Int = 1024): Flow<String> {
        val spec = loaded ?: error("No model loaded")
        cancelIdleTimer()
        _status.value = BrainStatus.Generating(spec)
        return engine.sendUserPrompt(prompt, maxTokens).onCompletion {
            val s = _status.value
            if (s is BrainStatus.Generating) {
                val ready = loadedReadyStatus(spec)
                _status.value = ready
            }
            scheduleIdleUnload()
        }
    }

    private var readyCache: BrainStatus.Ready? = null
    private fun loadedReadyStatus(spec: ModelSpec): BrainStatus {
        val st = engine.state.value
        if (st is InferenceEngine.State.Error) return BrainStatus.Error(st.exception.message ?: "Engine error")
        return readyCache?.takeIf { it.model == spec } ?: BrainStatus.Ready(spec, 0, 0, 0)
    }

    fun stop() = engine.stopGeneration()

    suspend fun bench(pp: Int, tg: Int): String = engine.bench(pp, tg, 1, 1)

    suspend fun nativeSystemInfo(): String = engine.systemInfo()

    suspend fun unload(): Unit = lock.withLock { unloadLocked() }

    private suspend fun unloadLocked() {
        cancelIdleTimer()
        if (loaded != null || engine.state.value is InferenceEngine.State.Error) {
            withContext(Dispatchers.Default) { engine.cleanUp() }
        }
        loaded = null
        _status.value = BrainStatus.NoModel
    }

    private suspend fun waitForEngine() {
        val st = engine.state.first { it !is InferenceEngine.State.Uninitialized && it !is InferenceEngine.State.Initializing }
        if (st is InferenceEngine.State.Error && loaded == null) {
            // A previous failure left the engine in Error; reset it so a new load can start.
            runCatching { engine.cleanUp() }
            val now = engine.state.value
            if (now is InferenceEngine.State.Error) throw IllegalStateException(
                "The AI engine could not start on this phone: ${now.exception.message}"
            )
        }
    }

    private fun scheduleIdleUnload() {
        cancelIdleTimer()
        idleJob = scope.launch {
            delay(IDLE_UNLOAD_MS)
            lock.withLock {
                if (engine.state.value is InferenceEngine.State.ModelReady) unloadLocked()
                Unit
            }
        }
    }

    private fun cancelIdleTimer() {
        idleJob?.cancel()
        idleJob = null
    }

    init {
        scope.launch {
            status.collect { if (it is BrainStatus.Ready) readyCache = it }
        }
    }

    companion object {
        private const val TAG = "LocalBrain"
        const val IDLE_UNLOAD_MS = 5 * 60 * 1000L
    }
}

sealed class LoadOutcome {
    data class Success(val model: ModelSpec, val note: String?) : LoadOutcome()
    data class Failure(val message: String) : LoadOutcome()
}
