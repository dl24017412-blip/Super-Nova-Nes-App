#include "snes_ppu.h"
#include "snes_memory.h"
#include <cstring>
#include <algorithm>

SnesPpu::SnesPpu(SnesMemory* mem) : mem(mem) {
    reset();
}

SnesPpu::~SnesPpu() = default;

void SnesPpu::reset() {
    std::memset(vram, 0, sizeof(vram));
    std::memset(oam, 0, sizeof(oam));
    std::memset(cgram, 0, sizeof(cgram));
    std::memset(framebuffer, 0, sizeof(framebuffer));

    vramAddr = 0;
    vmain = 0;
    oamAddr = 0;
    oamWriteToggle = 0;
    cgramAddr = 0;
    cgramToggle = 0;

    inidisp = 0x0F;
    obsel = 0;
    bgmode = 1; // Mode 1 is most common
    mosaic = 0;
    std::memset(bgsc, 0, sizeof(bgsc));
    std::memset(bgnba, 0, sizeof(bgnba));
    std::memset(bghofs, 0, sizeof(bghofs));
    std::memset(bgvofs, 0, sizeof(bgvofs));
    bgScrollPrev = 0;
    tm = 0x11; // BG1 and Sprites on
    ts = 0x00;
    cgwsel = 0;
    cgadsub = 0;
    setini = 0;

    m7a = 0x0100; m7b = 0; m7c = 0; m7d = 0x0100;
    m7x = 0; m7y = 0;
    m7Prev = 0;
}

uint32_t SnesPpu::cgramToRgba(uint16_t bgr555, uint8_t brightness) {
    // bgr555: bit 0-4 = R, 5-9 = G, 10-14 = B
    uint32_t r = (bgr555 & 0x1F);
    uint32_t g = ((bgr555 >> 5) & 0x1F);
    uint32_t b = ((bgr555 >> 10) & 0x1F);

    // Scale 0..31 to 0..255 with brightness 0..15
    r = (r * 255 * brightness) / (31 * 15);
    g = (g * 255 * brightness) / (31 * 15);
    b = (b * 255 * brightness) / (31 * 15);

    return 0xFF000000 | (b << 16) | (g << 8) | r;
}

uint8_t SnesPpu::readRegister(uint16_t addr) {
    switch (addr) {
        case 0x2134: // MPYL
            return (m7a * (int8_t)(m7b >> 8)) & 0xFF;
        case 0x2135: // MPYM
            return ((m7a * (int8_t)(m7b >> 8)) >> 8) & 0xFF;
        case 0x2136: // MPYH
            return ((m7a * (int8_t)(m7b >> 8)) >> 16) & 0xFF;
        case 0x2137: // SLHV
            return 0x00;
        case 0x2138: { // OAMDATAREAD
            uint8_t val = oam[oamAddr % 544];
            oamAddr = (oamAddr + 1) % 544;
            return val;
        }
        case 0x2139: { // VMDATAREADL
            uint8_t val = vramReadBuffer & 0xFF;
            vramReadBuffer = vram[vramAddr & 0x7FFF];
            if (!(vmain & 0x80)) {
                vramAddr += (vmain & 0x03) == 0 ? 1 : ((vmain & 0x03) == 1 ? 32 : 128);
            }
            return val;
        }
        case 0x213A: { // VMDATAREADH
            uint8_t val = (vramReadBuffer >> 8) & 0xFF;
            vramReadBuffer = vram[vramAddr & 0x7FFF];
            if (vmain & 0x80) {
                vramAddr += (vmain & 0x03) == 0 ? 1 : ((vmain & 0x03) == 1 ? 32 : 128);
            }
            return val;
        }
        case 0x213B: { // CGDATAREAD
            uint8_t val = (cgramToggle == 0) ? (cgram[cgramAddr] & 0xFF) : ((cgram[cgramAddr] >> 8) & 0x7F);
            cgramToggle ^= 1;
            if (cgramToggle == 0) cgramAddr++;
            return val;
        }
        case 0x213E: // STAT77
            return 0x01; // PPU1 version
        case 0x213F: // STAT78
            return 0x01; // PPU2 version, NTSC
        default:
            return 0x00;
    }
}

