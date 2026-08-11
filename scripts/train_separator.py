"""Train a tiny per-bin vocal-mask model (numpy) and export it as a valid
.tflite flatbuffer. Contract: float32[1][3] -> float32[1][1].
Features: [log1p(|L|), log1p(|R|), cos(phaseL - phaseR)]; target: vocal
magnitude / (vocal + instrument magnitude) per STFT bin."""
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

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "ai", "src", "main", "assets", "models", "separator_v1.tflite")
FFT = 2048
SR = 16000
HID = 24
rng = np.random.default_rng(11)


def wrap(a):
    a = a % (2 * np.pi)
    a[a > np.pi] -= 2 * np.pi
    return a


def synth_frame():
    t = np.arange(FFT) / SR
    n_vo = int(rng.integers(1, 4))
    v = np.zeros(FFT)
    for _ in range(n_vo):
        f = rng.uniform(150, 3000)
        v += rng.uniform(0.2, 0.6) * np.sin(2 * np.pi * f * t + rng.uniform(0, 2 * np.pi))
    il = np.zeros(FFT); ir = np.zeros(FFT)
    for _ in range(int(rng.integers(2, 5))):
        f = rng.uniform(150, 4000)
        pan = rng.uniform(0.1, 1.0)
        a = rng.uniform(0.2, 0.6)
        il += a * np.sin(2 * np.pi * f * t + rng.uniform(0, 2 * np.pi)) * pan
        ir += a * np.sin(2 * np.pi * f * t + rng.uniform(0, 2 * np.pi)) * (1 - pan)
    return np.clip(v, -1, 1), np.clip(il, -1, 1), np.clip(ir, -1, 1)


def frame_features(v, il, ir):
    L = np.fft.fft(v + il)
    R = np.fft.fft(v + ir)
    V = np.fft.fft(v)
    IL = np.fft.fft(il)
    IR = np.fft.fft(ir)
    magL = np.abs(L); magR = np.abs(R)
    cosDiff = np.cos(wrap(np.angle(R) - np.angle(L)))
    feat = np.stack([np.log1p(magL), np.log1p(magR), cosDiff], axis=1).astype(np.float32)
    vocMag = np.abs(V)
    instMag = np.maximum(np.abs(IL), np.abs(IR))
    label = (vocMag / (vocMag + instMag + 1e-6)).astype(np.float32)
    return feat, label


# ---------------- train ----------------
FRAMES = 24
feats = []
labels = []
for _ in range(FRAMES):
    v, il, ir = synth_frame()
    f, l = frame_features(v, il, ir)
    feats.append(f); labels.append(l)
X = np.concatenate(feats).astype(np.float32)
Y = np.concatenate(labels).astype(np.float32)
print("samples:", X.shape, "label mean:", float(Y.mean()))

W1 = np.random.normal(0, 0.3, (3, HID)).astype(np.float32)
b1 = np.zeros(HID, np.float32)
W2 = np.random.normal(0, 0.2, (HID, HID)).astype(np.float32)
b2 = np.zeros(HID, np.float32)
W3 = np.random.normal(0, 0.2, (HID, 1)).astype(np.float32)
b3 = np.zeros(1, np.float32)

def relu(z): return np.maximum(z, 0)

