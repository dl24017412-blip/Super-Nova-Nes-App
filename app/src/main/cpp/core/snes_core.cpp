#include "snes_core.h"
#include "gl_renderer.h"
#include "audio_engine.h"

#include <chrono>
#include <fstream>
#include <cstring>
#include <algorithm>

SnesCore* SnesCore::instance = nullptr;

SnesCore* SnesCore::getInstance() {
    if (!instance) {
        instance = new SnesCore();
    }
    return instance;
}

SnesCore::SnesCore() {
    memory = new SnesMemory(this);
    cpu = new Cpu65816(memory);
    ppu = new SnesPpu(memory);
    spc = new SnesSpc();
}

SnesCore::~SnesCore() {
    shutdown();
    delete spc;
    delete ppu;
    delete cpu;
    delete memory;
}

bool SnesCore::initialize(const std::string& storagePath) {
    std::lock_guard<std::mutex> lock(stateMutex);
    internalPath = storagePath;
    LOGI("SnesCore initialized with storage path: %s", storagePath.c_str());
    return true;
}

int SnesCore::loadRom(const uint8_t* data, size_t size, const std::string& filename) {
    std::lock_guard<std::mutex> lock(stateMutex);
    stop();

    RomHeaderInfo header{};
    if (!memory->loadRom(data, size, header)) {
        LOGE("Failed to load ROM: %s", filename.c_str());
        return 1; // Invalid ROM
    }

    currentRomName = filename;
    cpu->reset();
    ppu->reset();
    spc->reset();

    // Try auto-loading SRAM
    if (header.hasBattery && !internalPath.empty()) {
        std::string sramPath = internalPath + "/" + filename + ".srm";
        memory->loadSramFromFile(sramPath);
    }

    romLoaded = true;
    LOGI("ROM successfully initialized: %s", header.title);
    return 0; // Success
}

void SnesCore::reset() {
    std::lock_guard<std::mutex> lock(stateMutex);
    if (!romLoaded) return;
    cpu->reset();
    ppu->reset();
    spc->reset();
    memory->reset();
    LOGI("SnesCore reset performed.");
}

void SnesCore::start() {
    if (!romLoaded || running) return;

    running = true;
    paused = false;
    threadExit = false;

    if (audioEngine) {
        audioEngine->start();
    }

    emuThread = std::thread(&SnesCore::emulationThreadLoop, this);
    LOGI("Emulation thread started.");
}

void SnesCore::pause() {
    if (!running || paused) return;
    paused = true;
    if (audioEngine) {
        audioEngine->pause();
    }
    LOGI("Emulation paused.");
}

void SnesCore::resume() {
    if (!running || !paused) return;
    paused = false;
    if (audioEngine) {
        audioEngine->start();
    }
    LOGI("Emulation resumed.");
}

void SnesCore::stop() {
    if (!running) return;

    running = false;
    paused = false;
    threadExit = true;

    if (emuThread.joinable()) {
        emuThread.join();
    }

    if (audioEngine) {
        audioEngine->stop();
    }

    // Auto-save battery SRAM
    if (romLoaded && memory->hasSram() && !internalPath.empty() && !currentRomName.empty()) {
        std::string sramPath = internalPath + "/" + currentRomName + ".srm";
        memory->saveSramToFile(sramPath);
    }

    LOGI("Emulation stopped.");
}

void SnesCore::shutdown() {
    stop();
    romLoaded = false;
}

void SnesCore::setInputState(int controller, uint16_t buttons) {
    if (controller == 0) {
        joypad1State.store(buttons);
    } else {
        joypad2State.store(buttons);
    }
}

void SnesCore::runOneFrame() {
    // Transfer inputs
    memory->setJoypad(0, joypad1State.load());
    memory->setJoypad(1, joypad2State.load());

    // 262 total scanlines in NTSC (0..223 visible, 224..261 VBlank)
    for (int line = 0; line < 224; ++line) {
        memory->setScanline(line);
        memory->runHdma();
        ppu->renderScanline(line);

        // Run CPU cycles for scanline (~1364 master cycles = ~340 CPU cycles)
        int cycles = 0;
        while (cycles < 340) {
            int c = cpu->step();
            spc->step(c);
            cycles += c;
        }
    }

    // VBlank begin
    memory->triggerVBlank();
    if (memory->isNmiEnabled()) {
        cpu->triggerNmi();
    }

    for (int line = 224; line < 262; ++line) {
        memory->setScanline(line);
        int cycles = 0;
        while (cycles < 340) {
            int c = cpu->step();
            spc->step(c);
            cycles += c;
        }
    }

    // Audio generation for this frame (~534 stereo samples at 32kHz)
    if (audioEngine) {
        spc->generateSamples(audioSampleBuffer, SNES_SAMPLES_PER_FRAME);
        audioEngine->writeSamples(audioSampleBuffer, SNES_SAMPLES_PER_FRAME);
    }

    // Video rendering
    if (renderer && renderer->isReady()) {
        renderer->renderFrame(ppu->getFramebuffer());
    }
}

