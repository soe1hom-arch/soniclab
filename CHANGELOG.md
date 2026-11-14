# Changelog

Notable changes to SonicLab are recorded here. Dates refer to the project changes, not necessarily to published app releases.

## Unreleased

### Added

- Added a prominent Donate CTA badge to the README linking to the donation guide.

### Fixed

- Made the `FakeAudioSink` test double extensible so the frame-alignment regression test can override `handleBuffer`.

### Notes

- Android CI run #68 succeeded after the test fix. It built the debug APK; this is not a signed release APK.
- No signed release APK is published by this change.
