// Copyright 2026 soe1hom-arch
// SPDX-License-Identifier: Apache-2.0
//
// Native SonicLab engine: low-latency capture/playback built on Oboe.
// Exposed to Kotlin via JNI as com.soniclab.audioengine.OboeNativeEngine.

#include <jni.h>
#include <android/log.h>
#include <oboe/Oboe.h>

#include <algorithm>
#include <atomic>
#include <cstdint>
#include <memory>
#include <mutex>

#define LOG_TAG "SonicLabOboe"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

constexpr int32_t kSampleRate = 48000;
constexpr int32_t kFramesPerBuffer = 256;
constexpr int32_t kChannelCount = 2;

// Callback signatures on the Kotlin side.
constexpr const char* kCallbackOnCapture =
    "(I[F)V";  // onCapture(sessionId, frames)
constexpr const char* kCallbackOnPlayback =
    "(II)[F";  // onPlayback(sessionId, numFrames) -> float[] or null

jmethodID sOnCapture = nullptr;
jmethodID sOnPlayback = nullptr;

}  // namespace

namespace soniclab {

class OboeEngineImpl : public oboe::AudioStreamCallback {
 public:
  OboeEngineImpl() = default;
  ~OboeEngineImpl() { release(); }

  bool start(JNIEnv* env, jobject callback, int32_t mode) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (running_) return false;
    if (callback == nullptr) return false;

    env_ = env;
    // Keep the Kotlin callback alive for the whole stream lifetime; cleared
    // in release()/cleanup().
    callback_ = env->NewGlobalRef(callback);
    mode_ = mode;

    oboe::AudioStreamBuilder builder;
    builder.setAudioApi(oboe::AudioApi::Unspecified);
    builder.setPerformanceMode(oboe::PerformanceMode::LowLatency);
    builder.setSharingMode(oboe::SharingMode::Exclusive);
    builder.setSampleRate(kSampleRate);
    builder.setFramesPerCallback(kFramesPerBuffer);
    builder.setChannelCount(kChannelCount);
    builder.setFormat(oboe::AudioFormat::Float);
    builder.setCallback(this);

    oboe::Result result = oboe::Result::OK;
    if (mode == 0) {
      // Capture only.
      builder.setDirection(oboe::Direction::Input);
      result = builder.openStream(inputStream_);
    } else if (mode == 1) {
      // Playback only.
      builder.setDirection(oboe::Direction::Output);
      result = builder.openStream(outputStream_);
    } else {
      // Full-duplex: one input + one output with the same configuration.
      builder.setDirection(oboe::Direction::Input);
      result = builder.openStream(inputStream_);
      if (result == oboe::Result::OK) {
        builder.setDirection(oboe::Direction::Output);
        result = builder.openStream(outputStream_);
      }
    }
    if (result != oboe::Result::OK) {
      LOGE("openStream failed: %s", oboe::convertToText(result));
      cleanup();
      return false;
    }

    oboe::Result startInput = oboe::Result::OK;
    oboe::Result startOutput = oboe::Result::OK;
    if (inputStream_) startInput = inputStream_->requestStart();
    if (outputStream_) startOutput = outputStream_->requestStart();
    if (startInput != oboe::Result::OK || startOutput != oboe::Result::OK) {
      LOGE("requestStart failed: in=%s out=%s",
           oboe::convertToText(startInput), oboe::convertToText(startOutput));
      cleanup();
      return false;
    }

    running_ = true;
    LOGI("Oboe engine running: mode=%d sr=%d frames=%d channels=%d", mode_,
         kSampleRate, kFramesPerBuffer, kChannelCount);
    return true;
  }

  void stop() {
    std::lock_guard<std::mutex> lock(mutex_);
    if (inputStream_) inputStream_->requestStop();
    if (outputStream_) outputStream_->requestStop();
  }

  void release() {
    std::lock_guard<std::mutex> lock(mutex_);
    running_ = false;
    inputStream_.reset();
    outputStream_.reset();
    dropCallback();
  }

