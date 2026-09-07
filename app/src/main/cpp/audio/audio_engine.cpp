#include "audio_engine.h"
#include <cstring>
#include <cmath>
#include <algorithm>

static void bqCallbackWrapper(SLAndroidSimpleBufferQueueItf bq, void* context) {
    if (context) {
        reinterpret_cast<AudioEngine*>(context)->bqPlayerCallback(bq);
    }
}

AudioEngine::AudioEngine() {
    std::memset(ringBuffer, 0, sizeof(ringBuffer));
    std::memset(playbackBuffer, 0, sizeof(playbackBuffer));
}

AudioEngine::~AudioEngine() {
    shutdown();
}

bool AudioEngine::init() {
    if (initialized) return true;

    SLresult result = slCreateEngine(&engineObject, 0, nullptr, 0, nullptr, nullptr);
    if (result != SL_RESULT_SUCCESS) return false;

    result = (*engineObject)->Realize(engineObject, SL_BOOLEAN_FALSE);
    if (result != SL_RESULT_SUCCESS) return false;

    result = (*engineObject)->GetInterface(engineObject, SL_IID_ENGINE, &engineEngine);
    if (result != SL_RESULT_SUCCESS) return false;

    result = (*engineEngine)->CreateOutputMix(engineEngine, &outputMixObject, 0, nullptr, nullptr);
    if (result != SL_RESULT_SUCCESS) return false;

    result = (*outputMixObject)->Realize(outputMixObject, SL_BOOLEAN_FALSE);
    if (result != SL_RESULT_SUCCESS) return false;

    SLDataLocator_AndroidSimpleBufferQueue loc_bufq = {
        SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE, 2
    };

    SLDataFormat_PCM format_pcm = {
        SL_DATAFORMAT_PCM,
        2, // Stereo
        SL_SAMPLINGRATE_32, // 32000 Hz
        SL_PCMSAMPLEFORMAT_FIXED_16,
        SL_PCMSAMPLEFORMAT_FIXED_16,
        SL_SPEAKER_FRONT_LEFT | SL_SPEAKER_FRONT_RIGHT,
        SL_BYTEORDER_LITTLEENDIAN
    };

    SLDataSource audioSrc = { &loc_bufq, &format_pcm };

    SLDataLocator_OutputMix loc_outmix = {
        SL_DATALOCATOR_OUTPUTMIX, outputMixObject
    };
    SLDataSink audioSnk = { &loc_outmix, nullptr };

    const SLInterfaceID ids[2] = { SL_IID_ANDROIDSIMPLEBUFFERQUEUE, SL_IID_VOLUME };
    const SLboolean req[2] = { SL_BOOLEAN_TRUE, SL_BOOLEAN_TRUE };

    result = (*engineEngine)->CreateAudioPlayer(engineEngine, &bqPlayerObject, &audioSrc, &audioSnk, 2, ids, req);
    if (result != SL_RESULT_SUCCESS) return false;

    result = (*bqPlayerObject)->Realize(bqPlayerObject, SL_BOOLEAN_FALSE);
    if (result != SL_RESULT_SUCCESS) return false;

    result = (*bqPlayerObject)->GetInterface(bqPlayerObject, SL_IID_PLAY, &bqPlayerPlay);
    if (result != SL_RESULT_SUCCESS) return false;

    result = (*bqPlayerObject)->GetInterface(bqPlayerObject, SL_IID_ANDROIDSIMPLEBUFFERQUEUE, &bqPlayerBufferQueue);
    if (result != SL_RESULT_SUCCESS) return false;

    result = (*bqPlayerBufferQueue)->RegisterCallback(bqPlayerBufferQueue, bqCallbackWrapper, this);
    if (result != SL_RESULT_SUCCESS) return false;

    result = (*bqPlayerObject)->GetInterface(bqPlayerObject, SL_IID_VOLUME, &bqPlayerVolume);
    if (result != SL_RESULT_SUCCESS) {
        bqPlayerVolume = nullptr;
    }

    initialized = true;
    LOGI("AudioEngine initialized with OpenSL ES at 32kHz stereo.");
    return true;
}

