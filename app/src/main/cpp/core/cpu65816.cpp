#include "cpu65816.h"
#include "snes_memory.h"
#include <cstring>

Cpu65816::Cpu65816(SnesMemory* mem) : mem(mem) {
    reset();
}

Cpu65816::~Cpu65816() = default;

void Cpu65816::reset() {
    flagE = true;
    flagM = true;
    flagX = true;
    flagD = false;
    flagI = true;
    flagC = false;
    flagZ = false;
    flagV = false;
    flagN = false;

    D = 0x0000;
    DB = 0x00;
    PB = 0x00;
    SP = 0x01FF;

    // Read 6502 reset vector from $00FFFC
    PC = mem->read16(0x00FFFC);
    if (PC == 0 || PC == 0xFFFF) {
        PC = 0x8000; // sensible fallback
    }

    waitingForInterrupt = false;
    stopped = false;
    nmiPending = false;
    irqPending = false;
}

uint8_t Cpu65816::getP() const {
    uint8_t p = 0;
    if (flagC) p |= 0x01;
    if (flagZ) p |= 0x02;
    if (flagI) p |= 0x04;
    if (flagD) p |= 0x08;
    if (flagX) p |= 0x10;
    if (flagM) p |= 0x20;
    if (flagV) p |= 0x40;
    if (flagN) p |= 0x80;
    return p;
}

void Cpu65816::setP(uint8_t p) {
    flagC = (p & 0x01) != 0;
    flagZ = (p & 0x02) != 0;
    flagI = (p & 0x04) != 0;
    flagD = (p & 0x08) != 0;
    flagX = (p & 0x10) != 0;
    flagM = (p & 0x20) != 0;
    flagV = (p & 0x40) != 0;
    flagN = (p & 0x80) != 0;
    if (flagE) {
        flagM = true;
        flagX = true;
    }
    if (flagX) {
        X &= 0xFF;
        Y &= 0xFF;
    }
}

uint8_t Cpu65816::fetch8() {
    uint8_t val = mem->read((PB << 16) | PC);
    PC++;
    return val;
}

uint16_t Cpu65816::fetch16() {
    uint8_t low = fetch8();
    uint8_t high = fetch8();
    return low | (high << 8);
}

uint32_t Cpu65816::fetch24() {
    uint8_t low = fetch8();
    uint8_t mid = fetch8();
    uint8_t high = fetch8();
    return low | (mid << 8) | (high << 16);
}

void Cpu65816::push8(uint8_t val) {
    mem->write(SP, val);
    if (flagE) {
        SP = 0x0100 | ((SP - 1) & 0xFF);
    } else {
        SP--;
    }
}

uint8_t Cpu65816::pop8() {
    if (flagE) {
        SP = 0x0100 | ((SP + 1) & 0xFF);
    } else {
        SP++;
    }
    return mem->read(SP);
}

void Cpu65816::push16(uint16_t val) {
    push8((val >> 8) & 0xFF);
    push8(val & 0xFF);
}

uint16_t Cpu65816::pop16() {
    uint8_t low = pop8();
    uint8_t high = pop8();
    return low | (high << 8);
}

void Cpu65816::setNZ8(uint8_t val) {
    flagZ = (val == 0);
    flagN = (val & 0x80) != 0;
}

void Cpu65816::setNZ16(uint16_t val) {
    flagZ = (val == 0);
    flagN = (val & 0x8000) != 0;
}

void Cpu65816::triggerNmi() {
    nmiPending = true;
    waitingForInterrupt = false;
}

void Cpu65816::triggerIrq() {
    if (!flagI) {
        irqPending = true;
        waitingForInterrupt = false;
    }
}

// Addressing modes
uint32_t Cpu65816::addrDirect() {
    uint8_t offset = fetch8();
    return (D + offset) & 0xFFFF;
}

uint32_t Cpu65816::addrDirectX() {
    uint8_t offset = fetch8();
    return (D + offset + X) & 0xFFFF;
}

uint32_t Cpu65816::addrDirectY() {
    uint8_t offset = fetch8();
    return (D + offset + Y) & 0xFFFF;
}

uint32_t Cpu65816::addrAbsolute() {
    uint16_t offset = fetch16();
    return (DB << 16) | offset;
}

uint32_t Cpu65816::addrAbsoluteX() {
    uint16_t offset = fetch16();
    return (DB << 16) | ((offset + X) & 0xFFFF);
}

uint32_t Cpu65816::addrAbsoluteY() {
    uint16_t offset = fetch16();
    return (DB << 16) | ((offset + Y) & 0xFFFF);
}