  // Oboe callback (real-time audio thread). No locks and no allocation in the
  // hot path beyond the per-callback Java array; the Java layer is only
  // reached through a cached method ID and the pre-registered callback.
  oboe::DataCallbackResult onAudioReady(oboe::AudioStream* stream,
                                        void* audioData, int32_t numFrames) {
    if (!running_ || env_ == nullptr || callback_ == nullptr) {
      return oboe::DataCallbackResult::Continue;
    }
    auto* samples = static_cast<float*>(audioData);
    const int32_t sampleCount = numFrames * kChannelCount;

    if (stream == outputStream_.get()) {
      // Playback: ask the Java layer for the next chunk; silence when none.
      std::fill(samples, samples + sampleCount, 0.0f);
      if (sOnPlayback == nullptr) return oboe::DataCallbackResult::Continue;
      jfloatArray array = static_cast<jfloatArray>(
          env_->CallObjectMethod(callback_, sOnPlayback, sessionId_, numFrames));
      if (env_->ExceptionCheck()) {
        env_->ExceptionDescribe();
        env_->ExceptionClear();
      }
      if (array != nullptr) {
        jfloat* ptr = env_->GetFloatArrayElements(array, nullptr);
        if (ptr != nullptr) {
          const int32_t copy = std::min<int32_t>(
              static_cast<int32_t>(env_->GetArrayLength(array)), sampleCount);
          std::copy(ptr, ptr + copy, samples);
          env_->ReleaseFloatArrayElements(array, ptr, JNI_ABORT);
        }
        env_->DeleteLocalRef(array);
      }
      return oboe::DataCallbackResult::Continue;
    }

    if (stream != inputStream_.get() || sOnCapture == nullptr) {
      return oboe::DataCallbackResult::Continue;
    }
    jfloatArray array = env_->NewFloatArray(sampleCount);
    if (array == nullptr) return oboe::DataCallbackResult::Continue;
    env_->SetFloatArrayRegion(array, 0, sampleCount, samples);
    env_->CallVoidMethod(callback_, sOnCapture, sessionId_, array);
    env_->DeleteLocalRef(array);
    if (env_->ExceptionCheck()) {
      env_->ExceptionDescribe();
      env_->ExceptionClear();
    }
    return oboe::DataCallbackResult::Continue;
  }

  void onErrorBeforeClose(oboe::AudioStream* stream, oboe::Result error) {
    (void)stream;
    LOGE("stream error before close: %s", oboe::convertToText(error));
  }

  void onErrorAfterClose(oboe::AudioStream* stream, oboe::Result error) {
    (void)stream;
    LOGE("stream closed with error: %s", oboe::convertToText(error));
  }

  bool isRunning() const { return running_.load(); }

  void setSessionId(int32_t id) { sessionId_ = id; }

 private:
  void cleanup() {
    inputStream_.reset();
    outputStream_.reset();
    dropCallback();
  }

  void dropCallback() {
    if (env_ != nullptr && callback_ != nullptr) {
      env_->DeleteGlobalRef(callback_);
      callback_ = nullptr;
      env_ = nullptr;
    }
  }

  JNIEnv* env_ = nullptr;
  jobject callback_ = nullptr;
  std::shared_ptr<oboe::AudioStream> inputStream_;
  std::shared_ptr<oboe::AudioStream> outputStream_;
  std::mutex mutex_;
  std::atomic<bool> running_{false};
  int32_t mode_ = 0;
  int32_t sessionId_ = 0;
};

}  // namespace soniclab

static soniclab::OboeEngineImpl gEngine;

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jboolean JNICALL
Java_com_soniclab_audioengine_OboeNativeEngine_nativeStart(
    JNIEnv* env, jobject thiz, jobject callback, jint mode, jint sessionId) {
  (void)thiz;
  if (sOnCapture == nullptr || sOnPlayback == nullptr) {
    jclass cls = env->GetObjectClass(callback);
    if (cls == nullptr) return JNI_FALSE;
    sOnCapture = env->GetMethodID(cls, "onCapture", kCallbackOnCapture);
    sOnPlayback = env->GetMethodID(cls, "onPlayback", kCallbackOnPlayback);
    env->DeleteLocalRef(cls);
    if (sOnCapture == nullptr || sOnPlayback == nullptr) return JNI_FALSE;
  }
  gEngine.setSessionId(sessionId);
  return gEngine.start(env, callback, mode) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_soniclab_audioengine_OboeNativeEngine_nativeStop(JNIEnv*, jobject) {
  gEngine.stop();
}

JNIEXPORT void JNICALL
Java_com_soniclab_audioengine_OboeNativeEngine_nativeRelease(JNIEnv* env,
                                                             jobject) {
  (void)env;
  gEngine.release();
}

JNIEXPORT jboolean JNICALL
Java_com_soniclab_audioengine_OboeNativeEngine_nativeIsRunning(JNIEnv*,
                                                               jobject) {
  return gEngine.isRunning() ? JNI_TRUE : JNI_FALSE;
}

#ifdef __cplusplus
}
#endif
