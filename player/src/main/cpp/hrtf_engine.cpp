#include "hrtf_engine.h"

#include <android/log.h>
#include <jni.h>

#define LOG_TAG "SonicLabHRTF"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

HrtfEngine::HrtfEngine() = default;

HrtfEngine::~HrtfEngine() {
    release();
}

bool HrtfEngine::init(int sampleRate, const char *assetPath) {
    if (sampleRate <= 0 || assetPath == nullptr) {
        LOGE("HRTF init failed: invalid sample rate or asset path");
        return false;
    }

    sampleRate_ = sampleRate;

    // Saat ini engine belum memuat dataset HRTF.
    // Return false agar aplikasi memakai fallback 8D lama.
    LOGI("HRTF engine placeholder initialized. sampleRate=%d asset=%s",
         sampleRate,
         assetPath);

    ready_ = false;
    return false;
}

int HrtfEngine::process(
    const float *input,
    float *output,
    int frames,
    float azimuthDegrees,
    float elevationDegrees,
    float reverbWet
) {
    // Belum ada HRTF processing.
    // Aplikasi akan tetap memakai proses 8D lama.
    return 0;
}

void HrtfEngine::release() {
    ready_ = false;
}

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_soniclab_player_HrtfSpatialEngine_nativeInit(
        JNIEnv *env,
        jobject,
        jint sampleRate,
        jstring hrtfAssetPath
) {
    const char *path = env->GetStringUTFChars(hrtfAssetPath, nullptr);

    auto *engine = new HrtfEngine();

    if (!engine->init(sampleRate, path)) {
        env->ReleaseStringUTFChars(hrtfAssetPath, path);
        delete engine;
        return 0;
    }

    env->ReleaseStringUTFChars(hrtfAssetPath, path);
    return reinterpret_cast<jlong>(engine);
}

JNIEXPORT jint JNICALL
Java_com_soniclab_player_HrtfSpatialEngine_nativeProcess(
        JNIEnv *env,
        jobject,
        jlong handle,
        jfloatArray input,
        jfloatArray output,
        jint frames,
        jfloat azimuthDegrees,
        jfloat elevationDegrees,
        jfloat reverbWet
) {
    if (handle == 0) {
        return 0;
    }

    auto *engine = reinterpret_cast<HrtfEngine *>(handle);

    jfloat *inputData = env->GetFloatArrayElements(input, nullptr);
    jfloat *outputData = env->GetFloatArrayElements(output, nullptr);

    int result = engine->process(
            inputData,
            outputData,
            frames,
            azimuthDegrees,
            elevationDegrees,
            reverbWet
    );

    env->ReleaseFloatArrayElements(input, inputData, JNI_ABORT);
    env->ReleaseFloatArrayElements(output, outputData, 0);

    return result;
}

JNIEXPORT void JNICALL
Java_com_soniclab_player_HrtfSpatialEngine_nativeRelease(
        JNIEnv *env,
        jobject,
        jlong handle
) {
    if (handle == 0) {
        return;
    }

    auto *engine = reinterpret_cast<HrtfEngine *>(handle);
    delete engine;
}

}