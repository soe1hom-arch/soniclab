# Model training scripts

Generates the on-device denoiser TFLite model bundled in
`dsp/src/main/assets/models/`. All training runs in plain numpy and exports a
valid TFLite flatbuffer via the `tflite` + `flatbuffers` Python packages (no
TensorFlow toolchain needed).

```bash
python3 -m venv .venv && . .venv/bin/activate
pip install numpy flatbuffers tflite
python scripts/train_denoiser.py    # -> dsp/src/main/assets/models/denoiser_v1.tflite
```

## Using real music data

`train_denoiser.py` currently synthesizes training data for reproducibility.
To train on real music: replace `synth_clean` / `add_noise` with real clean
tracks plus realistic noise captures. The model contract is unchanged:
`float32[1][512]` frames in, enhanced frames out.

Model contract details live in `dsp/src/main/assets/models/README.md`.

> Note: vocal separation has no bundled model. The Studio vocal remover is a
> spectral center-channel extractor (`SpectralVocalRemover`) — see that file
> if you want to replace it with a real Demucs-style separator later.

## Verification

The script parses its own output back through the `tflite` reader classes
and runs a forward pass to confirm the exported flatbuffer matches the
trained weights.
