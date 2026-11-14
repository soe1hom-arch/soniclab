# SonicLab

[![Android Build](https://github.com/soe1hom-arch/soniclab/actions/workflows/android-build.yml/badge.svg)](https://github.com/soe1hom-arch/soniclab/actions/workflows/android-build.yml)

**An offline-first music player and audio toolkit for Android.** SonicLab brings local music playback, real-time sound controls, and practical audio utilities together in one app. It is built with Kotlin, Jetpack Compose, and AndroidX Media3, with audio processing performed on the device.

## Highlights

- **Local music library** — browse songs, albums, and artists; search tracks and manage favorites.
- **Playback** — queue controls, shuffle and repeat, playback speed and pitch, sleep timer, crossfade, lock-screen controls, and Android Auto support.
- **Sound controls** — software 10-band equalizer, tone controls, room reverb, stereo/spatial modes, headroom, limiter, and optional loudness normalization.
- **Audio tools** — inspect, convert, trim, join, reverse, adjust pitch or tempo, normalize loudness, and create an instrumental-style output from supported stereo audio.
- **Analysis** — waveform preview, audio format information, and EBU R128 loudness measurement.
- **Offline by design** — no network permission is declared in the app manifest. Music playback and audio processing run locally.

## Audio processing

When DSP effects are enabled, decoded PCM is processed through the app's float-domain chain:

```text
balance → headroom → gain → 10-band EQ → tone → reverb → enhancement → spatial processing → limiter
```

Direct Output bypasses the custom DSP chain and uses Media3's standard audio sink. Output capabilities can vary with Android versions, device audio hardware, and source format.

## Requirements

- Android 8.0 (API 26) or later
- For development: JDK 17+ and Android SDK Platform 36 with Build Tools 36.0.0

## Build

Build a debug APK locally:

```bash
./gradlew :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Tests and checks

Run the JVM unit tests:

```bash
./gradlew test
```

Run module-specific tests or Android lint:

```bash
./gradlew :dsp:testDebugUnitTest
./gradlew :player:testDebugUnitTest
./gradlew :app:lintDebug
```

The GitHub Actions workflow runs the unit tests and builds a debug APK for pushes to `main` and pull requests. Open [Actions](https://github.com/soe1hom-arch/soniclab/actions) and select a successful **Android Build** run to download its `soniclab-debug-apk` artifact. This is a debug build, not a signed production release.

## Project structure

| Module | Responsibility |
|---|---|
| `:app` | Jetpack Compose interface, navigation, and visualizers |
| `:core` | Domain models, local library access, preferences, and repositories |
| `:player` | Media3 playback service, queue and session control, and real-time DSP integration |
| `:dsp` | Audio processing, file tools, analysis, and optional on-device enhancement support |

## Support the project

If SonicLab is useful to you, you can support its continued development or buy the developer a coffee. Donation options and editable setup notes are in [DONATE.md](DONATE.md). Payment links and QR details are intentionally left as placeholders for the maintainer to complete.

## Licensing and acknowledgements

SonicLab's original source code is provided under the [Apache License, Version 2.0](LICENSE). Third-party libraries and components remain subject to their respective licenses; see [Third-Party Notices](THIRD_PARTY_NOTICES.md) and the bundled in-app notices for attribution and component details.

The project implements audio techniques based on published standards and literature. Their use does not imply endorsement by the standards bodies or authors. See the notices file for algorithm references and additional acknowledgements.

## Contributions

Bug reports, suggestions, and code contributions are welcome. Please open an issue to discuss substantial changes before submitting a pull request. By submitting a contribution, you agree that it is provided under the repository's Apache-2.0 license, subject to any separate written agreement.
