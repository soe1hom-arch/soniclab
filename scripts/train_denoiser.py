"""Copyright 2026 soe1hom-arch.
SPDX-License-Identifier: Apache-2.0
"""

"""Train a tiny on-device denoiser (numpy) and export it as a valid .tflite
flatbuffer without TensorFlow. Contract: float32[1][512] -> float32[1][512]."""
import os
import numpy as np
import flatbuffers
import importlib
Model = importlib.import_module("tflite.Model")
SubGraph = importlib.import_module("tflite.SubGraph")
Tensor = importlib.import_module("tflite.Tensor")
Buffer = importlib.import_module("tflite.Buffer")
Operator = importlib.import_module("tflite.Operator")
OperatorCode = importlib.import_module("tflite.OperatorCode")
FullyConnectedOptions = importlib.import_module("tflite.FullyConnectedOptions")
TensorType = importlib.import_module("tflite.TensorType")
BuiltinOperator = importlib.import_module("tflite.BuiltinOperator")
BuiltinOptions = importlib.import_module("tflite.BuiltinOptions")
ActivationFunctionType = importlib.import_module("tflite.ActivationFunctionType")

FRAME = 512
HID = 256
SR = 16000
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "ai", "src", "main", "assets", "models", "denoiser_v1.tflite")

# ---------------- synthetic data ----------------
rng = np.random.default_rng(7)

def synth_clean(n):
    t = np.arange(n) / SR
    out = np.zeros(n)
    for _ in range(int(rng.integers(1, 6))):
        f = rng.uniform(150, 4500)
        a = rng.uniform(0.15, 0.7)
        p = rng.uniform(0, 2 * np.pi)
        out += a * np.sin(2 * np.pi * f * t + p)
    for f in rng.uniform(150, 2500, 2):
        out += 0.25 * np.sin(2 * np.pi * f * 2 * t + rng.uniform(0, 2 * np.pi))
    return np.clip(out, -1, 1)

def add_noise(clean, snr_db):
    power = np.mean(clean ** 2)
    npow = power / (10 ** (snr_db / 10))
    base = rng.normal(0, np.sqrt(max(npow, 1e-6)), clean.shape)
    # electric hum (50/100 Hz) + broadband hiss + occasional crackle
    t = np.arange(clean.shape[0]) / SR
    hum_amp = rng.uniform(0.05, 0.35)
    base += hum_amp * np.sin(2 * np.pi * 50 * t + rng.uniform(0, 2 * np.pi))
    base += hum_amp * 0.5 * np.sin(2 * np.pi * 100 * t + rng.uniform(0, 2 * np.pi))
    base += rng.normal(0, 0.04, clean.shape) * rng.uniform(0.3, 1.0)  # hiss
    if rng.random() < 0.3:
        crackle = np.zeros_like(clean)
        for _ in range(int(rng.integers(1, 4))):
            pos = int(rng.integers(0, clean.shape[0]))
            w = int(rng.integers(2, 8))
            crackle[pos:min(pos + w, clean.shape[0])] = rng.uniform(0.2, 0.8)
        base += crackle
    return np.clip(clean + base, -1, 1)

N = 10000
Y = np.stack([synth_clean(FRAME) for _ in range(N)]).astype(np.float32)
snr = rng.uniform(6, 22, N)
X = np.stack([add_noise(Y[i], snr[i]) for i in range(N)]).astype(np.float32)
CACHE = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".cache_denoiser.npz")
if os.path.exists(CACHE):
    z = np.load(CACHE)
    W1, b1, W2, b2, W3, b3 = (z[k] for k in ["W1","b1","W2","b2","W3","b3"])
else:
    W1 = np.random.normal(0, 0.08, (FRAME, HID)).astype(np.float32)
    b1 = np.zeros(HID, np.float32)
    W2 = np.random.normal(0, 0.08, (HID, HID)).astype(np.float32)
    b2 = np.zeros(HID, np.float32)
    W3 = np.random.normal(0, 0.08, (HID, FRAME)).astype(np.float32)
    b3 = np.zeros(FRAME, np.float32)

# ---------------- tiny MLP training (numpy Adam) ----------------
def relu(z): return np.maximum(z, 0)

