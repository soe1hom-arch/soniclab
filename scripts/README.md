# Model training scripts

Generate the on-device TFLite models bundled in `ai/src/main/assets/models/`.
All training runs in plain numpy and exports valid TFLite flatbuffers via the
`tflite` + `flatbuffers` Python packages (no TensorFlow toolchain needed).

```bash
python3 -m venv .venv && . .venv/bin/activate
pip install numpy flatbuffers tflite
python scripts/train_denoiser.py    # -> ai/src/main/assets/models/denoiser_v1.tflite
python scripts/train_separator.py   # -> ai/src/main/assets/models/separator_v1.tflite
```

## Using real music data

Both scripts currently synthesize training data for reproducibility. To train
on real music:

- `train_denoiser.py`: replace `synth_clean` / `add_noise` with real clean
  tracks plus realistic noise captures. The model contract is unchanged:
  `float32[1][512]` frames in, enhanced frames out.
- `train_separator.py`: replace `synth_frame` with real stereo stems
  (vocals + instrumentals from the same track) and keep computing per-bin
  features/labels from the STFT. Contract: `float32[1][3]`
  (`log1p(|L|), log1p(|R|), cos(phase diff)`) -> `float32[1][1]` mask.

Model contract details live in `ai/src/main/assets/models/README.md`.

## Verification

Each script parses its own output back through the `tflite` reader classes
and runs a forward pass to confirm the exported flatbuffer matches the
trained weights.
