#ifndef SONICLAB_HRTF_ENGINE_H
#define SONICLAB_HRTF_ENGINE_H

#include <cstdint>

class HrtfEngine {
public:
    HrtfEngine();
    ~HrtfEngine();

    bool init(int sampleRate, const char *assetPath);

    int process(
        const float *input,
        float *output,
        int frames,
        float azimuthDegrees,
        float elevationDegrees,
        float reverbWet
    );

    void release();

private:
    int sampleRate_ = 0;
    bool ready_ = false;
};

#endif