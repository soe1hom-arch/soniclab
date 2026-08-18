# SonicLab — AI Audio Studio

[![Android Build](https://github.com/soe1hom-arch/soniclab/actions/workflows/android-build.yml/badge.svg)](https://github.com/soe1hom-arch/soniclab/actions/workflows/android-build.yml)


A premium, **fully offline** Android music player & audio toolkit (Kotlin + Jetpack Compose + Media3).
A player with real-time DSP effects, an audio toolkit, an analyzer, and on-device AI enhancement — no cloud, no account, no internet.

## Table of Contents

- [Screenshots](#screenshots)
- [Offline-first](#offline-first)
- [Features](#features)
- [Real-time DSP chain](#real-time-dsp-chain)
- [Modules (MVVM + Repository)](#modules-mvvm--repository)
- [Build](#build)
- [Test, lint & analysis](#test-lint--analysis)
- [Download APK](#download-apk)
- [Honest status](#honest-status)
- [Roadmap](#roadmap)
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
- **Real-time Tone & Effects** (always active, audible immediately while playing):
  - **Limiter (anti-clip)** — final DSP stage: peaks above 0 dB are compressed per-frame (instant attack, smooth release), so full boost presets or loud tracks no longer hard-clip,
  - **Treble** −12..+12 dB (real-time biquad shelf filter),
  - **Reverb (Room)** 0–100% + **Room Size** slider (Schroeder network: 4 combs + 2 all-passes per channel with stereo detune),
  - **Pitch (live)** −6..+6 st.
- **Software 10-band EQ** — peaking bands (31.25 Hz–16 kHz, ±15 dB) running inside the DSP chain, consistent across devices (no dependency on the deprecated `AudioEffect` API), effective even before playback starts. Rendered as a vertical slider visual (boost up, cut down). **Bass** is a real-time biquad shelf, plus stereo **Balance** and presets.
- **Reset** buttons (all / per-section) ask for confirmation before resetting.
- **Presets**: `Music HD` is smoother, `Gaming` (V-shape + virtualizer) differs from `Car Audio` (strong bass + boosted mids), `BT Speaker` focuses on mid-bass 100–250 Hz, plus **Bass + Vocal**, **Acoustic**, and **Jazz** (warm room reverb).
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

### AI (on-device)

- **AI Enhance** — real-time enhancement on the playback path via Media3 `AudioProcessor`; a **transparent DSP enhancer** (adaptive gain toward −18 dBFS + soft-knee limiter, no EQ coloring, no hard clip). Active for PCM16 and float/hi-res tracks.
- **Vocal separator** — STFT center-channel with soft ratio mask (no neural model).
- No neural models are bundled: the previous `denoiser_v1.tflite` was trained on synthetic 16 kHz audio and degraded real music into buzzing artifacts, so it was removed in favor of the transparent DSP enhancer.

### Settings

- **AMOLED** theme (pure black), **AI Enhance** toggle (real-time), **Auto Normalization** (EBU R128), **Direct Mode** — rebuilds the sink without the DSP chain for the cleanest output path, **Crossfade** slider, **Sleep Timer**, AI model status, and **About App & Developer** (full info screen).
- **Output Audio** (output path quality):
  - **Hi-Res 24-bit Output** — the decoder → DSP → AudioTrack path stays float (not down-converted to 16-bit); the player rebuilds automatically while preserving the queue & position,
  - **TPDF Dither + Noise Shaping** — on/off; when off, 16-bit conversion uses plain rounding,
  - **Headroom** −3..0 dB — headroom before effects so EQ/presets are less likely to clip.

### UI & icons

- Consistent Indonesian UI throughout the app.
- **The currently playing track is highlighted** in the track list — colored title + player icon.
- Premium dark theme (purple/cyan palette) with an AMOLED option.
- Premium launcher icon: adaptive icon (gradient + waveform EQ with glow), `round` icon, and `monochrome` for Android 13+ themed icons.
- Empty states, screen transitions (fade + slide), About screen, haptic feedback on main controls, and album covers downsampled for RAM efficiency.

## Real-time DSP chain

All playback effects run in one Media3 `AudioProcessor` chain inside `DefaultAudioSink`:

```
balance → headroom → gain (auto-normalization) → 10-band EQ → tone (bass/treble) → reverb (room) → AI enhance → spatial (3D/8D) → limiter (anti-clip)
```

**Direct** mode bypasses the whole chain (the service rebuilds the player
without `AudioProcessor`s; position and queue are preserved).

All processors extend `PcmAudioProcessor`, which:

- accepts **PCM16 and PCM float** (never fails on hi-res/FLAC tracks),
- stays always active so effect toggles apply instantly without seeking/track changes,
- up-mixes **mono → stereo** for 3D/8D effects,
- passes audio through untouched (zero-copy) when the effect is off,
- applies **TPDF dither + 2nd-order noise shaping** when re-encoding to PCM16 after an active effect (toggleable in Settings; passthrough stays bit-exact),
- with **Hi-Res Output** enabled, the whole chain runs in float all the way to AudioTrack.

## Modules (MVVM + Repository)

Modular structure (4 modules) with clear separation of concerns: data, audio processing, playback, and UI. Code stays grouped by package (`com.soniclab.*`), so the logical layers remain visible.

- `:core` — domain models (Track/Playlist/Preset), result wrapper, permissions, time utils, MediaStore scanning, playlists/favorites, DataStore preferences
- `:dsp` — audio signal processing & analysis: audioengine (Oboe scaffold), DSP toolkit & WAV conversion (MediaCodec), analyzer (FFT, **R128 LUFS**, waveform, codec info), and on-device AI (TensorFlow Lite + DSP fallback)
- `:player` — Media3 ExoPlayer + MediaSessionService (notifications, lock screen, Android Auto), `PcmAudioProcessor` chain (balance/headroom/gain/**10-band EQ**/tone/reverb/enhance/spatial/limiter), Direct mode, hi-res float output, dither on/off, speed, crossfade, sleep timer, live pitch via `PlaybackParameters`
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
./gradlew :player:testDebugUnitTest   # player DSP chain unit tests (balance/headroom/gain/eq/tone/reverb/spatial/limiter/dither)
./gradlew :app:lintDebug     # Android Lint
```

Status in this repo: build passes, unit tests pass (13/13 in `:dsp`, 23/23 in `:player`), lint 0 errors. CI runs the same checks on every push.

## Download APK

This repo is **public** and GitHub Actions builds a debug APK on every push to `main`:

1. Open **Actions** → latest green **Android Build** run.
2. Download the **soniclab-debug-apk** artifact.
3. Install `app-debug.apk` on a device (Android 8.0+ / API 26+).

Or build locally with the command above.

## On-device AI models

The app currently ships **without** neural models: the synthetic denoiser was
removed because its quality on real music was below the transparent DSP
enhancer. A higher-quality model trained on real music data is on the
[roadmap](#roadmap). Training scripts, when reintroduced, will live in
`scripts/` (pure NumPy + flatbuffers, no TensorFlow/PyTorch toolchain).

## Honest status

- **Verified by CI**: compile + unit tests + lint green; debug APK generated automatically.
- **Not yet verified**: runtime behavior on real devices (Direct mode & MediaSession reconnect, MediaSession/notifications, AI Enhance quality on real music, toolkit processing of large files, real-time DSP effect results). This needs manual testing on a physical Android device.

## Roadmap

- Higher-quality AI model trained on real music data.
- More WSOLA quality knobs (quality presets in the UI).
- Migrating UI strings to resources (`strings.xml`) for formal multi-language support.
- Signed release build.

## License

[Apache License 2.0](LICENSE) © 2026 soe1hom-arch.

Every source file in this repository carries an SPDX header
(`SPDX-License-Identifier: Apache-2.0`) so the license is machine-readable
and traceable per file.
