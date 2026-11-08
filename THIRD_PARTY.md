# Third-party components

Every reused component, its source, licence and what we changed.

| Component | Where | Source | Version / commit | Licence | Changed? |
| --- | --- | --- | --- | --- | --- |
| llama.cpp JNI bridge | `third_party/llama-android/` | [Helldez/HearoPilot-App](https://github.com/Helldez/HearoPilot-App) `lib-llama-android` | `5ae6655` (2026-06-10) | Apache-2.0 | Yes, see [MODIFICATIONS.md](third_party/llama-android/MODIFICATIONS.md) |
| llama.cpp | cloned to `external/llama.cpp` at build time (not committed) | [ggml-org/llama.cpp](https://github.com/ggml-org/llama.cpp) | tag in `scripts/llama_cpp_tag.txt` (b9574) | MIT | No |
| Gradle Wrapper | `gradlew`, `gradle/wrapper/` | Gradle via HearoPilot-App | Gradle 8.6 | Apache-2.0 | No |
| AndroidX / Jetpack Compose / Material 3 | Maven dependency | Google | see `gradle/libs.versions.toml` | Apache-2.0 | No |
| Kotlin stdlib, kotlinx.coroutines | Maven dependency | JetBrains | see `gradle/libs.versions.toml` | Apache-2.0 | No |
| JUnit 4 (tests only) | Maven dependency | junit.org | 4.13.2 | EPL-1.0 | No |

## Models (downloaded by the user, never bundled)

| Model | File | Source | Licence |
| --- | --- | --- | --- |
| Qwen3.5 0.8B / 2B / 4B / 9B | `Qwen3.5-*-Q*.gguf` | [Unsloth GGUF conversions](https://huggingface.co/unsloth) of [Qwen3.5](https://huggingface.co/Qwen) | Apache-2.0 |

## Planned for later milestones (licences already checked)

| Component | Purpose | Licence |
| --- | --- | --- |
| microWakeWord detector (brownard/Ava) + `hey_jarvis.tflite` (esphome/micro-wake-word-models) | "Hey Jarvis" wake word | Apache-2.0 (weights included) |
| Sherpa-ONNX | Offline speech recognition | Apache-2.0 |
| AI4Bharat IndicConformer (Hindi) | Hindi speech recognition | MIT |
| Vosk small Hindi / Indian English | Low-RAM speech recognition | Apache-2.0 |
| Bwarhness/jarvis-assistant `assist/` | Default-assistant service | Apache-2.0 |

## Rejected on licence grounds

krinry/Jarvis-android (All Rights Reserved) · Dicio code (GPL-3.0; JARVIS X is Apache-2.0) ·
openWakeWord pretrained models (CC BY-NC-SA 4.0) · Sarvam-1 (non-commercial) · Picovoice
Porcupine (proprietary access key).
