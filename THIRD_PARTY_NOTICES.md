# Third-Party Notices

## Project license

The original source code in SonicLab is licensed under the [Apache License, Version 2.0](LICENSE). Third-party components listed below are separate works and remain subject to their respective license terms. Their inclusion does not change the license of those components.

The app displays its bundled notices and license texts in **About → Third-party credits**. The notice is at `app/src/main/assets/third_party_notices.txt`; additional upstream license texts are in [`app/src/main/assets/licenses`](app/src/main/assets/licenses).

## Dependencies and acknowledgements

Versions below are those declared in the version catalog unless otherwise noted. An audit of `:app:releaseRuntimeClasspath` resolved 128 Maven modules, including dependency platforms and transitive modules; the list summarizes their component families rather than listing every module.

| Component | Version | License | Attribution |
|---|---|---|---|
| AndroidX Core, Activity, Lifecycle, and DataStore | 1.15.0 / 1.9.3 / 2.8.7 / 1.1.1 | Apache-2.0 | The Android Open Source Project |
| Jetpack Compose UI, Material 3, and Material Icons | BOM 2024.12.01 | Apache-2.0 | The Android Open Source Project |
| AndroidX Navigation Compose | 2.8.5 | Apache-2.0 | The Android Open Source Project |
| AndroidX Media3 (Common, ExoPlayer, Session, UI) | 1.5.0 | Apache-2.0 | The Android Open Source Project |
| Oboe | 1.9.0 | Apache-2.0 | Google / The Android Open Source Project |
| TensorFlow Lite runtime | 2.16.1 | Apache-2.0 | The TensorFlow Authors |
| Kotlin libraries, kotlinx.coroutines, and kotlinx.serialization | 2.0.21 / 1.9.0 / 1.6.3 | Apache-2.0 | JetBrains s.r.o. and contributors |
| JetBrains annotations | 23.0.0 | Apache-2.0 | JetBrains s.r.o. |
| Guava and failureaccess | 33.3.1-android / 1.0.2 | Apache-2.0 | Google LLC and contributors |
| Okio | 3.4.0 | Apache-2.0 | Square, Inc. |
| LLVM libc++ shared runtime (Android NDK) | NDK 27.2.12479018 | Apache-2.0 with LLVM exceptions; additional notices in upstream license | LLVM Project contributors |
| GitHub Octicons | mark-github-24 | MIT | GitHub Inc. |
| TensorFlow Lite native dependencies: XNNPACK, cpuinfo, pthreadpool | Revisions pinned by TensorFlow v2.16.1 | BSD-style | Upstream contributors |
| TensorFlow Lite native dependencies: FP16, FXdiv, FarmHash | Revisions pinned by TensorFlow v2.16.1 | MIT | Upstream contributors |
| ARM_NEON_2_x86_SSE | Revision pinned by TensorFlow v2.16.1 | Intel BSD-style license | Intel Corporation |
| Eigen | `aa6964bf3a34fd607837dd8123bc42465185c4f8` | MPL-2.0; portions under BSD and MINPACK notices | Eigen contributors |
| OouraFFT | v1.0, used by TensorFlow Lite's built-in FFT kernels | Upstream readme notice; see bundled text | Takuya Ooura |
| TensorFlow Lite native dependencies: gemmlowp, ruy, FlatBuffers | Revisions pinned by TensorFlow v2.16.1 | Apache-2.0 | Google and upstream contributors |
| Kotlin Gradle Plugin / Android Gradle Plugin | 2.0.21 / 8.7.3 | Apache-2.0 | JetBrains s.r.o. / Google (build-time) |
| JUnit 4 | 4.13.2 | EPL-2.0 | Eclipse Foundation (test-time) |

For complete terms, refer to each component's license and notices distributed with the dependency. The table is an informational summary and is not a substitute for those license texts.

The packaged APK's `libtensorflowlite_jni.so` for all four ABIs was byte-for-byte identical to the corresponding library in the official `org.tensorflow:tensorflow-lite:2.16.1` AAR. TensorFlow v2.16.1's upstream build metadata identifies the native components summarized above. The AAR's own [license](app/src/main/assets/licenses/tensorflow-lite.txt) includes an additional Caffe copyright and BSD-style notice. The app bundles the upstream license/notice texts for those native components under `app/src/main/assets/licenses`; Eigen's pinned source is available [here](https://gitlab.com/libeigen/eigen/-/archive/aa6964bf3a34fd607837dd8123bc42465185c4f8/eigen-aa6964bf3a34fd607837dd8123bc42465185c4f8.tar.gz).

TensorFlow Lite also uses OouraFFT v1.0; its upstream `readme2d.txt` is included verbatim because its distribution terms are expressed as a readme notice rather than a standard SPDX license. The phrase allowing distribution of the “original package” is not a clear binary-redistribution grant, so confirm that interpretation before distributing an APK publicly. The Maven dependency audit and upstream build metadata do not establish a complete software bill of materials for every statically linked native component. Recheck the exact release APK, dependency provenance, and notices whenever dependencies or build variants change; this inventory is not legal advice.

## Audio algorithms and standards

SonicLab's audio-processing code is implemented for this project and is informed by the referenced published techniques and standards. References to an algorithm or standard are acknowledgements, not endorsements.

- **SOLA / WSOLA time stretching and pitch processing** — based on published techniques, including Roucos and Wilgus (1985) and Laroche and Dolson (1999). No source code from SoundTouch is included.
- **EBU R128 loudness measurement** — implementation based on EBU R 128 and ITU-R BS.1770, including K-weighting and loudness gating.
- **Schroeder-style reverb** — based on the classic comb-filter and all-pass network described by Manfred R. Schroeder (1962).
- **STFT-based vocal separation** — uses a center-channel spectral-mask approach.

No DeepFilterNet model or implementation is bundled. Any future model or dependency will be credited here with its applicable license information.
