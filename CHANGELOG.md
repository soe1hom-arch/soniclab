# Changelog

Notable changes to SonicLab are recorded here. Dates refer to the project changes, not necessarily to published app releases.

## Unreleased

### Added

- Added a prominent Donate CTA badge to the README linking to the donation guide.
- Added a native binaural/HRTF-style spatial audio engine for the 8D effect.
- Added `HrtfSpatialEngine` Kotlin/JNI bridge for native audio processing.
- Added native binaural processing with interaural time difference, interaural level difference, crossfeed, and a light room tail.
- Enabled native binaural HRTF processing for the 8D playback path.
- Added diagnostic logging under the `SonicLabHRTF` tag so HRTF initialization and processing can be verified in Logcat/Log Fox.

### Fixed

- Made the `FakeAudioSink` test double extensible so the frame-alignment regression test can override `handleBuffer`.
- Fixed unit-test failures caused by Android `Log` calls in the spatial audio processor.
- Fixed native HRTF engine loading so unit tests fall back safely instead of failing with `UnsatisfiedLinkError`.
- Fixed JNI `nativeProcess` signature to correctly expose the JNI environment.

### Notes

- Android CI run #68 succeeded after the test fix. It built the debug APK; this is not a signed release APK.
- No signed release APK is published by this change.
- The current native engine provides a binaural 8D-style rendering path; SOFA/HRTF dataset-based convolution can be added as a later enhancement.