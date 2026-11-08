# Testing report — Milestone 1

Status legend: ✅ passed and verified · 🟡 built, waiting for a run on the phone · ⬜ later milestone.
Nothing is marked passed unless the test actually ran.

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
