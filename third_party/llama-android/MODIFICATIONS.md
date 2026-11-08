# Modifications made by JARVIS X

Apache-2.0 section 4(b) requires modified files to carry a notice. Each changed file has a
header line, and the full diff is `git log -p -- third_party/llama-android`.

| File | Change | Why |
| --- | --- | --- |
| `src/main/cpp/ai_chat.cpp` | Context size passed to `prepare()` (512–32768) instead of a fixed 4096 | Context is sized from free RAM |
| `src/main/cpp/ai_chat.cpp` | Thread cap raised from 4 to 8 | App passes the number of performance cores |
| `src/main/cpp/ai_chat.cpp` | New `contextUsed`, `contextSize`, `finishPartialAssistant` | Stop button keeps chat history and KV cache consistent; app can restart a full context |
| `src/main/cpp/ai_chat.cpp` | Failed `prepare()` frees the model; `systemInfo` renamed `systemInfoNative` | No half-initialised state after an out-of-memory failure |
| `src/main/cpp/CMakeLists.txt` | llama.cpp path is `<repo>/external/llama.cpp`, with a clear error if missing | Repository layout |
| `InferenceEngine.kt`, `internal/InferenceEngineImpl.kt` | Rewritten around the same JNI: `stopGeneration()`, `GenerationMetrics` (prompt/generated tokens, prefill, first-token and generation time), `contextUsage()`, removed `@FastNative` from long calls | Benchmarks and Diagnostics need real numbers; FastNative blocks GC during multi-second calls |
| `build.gradle.kts` | minSdk 30, `LLAMA_CURL=OFF`, removed DataStore dependency and publishing block | The engine must never open network connections; unused code removed |
