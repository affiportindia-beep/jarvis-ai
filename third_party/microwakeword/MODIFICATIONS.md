# Modifications made by JARVIS X

| File | Change | Why |
| --- | --- | --- |
| (layout) | Ava's `microfeatures` module and its `wakewords/microwakeword` Kotlin files combined into one module | One self-contained wake-word module |
| `build.gradle.kts` | Uses JARVIS X's version catalog, minSdk 30, arm64 only, CMake 3.31.6; adds LiteRT dependency | Match the app's toolchain |
| `MicroWakeWordModel.kt` | Removed Timber logging; added `lastProbability` and `reset()` | No logging dependency; diagnostics; avoid double triggers |
| `MicroWakeWordDetector.kt` | Removed dependency on Ava's `utils.fillFrom`; same function copied in below the class | Self-contained module |
| `MicroWakeWordDetector.kt` | Added `reset()` | Clear state after a detection |
