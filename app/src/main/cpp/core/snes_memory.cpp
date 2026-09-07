#include "snes_memory.h"
#include "snes_core.h"
#include <cstring>
#include <fstream>
#include <algorithm>

SnesMemory::SnesMemory(SnesCore* core) : core(core) {
    wram.resize(0x20000, 0); // 128KB
    sram.resize(0x8000, 0);  // 32KB default
    std::memset(&header, 0, sizeof(header));
    std::memset(dma, 0, sizeof(dma));
}

SnesMemory::~SnesMemory() = default;

void SnesMemory::reset() {
    std::fill(wram.begin(), wram.end(), 0);
    nmitimen = 0;
    wrio = 0xFF;
    wrmultA = wrmultB = 0;
    wrmultRes = 0;
    wrdivA = 0;
    wrdivB = 0;
    wrdivQuot = wrdivRem = 0;
    htimeL = htimeH = vtimeL = vtimeH = 0;
    mdmaen = hdmaen = 0;
    currentScanline = 0;
    vblankFlag = false;
    std::memset(dma, 0, sizeof(dma));
}

bool SnesMemory::loadRom(const uint8_t* data, size_t size, RomHeaderInfo& outHeader) {
    if (!data || size < 0x8000) {
        LOGE("ROM file too small: %zu bytes", size);
        return false;
    }

    size_t offset = 0;
    // Check for 512-byte SMC header
    if ((size % 1024) == 512) {
        LOGI("Detected 512-byte SMC copier header, skipping.");
        offset = 512;
    }

    size_t romSize = size - offset;
    rom.resize(romSize);
    std::memcpy(rom.data(), data + offset, romSize);

    parseHeader();
    outHeader = header;

    // Allocate SRAM if game uses it
    if (header.sramSize > 0) {
        size_t sramBytes = 1024 << header.sramSize;
        if (sramBytes > 0x40000) sramBytes = 0x40000;
        sram.resize(sramBytes, 0);
    } else {
        sram.resize(0x8000, 0);
    }

    reset();
    LOGI("ROM loaded: '%s', Size: %u KB, Map: %s, Battery: %s",
         header.title, (unsigned int)(header.realRomSize / 1024),
         header.isHiRom ? "HiROM" : "LoROM",
         header.hasBattery ? "YES" : "NO");

    return true;
}

void SnesMemory::parseHeader() {
    std::memset(&header, 0, sizeof(header));
    if (rom.size() < 0x8000) return;

    // Test LoROM ($7FC0) vs HiROM ($FFC0)
    int loromOffset = 0x7FC0;
    int hiromOffset = 0xFFC0;

    auto testHeader = [this](int baseOffset, RomHeaderInfo& info) -> bool {
        if (baseOffset + 32 > (int)rom.size()) return false;
        uint16_t comp = rom[baseOffset + 28] | (rom[baseOffset + 29] << 8);
        uint16_t check = rom[baseOffset + 30] | (rom[baseOffset + 31] << 8);
        if ((uint16_t)(comp + check) == 0xFFFF) {
            for (int i = 0; i < 21; ++i) {
                uint8_t c = rom[baseOffset + i];
                info.title[i] = (c >= 32 && c <= 126) ? (char)c : ' ';
            }
            info.title[21] = '\0';
            info.romType = rom[baseOffset + 22];
            info.romSize = rom[baseOffset + 23];
            info.sramSize = rom[baseOffset + 24];
            info.checksum = check;
            info.complement = comp;
            info.hasBattery = (info.romType == 0x02 || info.romType == 0x05 || info.romType == 0x06);
            return true;
        }
        return false;
    };

    RomHeaderInfo loInfo{}, hiInfo{};
    bool loValid = testHeader(loromOffset, loInfo);
    bool hiValid = testHeader(hiromOffset, hiInfo);

    if (hiValid && !loValid) {
        header = hiInfo;
        header.isHiRom = true;
    } else if (loValid) {
        header = loInfo;
        header.isHiRom = false;
    } else {
        // Fallback to LoROM attempt
        for (int i = 0; i < 21 && (loromOffset + i < (int)rom.size()); ++i) {
            uint8_t c = rom[loromOffset + i];
            header.title[i] = (c >= 32 && c <= 126) ? (char)c : ' ';
        }
        header.title[21] = '\0';
        header.isHiRom = false;
        header.hasBattery = true;
    }
    header.realRomSize = static_cast<uint32_t>(rom.size());
}

