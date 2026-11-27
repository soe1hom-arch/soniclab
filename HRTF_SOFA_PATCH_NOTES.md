# SonicLab — SOFA-based HRTF patch

This patch replaces the previous synthetic delay/gain HRTF placeholder in the native 8D engine with measured SOFA HRIR loading and left/right FIR convolution through libmysofa 1.3.5.

## Apply

Extract this patch at the root of the SonicLab repository and allow the files to overwrite the matching paths. The ZIP preserves repository-relative paths.

## Changed files

- `player/src/main/cpp/CMakeLists.txt` — fetch and build libmysofa 1.3.5 as a static Android dependency.
- `player/src/main/cpp/hrtf_engine.cpp` — parse SOFA bytes, query interpolated left/right HRIRs, and run FIR convolution with SOFA delays.
- `player/src/main/java/com/soniclab/player/HrtfSpatialEngine.kt` — pass SOFA bytes to JNI and make native release idempotent.
- `player/src/main/java/com/soniclab/player/SpatialAudioProcessor.kt` — load HRTF on the 8D path, handle mono/stereo input, check native processing result, and retain an audible fallback.
- `player/src/main/java/com/soniclab/player/AudioSpatialBridge.kt` — read the bundled asset through Android AssetManager.
- `player/src/main/java/com/soniclab/player/PlaybackService.kt` — initialize the bundled SOFA asset before building playback.
- `player/src/main/java/com/soniclab/player/PcmAudioProcessor.kt` — add a reset lifecycle hook for native resource cleanup.
- `app/src/main/assets/licenses/libmysofa-license.txt`
- `app/src/main/assets/third_party_notices.txt`
- `THIRD_PARTY_NOTICES.md`

## Build requirements

The first native CMake configuration requires network access because CMake FetchContent downloads upstream `hoene/libmysofa` tag `v1.3.5`. The Android NDK toolchain and zlib from the NDK/system toolchain are also required. A build without network access will fail until libmysofa has been vendored or pre-fetched.

## Expected diagnostics

- `SOFA_ASSET_FOUND` — Android AssetManager found and read the packaged file.
- `SOFA_LOADED` — libmysofa parsed the SOFA file and built its HRTF lookup/filter structures.
- `HRTF_READY=true` — JNI engine and initial measured filters were created.
- `HRTF_PROCESSING_FAILED` — runtime filter query or native processing failed; the processor switches to the existing fallback path.

## Verification status

The modified C++ translation unit passed a local C++17 syntax-only check against JNI and the libmysofa API signatures. A full Gradle/Android native build could not be run in this environment because external network access was unavailable: the Gradle wrapper could not download its Gradle distribution, and CMake could not fetch libmysofa. Therefore this patch is not represented as a successfully built or device-tested APK. Run `./gradlew :app:assembleDebug` in a network-enabled environment and test on the target device.

## Important dataset provenance check

The bundled `hrtf_48k.sofa` file's embedded HDF5 metadata reports `Title=HRTF C`, `DatabaseName=AXD HRTF database`, and `License=Creative Commons Attribution-ShareAlike 3.0 Unported License`. The adjacent `player/src/main/assets/hrtf/LICENSE.txt` and app notices currently identify a SONICOM MIT-licensed file. These records do not match. Verify the dataset's provenance and redistribution terms before publishing an APK; this patch does not replace or relabel the dataset.
