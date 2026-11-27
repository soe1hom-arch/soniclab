# Third-Party Notices

## Project license

The original source code in SonicLab is licensed under the [Apache License, Version 2.0](LICENSE). Third-party components listed below are separate works and remain subject to their respective license terms. Their inclusion does not change the license of those components.

The app displays its bundled notices and license texts in **About → Third-party credits**. The notice is at `app/src/main/assets/third_party_notices.txt`; additional upstream license texts are in [`app/src/main/assets/licenses`](app/src/main/assets/licenses).

## Dependencies and acknowledgements

The following table summarizes the principal third-party components included in the app. Versions are those declared in the project unless otherwise noted. Transitive dependency versions may vary with dependency updates.

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
| SONICOM Acoustic HRTFs 48k | 2021-2025 | MIT | Engel, I., Daugintis, R., Vicente, T., Hogg, A., Pauwels, J., Tournier, A., Meyer, J., Martin, V., Marggraf-Turley, N., Webb, J., Pirard, L., La Magna, N., Turvey, O., Poole, K., and Picinali, L. |

For complete terms, refer to each component's license and notices distributed with the dependency. The table is an informational summary and is not a substitute for those license texts.

## Audio algorithms and standards

SonicLab's audio-processing code is implemented for this project and is informed by the referenced published techniques and standards. References to an algorithm or standard are acknowledgements, not endorsements.

- **SOLA / WSOLA time stretching and pitch processing** — based on published techniques, including Roucos and Wilgus (1985) and Laroche and Dolson (1999).
- **EBU R128 loudness measurement** — implementation based on EBU R 128 and ITU-R BS.1770, including K-weighting and loudness gating.
- **Schroeder-style reverb** — based on the classic comb-filter and all-pass network described by Manfred R. Schroeder (1962).
- **STFT-based vocal separation** — uses a center-channel spectral-mask approach.
- **Binaural spatial audio / HRTF-style rendering** — the native 8D engine uses published binaural audio concepts, including interaural time difference (ITD), interaural level difference (ILD), crossfeed, and early-reflection-style room processing. These techniques are informed by research on head-related transfer functions and binaural hearing.
- **Head-Related Transfer Functions (HRTF)** — the project acknowledges the SOFA (Spatially Oriented Format for Acoustics) convention and the broader HRTF research community. SOFA is a standard format for storing spatial acoustic data, including HRIR/HRTF measurements.
- **SOFA specification** — the SOFA convention and its associated APIs are maintained by the SOFA community; see the SOFA specifications and software resources at https://www.sofaconventions.org/.
- **Binaural synthesis / convolution** — the future HRTF convolution path is informed by standard binaural synthesis practice, in which an audio signal is convolved with left/right head-related impulse responses (HRIRs). FFT-based convolution is commonly used for efficient implementation.