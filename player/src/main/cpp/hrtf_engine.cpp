// Copyright 2026 soe1hom-arch
// SPDX-License-Identifier: Apache-2.0

#include <jni.h>
#include <android/log.h>
#include <mysofa.h>

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <memory>
#include <new>
#include <vector>

#define LOG_TAG "SonicLabHRTF"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

constexpr float kPi = 3.14159265358979323846f;
constexpr int kDelayBufferSize = 4096;
constexpr float kSourceDistanceMeters = 1.0f;

struct BinauralEngine {
    MYSOFA_EASY *sofa = nullptr;
    int sampleRate = 48000;
    int filterLength = 0;
    std::vector<float> targetLeft;
    std::vector<float> targetRight;
    std::vector<float> activeLeft;
    std::vector<float> activeRight;
    std::vector<float> history;
    std::vector<float> delayLeft;
    std::vector<float> delayRight;
    int historyWrite = 0;
    int delayWrite = 0;
    float delayLeftSamples = 0.0f;
    float delayRightSamples = 0.0f;
    float lastAzimuth = 10000.0f;
    float lastElevation = 10000.0f;
    float reverbStateLeft = 0.0f;
    float reverbStateRight = 0.0f;
};

BinauralEngine *asEngine(jlong handle) {
    return reinterpret_cast<BinauralEngine *>(handle);
}

float readFractionalDelay(const std::vector<float> &buffer, int write, float delay) {
    const float bounded = std::clamp(delay, 0.0f, static_cast<float>(kDelayBufferSize - 2));
    float position = static_cast<float>(write) - bounded;
    while (position < 0.0f) position += static_cast<float>(kDelayBufferSize);
    while (position >= static_cast<float>(kDelayBufferSize)) position -= static_cast<float>(kDelayBufferSize);
    const int index0 = static_cast<int>(position);
    const int index1 = (index0 + 1) % kDelayBufferSize;
    const float fraction = position - static_cast<float>(index0);
    return buffer[index0] * (1.0f - fraction) + buffer[index1] * fraction;
}

bool updateFilter(BinauralEngine &engine, float azimuthDegrees, float elevationDegrees) {
    if (engine.sofa == nullptr || engine.filterLength <= 0) return false;
    const float azimuth = std::remainder(azimuthDegrees, 360.0f);
    const float elevation = std::clamp(elevationDegrees, -90.0f, 90.0f);
    if (std::fabs(azimuth - engine.lastAzimuth) < 0.25f &&
        std::fabs(elevation - engine.lastElevation) < 0.25f) {
        return true;
    }

    float position[3] = {azimuth, elevation, kSourceDistanceMeters};
    mysofa_s2c(position);
    float delayLSeconds = 0.0f;
    float delayRSeconds = 0.0f;
    mysofa_getfilter_float(
        engine.sofa, position[0], position[1], position[2],
        engine.targetLeft.data(), engine.targetRight.data(),
        &delayLSeconds, &delayRSeconds
    );

    if (!std::isfinite(delayLSeconds) || !std::isfinite(delayRSeconds)) return false;
    for (int i = 0; i < engine.filterLength; ++i) {
        if (!std::isfinite(engine.targetLeft[i]) || !std::isfinite(engine.targetRight[i])) return false;
    }

    engine.delayLeftSamples = std::clamp(delayLSeconds * engine.sampleRate, 0.0f, 100.0f);
    engine.delayRightSamples = std::clamp(delayRSeconds * engine.sampleRate, 0.0f, 100.0f);
    engine.lastAzimuth = azimuth;
    engine.lastElevation = elevation;
    return true;
}

