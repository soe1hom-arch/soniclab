// Copyright 2026 soe1hom-arch
// SPDX-License-Identifier: Apache-2.0

#include <jni.h>
#include <android/log.h>
#include <cmath>
#include <cstring>
#include <vector>

#define LOG_TAG "SonicLabHRTF"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

constexpr float kPi = 3.14159265358979323846f;
constexpr int kMaxDelaySamples = 2048;

struct BinauralEngine {
    int sampleRate = 48000;

    float leftDelay[kMaxDelaySamples] = {};
    float rightDelay[kMaxDelaySamples] = {};
    int delayWrite = 0;

    float leftLow = 0.0f;
    float rightLow = 0.0f;

    float reverbDelayL[kMaxDelaySamples] = {};
    float reverbDelayR[kMaxDelaySamples] = {};
    int reverbWrite = 0;

    float smoothAzimuth = 0.0f;
};

float wrapAngle(float degrees) {
    float value = degrees;

    while (value < -180.0f) {
        value += 360.0f;
    }

    while (value >= 180.0f) {
        value -= 360.0f;
    }

    return value;
}

float delayRead(
    const float *buffer,
    int writePosition,
    int delaySamples
) {
    if (delaySamples <= 0) {
        return 0.0f;
    }

    int readPosition =
        writePosition - delaySamples;

    if (readPosition < 0) {
        readPosition += kMaxDelaySamples;
    }

    return buffer[readPosition];
}

void delayWriteSample(
    float *buffer,
    int writePosition,
    float value
) {
    buffer[writePosition] = value;
}

float onePoleLowpass(
    float previous,
    float input,
    float coefficient
) {
    return previous + coefficient * (input - previous);
}

void processFrame(
    BinauralEngine &engine,
    float inputLeft,
    float inputRight,
    float azimuthDegrees,
    float reverbWet,
    float &outputLeft,
    float &outputRight
) {
    const float azimuth = wrapAngle(azimuthDegrees);

    // Smooth rotation to prevent zipper noise when azimuth changes.
    engine.smoothAzimuth +=
        0.08f * (azimuth - engine.smoothAzimuth);

    const float radians =
        engine.smoothAzimuth * kPi / 180.0f;

    // Interaural time difference: maximum around 0.65 ms.
    const float itdSeconds =
        0.00065f * sinf(radians);

    const int itdSamples = static_cast<int>(
        fabsf(itdSeconds) * static_cast<float>(engine.sampleRate)
    );

    // Interaural level difference.
    float leftGain = 1.0f;
    float rightGain = 1.0f;

    if (radians > 0.0f) {
        rightGain = cosf(radians * 0.5f);
        leftGain = 1.0f;
    } else {
        leftGain = cosf(-radians * 0.5f);
        rightGain = 1.0f;
    }

    const float mono = (inputLeft + inputRight) * 0.5f;

    // Delay the ear farther from the source.
    float delayedLeft = mono;
    float delayedRight = mono;

    if (itdSamples > 0) {
        if (radians > 0.0f) {
            delayedRight = delayRead(
                engine.rightDelay,
                engine.delayWrite,
                itdSamples
            );
        } else {
            delayedLeft = delayRead(
                engine.leftDelay,
                engine.delayWrite,
                itdSamples
            );
        }
    }

    // Rear sources are darker and slightly more diffuse.
    const float rearAmount =
        fabsf(radians) > kPi * 0.5f
            ? (fabsf(radians) - kPi * 0.5f) / (kPi * 0.5f)
            : 0.0f;

    const float lowpassCoefficient =
        0.35f - 0.18f * rearAmount;

    engine.leftLow = onePoleLowpass(
        engine.leftLow,
        delayedLeft,
        lowpassCoefficient
    );

    engine.rightLow = onePoleLowpass(
        engine.rightLow,
        delayedRight,
        lowpassCoefficient
    );

    float left = delayedLeft * leftGain;
    float right = delayedRight * rightGain;

    // Crossfeed keeps the image natural on headphones.
    left += engine.rightLow * 0.16f;
    right += engine.leftLow * 0.16f;

    // Gentle early-reflection style room tail.
    const float reverbDelaySamples =
        static_cast<float>(engine.sampleRate) * 0.037f;

    const int reverbDelay = static_cast<int>(reverbDelaySamples);

    const float reverbLeft = delayRead(
        engine.reverbDelayL,
        engine.reverbWrite,
        reverbDelay
    );

    const float reverbRight = delayRead(
        engine.reverbDelayR,
        engine.reverbWrite,
        reverbDelay
    );

    const float wet =
        reverbWet < 0.0f ? 0.0f :
        reverbWet > 0.5f ? 0.5f :
        reverbWet;

    left += reverbLeft * wet;
    right += reverbRight * wet;

    delayWriteSample(
        engine.reverbDelayL,
        engine.reverbWrite,
        left * 0.22f
    );

    delayWriteSample(
        engine.reverbDelayR,
        engine.reverbWrite,
        right * 0.22f
    );

    // Store mono input for future ITD reads.
    delayWriteSample(
        engine.leftDelay,
        engine.delayWrite,
        mono
    );

    delayWriteSample(
        engine.rightDelay,
        engine.delayWrite,
        mono
    );

    engine.delayWrite =
        (engine.delayWrite + 1) % kMaxDelaySamples;

    engine.reverbWrite =
        (engine.reverbWrite + 1) % kMaxDelaySamples;

    outputLeft = left;
    outputRight = right;
}

