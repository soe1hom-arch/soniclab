# Third-Party Notices

## Project license

The original source code in SonicLab is licensed under the [Apache License, Version 2.0](LICENSE). Third-party components listed below are separate works and remain subject to their respective license terms. Their inclusion does not change the license of those components.

The app displays its bundled notices and license texts in **About → Third-party credits**. The notice is at `app/src/main/assets/third_party_notices.txt`; additional upstream license texts are in [`app/src/main/assets/licenses`](app/src/main/assets/licenses).

## Dependencies and acknowledgements

Versions below are those declared in the version catalog unless otherwise noted. CI resolves and archives the exact `:app:releaseRuntimeClasspath` dependency report and builds an unsigned release APK for each source revision. The list summarizes component families; the archived report and release APK native-library inventory provide the revision-specific audit inputs.

| Component | Version | License | Attribution |
|---|---|---|---|
| AndroidX Core, Activity, Lifecycle, DataStore, and Graphics Path | 1.15.0 / 1.9.3 / 2.8.7 / 1.1.1 / transitive | Apache-2.0 | The Android Open Source Project |
| Jetpack Compose UI, Material 3, and Material Icons | BOM 2024.12.01 | Apache-2.0 | The Android Open Source Project |
| AndroidX Navigation Compose | 2.8.5 | Apache-2.0 | The Android Open Source Project |
| AndroidX Media3 (Common, ExoPlayer, Session, UI) | 1.5.0 | Apache-2.0 | The Android Open Source Project |
| Oboe | 1.9.0 | Apache-2.0 | Google / The Android Open Source Project |
| Kotlin libraries, kotlinx.coroutines, and kotlinx.serialization | 2.0.21 / 1.9.0 / 1.6.3 | Apache-2.0 | JetBrains s.r.o. and contributors |
| JetBrains annotations | 23.0.0 | Apache-2.0 | JetBrains s.r.o. |
| Guava and failureaccess | 33.3.1-android / 1.0.2 | Apache-2.0 | Google LLC and contributors |
| Okio | 3.4.0 | Apache-2.0 | Square, Inc. |
| LLVM libc++ shared runtime (Android NDK) | NDK 27.2.12479018 | Apache-2.0 with LLVM exceptions; additional notices in upstream license | LLVM Project contributors |
| GitHub Octicons | mark-github-24 | MIT | GitHub Inc. |
| Kotlin Gradle Plugin / Android Gradle Plugin | 2.0.21 / 8.7.3 | Apache-2.0 | JetBrains s.r.o. / Google (build-time) |
| JUnit 4 | 4.13.2 | EPL-2.0 | Eclipse Foundation (test-time) |

For complete terms, refer to each component's license and notices distributed with the dependency. The table is an informational summary and is not a substitute for those license texts.

The TensorFlow Lite runtime is intentionally not included: SonicLab currently ships no neural model, so AI Enhance uses the classic DSP fallback. This also excludes TensorFlow Lite's bundled native code, including OouraFFT and the associated unresolved redistribution question. Revisit runtime selection, model quality, licenses, and the complete native dependency inventory before adding a model. CI's release audit covers Gradle runtime dependencies and shared libraries in the unsigned APK; recheck it whenever dependencies or build variants change. This inventory is not legal advice.

## Audio algorithms and standards

SonicLab's audio-processing code is implemented for this project and is informed by the referenced published techniques and standards. References to an algorithm or standard are acknowledgements, not endorsements.

- **SOLA / WSOLA time stretching and pitch processing** — based on published techniques, including Roucos and Wilgus (1985) and Laroche and Dolson (1999). No source code from SoundTouch is included.
- **EBU R128 loudness measurement** — implementation based on EBU R 128 and ITU-R BS.1770, including K-weighting and loudness gating.
- **Schroeder-style reverb** — based on the classic comb-filter and all-pass network described by Manfred R. Schroeder (1962).
- **STFT-based vocal separation** — uses a center-channel spectral-mask approach.

No DeepFilterNet model or implementation is bundled. Any future model or dependency will be credited here with its applicable license information.
