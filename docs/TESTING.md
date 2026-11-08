# Testing report — Milestone 1

Status legend: ✅ passed and verified · 🟡 built, waiting for a run on the phone · ⬜ later milestone.
Nothing is marked passed unless the test actually ran.

## Results so far (2026-11-08)

| Check | Result | Evidence |
| --- | --- | --- |
| API-independence guard | ✅ passed | CI run 42453800461, step "API-independence check" |
| Unit tests (`core`) | ✅ 30/30 passed | same run, step "Unit tests (core)" |
| Native build: llama.cpp b9574 + JNI bridge for arm64 | ✅ compiled; 7 CPU variants (armv8.0 → armv9.2) packaged | same run, step "Inspect APK" |
| Debug APK | ✅ built, 99 MB | release `latest-debug` |
| APK permissions | ✅ only INTERNET, ACCESS_NETWORK_STATE, FOREGROUND_SERVICE(_DATA_SYNC), POST_NOTIFICATIONS | `aapt2 dump permissions` in CI |
| Model URLs and checksums | ✅ all 4 exist; SHA-256 pinned in `ModelCatalog.kt` | CI "Model catalog check" |
| Install and run on a phone | 🟡 waiting for the first install | — |

The APK is large because debug builds keep native debug symbols (`libllama-common.so` alone is
~70 MB). A stripped release build in a later milestone should be much smaller.

## Automated tests (run in CI on every push)

| Test | What it checks | Where |
| --- | --- | --- |
| API-independence guard | No cloud-AI endpoint, telemetry or ad SDK anywhere; network code only in `ModelDownloader.kt`; no networking/analytics dependency | `scripts/check_no_cloud.sh` |
| Language detection | English / Hindi / Hinglish, ambiguous English words | `core/src/test/.../LanguageDetectorTest.kt` |
| Calculator | Operators, precedence, %, roots, Hinglish/Hindi phrasings, Devanagari digits, Indian digit grouping, division by zero, non-maths rejected, dates rejected | `CalculatorToolTest.kt` |
| Router and tools | Maths → calculator, clock questions → phone clock, rest → model with language hint; tool time limit; failed verification hides the answer | `RouterAndToolsTest.kt` |
| Model selection | Note 20 Ultra gets 4B; 9B refused at 5.7 GB free; tiers by RAM; fallback to smaller model; threads drop with heat; context shrinks with low RAM | `ModelSelectorTest.kt` |
| Benchmark report | Valid JSON shape, escaping, `network_used_for_inference: false` | `ModelSelectorTest.kt` |
| Eval sync | Desktop eval uses the app's exact language hints | `EvalSyncTest.kt` |
| Native compile | JNI bridge compiles against llama.cpp at the pinned tag | APK build job |
| Model URLs | Every catalog file exists on Hugging Face and publishes a SHA-256 | `.github/workflows/model-catalog.yml` |
| Answer quality (desktop) | 16 Hindi/English/Hinglish prompts per model; script check + keyword check; answers saved for review | `.github/workflows/model-eval.yml` |

## Acceptance tests from the brief

| Test | Milestone 1 status | How to run it |
| --- | --- | --- |
| A — Offline operation (chat) | 🟡 | Airplane mode → ask 5 questions in Chat |
| B — Persistent memory | ⬜ Milestone 3 | — |
| C — Hindi and Hinglish (text) | 🟡 desktop eval + phone benchmark | Diagnostics → Run benchmark; review eval workflow output |
| D — CPU inference numbers | 🟡 | Diagnostics → Run benchmark → Share report |
| E — Android actions | ⬜ Milestone 4 | — |
| F — Permission safety | ✅ by design for M1: no sensitive permission requested | See permissions guide |
| G — Document understanding | ⬜ Milestone 5 | — |
| H — Recovery | 🟡 | Kill the app during model load; delete a model mid-download; reopen |
| I — Performance / heat | 🟡 | Benchmark report: thermal status and battery temperature before/after |
| J — Licence audit | ✅ | `THIRD_PARTY.md`, `NOTICE`, `third_party/llama-android/MODIFICATIONS.md` |
| API independence | ✅ statically (CI guard) · 🟡 on phone | Airplane mode, no keys exist to remove |

Phone results are added here when the first benchmark report arrives.
