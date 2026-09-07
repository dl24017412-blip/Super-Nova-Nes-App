#ifndef SNES_MEMORY_H
#define SNES_MEMORY_H

#include "snes_types.h"
#include <vector>
#include <string>

class SnesCore;

class SnesMemory {
public:
    SnesMemory(SnesCore* core);
    ~SnesMemory();

    bool loadRom(const uint8_t* data, size_t size, RomHeaderInfo& outHeader);
    void reset();

    uint8_t read(uint32_t addr);
    void write(uint32_t addr, uint8_t val);
    uint16_t read16(uint32_t addr);
    void write16(uint32_t addr, uint16_t val);

    // Direct access to buffers
    uint8_t* getWram() { return wram.data(); }
    uint8_t* getSram() { return sram.data(); }
    size_t getSramSize() const { return sram.size(); }
    bool hasSram() const { return header.hasBattery && !sram.empty(); }

    bool saveSramToFile(const std::string& path);
    bool loadSramFromFile(const std::string& path);

    const RomHeaderInfo& getHeader() const { return header; }

    // Serialization for Save States
    size_t getStateSize() const;
    bool serialize(uint8_t* dest, size_t maxSize, size_t& written) const;
    bool deserialize(const uint8_t* src, size_t size, size_t& readBytes);

    // DMA & HDMA
    void doDma(uint8_t channel);
    void runHdma();

    // Joypad states
    void setJoypad(int controller, uint16_t buttons);
    uint16_t getJoypad(int controller) const { return controller == 0 ? joypad1 : joypad2; }

    // Scanline & VBlank tracking
    void setScanline(int line);
    void triggerVBlank();
    bool isNmiEnabled() const { return (nmitimen & 0x80) != 0; }
    bool isJoypadAutoReadEnabled() const { return (nmitimen & 0x01) != 0; }
    int getScanline() const { return currentScanline; }

private:
    SnesCore* core;
    std::vector<uint8_t> rom;
    std::vector<uint8_t> wram;  // 128 KB
    std::vector<uint8_t> sram;  // typically 8KB - 64KB
    RomHeaderInfo header;

    int currentScanline = 0;
    bool vblankFlag = false;

    uint16_t joypad1 = 0;
    uint16_t joypad2 = 0;

    // Registers $4200-$421F
    uint8_t nmitimen = 0;
    uint8_t wrio = 0xFF;
    uint8_t wrmultA = 0;
    uint8_t wrmultB = 0;
    uint16_t wrmultRes = 0;
    uint16_t wrdivA = 0;
    uint8_t wrdivB = 0;
    uint16_t wrdivQuot = 0;
    uint16_t wrdivRem = 0;
    uint8_t htimeL = 0, htimeH = 0;
    uint8_t vtimeL = 0, vtimeH = 0;
    uint8_t mdmaen = 0;
    uint8_t hdmaen = 0;

    // DMA Channels 0-7 ($4300-$437F)
    struct DmaChannel {
        uint8_t dmap;
        uint8_t bbad;
        uint16_t a1t;
        uint8_t a1b;
        uint16_t das;
        uint8_t dasb;
        uint16_t a2a;
        uint8_t ntrl;
    } dma[8];

    void parseHeader();
};

#endif // SNES_MEMORY_H