void SnesCore::emulationThreadLoop() {
    using namespace std::chrono;
    constexpr auto targetFrameDuration = microseconds(16667); // ~60 FPS

    auto lastTime = steady_clock::now();
    int frameCount = 0;
    auto fpsStartTime = lastTime;

    while (!threadExit.load()) {
        if (paused.load()) {
            std::this_thread::sleep_for(milliseconds(20));
            lastTime = steady_clock::now();
            continue;
        }

        auto frameStart = steady_clock::now();

        // Run 1 emulation frame
        runOneFrame();

        auto frameEnd = steady_clock::now();
        auto cpuDuration = duration_cast<microseconds>(frameEnd - frameStart);
        currentCpuTimeMs = cpuDuration.count() / 1000.0f;

        // Frame pacing
        auto target = fastForward.load() ? microseconds(8333) : microseconds(16667);
        auto elapsed = duration_cast<microseconds>(frameEnd - lastTime);
        if (elapsed < target) {
            std::this_thread::sleep_for(target - elapsed);
        }
        auto totalFrameDuration = duration_cast<microseconds>(steady_clock::now() - lastTime);
        currentFrameTimeMs = totalFrameDuration.count() / 1000.0f;
        lastTime = steady_clock::now();

        // FPS Calculation
        frameCount++;
        auto fpsElapsed = duration_cast<milliseconds>(lastTime - fpsStartTime);
        if (fpsElapsed.count() >= 1000) {
            currentFps = (float)frameCount * 1000.0f / (float)fpsElapsed.count();
            frameCount = 0;
            fpsStartTime = lastTime;
        }
    }
}

void SnesCore::getStats(float& outFps, float& outFrameTimeMs, float& outCpuTimeMs) {
    outFps = currentFps;
    outFrameTimeMs = currentFrameTimeMs;
    outCpuTimeMs = currentCpuTimeMs;
}

void SnesCore::setVideoOptions(AspectRatioMode aspect, VideoFilter filter, PerformanceProfile profile) {
    if (renderer) {
        renderer->setOptions(aspect, filter, profile);
    }
    if (ppu) {
        ppu->setPerformanceProfile(profile);
    }
}

void SnesCore::setAudioOptions(bool enabled, float volume) {
    if (audioEngine) {
        audioEngine->setEnabled(enabled);
        audioEngine->setVolume(volume);
    }
    if (spc) {
        spc->setAudioEnabled(enabled);
        spc->setMasterVolume(volume);
    }
}

bool SnesCore::saveState(int slot, const std::string& path) {
    std::lock_guard<std::mutex> lock(stateMutex);
    if (!romLoaded) return false;

    std::ofstream file(path, std::ios::binary);
    if (!file.is_open()) return false;

    char magic[8] = {'S', 'N', 'O', 'V', 'A', 'S', '0', '1'};
    file.write(magic, 8);

    uint64_t timestamp = std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::system_clock::now().time_since_epoch()).count();
    file.write(reinterpret_cast<const char*>(&timestamp), sizeof(timestamp));

    std::vector<uint8_t> buffer(memory->getStateSize() + cpu->getStateSize() + ppu->getStateSize() + spc->getStateSize() + 1024);

    size_t wMem = 0, wCpu = 0, wPpu = 0, wSpc = 0;
    memory->serialize(buffer.data(), buffer.size(), wMem);
    cpu->serialize(buffer.data() + wMem, buffer.size() - wMem, wCpu);
    ppu->serialize(buffer.data() + wMem + wCpu, buffer.size() - wMem - wCpu, wPpu);
    spc->serialize(buffer.data() + wMem + wCpu + wPpu, buffer.size() - wMem - wCpu - wPpu, wSpc);

    uint32_t totalSize = wMem + wCpu + wPpu + wSpc;
    file.write(reinterpret_cast<const char*>(&totalSize), sizeof(totalSize));
    file.write(reinterpret_cast<const char*>(buffer.data()), totalSize);

    file.close();
    LOGI("Saved state slot %d to %s (%u bytes)", slot, path.c_str(), totalSize);
    return true;
}

bool SnesCore::loadState(int slot, const std::string& path) {
    std::lock_guard<std::mutex> lock(stateMutex);
    if (!romLoaded) return false;

    std::ifstream file(path, std::ios::binary);
    if (!file.is_open()) return false;

    char magic[8];
    file.read(magic, 8);
    if (std::memcmp(magic, "SNOVAS01", 8) != 0) {
        LOGE("Invalid save state header!");
        return false;
    }

    uint64_t timestamp = 0;
    file.read(reinterpret_cast<char*>(&timestamp), sizeof(timestamp));

    uint32_t totalSize = 0;
    file.read(reinterpret_cast<char*>(&totalSize), sizeof(totalSize));

    std::vector<uint8_t> buffer(totalSize);
    file.read(reinterpret_cast<char*>(buffer.data()), totalSize);
    file.close();

    size_t rMem = 0, rCpu = 0, rPpu = 0, rSpc = 0;
    memory->deserialize(buffer.data(), totalSize, rMem);
    cpu->deserialize(buffer.data() + rMem, totalSize - rMem, rCpu);
    ppu->deserialize(buffer.data() + rMem + rCpu, totalSize - rMem - rCpu, rPpu);
    spc->deserialize(buffer.data() + rMem + rCpu + rPpu, totalSize - rMem - rCpu - rPpu, rSpc);

    LOGI("Loaded state slot %d from %s", slot, path.c_str());
    return true;
}

bool SnesCore::saveSram(const std::string& path) {
    std::lock_guard<std::mutex> lock(stateMutex);
    return memory->saveSramToFile(path);
}

bool SnesCore::loadSram(const std::string& path) {
    std::lock_guard<std::mutex> lock(stateMutex);
    return memory->loadSramFromFile(path);
}
