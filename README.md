# SonicLab — Offline Audio Player & Toolkit

[![Android Build](https://github.com/soe1hom-arch/soniclab/actions/workflows/android-build.yml/badge.svg)](https://github.com/soe1hom-arch/soniclab/actions/workflows/android-build.yml)


An offline Android music player and audio toolkit built with Kotlin, Jetpack Compose, and Media3.
It includes local library management, real-time playback effects, file tools, and audio analysis.

## Table of Contents

- [Screenshots](#screenshots)
- [Offline-first](#offline-first)
- [Features](#features)
- [Real-time DSP chain](#real-time-dsp-chain)
- [Modules (MVVM + Repository)](#modules-mvvm--repository)
- [Build](#build)
- [Test, lint & analysis](#test-lint--analysis)
- [Download APK](#download-apk)
- [Current status](#current-status)
- [Planned work](#planned-work)
- [License](#license)

## Screenshots

This section is waiting for screenshots from a real device. Save the files in `docs/screenshots/`
named `player.png`, `equalizer.png`, `settings.png`, and `studio.png`
(full details: see [docs/screenshots/README.md](docs/screenshots/README.md)),
then enable the table below:

<!--
| Player | Equalizer | Settings | Studio |
|---|---|---|---|
| ![Player](docs/screenshots/player.png) | ![Equalizer](docs/screenshots/equalizer.png) | ![Settings](docs/screenshots/settings.png) | ![Studio](docs/screenshots/studio.png) |
-->

## Offline-first

- The app manifest does not declare the `INTERNET` permission — nothing "phones home".
- All processing (playback effects, toolkit, analysis, AI models) runs on-device.
- Permissions are limited to features only: reading local audio, foreground media playback, notifications, wake lock, and audio session control.

## Features

### Library

- Scans local media via MediaStore (with the app's built-in "audio access" permission flow).
- **Songs / Albums / Artists** tabs: album grid with cover art, artist list, and drill-down into every album/artist.
- Search across songs/artists/albums, favorites, and a track list with cover thumbnails.
- Clear empty states for an empty library, no search results, or missing permission.

### Mini-player (all screens)

- Now-playing bar above the bottom navigation: cover, play/pause, next, and a thin progress line; tap to open the full player.

### Player

- Media3 ExoPlayer + `MediaSessionService`: media notifications, lock-screen controls, and Android Auto support; decoder fallback for hi-res/FLAC compatibility.
- Cover header, shuffle, previous / play-pause / next, repeat, and speed presets (0.75×–2×).
- **Live pitch** (−6..+6 st) — changes the pitch while the track plays, without changing the speed.
- **Track format info** — codec, sample rate, bit depth, and channels (e.g. `FLAC • 96.0 kHz • 24-bit • Stereo`) read live from the decoder.
- **Queue view** — shows only the **Up Next** tracks (items after the current one); hidden when there are none. Tap to jump, reorder with up/down arrows. From the track list, every track offers **Play Next** / **Add to Queue**.
- Crossfade (0–12 s), sleep timer (15/30/60 min), and auto-normalization toward −14 LUFS — real **EBU R128** measurement (K-weighting + 400 ms blocks + absolute/relative gating, not plain RMS).
- **3D / 8D mode** — presets **Off / 3D / 8D / 8D+Center / Surround** (quick chips in the player, full controls in the Equalizer). **8D+Center** applies 8D rotation to normal audio without mid/side widening so it doesn't sound harsh on headphones, while keeping the center audible. 8D pan never mutes one channel (the **Pan Depth** slider); **Surround** adds room echo without rotation. Effects work on every track — including mono and hi-res/FLAC — and toggles apply instantly without changing tracks.

### Sound & Effects (Equalizer)

- The Equalizer screen is organized into collapsible sections: **EQ Presets**, **3D/8D Mode**, **Tone & Effects**. Only the EQ section is expanded on entry; the rest are collapsed by default.
- **3D / 8D Mode** — ready-made presets or custom: the **3D**, **8D**, and **Surround** switches can be freely combined (mode becomes "Custom"), plus **3D Strength**, **8D Rotation Speed**, and **8D Pan Depth** sliders (limits pan movement so centered sound stays audible on headphones). **8D+Center** = 8D on normal audio with a center anchor, no stereo widening.
- **Real-time Tone & Effects** (applied while playing):
  - **Limiter (anti-clip)** — optional final safety stage for boosted presets; it is off in the clean default profile and uses a 0.98 peak ceiling when enabled,
  - **Treble** −6..+6 dB (real-time biquad shelf filter),
  - **Reverb (Room)** 0–40% in the UI + **Room Size** slider (Schroeder network: 4 combs + 2 all-passes per channel with stereo detune),
  - **Pitch (live)** −6..+6 st.
- **Software 10-band EQ** — peaking bands (31.25 Hz–16 kHz, ±15 dB) running inside the DSP chain, consistent across devices (no dependency on the deprecated `AudioEffect` API), effective even before playback starts. Rendered as a vertical slider visual (boost up, cut down). **Bass** is a real-time biquad shelf, plus stereo **Balance** and presets.
- **Reset** buttons (all / per-section) ask for confirmation before resetting.
- **Presets**: the bundled profiles use restrained curves to avoid stacking large EQ and bass boosts. They include `Music HD`, `Hi-Fi`, `Studio Monitor`, `Gaming`, `Car Audio`, `Bluetooth Speaker`, `Bass + Vocal`, `Acoustic`, and `Jazz`.
- **Automatic persistence** — balance, 3D/8D mode, treble, reverb, pitch, bass, and EQ bands are restored when the app reopens.

### Studio (single screen — opened from the "More" menu of any library track)

- The selected library track automatically becomes **File 1** (falls back to the currently playing track when opened without a selection), with analyzer + toolkit in one place:
  - **File Info** — decode/codec info,
  - **Convert to WAV** — transcode to WAV via MediaCodec,
  - **Cut** — export the first 30 seconds.
- **DSP engine (on-device)** — fully wired to the UI (full-file processing is capped at ~10 minutes per file for memory safety):
  - **Join** — merge two files into one WAV,
  - **Reverse** — play the audio backwards,
  - **Pitch** (−12..+12 st) and **Tempo** (0.5×–2×) — interactive WSOLA sliders,
  - **Normalizer (−14 LUFS)** — EBU R128-based loudness normalization,
  - **Vocal Remover** — instrumental output (requires a stereo file; STFT center-channel with soft ratio mask + smoothing).
- **Tool results** — every finished tool shows a **Result Ready** card with **Play** (play the result immediately), **Share** (share sheet via FileProvider), and **Save to Downloads** (MediaStore, Android 10+). Status messages (success/failure) appear as a dismissible **Snackbar**.
- **Analyzer** — one-tap analysis for the playing track or a picked file: waveform preview, audio file info, and **EBU R128 LUFS** loudness via the `PcmReader` pipeline.

### Enhancement and analysis

- **Enhance** — optional real-time processing on the playback path. With no model installed it uses the transparent DSP fallback, which adapts gain toward −20 dBFS and uses a soft-knee peak limiter.
- **Vocal separator** — STFT center-channel separation with a soft ratio mask; it does not require a neural model.
- **Native low-latency engine** — the `dsp` module contains an Oboe C++ engine compiled for four ABIs and exposed through JNI. It is currently a separate engine and is not used by the Media3 playback path.
- No neural model is bundled. If a compatible model is added under `dsp/src/main/assets/models/`, `NeuralEnhancer` can load it; otherwise playback uses the DSP fallback.

### Settings

- **AMOLED** theme (pure black), **AI Enhance** toggle (real-time), **Auto Normalization** (EBU R128), **Direct Mode** — rebuilds the sink without the DSP chain for the cleanest output path, **Crossfade** slider, **Sleep Timer**, AI model status, and **About App & Developer** (full info screen).
- **Output Audio**:
  - **Direct Mode** — bypasses the custom DSP chain and rebuilds the player while preserving the queue and position.
  - **Float output** — the DSP path processes audio in 32-bit float. In Direct Mode, the float-output option controls Media3's float path. The final format still depends on Android's audio mixer and the connected output device.
  - **Headroom** −3..0 dB — headroom before effects so EQ/presets are less likely to clip.

### UI & icons

- Consistent Indonesian UI throughout the app.
- **The currently playing track is highlighted** in the track list — colored title + player icon.
- Dark theme with purple/cyan accents and an AMOLED option.
- Adaptive, round, and Android 13 monochrome launcher icons.
- Empty states, screen transitions (fade + slide), About screen, haptic feedback on main controls, and album covers downsampled for RAM efficiency.

## Real-time DSP chain

All playback effects run in one Media3 `AudioProcessor` chain inside `DefaultAudioSink`:

```
balance → headroom → gain (auto-normalization) → 10-band EQ → tone (bass/treble) → reverb (room) → AI enhance → spatial (3D/8D) → limiter (anti-clip)
```

**Direct** mode bypasses the whole chain (the service rebuilds the player
without `AudioProcessor`s; position and queue are preserved).

All processors extend `PcmAudioProcessor`, which:

- accepts PCM16, PCM24, PCM32, and PCM float renderer input,
- stays always active so effect toggles apply instantly without seeking/track changes,
- up-mixes **mono → stereo** for 3D/8D effects,
- passes audio through untouched (zero-copy) when the effect is off,
- never re-encodes to PCM16 during playback: the chain stays in 32-bit float end-to-end (the 16-bit converter is only a format fallback),
- runs inside `DspAudioSink`, which converts renderer input to **32-bit float** before the chain and feeds the result to a float-capable `DefaultAudioSink`. Android's mixer or the output device may still resample or change the final hardware format.

## Modules (MVVM + Repository)

Modular structure (4 modules) with clear separation of concerns: data, audio processing, playback, and UI. Code stays grouped by package (`com.soniclab.*`), so the logical layers remain visible.

- `:core` — domain models (Track/Playlist/Preset), result wrapper, permissions, time utils, MediaStore scanning, playlists/favorites, DataStore preferences
- `:dsp` — audio signal processing & analysis: audioengine (Oboe scaffold), DSP toolkit & WAV conversion (MediaCodec), analyzer (FFT, **R128 LUFS**, waveform, codec info), and on-device AI (TensorFlow Lite + DSP fallback)
- `:player` — Media3 ExoPlayer + MediaSessionService (notifications, lock screen, Android Auto), `PcmAudioProcessor` chain (balance/headroom/gain/**10-band EQ**/tone/reverb/enhance/spatial/limiter), Direct mode, hi-res float output, speed, crossfade, sleep timer, live pitch via `PlaybackParameters`
- `:app` — Compose UI (theme, navigation, screens, About) + spectrum/waveform visualizer

## Build

Requirements: JDK 17+, Android SDK with `platforms;android-36` and `build-tools;36.0.0`.

```bash
./gradlew :app:assembleDebug
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`

## Test, lint & analysis

```bash
./gradlew test               # all JVM unit tests
./gradlew :dsp:testDebugUnitTest      # DSP/PCM toolkit unit tests
./gradlew :player:testDebugUnitTest   # player DSP chain unit tests (balance/headroom/gain/eq/tone/reverb/spatial/limiter)
./gradlew :app:lintDebug     # Android Lint
```

The latest GitHub Actions build passed on commit `8d65c27`. Device-level audio behavior still requires testing on physical hardware.

## Download APK

This repo is **public** and GitHub Actions builds a debug APK on every push to `main`:

1. Open **Actions** → latest green **Android Build** run.
2. Download the **soniclab-debug-apk** artifact.
3. Install `app-debug.apk` on a device (Android 8.0+ / API 26+).

Or build locally with the command above.

## Optional enhancement models

The application does not bundle a neural model. `NeuralEnhancer` checks for a
compatible model at `dsp/src/main/assets/models/ai_enhancer_v1.tflite`; when no
model is present it uses the DSP fallback. Model requirements are documented in
[`dsp/src/main/assets/models/README.md`](dsp/src/main/assets/models/README.md).

## Current status

- CI build, unit tests, and Android compilation pass on the latest pushed commit.
- Playback, MediaSession reconnection, and effect tuning still need broader testing on physical devices.
- Direct output uses the stock Media3 sink; DSP playback uses the custom float pipeline.

## Planned work

- Wire `OboeNativeEngine` into the playback sink (custom `AudioSink` on the fast audio path, replacing the current Media3 `DefaultAudioSink` wrapper) so the DSP chain runs inside the native engine; add live mic monitoring via the full-duplex stream.
- Optional music-trained enhancement model.
- More WSOLA quality knobs (quality presets in the UI).
- Migrating UI strings to resources (`strings.xml`) for formal multi-language support.
- Signed release build.

## License

[Apache License 2.0](LICENSE) © 2026 soe1hom-arch.

Every source file in this repository carries an SPDX header
(`SPDX-License-Identifier: Apache-2.0`) so the license is machine-readable
and traceable per file.

Third-party components (AndroidX, Media3, Oboe, TensorFlow Lite, Kotlin, …)
are credited in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md); the APK
also bundles the notices and shows them in **Tentang → Kredit pihak ketiga**.
