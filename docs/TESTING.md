# Testing report — Milestone 1 + online search + fixes

Status legend: ✅ passed and verified · 🟡 built, waiting for a run on the phone · ⬜ later milestone.
Nothing is marked passed unless the test actually ran.

## Results so far (2026-11-08)

| Check | Result | Evidence |
| --- | --- | --- |
| API-independence guard | ✅ passed | CI run 42453800461, step "API-independence check" |
| Unit tests (`core`) | ✅ 44/44 passed (build 11) | CI step "Unit tests (core)" |
| Online search parsers on LIVE Google News + Wikipedia responses | ✅ passed | `ResearchTest.liveResponsesStillParse` in CI |
| Native build: llama.cpp b9574 + JNI bridge for arm64 | ✅ compiled; 7 CPU variants (armv8.0 → armv9.2) packaged | same run, step "Inspect APK" |
| Debug APK | ✅ built, 99 MB | release `latest-debug` |
| APK permissions | ✅ only INTERNET, ACCESS_NETWORK_STATE, FOREGROUND_SERVICE(_DATA_SYNC), POST_NOTIFICATIONS | `aapt2 dump permissions` in CI |
| Model URLs and checksums | ✅ all 4 exist; SHA-256 pinned in `ModelCatalog.kt` | CI "Model catalog check" |
| Install and run on a phone | 🟡 waiting for the first install | — |

## Bugs found on the phone and fixed

| Report | Cause | Fix |
| --- | --- | --- |
| APK download "Failed - No file" | Each build deleted and re-created the release while it was downloading | One numbered release per build; nothing deleted |
| Answers cut off mid-sentence in longer chats (53, 44, 11 tokens) | Context window full; shifting it corrupts Qwen3.5's recurrent memory and the failure looked like a normal end | Native layer never shifts recurrent memory and reports why generation ended; app restarts the context before a turn that might not fit, retries once, and marks any incomplete answer; context 8192 when RAM allows |
| Follow-up "so what should you do" answered generically | Small model loses the thread | Prompt rule for follow-ups; suggests the online-search button; eval cases added (`en-followup`, `hinglish-followup`) |

## Answer quality: desktop eval (2026-11-08)

16 prompts (7 English, 4 Hindi, 5 Hinglish) per model, with the app's exact system prompt and
language hint, llama.cpp b9574 on a GitHub CPU. One run per model at temperature 0.7, so
results vary between runs. Speeds are a desktop CPU's, not a phone's.

| Model | Correct script | Expected fact present | Notes |
| --- | --- | --- | --- |
| Qwen3.5 4B Q4_K_M | 16/16 | 7/7 | Correct Hindi maths after the fraction-word fix; honest about no internet; refused fake actions; disagreed with a risky plan. Earlier run invented a second Indian capital in Hindi. |
| Qwen3.5 2B Q4_K_M | 16/16 | 7/7 | Correct facts, but mentioned specific finance websites when asked for a live price. |
| Qwen3.5 0.8B Q8_0 | 16/16 | 3/7 | Frequent invented facts (wrong capital, made-up gold price). Not recommended above 4 GB RAM. |

Fixes made from this eval: Hindi fraction words (ढाई, डेढ़, साढ़े, आधा, पाव, पौने and Roman
spellings) get their value added before the model sees them (`HindiNumberWords`, tested);
the system prompt now lists what this version can and cannot do and forbids inventing
websites, places or sources. Before the fix every model read "ढाई" as 1.5 or 0.5.

Typo and follow-up cases (4B): `wht is the captial of jappan` → Tokyo ✅ · `mahatma gandi ka janm kab hua tha` → 1869 ✅ · `aur Mahabharata?` after a Ramayana question → Vyasa ✅ · `so what should you do` → generic advice ❌ (prompt fix added afterwards).

Remaining known limit: small models still invent facts sometimes, more often in Hindi than in
English. JARVIS X says so on the About screen; document-grounded answers (Milestone 5) and
memory (Milestone 3) will reduce this for the user's own information.

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
| C — Hindi and Hinglish (text) | ✅ desktop eval (4B: 16/16 script, 7/7 facts) · 🟡 phone | Diagnostics → Run benchmark; review eval workflow output |
| D — CPU inference numbers | 🟡 | Diagnostics → Run benchmark → Share report |
| E — Android actions | ⬜ Milestone 4 | — |
| F — Permission safety | ✅ by design for M1: no sensitive permission requested | See permissions guide |
| G — Document understanding | ⬜ Milestone 5 | — |
| H — Recovery | 🟡 | Kill the app during model load; delete a model mid-download; reopen |
| I — Performance / heat | 🟡 | Benchmark report: thermal status and battery temperature before/after |
| J — Licence audit | ✅ | `THIRD_PARTY.md`, `NOTICE`, `third_party/llama-android/MODIFICATIONS.md` |
| API independence | ✅ statically (CI guard) · 🟡 on phone | Airplane mode, no keys exist to remove |

Phone results are added here when the first benchmark report arrives.