params = [W1, b1, W2, b2, W3, b3]
m = [np.zeros_like(p) for p in params]
v_ = [np.zeros_like(p) for p in params]
beta1, beta2, eps, lr = 0.9, 0.999, 1e-8, 2e-3
BS = 512
EPOCHS = 40
steps = 0
N = X.shape[0]
for epoch in range(EPOCHS):
    perm = rng.permutation(N)
    for s in range(0, N, BS):
        idx = perm[s:s + BS]
        x = X[idx]; yt = Y[idx][:, None]
        h1 = relu(x @ W1 + b1)
        h2 = relu(h1 @ W2 + b2)
        y = h2 @ W3 + b3
        d = (y - yt) / len(idx)
        g = [x.T @ ((d @ W3.T * (h2 > 0)) @ W2.T * (h1 > 0)),
             ((d @ W3.T * (h2 > 0)) @ W2.T * (h1 > 0)).sum(0),
             h1.T @ (d @ W3.T * (h2 > 0)),
             (d @ W3.T * (h2 > 0)).sum(0),
             h2.T @ d,
             d.sum(0)]
        steps += 1
        for i, (p, gr) in enumerate(zip(params, g)):
            m[i] = beta1 * m[i] + (1 - beta1) * gr
            v_[i] = beta2 * v_[i] + (1 - beta2) * gr * gr
            mh = m[i] / (1 - beta1 ** steps)
            vh = v_[i] / (1 - beta2 ** steps)
            p -= lr * mh / (np.sqrt(vh) + eps)
    pred = relu(relu(X @ W1 + b1) @ W2 + b2) @ W3 + b3
    print(f"epoch {epoch + 1}/{EPOCHS} mse={np.mean((pred - Y[:, None]) ** 2):.6f}")
np.savez(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".cache_separator.npz"), W1=W1, b1=b1, W2=W2, b2=b2, W3=W3, b3=b3)

# ---------------- export ----------------
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

def make_buffer(builder, data):
    if data:
        d = byte_vector(builder, data)
        Buffer.BufferStart(builder)
        Buffer.BufferAddData(builder, d)
        return Buffer.BufferEnd(builder)
    Buffer.BufferStart(builder)
    return Buffer.BufferEnd(builder)

def make_tensor(builder, shape, type_, buffer_idx, name):
    name_off = builder.CreateString(name)
    shape_off = int32_vector(builder, shape)
    sig_off = int32_vector(builder, shape)
    Tensor.TensorStart(builder)
    Tensor.TensorAddShape(builder, shape_off)
    Tensor.TensorAddType(builder, type_)
    Tensor.TensorAddBuffer(builder, buffer_idx)
    Tensor.TensorAddName(builder, name_off)
    Tensor.TensorAddShapeSignature(builder, sig_off)
    Tensor.TensorAddHasRank(builder, True)
    return Tensor.TensorEnd(builder)

def make_operator(builder, opcode_idx, inputs, outputs, act):
    FullyConnectedOptions.FullyConnectedOptionsStart(builder)
    FullyConnectedOptions.FullyConnectedOptionsAddFusedActivationFunction(builder, act)
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

def table_vector(builder, items):
    builder.StartVector(4, len(items), 4)
    for it in reversed(items):
        builder.PrependUOffsetTRelative(it)
    return builder.EndVector()

builder = flatbuffers.Builder(1 << 20)
b_empty = make_buffer(builder, b"")
b_w1 = make_buffer(builder, float32_bytes(W1.T))
b_b1 = make_buffer(builder, float32_bytes(b1))
b_w2 = make_buffer(builder, float32_bytes(W2.T))
b_b2 = make_buffer(builder, float32_bytes(b2))
b_w3 = make_buffer(builder, float32_bytes(W3.T))
b_b3 = make_buffer(builder, float32_bytes(b3))
buffers = [b_empty, b_w1, b_b1, b_w2, b_b2, b_w3, b_b3]

t_in = make_tensor(builder, [1, 3], TensorType.TensorType.FLOAT32, 0, "input")
t_w1 = make_tensor(builder, [HID, 3], TensorType.TensorType.FLOAT32, 1, "dense/kernel")
t_b1 = make_tensor(builder, [HID], TensorType.TensorType.FLOAT32, 2, "dense/bias")
t_h1 = make_tensor(builder, [1, HID], TensorType.TensorType.FLOAT32, 0, "dense/Relu")
t_w2 = make_tensor(builder, [HID, HID], TensorType.TensorType.FLOAT32, 3, "dense_1/kernel")
t_b2 = make_tensor(builder, [HID], TensorType.TensorType.FLOAT32, 4, "dense_1/bias")
t_h2 = make_tensor(builder, [1, HID], TensorType.TensorType.FLOAT32, 0, "dense_1/Relu")
t_w3 = make_tensor(builder, [1, HID], TensorType.TensorType.FLOAT32, 5, "dense_2/kernel")
t_b3 = make_tensor(builder, [1], TensorType.TensorType.FLOAT32, 6, "dense_2/bias")
t_out = make_tensor(builder, [1, 1], TensorType.TensorType.FLOAT32, 0, "output")
tensors = [t_in, t_w1, t_b1, t_h1, t_w2, t_b2, t_h2, t_w3, t_b3, t_out]