void AudioEngine::start() {
    if (!initialized || isPlaying) return;
    if (bqPlayerPlay) {
        (*bqPlayerPlay)->SetPlayState(bqPlayerPlay, SL_PLAYSTATE_PLAYING);
        isPlaying = true;
        // Prime buffer queue with silence
        std::memset(playbackBuffer, 0, sizeof(playbackBuffer));
        (*bqPlayerBufferQueue)->Enqueue(bqPlayerBufferQueue, playbackBuffer, sizeof(playbackBuffer));
    }
}

void AudioEngine::pause() {
    if (!initialized || !isPlaying) return;
    if (bqPlayerPlay) {
        (*bqPlayerPlay)->SetPlayState(bqPlayerPlay, SL_PLAYSTATE_PAUSED);
        isPlaying = false;
    }
}

void AudioEngine::stop() {
    if (!initialized) return;
    if (bqPlayerPlay) {
        (*bqPlayerPlay)->SetPlayState(bqPlayerPlay, SL_PLAYSTATE_STOPPED);
        isPlaying = false;
    }
    if (bqPlayerBufferQueue) {
        (*bqPlayerBufferQueue)->Clear(bqPlayerBufferQueue);
    }
    std::lock_guard<std::mutex> lock(audioMutex);
    ringRead = ringWrite = ringAvailable = 0;
}

void AudioEngine::shutdown() {
    stop();
    if (bqPlayerObject) {
        (*bqPlayerObject)->Destroy(bqPlayerObject);
        bqPlayerObject = nullptr;
        bqPlayerPlay = nullptr;
        bqPlayerBufferQueue = nullptr;
        bqPlayerVolume = nullptr;
    }
    if (outputMixObject) {
        (*outputMixObject)->Destroy(outputMixObject);
        outputMixObject = nullptr;
    }
    if (engineObject) {
        (*engineObject)->Destroy(engineObject);
        engineObject = nullptr;
        engineEngine = nullptr;
    }
    initialized = false;
}

void AudioEngine::setVolume(float volume) {
    currentVolume = std::max(0.0f, std::min(1.0f, volume));
    if (bqPlayerVolume) {
        SLmillibel mb = SL_MILLIBEL_MIN;
        if (currentVolume > 0.001f) {
            mb = static_cast<SLmillibel>(2000.0f * log10f(currentVolume));
        }
        (*bqPlayerVolume)->SetVolumeLevel(bqPlayerVolume, mb);
    }
}

void AudioEngine::setEnabled(bool en) {
    enabled = en;
    if (!enabled) {
        pause();
    } else {
        start();
    }
}

void AudioEngine::writeSamples(const int16_t* samples, int sampleCount) {
    if (!initialized || !enabled || !samples || sampleCount <= 0) return;

    std::lock_guard<std::mutex> lock(audioMutex);
    int totalElements = sampleCount * SNES_AUDIO_CHANNELS;

    for (int i = 0; i < totalElements; ++i) {
        ringBuffer[ringWrite] = samples[i];
        ringWrite = (ringWrite + 1) % RING_BUFFER_SIZE;
        if (ringAvailable < RING_BUFFER_SIZE) {
            ringAvailable++;
        } else {
            // Drop oldest to avoid overflow latency
            ringRead = (ringRead + 1) % RING_BUFFER_SIZE;
        }
    }
}

void AudioEngine::bqPlayerCallback(SLAndroidSimpleBufferQueueItf bq) {
    std::lock_guard<std::mutex> lock(audioMutex);

    size_t needed = SNES_SAMPLES_PER_FRAME * SNES_AUDIO_CHANNELS;
    if (ringAvailable >= needed) {
        for (size_t i = 0; i < needed; ++i) {
            playbackBuffer[i] = ringBuffer[ringRead];
            ringRead = (ringRead + 1) % RING_BUFFER_SIZE;
        }
        ringAvailable -= needed;
    } else {
        // Underflow: zero out remaining
        for (size_t i = 0; i < ringAvailable; ++i) {
            playbackBuffer[i] = ringBuffer[ringRead];
            ringRead = (ringRead + 1) % RING_BUFFER_SIZE;
        }
        std::memset(&playbackBuffer[ringAvailable], 0, (needed - ringAvailable) * sizeof(int16_t));
        ringAvailable = 0;
    }

    (*bq)->Enqueue(bq, playbackBuffer, needed * sizeof(int16_t));
}
