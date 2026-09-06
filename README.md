# 👁️ God Eye

Smart camera scene-analysis app for Android, built with **Kotlin**, **Jetpack Compose**, and **CameraX** — AI-powered scene understanding, voice alerts, and encrypted local profile storage.

[![Build APK](https://github.com/morindok/GodEye/actions/workflows/android.yml/badge.svg)](https://github.com/morindok/GodEye/actions/workflows/android.yml)
[![Platform](https://img.shields.io/badge/platform-Android%208.0%2B-green)](https://developer.android.com)
[![Language](https://img.shields.io/badge/language-Kotlin-blue.svg)](https://kotlinlang.org)
[![UI](https://img.shields.io/badge/UI-Jetpack%20Compose-7C4DFF)](https://developer.android.com/jetpack/compose)

## 📥 Download APK

1. Grab the latest `app-debug.apk` directly from the [Releases](https://github.com/morindok/GodEye/releases/latest) page.
2. Alternative: open the [Actions](https://github.com/morindok/GodEye/actions) tab and download the `GodEye-debug-APK` artifact from the latest successful **Build God Eye APK** run.
3. Install on Android 8.0+ (allow "install from unknown sources" for your browser/file manager when prompted).

## 🎯 Features

- 🔍 **AI scene analysis** — connects to your own vision model service (HTTPS + Bearer key).
- 🔊 **Automatic voice alerts** — announces results via the system TTS engine.
- 🔐 **AES-GCM encryption with Android Keystore** — secure storage for API keys and profiles.
- 📸 **Real CameraX integration** — back camera (front fallback) with runtime permission handling.
- 🎨 **Modern UI** — Jetpack Compose + Material 3.
- 🧪 **Test coverage** — JUnit unit tests and lint run automatically on every push.

## How it works

- Real camera preview with permission request; back camera first, front camera as fallback.
- Multiple model profiles: name, full HTTPS endpoint, model ID, and Bearer key.
- Profiles are stored encrypted with AES-GCM and an Android Keystore key; no hardcoded keys.
- On-demand single-shot analysis; optional periodic monitoring every 15 seconds with at most one in-flight request.
- Five-part structured report: evidence, relations, hypotheses, unknowns, and follow-up checks.
- Raw-text fallback display when the model does not return structured JSON.
- Monitoring stops and requests are cancelled when the app goes to background or the tab changes.
- Images are downscaled to a 1280px long edge, orientation-corrected, and rewritten as JPEG with EXIF stripped.
- The last analysis shows timestamp and model name; a report refers to one captured photo, not a live feed.

The name "God Eye" is metaphorical. The app cannot see through objects, read minds, identify hidden truths, or perform 3D scanning. The model may be wrong; cautionary prompt instructions do not guarantee model behavior.

## Building locally

Prerequisites: full **JDK 17**, Android SDK command-line tools, Android Platform 35, Build Tools 35.0.0, and access to Google Maven / Maven Central / Gradle.

```sh
sdkmanager "platforms;android-35" "build-tools;35.0.0" "platform-tools"
sdkmanager --licenses
export ANDROID_HOME=/path/to/Android/Sdk
bash scripts/build.sh
```

On Windows, set `ANDROID_HOME` and run:

```powershell
powershell -File scripts/build.ps1
```

The scripts download Gradle 8.9 from the official source and verify it against the published SHA-256. **The Gradle Wrapper binary is not bundled.** If Gradle 8.9 is installed, run directly:

```sh
gradle testDebugUnitTest lintDebug assembleDebug
# Optional: generate the conventional wrapper locally
gradle wrapper --gradle-version 8.9
```

In Android Studio, open the project and select JDK 17 for Gradle and Gradle 8.9 as the local distribution, or generate the wrapper first. After building:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Connecting a model

In **Models → Define Model**, provide:

- **Profile name:** any label you like.
- **HTTPS endpoint:** the full Chat Completions URL, e.g. `https://api.openai.com/v1/chat/completions`. This is an example only — nothing is preconfigured.
- **Vision model ID:** the exact ID of a multimodal (image-input) model from your own provider. No default model is assumed.
- **API key:** the key for that host. Never share your key in chats or repositories.

The app sends a `messages` request with `image_url` as a JPEG data URL, `stream: false`, and `max_tokens: 1800`. The service must accept this contract. This is not a universal API gateway: Anthropic native, Gemini native, OpenAI Responses, APIs requiring custom headers, and models incompatible with `max_tokens` need a separate adapter. Some OpenAI-compatible providers reject image data URLs.

Plain-HTTP endpoints, automatic redirects, and URLs containing credentials/query secrets are blocked. A local server must have valid HTTPS; there is no TLS exception or trust of invalid certificates.

## Usage

1. Save and select a model profile.
2. Go to the **Eye** tab and grant camera permission.
3. Optionally type a question and tap **Analyze this moment**.
4. Read the recipient address and cost notice in the confirmation dialog, then confirm.
5. For periodic monitoring, enable it separately. If responses are slow, subsequent ticks are skipped until the in-flight request finishes; this is not real-time video.
6. **Stop** cancels the active request, but data already sent to the server cannot be recalled.

## Privacy & limitations

- No trackers, no microphone permission, no location, no extra background services.
- The temporary image is created in the app's private cache and deleted after preparation or when the operation ends; leftover cleanup runs on the next start. This is not a guaranteed physical-memory wipe.
- Responses live only in session memory. There is no permanent history or cloud sync.
- The model provider receives the image and prompt and may retain them per its own policy. Get consent before photographing people or documents.
- The API key is sent to the host you configure; local encryption does not protect against entering a malicious address. Only register endpoints you trust.
- Automatic backup of settings is disabled; encryption does not guarantee security on rooted or compromised devices.
- Only the latest response is shown; there is no object geolocation or live bounding boxes. The viewfinder overlay lines are purely cosmetic.

## Project structure

- `MainActivity.kt` — UI, permissions, camera, send confirmation, monitoring.
- `EyeViewModel.kt` — session state, request control, cleanup.
- `VisionClient.kt` — network contract, cancellation, response limits, analysis prompt.
- `ProfileStore.kt` — profile encryption and storage.
- `ImageTools.kt` — image orientation/size, metadata-free JPEG.
- `Models.kt` — endpoint validation and report parsing.
- `app/src/test` — runnable unit tests.
- `docs/TEST_PLAN.md` — manual tests and acceptance criteria.
- `scripts/validate_source.py` — offline structural checks; not a substitute for compilation.

## Troubleshooting

- 401/403: check the model key/authorization.
- 400/422: the model must support image data URLs and this app's contract.
- 404: wrong endpoint or model ID.
- 429: quota, balance, or rate limit — stop monitoring.
- Gradle/SDK download errors: check build-environment internet or proxy.
- Permanently denied camera permission: use the in-app **app settings** button.

## Roadmap

First complete CI and on-device testing. Then, as separate additions: adapters for other providers, gallery image import, encrypted history, or on-device offline analysis.
