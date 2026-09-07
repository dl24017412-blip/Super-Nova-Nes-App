#include "snes_spc.h"
#include <cstring>
#include <cmath>
#include <algorithm>

// Standard SPC700 Bootloader IPL ROM (64 bytes)
static const uint8_t SPC_IPL_ROM[64] = {
    0xCD, 0xEF, 0xBD, 0xE8, 0x00, 0xC6, 0x1D, 0xD0,
    0xFC, 0x8F, 0xAA, 0xF4, 0x8F, 0xBB, 0xF5, 0x78,
    0xCC, 0xF4, 0xD0, 0xFB, 0x2F, 0x19, 0xEB, 0xF4,
    0xD0, 0xFC, 0x7E, 0xF4, 0xD0, 0x0B, 0xE4, 0xF5,
    0xCB, 0xF4, 0xD7, 0x00, 0xFC, 0xD0, 0xF3, 0xAB,
    0x01, 0x10, 0xEF, 0x7E, 0xF4, 0x10, 0xEB, 0xBA,
    0xF6, 0xDA, 0x00, 0xBA, 0xF4, 0xC4, 0xF4, 0xDD,
    0x5D, 0xD0, 0xDB, 0x1F, 0x00, 0x00, 0xC0, 0xFF
};

SnesSpc::SnesSpc() {
    std::memcpy(iplRom, SPC_IPL_ROM, 64);
    reset();
}

SnesSpc::~SnesSpc() = default;

void SnesSpc::reset() {
    std::memset(ram, 0, sizeof(ram));
    std::memcpy(ram + 0xFFC0, iplRom, 64);

    std::memset(cpuToSpc, 0, sizeof(cpuToSpc));
    std::memset(spcToCpu, 0, sizeof(spcToCpu));
    spcToCpu[0] = 0xAA;
    spcToCpu[1] = 0xBB;

    A = 0;
    X = 0;
    Y = 0;
    SP = 0xEF;
    PC = 0xFFC0;
    PSW = 0;

    std::memset(timers, 0, sizeof(timers));
    std::memset(voices, 0, sizeof(voices));
    std::memset(dspRegs, 0, sizeof(dspRegs));
    dspAddr = 0;
    cycleAccumulator = 0;
}

uint8_t SnesSpc::readPort(uint8_t port) {
    return spcToCpu[port & 3];
}

void SnesSpc::writePort(uint8_t port, uint8_t val) {
    cpuToSpc[port & 3] = val;
    ram[0xF4 + (port & 3)] = val;
}

void SnesSpc::step(int cycles) {
    cycleAccumulator += cycles;
    // Step SPC CPU & DSP
    while (cycleAccumulator >= 16) {
        cycleAccumulator -= 16;
        executeSpcOpcode();
    }
}

void SnesSpc::executeSpcOpcode() {
    uint8_t op = ram[PC++];
    switch (op) {
        case 0x00: break; // NOP
        case 0xCD: { // MOV X, #imm
            X = ram[PC++];
            break;
        }
        case 0xBD: { // MOV SP, X
            SP = X;
            break;
        }
        case 0xE8: { // MOV A, #imm
            A = ram[PC++];
            break;
        }
        case 0xC6: { // MOV (X), A
            ram[X] = A;
            break;
        }
        case 0x1D: { // DEC X
            X--;
            break;
        }
        case 0xD0: { // BNE rel
            int8_t rel = (int8_t)ram[PC++];
            if (X != 0) PC += rel;
            break;
        }
        case 0x8F: { // MOV dp, #imm
            uint8_t imm = ram[PC++];
            uint8_t dp = ram[PC++];
            ram[dp] = imm;
            if (dp >= 0xF4 && dp <= 0xF7) {
                spcToCpu[dp - 0xF4] = imm;
            }
            break;
        }
        case 0xE4: { // MOV A, dp
            uint8_t dp = ram[PC++];
            A = (dp >= 0xF4 && dp <= 0xF7) ? cpuToSpc[dp - 0xF4] : ram[dp];
            break;
        }
        case 0x78: { // CMP dp, #imm
            uint8_t imm = ram[PC++];
            uint8_t dp = ram[PC++];
            uint8_t val = (dp >= 0xF4 && dp <= 0xF7) ? cpuToSpc[dp - 0xF4] : ram[dp];
            PSW = (val == imm) ? (PSW | 0x02) : (PSW & ~0x02);
            break;
        }
        case 0x2F: { // BRA rel
            int8_t rel = (int8_t)ram[PC++];
            PC += rel;
            break;
        }
        case 0x5F: { // JMP abs
            uint16_t target = ram[PC] | (ram[PC + 1] << 8);
            PC = target;
            break;
        }
        default:
            // Safe advance
            break;
    }

    // Mirror ports
    spcToCpu[0] = ram[0xF4];
    spcToCpu[1] = ram[0xF5];
    spcToCpu[2] = ram[0xF6];
    spcToCpu[3] = ram[0xF7];
}

int SnesSpc::generateSamples(int16_t* buffer, int numSamples) {
    if (!audioEnabled || !buffer || numSamples <= 0) {
        if (buffer) {
            std::memset(buffer, 0, numSamples * SNES_AUDIO_CHANNELS * sizeof(int16_t));
        }
        return numSamples;
    }

    // Synthesize 8 DSP audio voices or gentle ambient test tone
    float vol = masterVolume;
    for (int i = 0; i < numSamples; ++i) {
        int32_t mixL = 0;
        int32_t mixR = 0;

        for (int v = 0; v < 8; ++v) {
            Voice& voice = voices[v];
            if (voice.active && voice.pitch > 0) {
                voice.samplePos += voice.pitch;
                int16_t sample = (int16_t)((voice.samplePos & 0x8000) ? 4000 : -4000);
                mixL += (sample * voice.volL) >> 7;
                mixR += (sample * voice.volR) >> 7;
            }
        }

        // Apply master volume and clamp
        mixL = (int32_t)(mixL * vol);
        mixR = (int32_t)(mixR * vol);

        mixL = std::max(-32768, std::min(32767, mixL));
        mixR = std::max(-32768, std::min(32767, mixR));

        buffer[i * 2] = static_cast<int16_t>(mixL);
        buffer[i * 2 + 1] = static_cast<int16_t>(mixR);
    }

    return numSamples;
}

size_t SnesSpc::getStateSize() const {
    return sizeof(ram) + 128;
}

bool SnesSpc::serialize(uint8_t* dest, size_t maxSize, size_t& written) const {
    if (maxSize < getStateSize()) return false;
    uint8_t* ptr = dest;

    std::memcpy(ptr, ram, sizeof(ram)); ptr += sizeof(ram);
    *ptr++ = A;
    *ptr++ = X;
    *ptr++ = Y;
    *ptr++ = SP;
    *reinterpret_cast<uint16_t*>(ptr) = PC; ptr += 2;
    *ptr++ = PSW;

    written = ptr - dest;
    return true;
}

bool SnesSpc::deserialize(const uint8_t* src, size_t size, size_t& readBytes) {
    if (size < getStateSize()) return false;
    const uint8_t* ptr = src;

    std::memcpy(ram, ptr, sizeof(ram)); ptr += sizeof(ram);
    A = *ptr++;
    X = *ptr++;
    Y = *ptr++;
    SP = *ptr++;
    PC = *reinterpret_cast<const uint16_t*>(ptr); ptr += 2;
    PSW = *ptr++;

    readBytes = ptr - src;
    return true;
}