void SnesPpu::writeRegister(uint16_t addr, uint8_t val) {
    switch (addr) {
        case 0x2100: // INIDISP
            inidisp = val;
            break;
        case 0x2101: // OBSEL
            obsel = val;
            break;
        case 0x2102: // OAMADDL
            oamAddr = (oamAddr & 0x0200) | (val << 1);
            oamWriteToggle = 0;
            break;
        case 0x2103: // OAMADDH
            oamAddr = (oamAddr & 0x01FE) | ((val & 0x01) << 9);
            oamWriteToggle = 0;
            break;
        case 0x2104: { // OAMDATA
            if (oamAddr < 544) {
                if (oamAddr < 512) {
                    if (oamWriteToggle == 0) {
                        oamLsb = val;
                        oamWriteToggle = 1;
                    } else {
                        oam[oamAddr] = oamLsb;
                        oam[oamAddr + 1] = val;
                        oamAddr += 2;
                        oamWriteToggle = 0;
                    }
                } else {
                    oam[oamAddr] = val;
                    oamAddr++;
                }
            }
            break;
        }
        case 0x2105: // BGMODE
            bgmode = val;
            break;
        case 0x2106: // MOSAIC
            mosaic = val;
            break;
        case 0x2107: bgsc[0] = val; break; // BG1SC
        case 0x2108: bgsc[1] = val; break; // BG2SC
        case 0x2109: bgsc[2] = val; break; // BG3SC
        case 0x210A: bgsc[3] = val; break; // BG4SC
        case 0x210B: bgnba[0] = val & 0x0F; bgnba[1] = (val >> 4) & 0x0F; break;
        case 0x210C: bgnba[2] = val & 0x0F; bgnba[3] = (val >> 4) & 0x0F; break;

        // BG Scroll
        case 0x210D: // BG1HOFS / M7HOFS
            bghofs[0] = (val << 8) | (bgScrollPrev & ~7) | ((bghofs[0] >> 8) & 7);
            bgScrollPrev = val;
            m7x = (val << 8) | m7Prev;
            m7Prev = val;
            break;
        case 0x210E: // BG1VOFS / M7VOFS
            bgvofs[0] = (val << 8) | bgScrollPrev;
            bgScrollPrev = val;
            m7y = (val << 8) | m7Prev;
            m7Prev = val;
            break;
        case 0x210F: bghofs[1] = (val << 8) | bgScrollPrev; bgScrollPrev = val; break;
        case 0x2110: bgvofs[1] = (val << 8) | bgScrollPrev; bgScrollPrev = val; break;
        case 0x2111: bghofs[2] = (val << 8) | bgScrollPrev; bgScrollPrev = val; break;
        case 0x2112: bgvofs[2] = (val << 8) | bgScrollPrev; bgScrollPrev = val; break;
        case 0x2113: bghofs[3] = (val << 8) | bgScrollPrev; bgScrollPrev = val; break;
        case 0x2114: bgvofs[3] = (val << 8) | bgScrollPrev; bgScrollPrev = val; break;

        case 0x2115: vmain = val; break;
        case 0x2116: vramAddr = (vramAddr & 0xFF00) | val; break;
        case 0x2117: vramAddr = (vramAddr & 0x00FF) | (val << 8); break;

        case 0x2118: { // VMDATAL
            uint16_t word = vram[vramAddr & 0x7FFF];
            vram[vramAddr & 0x7FFF] = (word & 0xFF00) | val;
            if (!(vmain & 0x80)) {
                int step = (vmain & 0x03) == 0 ? 1 : ((vmain & 0x03) == 1 ? 32 : 128);
                vramAddr += step;
            }
            break;
        }
        case 0x2119: { // VMDATAH
            uint16_t word = vram[vramAddr & 0x7FFF];
            vram[vramAddr & 0x7FFF] = (word & 0x00FF) | (val << 8);
            if (vmain & 0x80) {
                int step = (vmain & 0x03) == 0 ? 1 : ((vmain & 0x03) == 1 ? 32 : 128);
                vramAddr += step;
            }
            break;
        }

        case 0x211B: // M7A
            m7a = (val << 8) | m7Prev;
            m7Prev = val;
            break;
        case 0x211C: // M7B
            m7b = (val << 8) | m7Prev;
            m7Prev = val;
            break;
        case 0x211D: // M7C
            m7c = (val << 8) | m7Prev;
            m7Prev = val;
            break;
        case 0x211E: // M7D
            m7d = (val << 8) | m7Prev;
            m7Prev = val;
            break;

        case 0x2121: // CGADD
            cgramAddr = val;
            cgramToggle = 0;
            break;
        case 0x2122: { // CGDATA
            if (cgramToggle == 0) {
                cgramLsb = val;
                cgramToggle = 1;
            } else {
                cgram[cgramAddr] = cgramLsb | ((val & 0x7F) << 8);
                cgramAddr++;
                cgramToggle = 0;
            }
            break;
        }

        case 0x212C: tm = val; break; // TM
        case 0x212D: ts = val; break; // TS
        case 0x2130: cgwsel = val; break;
        case 0x2131: cgadsub = val; break;
        case 0x2132: { // COLDATA
            if (val & 0x20) coldata[0] = val & 0x1F;
            if (val & 0x40) coldata[1] = val & 0x1F;
            if (val & 0x80) coldata[2] = val & 0x1F;
            break;
        }
        case 0x2133: setini = val; break;
    }
}

