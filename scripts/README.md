# Scripts

Utility scripts for the SonicLab repo.

## Model training

There are **no training scripts right now**: the previous
`train_denoiser.py` produced a synthetic 16 kHz denoiser that degraded real
music, so the model and script were removed. AI Enhance uses the transparent
DSP enhancer (`ClassicEnhancer`).

When a higher-quality model trained on real music data is ready, training
will be reintroduced here (plain NumPy + flatbuffers, no TensorFlow toolchain).