uint8_t SnesMemory::read(uint32_t addr) {
    uint8_t bank = (addr >> 16) & 0xFF;
    uint16_t offset = addr & 0xFFFF;

    // Mirroring & System memory
    if ((bank <= 0x3F) || (bank >= 0x80 && bank <= 0xBF)) {
        if (offset < 0x2000) {
            // WRAM mirror
            return wram[offset];
        }
        if (offset >= 0x2100 && offset <= 0x213F) {
            // PPU read
            return core->getPpu()->readRegister(offset);
        }
        if (offset >= 0x2140 && offset <= 0x2143) {
            // APU read
            return core->getSpc()->readPort(offset - 0x2140);
        }
        if (offset >= 0x4200 && offset <= 0x421F) {
            // CPU Bus registers
            switch (offset) {
                case 0x4210: { // RDNMI
                    uint8_t val = (vblankFlag ? 0x80 : 0x00) | 0x02;
                    vblankFlag = false; // Reading $4210 clears NMI flag
                    return val;
                }
                case 0x4211: { // TIMEUP
                    uint8_t val = core->getCpu()->irqPending ? 0x80 : 0x00;
                    core->getCpu()->irqPending = false;
                    return val;
                }
                case 0x4212: { // HVBJOY (Status)
                    uint8_t val = 0x00;
                    if (currentScanline >= 224) {
                        val |= 0x80; // In VBlank
                    }
                    return val;
                }
                case 0x4214: return wrdivQuot & 0xFF;
                case 0x4215: return (wrdivQuot >> 8) & 0xFF;
                case 0x4216: return wrmultRes & 0xFF;
                case 0x4217: return (wrmultRes >> 8) & 0xFF;
                case 0x4218: return joypad1 & 0xFF;
                case 0x4219: return (joypad1 >> 8) & 0xFF;
                case 0x421A: return joypad2 & 0xFF;
                case 0x421B: return (joypad2 >> 8) & 0xFF;
                default: return 0x00;
            }
        }
        if (offset >= 0x6000 && offset <= 0x7FFF) {
            // SRAM in LoROM
            if (!sram.empty()) {
                return sram[(offset - 0x6000) % sram.size()];
            }
        }
        if (offset >= 0x8000) {
            // ROM mapping
            if (!rom.empty()) {
                if (header.isHiRom) {
                    uint32_t romAddr = ((bank & 0x3F) << 16) | offset;
                    return rom[romAddr % rom.size()];
                } else {
                    uint32_t romAddr = ((bank & 0x7F) << 15) | (offset & 0x7FFF);
                    return rom[romAddr % rom.size()];
                }
            }
        }
    } else if (bank >= 0x40 && bank <= 0x7D) {
        if (!rom.empty()) {
            if (header.isHiRom) {
                uint32_t romAddr = ((bank - 0x40) << 16) | offset;
                return rom[romAddr % rom.size()];
            } else {
                uint32_t romAddr = ((bank & 0x7F) << 15) | (offset & 0x7FFF);
                return rom[romAddr % rom.size()];
            }
        }
    } else if (bank == 0x7E || bank == 0x7F) {
        // WRAM 128KB
        uint32_t wramAddr = ((bank - 0x7E) << 16) | offset;
        return wram[wramAddr % wram.size()];
    } else if (bank >= 0xC0) {
        // Upper ROM / HiROM
        if (!rom.empty()) {
            if (header.isHiRom) {
                uint32_t romAddr = ((bank - 0xC0) << 16) | offset;
                return rom[romAddr % rom.size()];
            } else {
                uint32_t romAddr = ((bank & 0x7F) << 15) | (offset & 0x7FFF);
                return rom[romAddr % rom.size()];
            }
        }
    }

    return 0x00;
}