void SnesPpu::renderScanline(int line) {
    if (line < 0 || line >= SNES_SCREEN_HEIGHT) return;

    uint32_t* linePtr = &framebuffer[line * SNES_SCREEN_WIDTH];

    // Check forced blank
    if (inidisp & 0x80) {
        std::memset(linePtr, 0, SNES_SCREEN_WIDTH * sizeof(uint32_t));
        return;
    }

    uint8_t brightness = inidisp & 0x0F;
    uint16_t backdropColor = cgram[0];
    uint32_t backdropRgba = cgramToRgba(backdropColor, brightness);

    for (int x = 0; x < SNES_SCREEN_WIDTH; ++x) {
        lineMain[x] = backdropColor;
        lineMainPrio[x] = 0;
    }

    uint8_t mode = bgmode & 0x07;
    if (mode == 7) {
        renderMode7(line);
    } else {
        // Render BGs according to TM register
        if (tm & 0x01) renderBgLayer(0, line);
        if (tm & 0x02) renderBgLayer(1, line);
        if (currentProfile != PerformanceProfile::LOW) {
            if (tm & 0x04) renderBgLayer(2, line);
            if (tm & 0x08) renderBgLayer(3, line);
        }
    }

    // Render sprites
    if (tm & 0x10) {
        renderSprites(line);
    }

    // Convert to RGBA
    for (int x = 0; x < SNES_SCREEN_WIDTH; ++x) {
        linePtr[x] = cgramToRgba(lineMain[x], brightness);
    }
}

void SnesPpu::renderBgLayer(int bgIndex, int line) {
    uint16_t scBase = (bgsc[bgIndex] & 0xFC) << 8;
    uint16_t chrBase = (bgnba[bgIndex] & 0x0F) << 12;

    int hScroll = bghofs[bgIndex] & 0x03FF;
    int vScroll = (bgvofs[bgIndex] + line) & 0x03FF;

    int tileY = (vScroll / 8) % 32;
    int fineY = vScroll % 8;

    for (int x = 0; x < SNES_SCREEN_WIDTH; ++x) {
        int effX = (hScroll + x) & 0x03FF;
        int tileX = (effX / 8) % 32;
        int fineX = effX % 8;

        uint16_t mapAddr = scBase + (tileY * 32 + tileX);
        uint16_t tileData = vram[mapAddr & 0x7FFF];

        uint16_t tileNum = tileData & 0x03FF;
        uint8_t palNum = (tileData >> 10) & 0x07;
        bool vFlip = (tileData & 0x8000) != 0;
        bool hFlip = (tileData & 0x4000) != 0;

        int row = vFlip ? (7 - fineY) : fineY;
        int col = hFlip ? (7 - fineX) : fineX;

        // 4bpp tile decode for standard mode 1 BG1
        uint16_t chrAddr = chrBase + (tileNum * 16) + row;
        uint16_t plane01 = vram[chrAddr & 0x7FFF];
        uint16_t plane23 = vram[(chrAddr + 8) & 0x7FFF];

        int shift = 7 - col;
        uint8_t p0 = (plane01 >> shift) & 1;
        uint8_t p1 = (plane01 >> (shift + 8)) & 1;
        uint8_t p2 = (plane23 >> shift) & 1;
        uint8_t p3 = (plane23 >> (shift + 8)) & 1;
        uint8_t colorIndex = (p3 << 3) | (p2 << 2) | (p1 << 1) | p0;

        if (colorIndex != 0) {
            uint16_t color = cgram[(palNum * 16 + colorIndex) & 0xFF];
            lineMain[x] = color;
            lineMainPrio[x] = bgIndex + 1;
        }
    }
}

