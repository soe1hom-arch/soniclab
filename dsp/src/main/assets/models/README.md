# Bundled AI models

No neural models are bundled in this APK right now.

## Why no model?

- The previous `denoiser_v1.tflite` was trained on synthetic 16 kHz audio
  (sine tones + electric hum/hiss/crackle). On real music it degraded the
  signal into buzzing/noise artifacts, so it was removed.
- **AI Enhance** now uses `ClassicEnhancer` — a transparent DSP enhancer
  (adaptive gain toward −18 dBFS + soft-knee limiter, no EQ coloring, no hard
  clip) that runs fully on-device.
- The Studio vocal remover is a spectral center-channel extractor
  (`SpectralVocalRemover`, STFT + soft ratio mask) — no neural model.

## Future

If a real model is trained on actual music later, place the `.tflite` here and
wire it through an `AiEnhancer` implementation. Contract for an enhancer model:

- Input:  `float32[1][N]` mono PCM samples in [-1, 1]
- Output: `float32[1][N]` enhanced PCM samples in [-1, 1]
- Keep models small (< 10 MB) so the APK stays lean; inference must stay
  on-device (the app has no `INTERNET` permission).