void SnesMemory::write(uint32_t addr, uint8_t val) {
    uint8_t bank = (addr >> 16) & 0xFF;
    uint16_t offset = addr & 0xFFFF;

    if ((bank <= 0x3F) || (bank >= 0x80 && bank <= 0xBF)) {
        if (offset < 0x2000) {
            wram[offset] = val;
            return;
        }
        if (offset >= 0x2100 && offset <= 0x213F) {
            core->getPpu()->writeRegister(offset, val);
            return;
        }
        if (offset >= 0x2140 && offset <= 0x2143) {
            core->getSpc()->writePort(offset - 0x2140, val);
            return;
        }
        if (offset >= 0x4200 && offset <= 0x421F) {
            switch (offset) {
                case 0x4200: // NMITIMEN
                    nmitimen = val;
                    break;
                case 0x4201: // WRIO
                    wrio = val;
                    break;
                case 0x4202: // WRMULTA
                    wrmultA = val;
                    break;
                case 0x4203: // WRMULTB
                    wrmultB = val;
                    wrmultRes = (uint16_t)wrmultA * (uint16_t)wrmultB;
                    break;
                case 0x4204: // WRDIVL
                    wrdivA = (wrdivA & 0xFF00) | val;
                    break;
                case 0x4205: // WRDIVH
                    wrdivA = (wrdivA & 0x00FF) | (val << 8);
                    break;
                case 0x4206: // WRDIVB
                    wrdivB = val;
                    if (wrdivB != 0) {
                        wrdivQuot = wrdivA / wrdivB;
                        wrdivRem = wrdivA % wrdivB;
                    } else {
                        wrdivQuot = 0xFFFF;
                        wrdivRem = wrdivA;
                    }
                    break;
                case 0x420B: // MDMAEN
                    mdmaen = val;
                    for (int ch = 0; ch < 8; ++ch) {
                        if (mdmaen & (1 << ch)) {
                            doDma(ch);
                        }
                    }
                    break;
                case 0x420C: // HDMAEN
                    hdmaen = val;
                    break;
            }
            return;
        }
        if (offset >= 0x4300 && offset <= 0x437F) {
            int ch = (offset >> 4) & 0x07;
            switch (offset & 0x0F) {
                case 0x00: dma[ch].dmap = val; break;
                case 0x01: dma[ch].bbad = val; break;
                case 0x02: dma[ch].a1t = (dma[ch].a1t & 0xFF00) | val; break;
                case 0x03: dma[ch].a1t = (dma[ch].a1t & 0x00FF) | (val << 8); break;
                case 0x04: dma[ch].a1b = val; break;
                case 0x05: dma[ch].das = (dma[ch].das & 0xFF00) | val; break;
                case 0x06: dma[ch].das = (dma[ch].das & 0x00FF) | (val << 8); break;
                case 0x07: dma[ch].dasb = val; break;
                case 0x08: dma[ch].a2a = (dma[ch].a2a & 0xFF00) | val; break;
                case 0x09: dma[ch].a2a = (dma[ch].a2a & 0x00FF) | (val << 8); break;
                case 0x0A: dma[ch].ntrl = val; break;
            }
            return;
        }
        if (offset >= 0x6000 && offset <= 0x7FFF) {
            if (!sram.empty()) {
                sram[(offset - 0x6000) % sram.size()] = val;
            }
            return;
        }
    } else if (bank == 0x7E || bank == 0x7F) {
        uint32_t wramAddr = ((bank - 0x7E) << 16) | offset;
        wram[wramAddr % wram.size()] = val;
        return;
    } else if (bank >= 0x70 && bank <= 0x7D) {
        if (header.isHiRom && !sram.empty()) {
            sram[offset % sram.size()] = val;
            return;
        }
    }
}

uint16_t SnesMemory::read16(uint32_t addr) {
    uint8_t low = read(addr);
    uint8_t high = read(addr + 1);
    return low | (high << 8);
}

void SnesMemory::write16(uint32_t addr, uint16_t val) {
    write(addr, val & 0xFF);
    write(addr + 1, (val >> 8) & 0xFF);
}

void SnesMemory::doDma(uint8_t channel) {
    if (channel >= 8) return;
    DmaChannel& ch = dma[channel];
    uint32_t aAddr = (ch.a1b << 16) | ch.a1t;
    uint32_t count = ch.das ? ch.das : 0x10000;
    bool inc = !(ch.dmap & 0x08);
    bool dec = (ch.dmap & 0x10);
    uint8_t mode = ch.dmap & 0x07;

    // Pattern table for DMA transfers
    static const uint8_t bOffsets[8][4] = {
        {0, 0, 0, 0}, // Mode 0: 1 reg (p)
        {0, 1, 0, 1}, // Mode 1: 2 regs (p, p+1)
        {0, 0, 0, 0}, // Mode 2: 1 reg write twice (p, p)
        {0, 0, 1, 1}, // Mode 3: 2 regs write twice (p, p, p+1, p+1)
        {0, 1, 2, 3}, // Mode 4: 4 regs (p, p+1, p+2, p+3)
        {0, 1, 0, 1}, // Mode 5
        {0, 0, 0, 0}, // Mode 6
        {0, 0, 1, 1}  // Mode 7
    };
    static const uint8_t patternLen[8] = {1, 2, 2, 4, 4, 4, 1, 4};
    int pLen = patternLen[mode];

    for (uint32_t i = 0; i < count; ++i) {
        uint8_t regOffset = bOffsets[mode][i % pLen];
        uint16_t bReg = 0x2100 | ((ch.bbad + regOffset) & 0xFF);

        if (!(ch.dmap & 0x80)) {
            // CPU to PPU
            uint8_t data = read(aAddr);
            write(bReg, data);
        } else {
            // PPU to CPU
            uint8_t data = read(bReg);
            write(aAddr, data);
        }
        if (inc) {
            if (dec) aAddr--;
            else aAddr++;
        }
    }
    ch.a1t = aAddr & 0xFFFF;
    ch.das = 0;
}