void processOne(BinauralEngine &engine, float input, float wet, float &left, float &right) {
    engine.history[engine.historyWrite] = input;

    float sumL = 0.0f;
    float sumR = 0.0f;
    int historyIndex = engine.historyWrite;
    for (int tap = 0; tap < engine.filterLength; ++tap) {
        sumL += engine.activeLeft[tap] * engine.history[historyIndex];
        sumR += engine.activeRight[tap] * engine.history[historyIndex];
        if (--historyIndex < 0) historyIndex = engine.filterLength - 1;
    }

    engine.delayLeft[engine.delayWrite] = sumL;
    engine.delayRight[engine.delayWrite] = sumR;
    left = readFractionalDelay(engine.delayLeft, engine.delayWrite, engine.delayLeftSamples);
    right = readFractionalDelay(engine.delayRight, engine.delayWrite, engine.delayRightSamples);

    // Keep the optional room component subtle; the spatial filtering itself is
    // exclusively produced by the measured SOFA impulse responses above.
    const float reverb = std::clamp(wet, 0.0f, 0.25f);
    engine.reverbStateLeft = engine.reverbStateLeft * 0.72f + right * 0.08f;
    engine.reverbStateRight = engine.reverbStateRight * 0.72f + left * 0.08f;
    left += engine.reverbStateLeft * reverb;
    right += engine.reverbStateRight * reverb;

    engine.historyWrite = (engine.historyWrite + 1) % engine.filterLength;
    engine.delayWrite = (engine.delayWrite + 1) % kDelayBufferSize;
}

} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_soniclab_player_HrtfSpatialEngine_nativeCreate(
    JNIEnv *env, jobject, jint sampleRate, jbyteArray sofaData
) {
    if (sofaData == nullptr || sampleRate < 8000 || sampleRate > 192000) {
        LOGE("Invalid HRTF initialization arguments: sampleRate=%d", sampleRate);
        return 0;
    }

    const jsize dataSize = env->GetArrayLength(sofaData);
    if (dataSize <= 0) {
        LOGE("SOFA asset is empty");
        return 0;
    }

    std::vector<char> data(static_cast<size_t>(dataSize));
    env->GetByteArrayRegion(sofaData, 0, dataSize, reinterpret_cast<jbyte *>(data.data()));
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        LOGE("Unable to copy SOFA bytes from Java");
        return 0;
    }

    int filterLength = 0;
    int error = MYSOFA_OK;
    MYSOFA_EASY *sofa = mysofa_open_data_no_norm(
        data.data(), static_cast<long>(data.size()), static_cast<float>(sampleRate),
        &filterLength, &error
    );
    if (sofa == nullptr || filterLength <= 0) {
        LOGE("SOFA load failed: error=%d filterLength=%d bytes=%d", error, filterLength, dataSize);
        if (sofa != nullptr) mysofa_close(sofa);
        return 0;
    }

    auto engine = std::unique_ptr<BinauralEngine>(new (std::nothrow) BinauralEngine());
    if (!engine) {
        mysofa_close(sofa);
        LOGE("Not enough memory for HRTF engine");
        return 0;
    }

    engine->sofa = sofa;
    engine->sampleRate = sampleRate;
    engine->filterLength = filterLength;
    engine->targetLeft.resize(filterLength);
    engine->targetRight.resize(filterLength);
    engine->activeLeft.resize(filterLength);
    engine->activeRight.resize(filterLength);
    engine->history.assign(filterLength, 0.0f);
    engine->delayLeft.assign(kDelayBufferSize, 0.0f);
    engine->delayRight.assign(kDelayBufferSize, 0.0f);

    if (!updateFilter(*engine, 0.0f, 0.0f)) {
        LOGE("SOFA loaded but initial HRTF filter query failed");
        mysofa_close(sofa);
        return 0;
    }
    engine->activeLeft = engine->targetLeft;
    engine->activeRight = engine->targetRight;

    LOGI("SOFA_LOADED: bytes=%d sampleRate=%d filterLength=%d", dataSize, sampleRate, filterLength);
    return reinterpret_cast<jlong>(engine.release());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_soniclab_player_HrtfSpatialEngine_nativeProcess(
    JNIEnv *env, jobject, jlong handle, jfloatArray input, jfloatArray output,
    jint frames, jfloat azimuthDegrees, jfloat elevationDegrees, jfloat reverbWet
) {
    auto *engine = asEngine(handle);
    if (engine == nullptr || input == nullptr || output == nullptr || frames <= 0) return JNI_FALSE;
    const jsize inputLength = env->GetArrayLength(input);
    const jsize outputLength = env->GetArrayLength(output);
    if (inputLength < frames || outputLength < frames * 2) return JNI_FALSE;

    jfloat *inputData = env->GetFloatArrayElements(input, nullptr);
    jfloat *outputData = env->GetFloatArrayElements(output, nullptr);
    if (inputData == nullptr || outputData == nullptr) {
        if (inputData != nullptr) env->ReleaseFloatArrayElements(input, inputData, JNI_ABORT);
        if (outputData != nullptr) env->ReleaseFloatArrayElements(output, outputData, 0);
        return JNI_FALSE;
    }

    if (!updateFilter(*engine, azimuthDegrees, elevationDegrees)) {
        env->ReleaseFloatArrayElements(input, inputData, JNI_ABORT);
        env->ReleaseFloatArrayElements(output, outputData, JNI_ABORT);
        LOGE("HRTF filter lookup failed during processing");
        return JNI_FALSE;
    }

    // Smooth filter changes over a short interval to avoid clicks while the
    // virtual source rotates through the measured HRTF field.
    constexpr float kFilterSmoothing = 0.18f;
    for (int tap = 0; tap < engine->filterLength; ++tap) {
        engine->activeLeft[tap] += kFilterSmoothing * (engine->targetLeft[tap] - engine->activeLeft[tap]);
        engine->activeRight[tap] += kFilterSmoothing * (engine->targetRight[tap] - engine->activeRight[tap]);
    }

    for (int frame = 0; frame < frames; ++frame) {
        float left = 0.0f;
        float right = 0.0f;
        processOne(*engine, inputData[frame], reverbWet, left, right);
        outputData[frame * 2] = left;
        outputData[frame * 2 + 1] = right;
    }

    env->ReleaseFloatArrayElements(input, inputData, JNI_ABORT);
    env->ReleaseFloatArrayElements(output, outputData, 0);
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_soniclab_player_HrtfSpatialEngine_nativeDestroy(JNIEnv *, jobject, jlong handle) {
    auto *engine = asEngine(handle);
    if (engine != nullptr) {
        if (engine->sofa != nullptr) mysofa_close(engine->sofa);
        delete engine;
    }
}