uint32_t Cpu65816::addrAbsoluteLong() {
    return fetch24();
}

uint32_t Cpu65816::addrAbsoluteLongX() {
    uint32_t base = fetch24();
    return (base + X) & 0xFFFFFF;
}

uint32_t Cpu65816::addrDirectIndirect() {
    uint32_t dAddr = addrDirect();
    uint16_t ptr = mem->read16(dAddr);
    return (DB << 16) | ptr;
}

uint32_t Cpu65816::addrDirectIndirectLong() {
    uint32_t dAddr = addrDirect();
    uint8_t low = mem->read(dAddr);
    uint8_t mid = mem->read(dAddr + 1);
    uint8_t high = mem->read(dAddr + 2);
    return low | (mid << 8) | (high << 16);
}

uint32_t Cpu65816::addrDirectIndexedIndirect() {
    uint8_t offset = fetch8();
    uint32_t dAddr = (D + offset + X) & 0xFFFF;
    uint16_t ptr = mem->read16(dAddr);
    return (DB << 16) | ptr;
}

uint32_t Cpu65816::addrDirectIndirectIndexed() {
    uint8_t offset = fetch8();
    uint32_t dAddr = (D + offset) & 0xFFFF;
    uint16_t ptr = mem->read16(dAddr);
    return (DB << 16) | ((ptr + Y) & 0xFFFF);
}

uint32_t Cpu65816::addrDirectIndirectLongIndexed() {
    uint8_t offset = fetch8();
    uint32_t dAddr = (D + offset) & 0xFFFF;
    uint8_t low = mem->read(dAddr);
    uint8_t mid = mem->read(dAddr + 1);
    uint8_t high = mem->read(dAddr + 2);
    uint32_t base = low | (mid << 8) | (high << 16);
    return (base + Y) & 0xFFFFFF;
}

uint32_t Cpu65816::addrStackRelative() {
    uint8_t offset = fetch8();
    return (SP + offset) & 0xFFFF;
}

uint32_t Cpu65816::addrStackRelativeIndirectIndexed() {
    uint32_t srAddr = addrStackRelative();
    uint16_t ptr = mem->read16(srAddr);
    return (DB << 16) | ((ptr + Y) & 0xFFFF);
}

int Cpu65816::step() {
    if (stopped) return 4;
    if (waitingForInterrupt) {
        if (!nmiPending && !irqPending) return 4;
        waitingForInterrupt = false;
    }

    if (nmiPending) {
        nmiPending = false;
        if (!flagE) push8(PB);
        push16(PC);
        push8(getP());
        flagI = true;
        flagD = false;
        PB = 0x00;
        PC = mem->read16(flagE ? 0x00FFFA : 0x00FFE8);
        return 7;
    }

    if (irqPending && !flagI) {
        irqPending = false;
        if (!flagE) push8(PB);
        push16(PC);
        push8(getP());
        flagI = true;
        flagD = false;
        PB = 0x00;
        PC = mem->read16(flagE ? 0x00FFFE : 0x00FFEE);
        return 7;
    }

    uint8_t opcode = fetch8();
    executeOpcode(opcode);
    return 4; // average cycles
}