void SnesMemory::runHdma() {
    // Basic HDMA runner for scanline effects
    for (int ch = 0; ch < 8; ++ch) {
        if (hdmaen & (1 << ch)) {
            uint32_t aAddr = (dma[ch].a1b << 16) | dma[ch].a1t;
            uint16_t bReg = 0x2100 | dma[ch].bbad;
            write(bReg, read(aAddr));
            dma[ch].a1t++;
        }
    }
}

static uint16_t convertToSnesJoypad(uint16_t btns) {
    uint16_t word = 0;
    // High byte: B, Y, Select, Start, Up, Down, Left, Right
    if (btns & SNES_BTN_B)      word |= (1 << 15);
    if (btns & SNES_BTN_Y)      word |= (1 << 14);
    if (btns & SNES_BTN_SELECT) word |= (1 << 13);
    if (btns & SNES_BTN_START)  word |= (1 << 12);
    if (btns & SNES_BTN_UP)     word |= (1 << 11);
    if (btns & SNES_BTN_DOWN)   word |= (1 << 10);
    if (btns & SNES_BTN_LEFT)   word |= (1 << 9);
    if (btns & SNES_BTN_RIGHT)  word |= (1 << 8);
    // Low byte: A, X, L, R
    if (btns & SNES_BTN_A)      word |= (1 << 7);
    if (btns & SNES_BTN_X)      word |= (1 << 6);
    if (btns & SNES_BTN_L)      word |= (1 << 5);
    if (btns & SNES_BTN_R)      word |= (1 << 4);
    return word;
}

void SnesMemory::setJoypad(int controller, uint16_t buttons) {
    uint16_t word = convertToSnesJoypad(buttons);
    if (controller == 0) joypad1 = word;
    else joypad2 = word;
}

void SnesMemory::setScanline(int line) {
    currentScanline = line;
    if (line == 0) {
        vblankFlag = false;
    }
}

void SnesMemory::triggerVBlank() {
    vblankFlag = true;
    currentScanline = 224;
}

bool SnesMemory::saveSramToFile(const std::string& path) {
    if (sram.empty()) return false;
    std::ofstream file(path, std::ios::binary);
    if (!file.is_open()) {
        LOGE("Failed to open SRAM file for writing: %s", path.c_str());
        return false;
    }
    file.write(reinterpret_cast<const char*>(sram.data()), sram.size());
    file.close();
    LOGI("Saved SRAM (%zu bytes) to %s", sram.size(), path.c_str());
    return true;
}

bool SnesMemory::loadSramFromFile(const std::string& path) {
    if (sram.empty()) return false;
    std::ifstream file(path, std::ios::binary);
    if (!file.is_open()) return false;
    file.read(reinterpret_cast<char*>(sram.data()), sram.size());
    file.close();
    LOGI("Loaded SRAM from %s", path.c_str());
    return true;
}

size_t SnesMemory::getStateSize() const {
    return wram.size() + sram.size() + sizeof(DmaChannel) * 8 + 64;
}

bool SnesMemory::serialize(uint8_t* dest, size_t maxSize, size_t& written) const {
    size_t needed = getStateSize();
    if (maxSize < needed) return false;
    uint8_t* ptr = dest;

    std::memcpy(ptr, wram.data(), wram.size()); ptr += wram.size();
    std::memcpy(ptr, sram.data(), sram.size()); ptr += sram.size();
    std::memcpy(ptr, dma, sizeof(dma)); ptr += sizeof(dma);

    *ptr++ = nmitimen;
    *ptr++ = mdmaen;
    *ptr++ = hdmaen;

    written = ptr - dest;
    return true;
}

bool SnesMemory::deserialize(const uint8_t* src, size_t size, size_t& readBytes) {
    size_t needed = getStateSize();
    if (size < needed) return false;
    const uint8_t* ptr = src;

    std::memcpy(wram.data(), ptr, wram.size()); ptr += wram.size();
    std::memcpy(sram.data(), ptr, sram.size()); ptr += sram.size();
    std::memcpy(dma, ptr, sizeof(dma)); ptr += sizeof(dma);

    nmitimen = *ptr++;
    mdmaen = *ptr++;
    hdmaen = *ptr++;

    readBytes = ptr - src;
    return true;
}