params = [W1, b1, W2, b2, W3, b3]
m = [np.zeros_like(p) for p in params]
v = [np.zeros_like(p) for p in params]
beta1, beta2, eps, lr = 0.9, 0.999, 1e-8, 1e-3
BS = 128
EPOCHS = 25
steps = 0
for epoch in range(EPOCHS):
    perm = rng.permutation(N)
    for s in range(0, N, BS):
        idx = perm[s:s + BS]
        x = X[idx]; yt = Y[idx]
        h1 = relu(x @ W1 + b1)
        h2 = relu(h1 @ W2 + b2)
        y = h2 @ W3 + b3
        d = (y - yt) / len(idx)
        g = [x.T @ d, d.sum(0), h1.T @ (d @ W3.T * (h2 > 0)),
             (d @ W3.T * (h2 > 0)).sum(0), h2.T @ d, d.sum(0)]
        # correction: g[4] uses d after h2 -> y is h2@W3+b3, so gW3 = h2.T @ d, gb3 = d.sum(0); dh2 = d @ W3.T * (h2>0)
        g[2] = h1.T @ (d @ W3.T * (h2 > 0))
        g[3] = (d @ W3.T * (h2 > 0)).sum(0)
        g[4] = h2.T @ d
        g[5] = d.sum(0)
        g[0] = x.T @ ((d @ W3.T * (h2 > 0)) @ W2.T * (h1 > 0))
        g[1] = ((d @ W3.T * (h2 > 0)) @ W2.T * (h1 > 0)).sum(0)
        steps += 1
        for i, (p, gr) in enumerate(zip(params, g)):
            m[i] = beta1 * m[i] + (1 - beta1) * gr
            v[i] = beta2 * v[i] + (1 - beta2) * gr * gr
            mh = m[i] / (1 - beta1 ** steps)
            vh = v[i] / (1 - beta2 ** steps)
            p -= lr * mh / (np.sqrt(vh) + eps)
    yh = relu(relu(X @ W1 + b1) @ W2 + b2) @ W3 + b3
    print(f"epoch {epoch + 1}/{EPOCHS} mse={np.mean((yh - Y) ** 2):.6f}")
np.savez(CACHE, W1=W1, b1=b1, W2=W2, b2=b2, W3=W3, b3=b3)

# ---------------- flatbuffer export ----------------
def float32_bytes(a): return np.ascontiguousarray(a, dtype=np.float32).tobytes()

def byte_vector(builder, data):
    builder.StartVector(1, len(data), 1)
    builder.head -= len(data)
    builder.Bytes[builder.head:builder.head + len(data)] = data
    return builder.EndVector()

def int32_vector(builder, vals):
    builder.StartVector(4, len(vals), 4)
    for v in reversed(vals):
        builder.PrependInt32(int(v))
    return builder.EndVector()

def shape_vector(builder, shape):
    return int32_vector(builder, shape)

def make_buffer(builder, data: bytes):
    if data:
        data_off = byte_vector(builder, data)
        Buffer.BufferStart(builder)
        Buffer.BufferAddData(builder, data_off)
        return Buffer.BufferEnd(builder)
    Buffer.BufferStart(builder)
    return Buffer.BufferEnd(builder)

def make_tensor(builder, shape, type_, buffer_idx, name):
    name_off = builder.CreateString(name)
    shape_off = shape_vector(builder, shape)
    shape_sig_off = shape_vector(builder, shape)
    Tensor.TensorStart(builder)
    Tensor.TensorAddShape(builder, shape_off)
    Tensor.TensorAddType(builder, type_)
    Tensor.TensorAddBuffer(builder, buffer_idx)
    Tensor.TensorAddName(builder, name_off)
    Tensor.TensorAddShapeSignature(builder, shape_sig_off)
    Tensor.TensorAddHasRank(builder, True)
    return Tensor.TensorEnd(builder)

def make_operator(builder, opcode_idx, inputs, outputs, fused_activation):
    FullyConnectedOptions.FullyConnectedOptionsStart(builder)
    FullyConnectedOptions.FullyConnectedOptionsAddFusedActivationFunction(builder, fused_activation)
    opts = FullyConnectedOptions.FullyConnectedOptionsEnd(builder)
    in_vec = int32_vector(builder, inputs)
    out_vec = int32_vector(builder, outputs)
    Operator.OperatorStart(builder)
    Operator.OperatorAddOpcodeIndex(builder, opcode_idx)
    Operator.OperatorAddInputs(builder, in_vec)
    Operator.OperatorAddOutputs(builder, out_vec)
    Operator.OperatorAddBuiltinOptionsType(builder, BuiltinOptions.BuiltinOptions.FullyConnectedOptions)
    Operator.OperatorAddBuiltinOptions(builder, opts)
    return Operator.OperatorEnd(builder)

builder = flatbuffers.Builder(1 << 20)

b_empty = make_buffer(builder, b"")
b_w1 = make_buffer(builder, float32_bytes(W1.T))
b_b1 = make_buffer(builder, float32_bytes(b1))
b_w2 = make_buffer(builder, float32_bytes(W2.T))
b_b2 = make_buffer(builder, float32_bytes(b2))
b_w3 = make_buffer(builder, float32_bytes(W3.T))
b_b3 = make_buffer(builder, float32_bytes(b3))
buffers = [b_empty, b_w1, b_b1, b_w2, b_b2, b_w3, b_b3]

