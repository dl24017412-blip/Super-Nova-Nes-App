#ifndef SNES_CORE_H
#define SNES_CORE_H

#include "snes_types.h"
#include "snes_memory.h"
#include "cpu65816.h"
#include "snes_ppu.h"
#include "snes_spc.h"

#include <string>
#include <thread>
#include <atomic>
#include <mutex>
#include <vector>

class GLRenderer;
class AudioEngine;

class SnesCore {
public:
    static SnesCore* getInstance();

    bool initialize(const std::string& storagePath);
    int loadRom(const uint8_t* data, size_t size, const std::string& filename);
    void reset();
    void start();
    void pause();
    void resume();
    void stop();
    void shutdown();

    bool isRunning() const { return running.load(); }
    bool isPaused() const { return paused.load(); }
    bool isRomLoaded() const { return romLoaded.load(); }

    void runOneFrame();

    // Input
    void setInputState(int controller, uint16_t buttons);

    // Save States (1..10)
    bool saveState(int slot, const std::string& path);
    bool loadState(int slot, const std::string& path);

    // SRAM
    bool saveSram(const std::string& path);
    bool loadSram(const std::string& path);

    // Fast Forward
    void setFastForward(bool enabled) { fastForward.store(enabled); }
    bool isFastForward() const { return fastForward.load(); }

    // Settings
    void setVideoOptions(AspectRatioMode aspect, VideoFilter filter, PerformanceProfile profile);
    void setAudioOptions(bool enabled, float volume);

    // Renderer & Audio hooks
    void setRenderer(GLRenderer* renderer) { this->renderer = renderer; }
    void setAudioEngine(AudioEngine* audio) { this->audioEngine = audio; }
    GLRenderer* getRenderer() const { return renderer; }
    AudioEngine* getAudioEngine() const { return audioEngine; }

    SnesMemory* getMemory() { return memory; }
    Cpu65816* getCpu() { return cpu; }
    SnesPpu* getPpu() { return ppu; }
    SnesSpc* getSpc() { return spc; }

    const RomHeaderInfo& getRomHeader() const { return memory->getHeader(); }

    // Stats
    void getStats(float& outFps, float& outFrameTimeMs, float& outCpuTimeMs);

private:
    SnesCore();
    ~SnesCore();

    static SnesCore* instance;

    SnesMemory* memory = nullptr;
    Cpu65816* cpu = nullptr;
    SnesPpu* ppu = nullptr;
    SnesSpc* spc = nullptr;

    GLRenderer* renderer = nullptr;
    AudioEngine* audioEngine = nullptr;

    std::string internalPath;
    std::string currentRomPath;
    std::string currentRomName;

    std::atomic<bool> romLoaded{false};
    std::atomic<bool> running{false};
    std::atomic<bool> paused{false};
    std::atomic<bool> threadExit{false};
    std::atomic<bool> fastForward{false};

    std::thread emuThread;
    std::mutex stateMutex;

    // Performance profiling & stats
    std::atomic<uint16_t> joypad1State{0};
    std::atomic<uint16_t> joypad2State{0};

    float currentFps = 0.0f;
    float currentFrameTimeMs = 0.0f;
    float currentCpuTimeMs = 0.0f;

    // Audio buffer for frame
    int16_t audioSampleBuffer[SNES_SAMPLES_PER_FRAME * 2];

    void emulationThreadLoop();
};

#endif // SNES_CORE_H
