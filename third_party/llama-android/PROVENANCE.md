# Provenance: third_party/llama-android

| Field | Value |
| --- | --- |
| Upstream repository | https://github.com/Helldez/HearoPilot-App |
| Upstream path | `lib-llama-android/` |
| Upstream commit | `5ae6655` (2026-06-10) |
| Licence | Apache-2.0 (see `LICENSE` in this folder) |
| Origin of the code | Derived by HearoPilot from llama.cpp's Android example (`examples/llama.android`, MIT, © The ggml authors) |
| Native engine | llama.cpp, MIT, cloned at a pinned tag by CI (see `.github/workflows/android.yml`) |

The first commit that adds this folder contains the upstream files unchanged
(tests and prebuilt `jniLibs` removed). Every later change is listed in
`MODIFICATIONS.md` and is visible in `git log -- third_party/llama-android`.
