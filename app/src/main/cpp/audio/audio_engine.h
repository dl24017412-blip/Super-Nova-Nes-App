#ifndef AUDIO_ENGINE_H
#define AUDIO_ENGINE_H

#include "snes_types.h"
#include <SLES/OpenSLES.h>
#include <SLES/OpenSLES_Android.h>
#include <mutex>
#include <vector>

class AudioEngine {
public:
    AudioEngine();
    ~AudioEngine();

    bool init();
    void shutdown();

    void start();
    void pause();
    void stop();

    void setVolume(float volume);
    void setEnabled(bool enabled);

    void writeSamples(const int16_t* samples, int sampleCount);

    // OpenSL ES callback
    void bqPlayerCallback(SLAndroidSimpleBufferQueueItf bq);

private:
    SLObjectItf engineObject = nullptr;
    SLEngineItf engineEngine = nullptr;

    SLObjectItf outputMixObject = nullptr;

    SLObjectItf bqPlayerObject = nullptr;
    SLPlayItf bqPlayerPlay = nullptr;
    SLAndroidSimpleBufferQueueItf bqPlayerBufferQueue = nullptr;
    SLVolumeItf bqPlayerVolume = nullptr;

    bool initialized = false;
    bool isPlaying = false;
    bool enabled = true;
    float currentVolume = 1.0f;

    static constexpr size_t RING_BUFFER_SIZE = 16384; // Stereo samples
    int16_t ringBuffer[RING_BUFFER_SIZE];
    size_t ringRead = 0;
    size_t ringWrite = 0;
    size_t ringAvailable = 0;
    std::mutex audioMutex;

    int16_t playbackBuffer[SNES_SAMPLES_PER_FRAME * 2];
};

#endif // AUDIO_ENGINE_H
