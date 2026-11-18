# SonicLab

[![Android Build](https://github.com/soe1hom-arch/soniclab/actions/workflows/android-build.yml/badge.svg)](https://github.com/soe1hom-arch/soniclab/actions/workflows/android-build.yml)

**An offline music player and audio toolkit for Android.** SonicLab plays music stored on your device and includes playback controls and audio tools. It does not require an account or internet access.

> **Project status:** SonicLab is an early-stage project. Builds are available for evaluation through GitHub Actions; a signed public release is not currently published. Audio behavior and feature support can vary by device and source file.

## Features

- **Your local library** — browse songs, albums, and artists; search tracks and save favorites.
- **Playback controls** — manage the queue, shuffle and repeat, adjust playback speed and pitch, set a sleep timer, use crossfade, and control playback from notifications, the lock screen, or Android Auto.
- **Shape the sound** — adjust the software 10-band equalizer, tone, bass, balance, reverb, spatial effects, headroom, and limiter. Optional loudness normalization helps keep levels more consistent.
- **Useful audio tools** — inspect and convert files, trim, join, reverse, adjust pitch or tempo, normalize loudness, and create an instrumental-style output from supported stereo audio.
- **Understand what you hear** — view waveform previews and audio format information, and measure loudness using EBU R128.
- **Local by design** — the app does not request the Android Internet permission. Playback and audio processing run on-device.

### A note on enhancement

Audio enhancement uses classic DSP; no neural model is included. The Studio vocal remover uses spectral center-channel processing. Results depend on the material, and the vocal remover requires stereo audio.

## Audio processing

With DSP effects enabled, decoded PCM passes through this processing chain:

```text
balance → headroom → gain → 10-band EQ → tone → reverb → enhancement → spatial processing → limiter
```

Direct Output bypasses the custom DSP chain and uses Media3's standard audio sink. Output capabilities depend on Android, the device's audio hardware, and the source format.

## Compatibility

- Android 8.0 (API 26) or later
- Android Studio or a compatible Gradle environment for building from source
- JDK 17, Android SDK Platform 36, Build Tools 36.0.0, NDK 27.2.12479018, and CMake 3.22.1 for development

## Build from source

Build a debug APK:

```bash
./gradlew :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

Run the JVM unit tests:

```bash
./gradlew test
```

Optional focused checks:

```bash
./gradlew :dsp:testDebugUnitTest
./gradlew :player:testDebugUnitTest
./gradlew :app:lintDebug
```

The Android Build workflow runs tests and builds a debug APK for pushes to `main` and pull requests. Successful workflow runs may provide a downloadable debug artifact in [GitHub Actions](https://github.com/soe1hom-arch/soniclab/actions). Debug artifacts are for evaluation and are not signed public releases.

## Project structure

| Module | Responsibility |
|---|---|
| `:app` | Jetpack Compose interface, navigation, and visualizers |
| `:core` | Domain models, local library access, preferences, and repositories |
| `:player` | Media3 playback service, queue and session control, and real-time DSP integration |
| `:dsp` | Audio processing, file tools, analysis, and enhancement |

## Support SonicLab

If SonicLab is useful to you, you can support its development through [QRIS](DONATE.md). Contributions are optional and do not unlock paid features.

## Licensing and acknowledgements

SonicLab's original source code is provided under the [Apache License, Version 2.0](LICENSE). Third-party libraries and components remain subject to their own licenses; review the in-app license screen and [third-party notices](THIRD_PARTY_NOTICES.md) for attribution and details.

The project implements audio techniques based on published standards and literature. Their use does not imply endorsement by the standards bodies or authors; see the notices file for references.

## Contributing

Bug reports, suggestions, and code contributions are welcome. Please open an issue to discuss substantial changes before submitting a pull request. By submitting a contribution, you agree that it may be distributed under this project's Apache-2.0 license.