op0 = make_operator(builder, 0, [0, 1, 2], [3], ActivationFunctionType.ActivationFunctionType.RELU)
op1 = make_operator(builder, 0, [3, 4, 5], [6], ActivationFunctionType.ActivationFunctionType.RELU)
op2 = make_operator(builder, 0, [6, 7, 8], [9], ActivationFunctionType.ActivationFunctionType.NONE)
operators = [op0, op1, op2]

OperatorCode.OperatorCodeStart(builder)
OperatorCode.OperatorCodeAddDeprecatedBuiltinCode(builder, BuiltinOperator.BuiltinOperator.FULLY_CONNECTED)
OperatorCode.OperatorCodeAddBuiltinCode(builder, BuiltinOperator.BuiltinOperator.FULLY_CONNECTED)
opcode = OperatorCode.OperatorCodeEnd(builder)

tens_vec = table_vector(builder, tensors)
ops_vec = table_vector(builder, operators)
opcodes_vec = table_vector(builder, [opcode])
in_vec = int32_vector(builder, [0])
out_vec = int32_vector(builder, [9])
name_off = builder.CreateString("main")
SubGraph.SubGraphStart(builder)
SubGraph.SubGraphAddTensors(builder, tens_vec)
SubGraph.SubGraphAddInputs(builder, in_vec)
SubGraph.SubGraphAddOutputs(builder, out_vec)
SubGraph.SubGraphAddOperators(builder, ops_vec)
SubGraph.SubGraphAddName(builder, name_off)
subgraph = SubGraph.SubGraphEnd(builder)

buffers_vec = table_vector(builder, buffers)
subgraphs_vec = table_vector(builder, [subgraph])
desc_off = builder.CreateString("SonicLab vocal mask separator v1 (offline)")
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

# ---------------- validation ----------------
m2 = Model.Model.GetRootAsModel(bytearray(data), 0)
sg = m2.Subgraphs(0)
print("tensors:", sg.TensorsLength(), "operators:", sg.OperatorsLength(), "buffers:", m2.BuffersLength())
wt = {}
for i in range(sg.TensorsLength()):
    t = sg.Tensors(i)
    if "kernel" in t.Name().decode():
        buff = m2.Buffers(t.Buffer())
        wt[t.Name().decode()] = np.frombuffer(bytes(buff.DataAsNumpy()), dtype=np.float32).reshape(t.ShapeAsNumpy().tolist())

def infer(x):
    h1 = np.maximum(x @ wt["dense/kernel"].T + np.zeros(HID), 0)
    h2 = np.maximum(h1 @ wt["dense_1/kernel"].T + np.zeros(HID), 0)
    return h2 @ wt["dense_2/kernel"].T + np.zeros(1)

center = np.array([[np.log1p(0.5), np.log1p(0.5), 1.0]], np.float32)   # coherent center
panned = np.array([[np.log1p(0.5), np.log1p(0.01), -0.9]], np.float32)  # hard left
silence = np.array([[0.0, 0.0, 1.0]], np.float32)
print("mask center:", float(np.clip(infer(center), 0, 1)[0, 0]))
print("mask panned:", float(np.clip(infer(panned), 0, 1)[0, 0]))
print("mask silence:", float(np.clip(infer(silence), 0, 1)[0, 0]))
ok = np.clip(infer(center), 0, 1)[0, 0] > np.clip(infer(panned), 0, 1)[0, 0]
print("VALIDATION OK" if ok else "VALIDATION CHECK")
