#ifndef SNES_PPU_H
#define SNES_PPU_H

#include "snes_types.h"
#include <vector>
#include <cstdint>

class SnesMemory;

class SnesPpu {
public:
    SnesPpu(SnesMemory* mem);
    ~SnesPpu();

    void reset();
    void renderScanline(int line);
    
    uint8_t readRegister(uint16_t addr);
    void writeRegister(uint16_t addr, uint8_t val);

    const uint32_t* getFramebuffer() const { return framebuffer; }
    uint32_t* getFramebuffer() { return framebuffer; }

    void setPerformanceProfile(PerformanceProfile profile) { currentProfile = profile; }

    // Serialization
    size_t getStateSize() const;
    bool serialize(uint8_t* dest, size_t maxSize, size_t& written) const;
    bool deserialize(const uint8_t* src, size_t size, size_t& readBytes);

private:
    SnesMemory* mem;
    PerformanceProfile currentProfile = PerformanceProfile::BALANCED;

    // VRAM 64KB (32K 16-bit words)
    uint16_t vram[0x8000];
    uint16_t vramAddr = 0;
    uint8_t vmain = 0;
    uint16_t vramReadBuffer = 0;

    // OAM 544 bytes
    uint8_t oam[544];
    uint16_t oamAddr = 0;
    uint8_t oamWriteToggle = 0;
    uint8_t oamLsb = 0;

    // CGRAM 512 bytes (256 15-bit color entries)
    uint16_t cgram[256];
    uint8_t cgramAddr = 0;
    uint8_t cgramToggle = 0;
    uint8_t cgramLsb = 0;

    // Framebuffer: 256x224 RGBA8888
    uint32_t framebuffer[SNES_TOTAL_PIXELS];

    // PPU Registers
    uint8_t inidisp = 0x0F; // Brightness 15, screen on
    uint8_t obsel = 0;
    uint8_t bgmode = 0;
    uint8_t mosaic = 0;
    uint8_t bgsc[4] = {0, 0, 0, 0};
    uint8_t bgnba[4] = {0, 0, 0, 0};
    uint16_t bghofs[4] = {0, 0, 0, 0};
    uint16_t bgvofs[4] = {0, 0, 0, 0};
    uint8_t bgScrollPrev = 0;
    uint8_t tm = 0x01; // Main screen designation (BG1 on by default)
    uint8_t ts = 0x00; // Sub screen designation
    uint8_t cgwsel = 0;
    uint8_t cgadsub = 0;
    uint8_t coldata[3] = {0, 0, 0}; // Fixed color R, G, B
    uint8_t setini = 0;

    // Mode 7 Matrix registers
    int16_t m7a = 0, m7b = 0, m7c = 0, m7d = 0;
    int16_t m7x = 0, m7y = 0;
    uint8_t m7Prev = 0;

    // Scanline buffers for line rendering
    uint16_t lineMain[SNES_SCREEN_WIDTH];
    uint8_t lineMainPrio[SNES_SCREEN_WIDTH];

    uint32_t cgramToRgba(uint16_t bgr555, uint8_t brightness);
    void renderBgLayer(int bgIndex, int line);
    void renderSprites(int line);
    void renderMode7(int line);
};

#endif // SNES_PPU_H