void Cpu65816::executeOpcode(uint8_t opcode) {
    switch (opcode) {
        case 0x00: { // BRK
            fetch8(); // signature byte
            if (!flagE) push8(PB);
            push16(PC);
            push8(getP() | (flagE ? 0x10 : 0x00));
            flagI = true;
            flagD = false;
            PB = 0x00;
            PC = mem->read16(flagE ? 0x00FFFE : 0x00FFE6);
            break;
        }
        case 0x18: flagC = false; break; // CLC
        case 0x38: flagC = true; break;  // SEC
        case 0x58: flagI = false; break; // CLI
        case 0x78: flagI = true; break;  // SEI
        case 0xD8: flagD = false; break; // CLD
        case 0xF8: flagD = true; break;  // SED
        case 0xB8: flagV = false; break; // CLV

        case 0xC2: { // REP
            uint8_t mask = fetch8();
            setP(getP() & ~mask);
            break;
        }
        case 0xE2: { // SEP
            uint8_t mask = fetch8();
            setP(getP() | mask);
            break;
        }
        case 0xFB: { // XCE
            bool temp = flagC;
            flagC = flagE;
            flagE = temp;
            if (flagE) {
                flagM = true;
                flagX = true;
                SP = 0x0100 | (SP & 0xFF);
            }
            break;
        }
        case 0xEB: { // XBA
            uint8_t low = A & 0xFF;
            uint8_t high = (A >> 8) & 0xFF;
            A = (low << 8) | high;
            setNZ8(A & 0xFF);
            break;
        }

        // LDA
        case 0xA9: { // Immediate
            if (flagM) {
                A = (A & 0xFF00) | fetch8();
                setNZ8(A & 0xFF);
            } else {
                A = fetch16();
                setNZ16(A);
            }
            break;
        }
        case 0xA5: { // Direct
            uint32_t a = addrDirect();
            if (flagM) { A = (A & 0xFF00) | mem->read(a); setNZ8(A & 0xFF); }
            else { A = mem->read16(a); setNZ16(A); }
            break;
        }
        case 0xAD: { // Absolute
            uint32_t a = addrAbsolute();
            if (flagM) { A = (A & 0xFF00) | mem->read(a); setNZ8(A & 0xFF); }
            else { A = mem->read16(a); setNZ16(A); }
            break;
        }
        case 0xAF: { // Absolute Long
            uint32_t a = addrAbsoluteLong();
            if (flagM) { A = (A & 0xFF00) | mem->read(a); setNZ8(A & 0xFF); }
            else { A = mem->read16(a); setNZ16(A); }
            break;
        }
        case 0xBD: { // Absolute,X
            uint32_t a = addrAbsoluteX();
            if (flagM) { A = (A & 0xFF00) | mem->read(a); setNZ8(A & 0xFF); }
            else { A = mem->read16(a); setNZ16(A); }
            break;
        }
        case 0xB9: { // Absolute,Y
            uint32_t a = addrAbsoluteY();
            if (flagM) { A = (A & 0xFF00) | mem->read(a); setNZ8(A & 0xFF); }
            else { A = mem->read16(a); setNZ16(A); }
            break;
        }

        // STA
        case 0x85: { // Direct
            uint32_t a = addrDirect();
            if (flagM) mem->write(a, A & 0xFF);
            else mem->write16(a, A);
            break;
        }
        case 0x8D: { // Absolute
            uint32_t a = addrAbsolute();
            if (flagM) mem->write(a, A & 0xFF);
            else mem->write16(a, A);
            break;
        }
        case 0x8F: { // Absolute Long
            uint32_t a = addrAbsoluteLong();
            if (flagM) mem->write(a, A & 0xFF);
            else mem->write16(a, A);
            break;
        }
        case 0x9D: { // Absolute,X
            uint32_t a = addrAbsoluteX();
            if (flagM) mem->write(a, A & 0xFF);
            else mem->write16(a, A);
            break;
        }
        case 0x99: { // Absolute,Y
            uint32_t a = addrAbsoluteY();
            if (flagM) mem->write(a, A & 0xFF);
            else mem->write16(a, A);
            break;
        }

        // LDX
        case 0xA2: { // Immediate
            if (flagX) { X = fetch8(); setNZ8(X); }
            else { X = fetch16(); setNZ16(X); }
            break;
        }
        case 0xA6: { // Direct
            uint32_t a = addrDirect();
            if (flagX) { X = mem->read(a); setNZ8(X); }
            else { X = mem->read16(a); setNZ16(X); }
            break;
        }
        case 0xAE: { // Absolute
            uint32_t a = addrAbsolute();
            if (flagX) { X = mem->read(a); setNZ8(X); }
            else { X = mem->read16(a); setNZ16(X); }
            break;
        }

        // STX
        case 0x86: { // Direct
            uint32_t a = addrDirect();
            if (flagX) mem->write(a, X & 0xFF);
            else mem->write16(a, X);
            break;
        }
        case 0x8E: { // Absolute
            uint32_t a = addrAbsolute();
            if (flagX) mem->write(a, X & 0xFF);
            else mem->write16(a, X);
            break;
        }

        // LDY
        case 0xA0: { // Immediate
            if (flagX) { Y = fetch8(); setNZ8(Y); }
            else { Y = fetch16(); setNZ16(Y); }
            break;
        }
        case 0xA4: { // Direct
            uint32_t a = addrDirect();
            if (flagX) { Y = mem->read(a); setNZ8(Y); }
            else { Y = mem->read16(a); setNZ16(Y); }
            break;
        }
        case 0xAC: { // Absolute
            uint32_t a = addrAbsolute();
            if (flagX) { Y = mem->read(a); setNZ8(Y); }
            else { Y = mem->read16(a); setNZ16(Y); }
            break;
        }

        // STY
        case 0x84: { // Direct
            uint32_t a = addrDirect();
            if (flagX) mem->write(a, Y & 0xFF);
            else mem->write16(a, Y);
            break;
        }
        case 0x8C: { // Absolute
            uint32_t a = addrAbsolute();
            if (flagX) mem->write(a, Y & 0xFF);
            else mem->write16(a, Y);
            break;
        }

        // STZ (Store Zero)
        case 0x64: { // Direct
            uint32_t a = addrDirect();
            if (flagM) mem->write(a, 0);
            else mem->write16(a, 0);
            break;
        }
        case 0x9C: { // Absolute
            uint32_t a = addrAbsolute();
            if (flagM) mem->write(a, 0);
            else mem->write16(a, 0);
            break;
        }

        // Transfers
        case 0xAA: X = A; if (flagX) { X &= 0xFF; setNZ8(X); } else setNZ16(X); break; // TAX
        case 0x8A: A = (A & (flagM ? 0xFF00 : 0)) | (flagM ? (X & 0xFF) : X); if (flagM) setNZ8(A & 0xFF); else setNZ16(A); break; // TXA
        case 0xA8: Y = A; if (flagX) { Y &= 0xFF; setNZ8(Y); } else setNZ16(Y); break; // TAY
        case 0x98: A = (A & (flagM ? 0xFF00 : 0)) | (flagM ? (Y & 0xFF) : Y); if (flagM) setNZ8(A & 0xFF); else setNZ16(A); break; // TYA
        case 0xBA: X = SP; if (flagX) { X &= 0xFF; setNZ8(X); } else setNZ16(X); break; // TSX
        case 0x9A: SP = flagE ? (0x0100 | (X & 0xFF)) : X; break; // TXS
        case 0x5B: D = A; setNZ16(D); break; // TCD
        case 0x7B: A = D; setNZ16(A); break; // TDC
        case 0x1B: SP = A; break; // TCS
        case 0x3B: A = SP; setNZ16(A); break; // TSC

        // Increments & Decrements
        case 0xE8: X = flagX ? ((X + 1) & 0xFF) : ((X + 1) & 0xFFFF); if (flagX) setNZ8(X); else setNZ16(X); break; // INX
        case 0xCA: X = flagX ? ((X - 1) & 0xFF) : ((X - 1) & 0xFFFF); if (flagX) setNZ8(X); else setNZ16(X); break; // DEX
        case 0xC8: Y = flagX ? ((Y + 1) & 0xFF) : ((Y + 1) & 0xFFFF); if (flagX) setNZ8(Y); else setNZ16(Y); break; // INY
        case 0x88: Y = flagX ? ((Y - 1) & 0xFF) : ((Y - 1) & 0xFFFF); if (flagX) setNZ8(Y); else setNZ16(Y); break; // DEY
        case 0x1A: A = flagM ? ((A & 0xFF00) | ((A + 1) & 0xFF)) : (A + 1); if (flagM) setNZ8(A & 0xFF); else setNZ16(A); break; // INC A
        case 0x3A: A = flagM ? ((A & 0xFF00) | ((A - 1) & 0xFF)) : (A - 1); if (flagM) setNZ8(A & 0xFF); else setNZ16(A); break; // DEC A

        // Stack pushes & pops
        case 0x48: if (flagM) push8(A & 0xFF); else push16(A); break; // PHA
        case 0x68: if (flagM) { A = (A & 0xFF00) | pop8(); setNZ8(A & 0xFF); } else { A = pop16(); setNZ16(A); } break; // PLA
        case 0xDA: if (flagX) push8(X & 0xFF); else push16(X); break; // PHX
        case 0xFA: if (flagX) { X = pop8(); setNZ8(X); } else { X = pop16(); setNZ16(X); } break; // PLX
        case 0x5A: if (flagX) push8(Y & 0xFF); else push16(Y); break; // PHY
        case 0x7A: if (flagX) { Y = pop8(); setNZ8(Y); } else { Y = pop16(); setNZ16(Y); } break; // PLY
        case 0x08: push8(getP()); break; // PHP
        case 0x28: setP(pop8()); break;  // PLP
        case 0x8B: push8(DB); break; // PHB
        case 0xAB: DB = pop8(); setNZ8(DB); break; // PLB
        case 0x4B: push8(PB); break; // PHK
        case 0x0B: push16(D); break; // PHD
        case 0x2B: D = pop16(); setNZ16(D); break; // PLD

        // Jumps and Subroutines
        case 0x4C: PC = fetch16(); break; // JMP Absolute
        case 0x5C: { uint16_t target = fetch16(); PB = fetch8(); PC = target; break; } // JML
        case 0x20: { uint16_t target = fetch16(); push16(PC - 1); PC = target; break; } // JSR
        case 0x22: { uint16_t target = fetch16(); uint8_t bank = fetch8(); push8(PB); push16(PC - 1); PB = bank; PC = target; break; } // JSL
        case 0x60: PC = pop16() + 1; break; // RTS
        case 0x6B: PC = pop16() + 1; PB = pop8(); break; // RTL
        case 0x40: { // RTI
            setP(pop8());
            PC = pop16();
            if (!flagE) PB = pop8();
            break;
        }

        // Branches
        case 0x80: { int8_t rel = (int8_t)fetch8(); PC += rel; break; } // BRA
        case 0x82: { int16_t rel = (int16_t)fetch16(); PC += rel; break; } // BRL
        case 0xF0: { int8_t rel = (int8_t)fetch8(); if (flagZ) PC += rel; break; } // BEQ
        case 0xD0: { int8_t rel = (int8_t)fetch8(); if (!flagZ) PC += rel; break; } // BNE
        case 0x90: { int8_t rel = (int8_t)fetch8(); if (!flagC) PC += rel; break; } // BCC
        case 0xB0: { int8_t rel = (int8_t)fetch8(); if (flagC) PC += rel; break; } // BCS
        case 0x30: { int8_t rel = (int8_t)fetch8(); if (flagN) PC += rel; break; } // BMI
        case 0x10: { int8_t rel = (int8_t)fetch8(); if (!flagN) PC += rel; break; } // BPL

        // Compare
        case 0xC9: { // CMP Immediate
            if (flagM) {
                uint8_t imm = fetch8();
                uint8_t aVal = A & 0xFF;
                flagC = aVal >= imm;
                setNZ8(aVal - imm);
            } else {
                uint16_t imm = fetch16();
                flagC = A >= imm;
                setNZ16(A - imm);
            }
            break;
        }
        case 0xE0: { // CPX Immediate
            if (flagX) {
                uint8_t imm = fetch8();
                flagC = (X & 0xFF) >= imm;
                setNZ8((X & 0xFF) - imm);
            } else {
                uint16_t imm = fetch16();
                flagC = X >= imm;
                setNZ16(X - imm);
            }
            break;
        }
        case 0xC0: { // CPY Immediate
            if (flagX) {
                uint8_t imm = fetch8();
                flagC = (Y & 0xFF) >= imm;
                setNZ8((Y & 0xFF) - imm);
            } else {
                uint16_t imm = fetch16();
                flagC = Y >= imm;
                setNZ16(Y - imm);
            }
            break;
        }

        case 0xEA: break; // NOP
        case 0xDB: stopped = true; break; // STP
        case 0xCB: waitingForInterrupt = true; break; // WAI

        default:
            // Unimplemented/rare opcode - advance PC safely
            break;
    }
}

