# SonicLab — AI Audio Studio

[![Android Build](https://github.com/soe1hom-arch/soniclab/actions/workflows/android-build.yml/badge.svg)](https://github.com/soe1hom-arch/soniclab/actions/workflows/android-build.yml)

Premium, **fully offline** Android music player & audio toolkit (Kotlin + Jetpack Compose + Media3).
Player, real-time DSP effects, audio toolkit, analyzer, and on-device AI enhancement — no cloud, no account, no internet required.

## Offline-first

- The app manifest declares **no `INTERNET` permission** — nothing in the app phones home.
- All processing (playback effects, toolkit, analysis, AI models) runs on-device.
- Permissions are limited to what the features need: reading local audio, foreground media playback, notifications, wake lock, and audio-session control.

## Features (matching the current APK)

**Library**
- Scans local media via MediaStore (needs the “allow access to audio” permission flow built into the app).
- Tabs for **Tracks / Albums / Artists**: album grid with art covers, artist list, and drill-down into each album/artist.
- Search by track/artist/album, favorites, and a browseable track list with album-art thumbnails.

**Mini-player (all screens)**
- Persistent now-playing bar above the bottom navigation with album art, play/pause, next, and a thin progress line; tap to open the full player.

**Player**
- Media3 ExoPlayer with a `MediaSessionService`: media notification, lock-screen controls, and Android Auto support; decoder fallback enabled for hi-res/FLAC compatibility.
- Album-art header, shuffle, previous / play-pause / next, repeat cycling, and playback speed presets (0.75×–2×).
- **Queue view**: tap any queued track to jump to it, reorder with up/down arrows.
- Crossfade (0–12 s), a sleep timer (15/30/60 min), and optional **auto normalization (ReplayGain-style)** toward −14 LUFS.
- **Mode 3D / 8D**: quick toggles on the player screen, or Equalizer > Mode 3D/8D; 3D widens the stereo image (strength slider), 8D applies a slow rotating pan with feedback echo (rotation speed slider).

**Equalizer**
- 5-band gain sliders (per-device band count from the platform `AudioEffect` API), presets, and reset.
- **Tone & effects**: Bass and Virtualizer sliders, plus a stereo **Balance** control (via a real-time audio processor in the playback chain).
- Active only while the player audio session is connected (plays when a track is loaded).

**Studio (one unified screen from the player)**
- Bound directly to the currently playing track (auto-used as File 1 when opened), with analyzer + toolkit in one place:
  - **Info File** — decode/codec info,
  - **Konversi ke WAV** — transcode to WAV via MediaCodec,
  - **Cut** — export the first 30 seconds.
- **DSP engine (on-device)** — fully wired to the UI (full-file processing is capped at ~10 minutes per file to stay within safe on-device memory):
  - **Join** — concatenate two picked files into one WAV,
  - **Reverse** — reversed audio,
  - **Pitch** (−12..+12 st) and **Tempo** (0.5×–2×) — interactive WSOLA sliders,
  - **Normalizer (−14 LUFS)** — loudness normalization,
  - **Vocal Remover** — instrumental output (stereo file required; TFLite mask + spectral fallback).
- **Analyzer** — one-tap analysis of the currently playing track or the picked file: waveform preview, audio file info, and loudness (LUFS) analysis driven by a `PcmReader` pipeline.

**AI (on-device)**
- **AI Enhance** — real-time enhancement wired into the playback path via a Media3 `AudioProcessor`; TensorFlow Lite denoiser model with a classic DSP fallback when no model is present.
- **Vocal separator** — bundled TFLite mask model (with a spectral/STFT center-channel fallback).
- Bundled models: `denoiser_v1.tflite`, `separator_v1.tflite` (shipped inside the APK assets).

**Settings**
- AMOLED (pure-black) theme, AI Enhance toggle (real-time), crossfade slider, sleep timer, and current AI model status.

## Modules (MVVM + Repository)

- `:core` — domain models (Track/Playlist/Preset), result wrapper, permissions, time utils
- `:library` — MediaStore scan & search
- `:playlist` — JSON-backed playlists + favorites
- `:player` — Media3 ExoPlayer + MediaSessionService (notification, lock screen, Android Auto), speed, crossfade, sleep timer
- `:audioengine` — Equalizer/BassBoost/Virtualizer (AudioEffect API), Oboe hook
- `:toolkit` — MediaCodec-based WAV convert / cut / file info; DSP ops: join / normalize / reverse / pitch / tempo / vocal reduction (all wired to the UI)
- `:analyzer` — FFT, spectrum buckets, LUFS meter, waveform, codec info
- `:visualizer` — Compose spectrum + waveform visualizers
- `:ai` — TensorFlow Lite enhancer (on-device) with classic DSP fallback; real-time via a Media3 AudioProcessor; vocal separator with bundled TFLite mask model + spectral fallback
- `:settings` — DataStore preferences (AMOLED, crossfade, sleep timer, EQ preset)
- `:app` — Compose UI (theme, navigation, screens)

## Build

Requirements: JDK 17+, Android SDK with `platforms;android-36` and `build-tools;36.0.0`.

```bash
./gradlew :app:assembleDebug
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`

## Test, lint & analysis

```bash
./gradlew test               # all JVM unit tests
./gradlew :toolkit:testDebugUnitTest  # DSP/PCM unit tests
./gradlew :app:lintDebug     # Android Lint
```

Verified on this repo: build succeeds, unit tests pass (9/9 on `:toolkit`), lint reports 0 errors. CI runs the same checks on every push.

## Download the APK

The repository is **public** and GitHub Actions builds a debug APK on every push to `main`:

1. Open **Actions** → the latest green **Android Build** run.
2. Download the **soniclab-debug-apk** artifact.
3. Install `app-debug.apk` on a device (Android 8.0+ / API 26+).

Or build locally with the command above.

## Bundled AI models & training

The tiny on-device models are generated from `scripts/` (pure NumPy + flatbuffers — no TensorFlow/PyTorch install needed):

```bash
python3 scripts/train_denoiser.py   # regenerates ai/.../assets/models/denoiser_v1.tflite
python3 scripts/train_separator.py  # regenerates ai/.../assets/models/separator_v1.tflite
```

## Honest status

- **Verified by CI**: compile + unit tests + lint are green; the debug APK is produced automatically.
- **Not yet verified**: real-device runtime behavior (EQ audio session behavior per device, MediaSession/notification, AI Enhance quality on real music, toolkit processing on large files). Those need manual testing on a physical Android device.

## Roadmap

- Higher-quality AI models trained on real music data.
- More WSOLA quality knobs (quality presets in the UI), release build signing.

## License

[MIT](LICENSE) © 2026 soe1hom-arch
