# Bundled AI models

Drop any `.tflite` flatbuffer here and it is automatically discovered by
`AiModelManager`, copied to the app files directory and loaded by
`TfLiteEnhancer`. All inference is on-device — the app stays fully offline.

## Model contract (denoiser / enhancer)

- Input:  `float32[1][N]` mono PCM samples in [-1, 1]
- Output: `float32[1][N]` enhanced PCM samples in [-1, 1]
- N is fixed at export time; `TfLiteEnhancer.enhance()` adapts the frame
  size to the model input length (chunking) — see the class docs.

## Suggested exports

- `denoiser_v1.tflite` — trained on synthetic audio (noise -> clean) with a
  512-sample MLP; ~1.3 MB, bundled in this APK
- `separator_v1.tflite` — per-bin vocal mask model (3->24->24->1 MLP, ~4 KB), trained on synthetic stereo; drives `TfliteVocalSeparator` with a spectral fallback

## Notes

- Keep models small (< 10 MB) so the APK stays lean.
- If no model is bundled, AI Enhance uses the classic DSP fallback
  (`ClassicEnhancer`) so the feature always works offline.