size_t Cpu65816::getStateSize() const {
    return 64;
}

bool Cpu65816::serialize(uint8_t* dest, size_t maxSize, size_t& written) const {
    if (maxSize < getStateSize()) return false;
    uint8_t* ptr = dest;

    *reinterpret_cast<uint16_t*>(ptr) = A; ptr += 2;
    *reinterpret_cast<uint16_t*>(ptr) = X; ptr += 2;
    *reinterpret_cast<uint16_t*>(ptr) = Y; ptr += 2;
    *reinterpret_cast<uint16_t*>(ptr) = SP; ptr += 2;
    *reinterpret_cast<uint16_t*>(ptr) = PC; ptr += 2;
    *reinterpret_cast<uint16_t*>(ptr) = D; ptr += 2;
    *ptr++ = DB;
    *ptr++ = PB;
    *ptr++ = getP();
    *ptr++ = flagE ? 1 : 0;

    written = ptr - dest;
    return true;
}

bool Cpu65816::deserialize(const uint8_t* src, size_t size, size_t& readBytes) {
    if (size < getStateSize()) return false;
    const uint8_t* ptr = src;

    A = *reinterpret_cast<const uint16_t*>(ptr); ptr += 2;
    X = *reinterpret_cast<const uint16_t*>(ptr); ptr += 2;
    Y = *reinterpret_cast<const uint16_t*>(ptr); ptr += 2;
    SP = *reinterpret_cast<const uint16_t*>(ptr); ptr += 2;
    PC = *reinterpret_cast<const uint16_t*>(ptr); ptr += 2;
    D = *reinterpret_cast<const uint16_t*>(ptr); ptr += 2;
    DB = *ptr++;
    PB = *ptr++;
    setP(*ptr++);
    flagE = (*ptr++ != 0);

    readBytes = ptr - src;
    return true;
}
