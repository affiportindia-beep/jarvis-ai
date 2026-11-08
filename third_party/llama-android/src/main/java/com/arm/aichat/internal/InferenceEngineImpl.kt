// Modified by JARVIS X (2026): context size, stop handling, token metrics, no @FastNative on
// long-running calls. See third_party/llama-android/MODIFICATIONS.md.
package com.arm.aichat.internal

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.arm.aichat.GenerationMetrics
import com.arm.aichat.InferenceEngine
import com.arm.aichat.UnsupportedArchitectureException
import com.arm.aichat.isModelLoaded
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * JNI wrapper for llama.cpp. Single instance; every native call runs on one dedicated
 * thread so the C++ globals in ai_chat.cpp are never touched concurrently.
 */
internal class InferenceEngineImpl private constructor(
    private val nativeLibDir: String
) : InferenceEngine {

    companion object {
        private val TAG = InferenceEngineImpl::class.java.simpleName

        @Volatile
        private var instance: InferenceEngine? = null

        internal fun getInstance(context: Context) =
            instance ?: synchronized(this) {
                instance ?: run {
                    val nativeLibDir = context.applicationInfo.nativeLibraryDir
                    require(nativeLibDir.isNotBlank()) { "Expected a valid native library path!" }
                    InferenceEngineImpl(nativeLibDir).also { instance = it }
                }
            }
    }

    // JNI methods, see ai_chat.cpp. @FastNative is deliberately NOT used on calls that can run
    // for seconds (model load, prefill, token decode): FastNative blocks the garbage collector.
    private external fun init(nativeLibDir: String)
    private external fun load(modelPath: String): Int
    private external fun prepare(nThreadsHint: Int, nCtx: Int): Int
    private external fun systemInfoNative(): String
    private external fun benchModel(pp: Int, tg: Int, pl: Int, nr: Int): String
    private external fun processSystemPrompt(systemPrompt: String): Int
    private external fun updateSamplerConfig(
        temperature: Float,
        topK: Int,
        topP: Float,
        minP: Float,
        repeatPenalty: Float
    ): Int
    private external fun processUserPrompt(userPrompt: String, predictLength: Int): Int
    private external fun generateNextToken(): String?
    private external fun contextUsed(): Int
    private external fun contextSize(): Int
    private external fun finishPartialAssistant(): Int
    private external fun setThinking(enabled: Boolean)
    private external fun unload()
    private external fun shutdown()

    private val _state =
        MutableStateFlow<InferenceEngine.State>(InferenceEngine.State.Uninitialized)
    override val state: StateFlow<InferenceEngine.State> = _state.asStateFlow()

    @Volatile
    private var cancelRequested = false

    /** True once libai-chat.so is loaded; an Error before that cannot be reset. */
    @Volatile
    private var nativeReady = false

    @Volatile
    private var metrics = GenerationMetrics()
    override val lastMetrics: GenerationMetrics get() = metrics

    @OptIn(ExperimentalCoroutinesApi::class)
    private val llamaDispatcher = Dispatchers.Default.limitedParallelism(1)
    private val llamaScope = CoroutineScope(llamaDispatcher + SupervisorJob())

    init {
        llamaScope.launch {
            try {
                _state.value = InferenceEngine.State.Initializing
                System.loadLibrary("ai-chat")
                init(nativeLibDir)
                nativeReady = true
                _state.value = InferenceEngine.State.Initialized
                Log.i(TAG, "Native library loaded")
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to load native library", e)
                val exception = if (e is Exception) e
                else RuntimeException("Native library failed to load: ${e.message}", e)
                _state.value = InferenceEngine.State.Error(exception)
            }
        }
    }

    override suspend fun loadModel(pathToModel: String, nThreadsHint: Int, contextSize: Int) =
        withContext(llamaDispatcher) {
            check(_state.value is InferenceEngine.State.Initialized) {
                "Cannot load model in ${_state.value.javaClass.simpleName}"
            }
            try {
                File(pathToModel).let {
                    require(it.exists()) { "Model file not found" }
                    require(it.isFile && it.canRead()) { "Model file cannot be read" }
                }
                _state.value = InferenceEngine.State.LoadingModel
                if (load(pathToModel) != 0) throw UnsupportedArchitectureException()
                if (prepare(nThreadsHint, contextSize) != 0) {
                    throw IOException("Not enough memory to create the model context")
                }
                cancelRequested = false
                _state.value = InferenceEngine.State.ModelReady
            } catch (e: Exception) {
                Log.e(TAG, "Error loading model", e)
                _state.value = InferenceEngine.State.Error(e)
                throw e
            }
        }

    override suspend fun setSystemPrompt(systemPrompt: String) =
        withContext(llamaDispatcher) {
            require(systemPrompt.isNotBlank()) { "Cannot process empty system prompt" }
            check(_state.value is InferenceEngine.State.ModelReady) {
                "Cannot process system prompt in ${_state.value.javaClass.simpleName}"
            }
            _state.value = InferenceEngine.State.ProcessingSystemPrompt
            val result = processSystemPrompt(systemPrompt)
            if (result != 0) {
                val e = RuntimeException("Failed to process system prompt: $result")
                _state.value = InferenceEngine.State.Error(e)
                throw e
            }
            _state.value = InferenceEngine.State.ModelReady
        }

    override suspend fun setSamplerConfig(
        temperature: Float,
        topK: Int,
        topP: Float,
        minP: Float,
        repeatPenalty: Float
    ) = withContext(llamaDispatcher) {
        check(_state.value.isModelLoaded) { "No model loaded" }
        updateSamplerConfig(temperature, topK, topP, minP, repeatPenalty)
        Unit
    }

    override suspend fun setThinkingEnabled(enabled: Boolean) = withContext(llamaDispatcher) {
        if (nativeReady) setThinking(enabled)
    }

    override fun stopGeneration() {
        cancelRequested = true
    }

    override suspend fun contextUsage(): Pair<Int, Int> = withContext(llamaDispatcher) {
        if (_state.value.isModelLoaded) contextUsed() to contextSize() else 0 to 0
    }

    override fun sendUserPrompt(message: String, predictLength: Int): Flow<String> = flow {
        require(message.isNotEmpty()) { "Empty prompt" }
        check(_state.value is InferenceEngine.State.ModelReady) {
            "Model is busy (${_state.value.javaClass.simpleName})"
        }
        cancelRequested = false
        var generated = 0
        var firstTokenMs = 0L
        val start = SystemClock.elapsedRealtime()
        try {
            _state.value = InferenceEngine.State.ProcessingUserPrompt
            val before = contextUsed()
            val result = processUserPrompt(message, predictLength)
            if (result != 0) throw IOException("Failed to process prompt (code $result)")
            val prefillEnd = SystemClock.elapsedRealtime()
            val promptTokens = contextUsed() - before

            _state.value = InferenceEngine.State.Generating
            while (!cancelRequested) {
                val piece = generateNextToken() ?: break
                generated++
                if (firstTokenMs == 0L) firstTokenMs = SystemClock.elapsedRealtime() - start
                if (piece.isNotEmpty()) emit(piece)
            }
            val stopped = cancelRequested
            if (stopped) finishPartialAssistant()
            metrics = GenerationMetrics(
                promptTokens = promptTokens,
                generatedTokens = generated,
                prefillMs = prefillEnd - start,
                firstTokenMs = firstTokenMs,
                generationMs = SystemClock.elapsedRealtime() - prefillEnd,
                stoppedByUser = stopped,
            )
            cancelRequested = false
            _state.value = InferenceEngine.State.ModelReady
        } catch (e: CancellationException) {
            // Collector went away (e.g. screen closed): keep the native chat state consistent.
            if (_state.value is InferenceEngine.State.Generating) finishPartialAssistant()
            cancelRequested = false
            _state.value = InferenceEngine.State.ModelReady
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error during generation", e)
            _state.value = InferenceEngine.State.Error(e)
            throw e
        }
    }.flowOn(llamaDispatcher)

    override suspend fun bench(pp: Int, tg: Int, pl: Int, nr: Int): String =
        withContext(llamaDispatcher) {
            check(_state.value is InferenceEngine.State.ModelReady) { "No model ready" }
            _state.value = InferenceEngine.State.Benchmarking
            try {
                benchModel(pp, tg, pl, nr)
            } finally {
                _state.value = InferenceEngine.State.ModelReady
            }
        }

    override suspend fun systemInfo(): String = withContext(llamaDispatcher) {
        if (_state.value is InferenceEngine.State.Uninitialized ||
            _state.value is InferenceEngine.State.Initializing
        ) "" else systemInfoNative()
    }

    override fun cleanUp() {
        cancelRequested = true
        runBlocking(llamaDispatcher) {
            when (_state.value) {
                is InferenceEngine.State.ModelReady -> {
                    _state.value = InferenceEngine.State.UnloadingModel
                    unload()
                    _state.value = InferenceEngine.State.Initialized
                }
                is InferenceEngine.State.Error -> {
                    if (nativeReady) {
                        unload() // frees whatever a failed load or prompt left behind (null-safe)
                        _state.value = InferenceEngine.State.Initialized
                    }
                }
                else -> Unit
            }
            Unit
        }
        cancelRequested = false
    }

    override fun destroy() {
        cancelRequested = true
        runBlocking(llamaDispatcher) {
            when (_state.value) {
                is InferenceEngine.State.Uninitialized -> {}
                is InferenceEngine.State.Initialized -> shutdown()
                else -> { unload(); shutdown() }
            }
            Unit
        }
        llamaScope.cancel()
    }
}
