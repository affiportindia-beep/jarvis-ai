# Roadmap

| Milestone | Scope | Reuses |
| --- | --- | --- |
| **M1 (this build)** | Offline text chat, model manager with SHA-256, hardware profiler, diagnostics, benchmark, tool registry, calculator, clock, CI with API-independence guard | HearoPilot llama bridge |
| M2 — Voice | Speech-to-text (Sherpa-ONNX + IndicConformer Hindi, Vosk fallback), Android TTS, "Hey Jarvis" wake word in a visible foreground service, stop/repeat, barge-in where the phone supports it | Ava microWakeWord, HearoPilot Sherpa binding |
| M3 — Memory | SQLite + FTS5 chat history and long-term memory with approval, project contexts, memory dashboard (view/edit/delete) | new |
| M4 — Phone actions | Open apps, alarms, timers, flashlight, maps, websites, prepared calls/SMS/WhatsApp with confirmation, default-assistant gesture | Bwarhness assist service |
| M5 — Documents | Import PDF/TXT/Markdown/code, local search, answers that cite the document | new |
| M6 — Coding and security | Project workspace, code search, static security checks of your own apps (manifest, permissions, secrets) | new |
| M7 — Research and self-improvement | Optional online research with sources and dates, feedback memory, benchmark history, proposed changes with approval | new |
| Release | Signed release build, full regression, permission and performance tests | — |
