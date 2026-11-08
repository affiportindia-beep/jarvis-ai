# Installing and setting up JARVIS X

## 1. Install the app

1. On your phone, open
   **https://github.com/affiportindia-beep/jarvis-ai/releases/tag/latest-debug**
2. Tap **jarvis-x-debug.apk** to download it (about 60–90 MB).
3. Open the downloaded file. Android says installs from this source are blocked:
   tap **Settings**, switch on **Allow from this source**, go back, tap **Install**.
4. If Google Play Protect warns about an unknown app, tap **More details → Install anyway**.
   This happens for every app not installed from the Play Store.

Updating: install a newer `jarvis-x-debug.apk` over the old one. Your downloaded models stay.

## 2. Download a model (needs internet once)

1. Open JARVIS X → **Models**. The card at the top says which model suits your phone.
2. Tap **Download** on the recommended model. Use Wi-Fi: the 4B model is about 2.7 GB.
3. Android may ask to allow notifications. This only shows download progress; downloading
   works either way.
4. When the download finishes, JARVIS X checks the file's SHA-256 checksum. A damaged or
   altered file is deleted and never loaded.
5. If the download stops (network drop, phone restart), tap **Resume download**.

| Phone RAM | Recommended model | Download |
| --- | --- | --- |
| 4 GB | Qwen3.5 0.8B | ~0.9 GB |
| 6 GB | Qwen3.5 2B | ~1.3 GB |
| 8 GB or more | Qwen3.5 4B | ~2.7 GB |

## 3. Use it offline

1. Switch on **airplane mode**.
2. Open **Chat** and ask something. The first question loads the model into RAM (5–30 s);
   later questions start faster. After 5 minutes unused, the model is unloaded to free RAM.
3. Maths and time/date questions are answered instantly by exact tools; the answer says so.

## 4. Run the benchmark (Test D)

**Diagnostics → Run benchmark.** It takes 1–5 minutes. When it finishes, tap
**Share report** and send the JSON to the developer (WhatsApp, email, anything). The report
contains phone model, RAM, CPU features, speeds and the four answers; no personal data.

## Permissions guide

| Permission | Why | When it is used | If you deny it |
| --- | --- | --- | --- |
| Internet | Download model files | Only when you tap Download | Cannot download models; everything else works |
| Notifications | Show download progress | During downloads | Download still works, without a notification |
| Foreground service | Keep a large download running with the screen off | During downloads | Not a user-facing permission |

Not requested in this version: microphone, contacts, phone, SMS, location, storage,
accessibility. Each will be asked for only when the feature that needs it is added, with an
explanation first.

## Removing everything

Android **Settings → Apps → JARVIS X → Uninstall** deletes the app, all models and all data.
Nothing is stored anywhere else. Android cloud backup is switched off for this app.