BinauralEngine *asEngine(jlong handle) {
    return reinterpret_cast<BinauralEngine *>(handle);
}

} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_soniclab_player_HrtfSpatialEngine_nativeCreate(
    JNIEnv *env,
    jobject,
    jint sampleRate,
    jstring hrtfAssetPath
) {
    const char *assetPath =
        env->GetStringUTFChars(hrtfAssetPath, nullptr);

    if (assetPath == nullptr) {
        return 0;
    }

    LOGI(
        "Binaural HRTF engine initialized. sampleRate=%d asset=%s",
        sampleRate,
        assetPath
    );

    env->ReleaseStringUTFChars(hrtfAssetPath, assetPath);

    if (sampleRate < 8000 || sampleRate > 192000) {
        return 0;
    }

    auto *engine = new BinauralEngine();
    engine->sampleRate = sampleRate;

    return reinterpret_cast<jlong>(engine);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_soniclab_player_HrtfSpatialEngine_nativeProcess(
    JNIEnv *,
    jobject,
    jlong handle,
    jfloatArray input,
    jfloatArray output,
    jint frames,
    jfloat azimuthDegrees,
    jfloat elevationDegrees,
    jfloat reverbWet
) {
    auto *engine = asEngine(handle);

    if (engine == nullptr || frames <= 0) {
        return false;
    }

    jsize inputLength = env->GetArrayLength(input);
    jsize outputLength = env->GetArrayLength(output);

    if (inputLength < frames * 2 || outputLength < frames * 2) {
        return false;
    }

    jfloat *inputData = env->GetFloatArrayElements(input, nullptr);
    jfloat *outputData = env->GetFloatArrayElements(output, nullptr);

    if (inputData == nullptr || outputData == nullptr) {
        if (inputData != nullptr) {
            env->ReleaseFloatArrayElements(input, inputData, JNI_ABORT);
        }

        if (outputData != nullptr) {
            env->ReleaseFloatArrayElements(output, outputData, JNI_ABORT);
        }

        return false;
    }

    for (int frame = 0; frame < frames; ++frame) {
        const int inputIndex = frame * 2;
        const int outputIndex = frame * 2;

        float left = 0.0f;
        float right = 0.0f;

        processFrame(
            *engine,
            inputData[inputIndex],
            inputData[inputIndex + 1],
            azimuthDegrees,
            reverbWet,
            left,
            right
        );

        outputData[outputIndex] = left;
        outputData[outputIndex + 1] = right;
    }

    env->ReleaseFloatArrayElements(input, inputData, JNI_ABORT);
    env->ReleaseFloatArrayElements(
        output,
        outputData,
        0
    );

    return true;
}

extern "C" JNIEXPORT void JNICALL
Java_com_soniclab_player_HrtfSpatialEngine_nativeDestroy(
    JNIEnv *,
    jobject,
    jlong handle
) {
    auto *engine = asEngine(handle);

    if (engine != nullptr) {
        delete engine;
    }
}