void SnesPpu::renderSprites(int line) {
    // 128 sprites in OAM
    int count = 0;
    for (int i = 127; i >= 0; --i) {
        int oamIdx = i * 4;
        int sprX = oam[oamIdx];
        int sprY = oam[oamIdx + 1];
        uint8_t sprTile = oam[oamIdx + 2];
        uint8_t sprAttr = oam[oamIdx + 3];

        // High table bit for X high bit and size
        int highIdx = 512 + (i / 4);
        int bitShift = (i % 4) * 2;
        uint8_t highBits = (oam[highIdx] >> bitShift) & 0x03;
        if (highBits & 1) sprX -= 256;

        int sprSize = (highBits & 2) ? 16 : 8;

        if (line >= sprY && line < sprY + sprSize) {
            count++;
            if (count > 32 && currentProfile == PerformanceProfile::LOW) break;

            int row = line - sprY;
            if (sprAttr & 0x80) row = sprSize - 1 - row; // V-Flip

            uint8_t palNum = (sprAttr >> 1) & 0x07;
            uint16_t chrBase = (obsel & 0x07) << 13;

            for (int col = 0; col < sprSize; ++col) {
                int px = sprX + col;
                if (px >= 0 && px < SNES_SCREEN_WIDTH) {
                    int c = (sprAttr & 0x40) ? (sprSize - 1 - col) : col; // H-Flip
                    uint16_t chrAddr = chrBase + (sprTile * 16) + (row % 8);
                    uint16_t plane01 = vram[chrAddr & 0x7FFF];
                    uint16_t plane23 = vram[(chrAddr + 8) & 0x7FFF];

                    int shift = 7 - (c % 8);
                    uint8_t p0 = (plane01 >> shift) & 1;
                    uint8_t p1 = (plane01 >> (shift + 8)) & 1;
                    uint8_t p2 = (plane23 >> shift) & 1;
                    uint8_t p3 = (plane23 >> (shift + 8)) & 1;
                    uint8_t colorIndex = (p3 << 3) | (p2 << 2) | (p1 << 1) | p0;

                    if (colorIndex != 0) {
                        lineMain[px] = cgram[128 + palNum * 16 + colorIndex];
                    }
                }
            }
        }
    }
}

void SnesPpu::renderMode7(int line) {
    // Mode 7 affine transformation
    for (int x = 0; x < SNES_SCREEN_WIDTH; ++x) {
        int32_t rx = x - m7x;
        int32_t ry = line - m7y;

        int32_t tx = ((m7a * rx + m7b * ry) >> 8) + m7x;
        int32_t ty = ((m7c * rx + m7d * ry) >> 8) + m7y;

        int tileX = ((tx >> 3) & 0x7F);
        int tileY = ((ty >> 3) & 0x7F);

        uint16_t mapAddr = tileY * 128 + tileX;
        uint8_t tileNum = vram[mapAddr & 0x7FFF] & 0xFF;

        int fineX = tx & 7;
        int fineY = ty & 7;

        uint16_t chrAddr = (tileNum * 32) + (fineY * 4) + (fineX / 2);
        uint8_t byteVal = (vram[chrAddr & 0x7FFF] >> ((fineX & 1) ? 8 : 0)) & 0xFF;

        if (byteVal != 0) {
            lineMain[x] = cgram[byteVal];
        }
    }
}

size_t SnesPpu::getStateSize() const {
    return sizeof(vram) + sizeof(oam) + sizeof(cgram) + 256;
}

bool SnesPpu::serialize(uint8_t* dest, size_t maxSize, size_t& written) const {
    if (maxSize < getStateSize()) return false;
    uint8_t* ptr = dest;

    std::memcpy(ptr, vram, sizeof(vram)); ptr += sizeof(vram);
    std::memcpy(ptr, oam, sizeof(oam)); ptr += sizeof(oam);
    std::memcpy(ptr, cgram, sizeof(cgram)); ptr += sizeof(cgram);

    *ptr++ = inidisp;
    *ptr++ = bgmode;
    *ptr++ = tm;
    *ptr++ = ts;

    written = ptr - dest;
    return true;
}

bool SnesPpu::deserialize(const uint8_t* src, size_t size, size_t& readBytes) {
    if (size < getStateSize()) return false;
    const uint8_t* ptr = src;

    std::memcpy(vram, ptr, sizeof(vram)); ptr += sizeof(vram);
    std::memcpy(oam, ptr, sizeof(oam)); ptr += sizeof(oam);
    std::memcpy(cgram, ptr, sizeof(cgram)); ptr += sizeof(cgram);

    inidisp = *ptr++;
    bgmode = *ptr++;
    tm = *ptr++;
    ts = *ptr++;

    readBytes = ptr - src;
    return true;
}
