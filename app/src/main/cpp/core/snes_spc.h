#ifndef SNES_SPC_H
#define SNES_SPC_H

#include "snes_types.h"
#include <cstdint>
#include <vector>

class SnesSpc {
public:
    SnesSpc();
    ~SnesSpc();

    void reset();
    void step(int cycles);
    
    // Communication ports with 65816 CPU ($2140-$2143)
    uint8_t readPort(uint8_t port);
    void writePort(uint8_t port, uint8_t val);

    // Audio generation
    int generateSamples(int16_t* buffer, int numSamples);
    void setAudioEnabled(bool enabled) { audioEnabled = enabled; }
    void setMasterVolume(float volume) { masterVolume = volume; }

    // Serialization for Save States
    size_t getStateSize() const;
    bool serialize(uint8_t* dest, size_t maxSize, size_t& written) const;
    bool deserialize(const uint8_t* src, size_t size, size_t& readBytes);

private:
    uint8_t ram[0x10000]; // 64KB SPC RAM
    uint8_t iplRom[64];   // SPC boot ROM

    // CPU Communication ports
    uint8_t cpuToSpc[4] = {0, 0, 0, 0};
    uint8_t spcToCpu[4] = {0, 0, 0, 0};

    // SPC Registers
    uint8_t A = 0;
    uint8_t X = 0;
    uint8_t Y = 0;
    uint8_t SP = 0xEF;
    uint16_t PC = 0xFFC0;
    uint8_t PSW = 0;

    // Timers 0, 1, 2
    struct Timer {
        uint8_t target = 0;
        uint8_t counter = 0;
        uint8_t internal = 0;
        bool enabled = false;
    } timers[3];

    // DSP registers & 8 voices
    struct Voice {
        int16_t volL = 0;
        int16_t volR = 0;
        uint16_t pitch = 0;
        uint8_t srcn = 0;
        uint8_t adsr1 = 0;
        uint8_t adsr2 = 0;
        uint8_t gain = 0;
        int32_t env = 0;
        uint32_t samplePos = 0;
        bool keyOn = false;
        bool active = false;
    } voices[8];

    uint8_t dspRegs[128];
    uint8_t dspAddr = 0;

    bool audioEnabled = true;
    float masterVolume = 1.0f;
    int cycleAccumulator = 0;

    void stepDsp(int cycles);
    void executeSpcOpcode();
};

#endif // SNES_SPC_H
