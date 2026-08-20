# Bundled AI models

No neural models are bundled in this APK right now.

## Why no model?

- The previous `denoiser_v1.tflite` was trained on synthetic 16 kHz audio
  (sine tones + electric hum/hiss/crackle). On real music it degraded the
  signal into buzzing/noise artifacts, so it was removed.
- The strongest open neural enhancer for speech today is
  **DeepFilterNet** (github.com/Rikorose/DeepFilterNet, Apache-2.0/MIT, ~4.6k
  stars). Its pre-trained models are **ONNX (48 kHz)**, and running ONNX
  requires the ONNX Runtime, not TensorFlow Lite. Converting those models to
  TFLite is fragile (unsupported operators) and typically loses the quality
  that makes them good in the first place.
- For **music** specifically there is no neural enhancer that is
  production-ready yet: the best open models target speech/narrowband
  denoising, and applying them to full-band music changes the timbre in
  audible ways. Bundling one would repeat the `denoiser_v1` mistake.

## What runs today

- **AI Enhance** uses `NeuralEnhancer` — an `AiEnhancer` implementation that
  loads `models/ai_enhancer_v1.tflite` when present and otherwise falls back
  to `ClassicEnhancer` (transparent DSP: adaptive gain toward −20 dBFS +
  soft-knee limiter, no EQ coloring, no hard clip).
- The Studio vocal remover is a spectral center-channel extractor
  (`SpectralVocalRemover`, STFT + soft ratio mask) — no neural model.

## Adding a model later

Drop a `.tflite` file at `models/ai_enhancer_v1.tflite` in this directory and
it will be picked up automatically (the Settings screen reports whether a
neural model is actually loaded). Contract:

- Input:  `float32[1][N]` mono PCM samples in [-1, 1]
- Output: `float32[1][N]` enhanced PCM samples in [-1, 1]
- Keep models small (< 10 MB) so the APK stays lean; inference must stay
  on-device (the app has no `INTERNET` permission).

If a genuinely music-safe model appears (trained on real music, not synthetic
noise), the right path is to ship it as ONNX with ONNX Runtime Mobile rather
than forcing a lossy TFLite conversion.
