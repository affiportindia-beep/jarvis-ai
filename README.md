# JARVIS X

A personal AI assistant that runs **on your Android phone**: no internet needed to answer,
no API keys, no subscriptions, no cloud AI. Free and open source (Apache-2.0).

> Status: **Milestone 1** — offline text chat with a local model, model manager, hardware
> diagnostics and benchmark. Voice ("Hey Jarvis"), memory and phone actions come in later
> milestones (see [docs/ROADMAP.md](docs/ROADMAP.md)).

## What works in this version

| Feature | Works offline | Notes |
| --- | --- | --- |
| Text chat in English, Hindi (Devanagari) and Hinglish | Yes | Qwen3.5 model running on the phone CPU via llama.cpp |
| Exact maths ("25 into 4", "2000 ka 15%") | Yes | Calculator tool, not the AI model |
| Time / date / weekday | Yes | Phone clock, not the AI model |
| Automatic model choice from your RAM, with fallback if RAM is short | Yes | 0.8B / 2B / 4B |
| Model download with resume and SHA-256 check | Needs internet once | Only network code in the app |
| Diagnostics: RAM, CPU features, heat, battery, context use | Yes | |
| Benchmark (Test D) with shareable JSON report | Yes | Load time, first-token latency, tokens/s, RAM, heat, battery |

## Install (no computer needed)

1. On the phone, open **https://github.com/amardeept-creator/jarvis-x/releases/tag/latest-debug**
2. Download **jarvis-x-debug.apk**.
3. Open it. Android asks to allow installs from your browser: allow it, then tap **Install**.
4. Open JARVIS X → **Models** → download the recommended model on Wi-Fi.
5. Turn on airplane mode and ask a question in **Chat**.

Full guide, including permissions and offline setup: [docs/INSTALL.md](docs/INSTALL.md).

## Phone requirements

64-bit ARM phone, Android 11 or newer. 6 GB RAM is the realistic minimum; 8 GB or more for
the 4B model. Storage: about 1–3 GB for one model.

## How it stays API-independent

* The only brain is llama.cpp on the phone's CPU. There is no cloud model in the code, so
  there is nothing to fall back to.
* `ModelDownloader.kt` is the only file allowed to open a network connection, and it is only
  used when you tap Download.
* CI runs [`scripts/check_no_cloud.sh`](scripts/check_no_cloud.sh) on every change and fails
  the build if any other network code, cloud-AI endpoint, analytics or ad SDK appears.

## Build from source

The APK is built by GitHub Actions on every push ([workflow](.github/workflows/android.yml)).
To build yourself: JDK 17, Android SDK 35, NDK 29.0.13113456, CMake 3.31.6, then

```bash
./scripts/fetch_llama.sh          # clones llama.cpp at the pinned tag (scripts/llama_cpp_tag.txt)
./gradlew :core:test :app:assembleDebug
```

## Repository layout

```
app/                      Android app (Kotlin, Jetpack Compose)
core/                     Assistant logic without Android: router, tools, language, model choice (unit-tested)
third_party/llama-android Reused llama.cpp JNI bridge (Apache-2.0), with MODIFICATIONS.md
eval/                     Hindi/English/Hinglish evaluation prompts for desktop quality checks
scripts/                  llama.cpp fetch, API-independence check
docs/                     Install, testing report, architecture, roadmap
```

## Credits and licences

See [THIRD_PARTY.md](THIRD_PARTY.md) and [NOTICE](NOTICE). JARVIS X is an independent project,
not affiliated with Marvel or Disney.