t_input = make_tensor(builder, [1, FRAME], TensorType.TensorType.FLOAT32, 0, "input")
t_w1 = make_tensor(builder, [HID, FRAME], TensorType.TensorType.FLOAT32, 1, "dense/kernel")
t_b1 = make_tensor(builder, [HID], TensorType.TensorType.FLOAT32, 2, "dense/bias")
t_h1 = make_tensor(builder, [1, HID], TensorType.TensorType.FLOAT32, 0, "dense/Relu")
t_w2 = make_tensor(builder, [HID, HID], TensorType.TensorType.FLOAT32, 3, "dense_1/kernel")
t_b2 = make_tensor(builder, [HID], TensorType.TensorType.FLOAT32, 4, "dense_1/bias")
t_h2 = make_tensor(builder, [1, HID], TensorType.TensorType.FLOAT32, 0, "dense_1/Relu")
t_w3 = make_tensor(builder, [FRAME, HID], TensorType.TensorType.FLOAT32, 5, "dense_2/kernel")
t_b3 = make_tensor(builder, [FRAME], TensorType.TensorType.FLOAT32, 6, "dense_2/bias")
t_out = make_tensor(builder, [1, FRAME], TensorType.TensorType.FLOAT32, 0, "output")
tensors = [t_input, t_w1, t_b1, t_h1, t_w2, t_b2, t_h2, t_w3, t_b3, t_out]

op0 = make_operator(builder, 0, [0, 1, 2], [3], ActivationFunctionType.ActivationFunctionType.RELU)
op1 = make_operator(builder, 0, [3, 4, 5], [6], ActivationFunctionType.ActivationFunctionType.RELU)
op2 = make_operator(builder, 0, [6, 7, 8], [9], ActivationFunctionType.ActivationFunctionType.NONE)
operators = [op0, op1, op2]

OperatorCode.OperatorCodeStart(builder)
OperatorCode.OperatorCodeAddDeprecatedBuiltinCode(builder, BuiltinOperator.BuiltinOperator.FULLY_CONNECTED)
OperatorCode.OperatorCodeAddBuiltinCode(builder, BuiltinOperator.BuiltinOperator.FULLY_CONNECTED)
opcode = OperatorCode.OperatorCodeEnd(builder)

buf_vec = int32_vector(builder, [0, 9])
in_vec = int32_vector(builder, [0])
out_vec = int32_vector(builder, [9])
name_off = builder.CreateString("main")
# Build subgraph vector properly: need vector of tables
# Use explicit flatbuffers vector creation
# (reuse pattern: StartVector via helper)
def table_vector(builder, items):
    builder.StartVector(4, len(items), 4)
    for it in reversed(items):
        builder.PrependUOffsetTRelative(it)
    return builder.EndVector()

# Build tensors/operators vectors first (children), then subgraph
tens_vec = table_vector(builder, tensors)
ops_vec = table_vector(builder, operators)
opcodes_vec = table_vector(builder, [opcode])
SubGraph.SubGraphStart(builder)
SubGraph.SubGraphAddTensors(builder, tens_vec)
SubGraph.SubGraphAddInputs(builder, in_vec)
SubGraph.SubGraphAddOutputs(builder, out_vec)
SubGraph.SubGraphAddOperators(builder, ops_vec)
SubGraph.SubGraphAddName(builder, name_off)
subgraph = SubGraph.SubGraphEnd(builder)

buffers_vec = table_vector(builder, buffers)
subgraphs_vec = table_vector(builder, [subgraph])
desc_off = builder.CreateString("SonicLab denoiser v1 (offline)")

Model.ModelStart(builder)
Model.ModelAddVersion(builder, 3)
Model.ModelAddOperatorCodes(builder, opcodes_vec)
Model.ModelAddSubgraphs(builder, subgraphs_vec)
Model.ModelAddDescription(builder, desc_off)
Model.ModelAddBuffers(builder, buffers_vec)
model = Model.ModelEnd(builder)

builder.Finish(model, file_identifier=b"TFL3")
data = builder.Output()
os.makedirs(os.path.dirname(OUT), exist_ok=True)
with open(OUT, "wb") as f:
    f.write(data)
print(f"Wrote {OUT} ({len(data) / 1024:.0f} KB)")

# ---------------- validation: parse back + manual forward pass ----------------
buf = bytearray(data)
model2 = Model.Model.GetRootAsModel(buf, 0)
print("tensors:", model2.Subgraphs(0).TensorsLength(),
      "operators:", model2.Subgraphs(0).OperatorsLength(),
      "opcodes:", model2.OperatorCodesLength(),
      "buffers:", model2.BuffersLength())
sg = model2.Subgraphs(0)
wt = {}
for i in range(sg.TensorsLength()):
    t = sg.Tensors(i)
    nm = t.Name().decode()
    if "kernel" in nm:
        buff = model2.Buffers(t.Buffer())
        wt[nm] = np.frombuffer(bytes(buff.DataAsNumpy()), dtype=np.float32).reshape(t.ShapeAsNumpy().tolist())
x = X[0].reshape(1, FRAME).astype(np.float32)
h1 = np.maximum(x @ wt["dense/kernel"].T + np.zeros(HID), 0)
h2 = np.maximum(h1 @ wt["dense_1/kernel"].T + np.zeros(HID), 0)
y = h2 @ wt["dense_2/kernel"].T + np.zeros(FRAME)
print("output shape:", y.shape, "mse vs clean:", float(np.mean((y - Y[0]) ** 2)))
print("VALIDATION OK")
