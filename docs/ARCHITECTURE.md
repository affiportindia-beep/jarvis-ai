# Architecture (Milestone 1)

```
 Chat screen ──▶ Assistant ──▶ Router ──▶ Tool registry ──▶ Calculator · Phone clock
                   │                         (exact answers, verified, time-limited)
                   └──▶ LocalBrain ──▶ llama.cpp JNI (third_party/llama-android) ──▶ Qwen3.5 GGUF on CPU
                          │
                          ├─ HardwareProfiler: RAM, CPU features, performance cores, heat, battery
                          ├─ ModelSelector (core): which model, threads, context size, fallback
                          └─ ModelStore: files/models/*.gguf + verified SHA-256 marker

 Models screen ──▶ ModelDownloadService (foreground) ──▶ ModelDownloader  ← the ONLY network code
 Diagnostics  ──▶ BenchmarkRunner ──▶ BenchmarkReport (core) → JSON file + share sheet
```

## Rules the code follows

1. **Tools first, model second.** A deterministic tool answers when it is confident (≥ 0.8);
   otherwise the local model answers. Tool answers say they came from a tool.
2. **Every tool** declares purpose, permission, time limit; validates input in `confidence()`;
   checks its own output in `verify()`. A failed check hides the answer and says why.
3. **One model in RAM.** Free RAM is checked before loading; a smaller installed model is used
   if needed, and the chat says so. Unloaded after 5 minutes idle.
4. **Threads = performance cores**, reduced on heat (thermal status 2 → −1, ≥3 → half).
5. **Context 4096 tokens** when RAM allows, else 2048. At 85 % full, a fresh context starts and
   the chat says so.
6. **Qwen3.5 thinking mode is off** (empty `<think>` block pre-filled), keeping answers fast.
7. **No network in the brain.** llama.cpp is built with no curl/OpenSSL; CI enforces the rest.

## Modules

| Module | Android? | Contents |
| --- | --- | --- |
| `core` | No (plain Kotlin/JVM) | Language detection, tools, router, system prompt, model catalog and selection, benchmark report |
| `third_party/llama-android` | Yes (NDK) | llama.cpp JNI bridge, reused and modified |
| `app` | Yes | UI, hardware profiler, downloads, LocalBrain, Assistant, BenchmarkRunner |
