# Third-Party Notices

SonicLab is licensed under the **Apache License 2.0** (see `LICENSE`). All
application code — Kotlin, Compose UI, the DSP chain (SOLA/WSOLA
time-stretch, EBU R128 loudness, Schroeder reverb, STFT vocal splitter), and
the native Oboe JNI wrapper — is original work by **soe1hom-arch**, written
from publicly published algorithms and API documentation. No third-party
source code is copied into this repository.

The app links the following third-party components (all redistributed under
permissive licenses; see the bundled notices in
`app/src/main/assets/third_party_notices.txt` and the About screen):

| Component | Version | License | Copyright |
|---|---|---|---|
| AndroidX (core-ktx, activity, lifecycle, datastore) | 1.15.0 / 1.9.3 / 2.8.7 / 1.1.1 | Apache-2.0 | The Android Open Source Project |
| Jetpack Compose (ui, material3, material-icons) | BOM 2024.12.01 | Apache-2.0 | The Android Open Source Project |
| AndroidX Navigation (navigation-compose) | 2.8.5 | Apache-2.0 | The Android Open Source Project |
| AndroidX Media3 (common, exoplayer, session, ui) | 1.5.0 | Apache-2.0 | The Android Open Source Project |
| Oboe (native audio, prefab `.so`) | 1.9.0 | Apache-2.0 | Google / The Android Open Source Project |
| TensorFlow Lite (runtime) | 2.16.1 | Apache-2.0 | The TensorFlow Authors |
| Kotlin stdlib + kotlinx-coroutines | Kotlin 2.0.21 / 1.9.0 | Apache-2.0 | JetBrains s.r.o. |
| Kotlin Gradle plugin, Android Gradle plugin | 2.0.21 / 8.7.3 | Apache-2.0 | JetBrains s.r.o. / Google (build-time only) |
| JUnit 4 | 4.13.2 | EPL-2.0 | Eclipse Foundation (test-time only) |

Algorithm notes for the record:

- **SOLA / WSOLA time-stretch & pitch** (`dsp/.../toolkit/PcmProcessor.kt`,
  `player/...`): original implementations of the published SOLA/WSOLA
  techniques (Roucos & Wilgus 1985; Laroche & Dolson 1999). No code taken
  from SoundTouch or any other library.
- **EBU R128 loudness** (`dsp/.../analyzer/R128Meter.kt`): original
  implementation of the EBU R128 / ITU-R BS.1770 standard (K-weighting,
  400 ms blocks, gating).
- **Schroeder reverb** (`player/.../ReverbAudioProcessor.kt`): original
  implementation of the classic comb/all-pass network (Schroeder 1962).
- **STFT vocal splitter** (`dsp/.../ai/SpectralVocalRemover.kt`): original
  implementation of a center-channel spectral mask.
- **DeepFilterNet** is mentioned in the README as a future model candidate
  (ONNX, Apache-2.0/MIT by Rikorose); it is not bundled and no code from it
  is used.
