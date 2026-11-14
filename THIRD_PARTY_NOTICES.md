# Third-Party Notices

## Project license

The original source code in SonicLab is licensed under the [Apache License, Version 2.0](LICENSE). Third-party components listed below are separate works and remain subject to their respective license terms. Their inclusion does not change the license of those components.

The app includes a copy of applicable third-party notices at `app/src/main/assets/third_party_notices.txt` and makes them available in **About → Third-party credits**.

## Dependencies and acknowledgements

The following components are used by the project. Versions are those declared in the project version catalog; transitive dependencies may also be included at build time.

| Component | Version | License | Attribution |
|---|---|---|---|
| AndroidX Core, Activity, Lifecycle, and DataStore | 1.15.0 / 1.9.3 / 2.8.7 / 1.1.1 | Apache-2.0 | The Android Open Source Project |
| Jetpack Compose UI, Material 3, and Material Icons | BOM 2024.12.01 | Apache-2.0 | The Android Open Source Project |
| AndroidX Navigation Compose | 2.8.5 | Apache-2.0 | The Android Open Source Project |
| AndroidX Media3 (Common, ExoPlayer, Session, UI) | 1.5.0 | Apache-2.0 | The Android Open Source Project |
| Oboe | 1.9.0 | Apache-2.0 | Google / The Android Open Source Project |
| TensorFlow Lite runtime | 2.16.1 | Apache-2.0 | The TensorFlow Authors |
| Kotlin Standard Library and kotlinx.coroutines | 2.0.21 / 1.9.0 | Apache-2.0 | JetBrains s.r.o. |
| Kotlin Gradle Plugin / Android Gradle Plugin | 2.0.21 / 8.7.3 | Apache-2.0 | JetBrains s.r.o. / Google (build-time) |
| JUnit 4 | 4.13.2 | EPL-2.0 | Eclipse Foundation (test-time) |

For complete terms, refer to each component's license and notices distributed with the dependency. The table is an informational summary and is not a substitute for those license texts.

## Audio algorithms and standards

SonicLab's audio-processing code is implemented for this project and is informed by the referenced published techniques and standards. References to an algorithm or standard are acknowledgements, not endorsements.

- **SOLA / WSOLA time stretching and pitch processing** — based on published techniques, including Roucos and Wilgus (1985) and Laroche and Dolson (1999). No source code from SoundTouch is included.
- **EBU R128 loudness measurement** — implementation based on EBU R 128 and ITU-R BS.1770, including K-weighting and loudness gating.
- **Schroeder-style reverb** — based on the classic comb-filter and all-pass network described by Manfred R. Schroeder (1962).
- **STFT-based vocal separation** — uses a center-channel spectral-mask approach.

No DeepFilterNet model or implementation is bundled. Any future model or dependency will be credited here with its applicable license information.
