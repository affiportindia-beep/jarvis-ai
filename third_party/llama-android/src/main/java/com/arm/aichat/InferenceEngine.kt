// Modified by JARVIS X (2026): adds context size, stop, token metrics. See MODIFICATIONS.md.
package com.arm.aichat

import com.arm.aichat.InferenceEngine.State
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface defining the core LLM inference operations.
 */
interface InferenceEngine {
    /** Current state of the inference engine. */
    val state: StateFlow<State>

    /**
     * Load a model from the given path.
     *
     * @param nThreadsHint Number of inference threads. Pass -1 to let the engine choose.
     * @param contextSize Context window in tokens. Pass -1 for the default (4096).
     * @throws UnsupportedArchitectureException if model architecture not supported
     */
    suspend fun loadModel(pathToModel: String, nThreadsHint: Int = -1, contextSize: Int = -1)

    /** Sends a system prompt to the loaded model. Resets the conversation. */
    suspend fun setSystemPrompt(systemPrompt: String)

    /** Updates the sampler configuration without reloading the model. */
    suspend fun setSamplerConfig(
        temperature: Float,
        topK: Int,
        topP: Float,
        minP: Float,
        repeatPenalty: Float
    )

    /** Sends a user prompt and returns a Flow of generated text pieces. */
    fun sendUserPrompt(message: String, predictLength: Int = DEFAULT_PREDICT_LENGTH): Flow<String>

    /** Asks a running generation to stop after the current token. Safe to call at any time. */
    fun stopGeneration()

    /** Metrics of the last [sendUserPrompt] call. */
    val lastMetrics: GenerationMetrics

    /** Positions used in the context window right now, and its size. */
    suspend fun contextUsage(): Pair<Int, Int>

    /** Runs llama.cpp's synthetic benchmark. */
    suspend fun bench(pp: Int, tg: Int, pl: Int, nr: Int = 1): String

    /** Native system info string (CPU features seen by ggml). */
    suspend fun systemInfo(): String

    /** Unloads the currently loaded model. */
    fun cleanUp()

    /** Cleans up resources when the engine is no longer needed. */
    fun destroy()

    sealed class State {
        object Uninitialized : State()
        object Initializing : State()
        object Initialized : State()

        object LoadingModel : State()
        object UnloadingModel : State()
        object ModelReady : State()

        object Benchmarking : State()
        object ProcessingSystemPrompt : State()
        object ProcessingUserPrompt : State()

        object Generating : State()

        data class Error(val exception: Exception) : State()
    }

    companion object {
        const val DEFAULT_PREDICT_LENGTH = 1024
    }
}

/** Timing and size of one generation. */
data class GenerationMetrics(
    val promptTokens: Int = 0,
    val generatedTokens: Int = 0,
    val prefillMs: Long = 0,
    val firstTokenMs: Long = 0,
    val generationMs: Long = 0,
    val stoppedByUser: Boolean = false,
) {
    val tokensPerSecond: Double
        get() = if (generationMs > 0) generatedTokens * 1000.0 / generationMs else 0.0
    val prefillTokensPerSecond: Double
        get() = if (prefillMs > 0) promptTokens * 1000.0 / prefillMs else 0.0
}

val State.isUninterruptible
    get() = this is State.Initializing ||
        this is State.LoadingModel ||
        this is State.UnloadingModel ||
        this is State.Benchmarking ||
        this is State.ProcessingSystemPrompt ||
        this is State.ProcessingUserPrompt

val State.isModelLoaded: Boolean
    get() = this is State.ModelReady ||
        this is State.Benchmarking ||
        this is State.ProcessingSystemPrompt ||
        this is State.ProcessingUserPrompt ||
        this is State.Generating

class UnsupportedArchitectureException : Exception("Model could not be loaded (unsupported architecture or damaged file)")
