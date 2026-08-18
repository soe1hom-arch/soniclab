# Bundled AI models

Drop any `.tflite` flatbuffer here and it is automatically discovered by
`AiModelManager`, copied to the app files directory and loaded by
`TfLiteEnhancer`. All inference is on-device — the app stays fully offline.

## Model contract (denoiser / enhancer)

- Input:  `float32[1][N]` mono PCM samples in [-1, 1]
- Output: `float32[1][N]` enhanced PCM samples in [-1, 1]
- N is fixed at export time; `TfLiteEnhancer.enhance()` adapts the frame
  size to the model input length (chunking) — see the class docs.

## Bundled model

- `denoiser_v1.tflite` — trained on synthetic audio (noise -> clean) with a
  512-sample MLP; ~1.3 MB, bundled in this APK.

## Notes

- Keep models small (< 10 MB) so the APK stays lean.
- Vocal separation intentionally ships WITHOUT a neural model: the bundled
  `separator_v1.tflite` was a toy per-bin classifier that sounded worse than
  the plain spectral center-channel extractor, so it was removed. The Studio
  vocal remover now uses `SpectralVocalRemover` (STFT + soft ratio mask).
- If no model is bundled, AI Enhance uses the transparent DSP fallback
  (`ClassicEnhancer`) so the feature always works offline.
