#ifndef CPU65816_H
#define CPU65816_H

#include <cstdint>
#include <cstddef>

class SnesMemory;

class Cpu65816 {
public:
    Cpu65816(SnesMemory* mem);
    ~Cpu65816();

    void reset();
    int step(); // Run one instruction, return cycles
    void triggerNmi();
    void triggerIrq();

    // Registers
    uint16_t A = 0;
    uint16_t X = 0;
    uint16_t Y = 0;
    uint16_t SP = 0x01FF;
    uint16_t PC = 0x8000;
    uint16_t D = 0;
    uint8_t DB = 0;
    uint8_t PB = 0;
    
    // Status flags
    bool flagC = false; // Carry
    bool flagZ = false; // Zero
    bool flagI = true;  // IRQ disable
    bool flagD = false; // Decimal
    bool flagX = true;  // Index register size (0 = 16-bit, 1 = 8-bit)
    bool flagM = true;  // Accumulator size (0 = 16-bit, 1 = 8-bit)
    bool flagV = false; // Overflow
    bool flagN = false; // Negative
    bool flagE = true;  // Emulation mode

    bool waitingForInterrupt = false;
    bool stopped = false;
    bool nmiPending = false;
    bool irqPending = false;

    // Status register helper
    uint8_t getP() const;
    void setP(uint8_t p);

    // Serialization for Save States
    size_t getStateSize() const;
    bool serialize(uint8_t* dest, size_t maxSize, size_t& written) const;
    bool deserialize(const uint8_t* src, size_t size, size_t& readBytes);

private:
    SnesMemory* mem;
    int cyclesLeft = 0;

    uint8_t fetch8();
    uint16_t fetch16();
    uint32_t fetch24();

    void push8(uint8_t val);
    uint8_t pop8();
    void push16(uint16_t val);
    uint16_t pop16();

    void setNZ8(uint8_t val);
    void setNZ16(uint16_t val);

    // Addressing mode resolvers returning 24-bit bus address
    uint32_t addrDirect();
    uint32_t addrDirectX();
    uint32_t addrDirectY();
    uint32_t addrAbsolute();
    uint32_t addrAbsoluteX();
    uint32_t addrAbsoluteY();
    uint32_t addrAbsoluteLong();
    uint32_t addrAbsoluteLongX();
    uint32_t addrDirectIndirect();
    uint32_t addrDirectIndirectLong();
    uint32_t addrDirectIndexedIndirect();
    uint32_t addrDirectIndirectIndexed();
    uint32_t addrDirectIndirectLongIndexed();
    uint32_t addrStackRelative();
    uint32_t addrStackRelativeIndirectIndexed();

    void executeOpcode(uint8_t opcode);
};

#endif // CPU65816_H
