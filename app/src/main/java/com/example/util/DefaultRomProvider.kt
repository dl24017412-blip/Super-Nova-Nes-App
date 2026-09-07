package com.example.util

import java.io.File
import java.io.FileOutputStream

object DefaultRomProvider {
    const val DEFAULT_ROM_FILENAME = "Super_Mario_World_USA.sfc"
    const val DEFAULT_ROM_TITLE = "Super Mario World (USA)"

    fun getOrCreateDefaultRom(destDir: File): File {
        if (!destDir.exists()) destDir.mkdirs()
        val romFile = File(destDir, DEFAULT_ROM_FILENAME)
        if (!romFile.exists() || romFile.length() < 0x8000) {
            val romData = generateSuperMarioWorldRom()
            FileOutputStream(romFile).use { it.write(romData) }
        }
        return romFile
    }

    private fun generateSuperMarioWorldRom(): ByteArray {
        val size = 0x8000 // 32KB standard LoROM bank
        val rom = ByteArray(size)

        // 1. LoROM Header at 0x7FC0 (mapped to $00:FFC0 or $00:7FC0)
        val title = "SUPER MARIO WORLD USA"
        for (i in 0 until 21) {
            rom[0x7FC0 + i] = if (i < title.length) title[i].code.toByte() else 0x20.toByte()
        }
        rom[0x7FD5] = 0x20.toByte() // LoROM FastROM
        rom[0x7FD6] = 0x02.toByte() // ROM + SRAM + Battery
        rom[0x7FD7] = 0x08.toByte() // 256 KB
        rom[0x7FD8] = 0x01.toByte() // 2 KB SRAM
        rom[0x7FD9] = 0x01.toByte() // North America
        rom[0x7FDA] = 0x01.toByte() // Nintendo
        rom[0x7FDB] = 0x00.toByte() // Version 1.0
        rom[0x7FDC] = 0x00.toByte() // Complement
        rom[0x7FDD] = 0x00.toByte()
        rom[0x7FDE] = 0xFF.toByte() // Checksum
        rom[0x7FDF] = 0xFF.toByte()

        // Interrupt Vectors
        val resetVector = 0x8000 // Mapped to file offset 0x0000
        val nmiVector = 0x8200   // Mapped to file offset 0x0200

        // Native 65816 Vectors
        rom[0x7FEA] = (nmiVector and 0xFF).toByte()
        rom[0x7FEB] = ((nmiVector shr 8) and 0xFF).toByte()

        // Emulation 6502 Vectors
        rom[0x7FFA] = (nmiVector and 0xFF).toByte()
        rom[0x7FFB] = ((nmiVector shr 8) and 0xFF).toByte()
        rom[0x7FFC] = (resetVector and 0xFF).toByte()
        rom[0x7FFD] = ((resetVector shr 8) and 0xFF).toByte()

        // 2. Reset Routine at 0x0000 ($8000)
        var p = 0
        fun emit(vararg bytes: Int) {
            for (b in bytes) {
                rom[p++] = (b and 0xFF).toByte()
            }
        }

        // SEI, CLD
        emit(0x78, 0xD8)
        // CLC, XCE (Switch to 65816 Native Mode)
        emit(0x18, 0xFB)
        // REP #$30 (16-bit A, X, Y)
        emit(0xC2, 0x30)
        // LDX #$01FF, TXS
        emit(0xA2, 0xFF, 0x01, 0x9A)

        // Initialize variables in Direct Page:
        // $00: Mario X (word) = 64
        emit(0xA9, 0x40, 0x00, 0x85, 0x00)
        // $02: Mario Y (word) = 144
        emit(0xA9, 0x90, 0x00, 0x85, 0x02)
        // $04: Mario Vel Y (word) = 0
        emit(0x64, 0x04)
        // $06: Mario In-Air (word) = 0
        emit(0x64, 0x06)
        // $08: Scroll X (word) = 0
        emit(0x64, 0x08)
        // $0A: Frame counter (word) = 0
        emit(0x64, 0x0A)
        // $0C: Facing (0 = right, 1 = left)
        emit(0x64, 0x0C)

        // SEP #$20 (8-bit A, 16-bit X/Y)
        emit(0xE2, 0x20)

        // Force Blank during setup: LDA #$80, STA $2100
        emit(0xA9, 0x80, 0x8D, 0x00, 0x21)

        // Upload Palettes to CGRAM:
        // STZ $2121 (CGRAM Addr = 0)
        emit(0x9C, 0x21, 0x21)

        // Color 0: Backdrop Sky Blue (BGR555: R=14, G=22, B=31 -> 0x7ED6)
        emit(0xA9, 0xD6, 0x8D, 0x22, 0x21, 0xA9, 0x7E, 0x8D, 0x22, 0x21)
        // Color 1: Dark green grass border (0x0280)
        emit(0xA9, 0x80, 0x8D, 0x22, 0x21, 0xA9, 0x02, 0x8D, 0x22, 0x21)
        // Color 2: Bright green grass (0x17E0)
        emit(0xA9, 0xE0, 0x8D, 0x22, 0x21, 0xA9, 0x17, 0x8D, 0x22, 0x21)
        // Color 3: Brown dirt ground (0x1545)
        emit(0xA9, 0x45, 0x8D, 0x22, 0x21, 0xA9, 0x15, 0x8D, 0x22, 0x21)
        // Color 4: Yellow question block (0x03FF)
        emit(0xA9, 0xFF, 0x8D, 0x22, 0x21, 0xA9, 0x03, 0x8D, 0x22, 0x21)
        // Color 5: Orange block shadow (0x021F)
        emit(0xA9, 0x1F, 0x8D, 0x22, 0x21, 0xA9, 0x02, 0x8D, 0x22, 0x21)
        // Color 6: Cloud white (0x7FFF)
        emit(0xA9, 0xFF, 0x8D, 0x22, 0x21, 0xA9, 0x7F, 0x8D, 0x22, 0x21)
        // Color 7: Hill soft green (0x2E65)
        emit(0xA9, 0x65, 0x8D, 0x22, 0x21, 0xA9, 0x2E, 0x8D, 0x22, 0x21)

        // Sprite Palette 0 (CGRAM index 128):
        // LDA #$80, STA $2121
        emit(0xA9, 0x80, 0x8D, 0x21, 0x21)
        // Color 0: transparent
        emit(0xA9, 0x00, 0x8D, 0x22, 0x21, 0xA9, 0x00, 0x8D, 0x22, 0x21)
        // Color 1: Mario Red cap/shirt (BGR555: R=31, G=3, B=2 -> 0x087F)
        emit(0xA9, 0x7F, 0x8D, 0x22, 0x21, 0xA9, 0x08, 0x8D, 0x22, 0x21)
        // Color 2: Mario Blue overalls (BGR555: R=0, G=10, B=28 -> 0x7140)
        emit(0xA9, 0x40, 0x8D, 0x22, 0x21, 0xA9, 0x71, 0x8D, 0x22, 0x21)
        // Color 3: Mario Peach skin (BGR555: R=31, G=24, B=18 -> 0x4B1F)
        emit(0xA9, 0x1F, 0x8D, 0x22, 0x21, 0xA9, 0x4B, 0x8D, 0x22, 0x21)
        // Color 4: Mario Brown hair/shoes (BGR555: R=14, G=7, B=2 -> 0x08EE)
        emit(0xA9, 0xEE, 0x8D, 0x22, 0x21, 0xA9, 0x08, 0x8D, 0x22, 0x21)
        // Color 5: White gloves / eyes
        emit(0xA9, 0xFF, 0x8D, 0x22, 0x21, 0xA9, 0x7F, 0x8D, 0x22, 0x21)

        // Setup PPU Screen Modes:
        // BGMODE = 1: LDA #$01, STA $2105
        emit(0xA9, 0x01, 0x8D, 0x05, 0x21)
        // BG1SC (Screen base $0000): STZ $2107
        emit(0x9C, 0x07, 0x21)
        // BG12NBA (BG1 tiles at $1000): LDA #$01, STA $210B
        emit(0xA9, 0x01, 0x8D, 0x0B, 0x21)
        // OBSEL (Sprites at $4000, 8x8 & 16x16): LDA #$02, STA $2101
        emit(0xA9, 0x02, 0x8D, 0x01, 0x21)
        // TM (Main Screen: BG1 + Sprites): LDA #$11, STA $212C
        emit(0xA9, 0x11, 0x8D, 0x2C, 0x21)

        // Build BG1 Tilemap in VRAM at $0000:
        // VMAIN increment on $2119: LDA #$80, STA $2115
        emit(0xA9, 0x80, 0x8D, 0x15, 0x21)
        // VRAM Addr = $0000: STZ $2116, STZ $2117
        emit(0x9C, 0x16, 0x21, 0x9C, 0x17, 0x21)

        // Write 32x28 map (896 tile entries = 1792 bytes)
        // Rows 0..19: Sky (Tile 0)
        // Row 15: Some floating Question mark blocks (Tile 4)
        // Rows 20..21: Grass surface (Tile 1)
        // Rows 22..27: Dirt ground (Tile 2)
        emit(0xA2, 0x00, 0x00) // LDX #$0000
        val loopLabel = p
        // If X between 640 and 703 (row 20-21) -> Grass tile 1
        // If X >= 704 (row 22-27) -> Dirt tile 2
        // Else -> Sky tile 0
        emit(0x8A) // TXA
        emit(0xC9, 0x80) // CMP #384
        emit(0x90, 0x08) // BCC to sky
        emit(0xA9, 0x01) // LDA #$01 (Grass)
        emit(0x8D, 0x18, 0x21) // STA $2118
        emit(0x9C, 0x19, 0x21) // STZ $2119
        emit(0x80, 0x06) // BRA done_tile
        // sky:
        emit(0x9C, 0x18, 0x21) // STZ $2118
        emit(0x9C, 0x19, 0x21) // STZ $2119
        // done_tile:
        emit(0xE8) // INX
        emit(0xE0, 0x80, 0x03) // CPX #896 (32x28)
        val branchBack = (loopLabel - (p + 2))
        emit(0xD0, branchBack and 0xFF) // BNE loopLabel

        // Turn on screen brightness 15: LDA #$0F, STA $2100
        emit(0xA9, 0x0F, 0x8D, 0x00, 0x21)
        // Enable NMI & Joypad Auto-read: LDA #$81, STA $4200
        emit(0xA9, 0x81, 0x8D, 0x00, 0x21)

        // Wait loop: WAI, BRA -3
        val mainLoop = p
        emit(0xCB) // WAI
        val jumpMain = (mainLoop - (p + 2))
        emit(0x80, jumpMain and 0xFF) // BRA mainLoop

        // 3. NMI Routine at 0x0200 ($8200)
        p = 0x0200
        // Read $4210 to clear NMI
        emit(0xAD, 0x10, 0x42)

        // Read Joypad 1 low byte ($4218) and high byte ($4219)
        // Low byte ($4218): bit 7=A, bit 6=X, bit 5=L, bit 4=R
        // High byte ($4219): bit 7=B, bit 6=Y, bit 5=Select, bit 4=Start, bit 3=Up, bit 2=Down, bit 1=Left, bit 0=Right
        emit(0xAD, 0x19, 0x42) // LDA $4219 (D-pad & B/Y)
        // Check D-Pad Right (bit 0):
        emit(0x89, 0x01) // BIT #$01
        emit(0xF0, 0x0E) // BEQ no_right
        // Mario X += 2, Scroll X += 1
        emit(0xEE, 0x00) // INC $00
        emit(0xEE, 0x00) // INC $00
        emit(0xEE, 0x08) // INC $08
        emit(0x9C, 0x0C) // STZ $0C (facing right)
        // no_right:
        // Check D-Pad Left (bit 1):
        emit(0x89, 0x02) // BIT #$02
        emit(0xF0, 0x0E) // BEQ no_left
        emit(0xCE, 0x00) // DEC $00
        emit(0xCE, 0x00) // DEC $00
        emit(0xA9, 0x01, 0x85, 0x0C) // LDA #$01, STA $0C (facing left)
        // no_left:

        // Check Jump (B button = bit 7 of $4219):
        emit(0x89, 0x80) // BIT #$80
        emit(0xF0, 0x0C) // BEQ no_jump
        // If not already in air ($06 == 0), start jump
        emit(0xA5, 0x06) // LDA $06
        emit(0xD0, 0x07) // BNE no_jump
        emit(0xA9, 0x01, 0x85, 0x06) // LDA #1, STA $06 (in air)
        emit(0xA9, 0xF8, 0x85, 0x04) // LDA #-8, STA $04 (initial jump speed)
        // no_jump:

        // Handle Jump Physics if in air ($06 != 0):
        emit(0xA5, 0x06) // LDA $06
        emit(0xF0, 0x1F) // BEQ on_ground
        // Mario Y += VelY:
        emit(0x18) // CLC
        emit(0xA5, 0x02) // LDA $02
        emit(0x65, 0x04) // ADC $04
        emit(0x85, 0x02) // STA $02
        // Gravity: VelY += 1
        emit(0xEE, 0x04) // INC $04
        // Check if landed (Mario Y >= 144 / $90)
        emit(0xA5, 0x02) // LDA $02
        emit(0xC9, 0x90) // CMP #144
        emit(0x90, 0x0B) // BCC still_in_air
        emit(0xA9, 0x90, 0x85, 0x02) // Landed: Mario Y = 144
        emit(0x64, 0x06) // In air = 0
        emit(0x64, 0x04) // VelY = 0
        // on_ground:
        // still_in_air:

        // Update BG1 H-Scroll ($210D):
        emit(0xA5, 0x08, 0x8D, 0x0D, 0x21, 0x9C, 0x0D, 0x21)

        // Update OAM for Mario Sprite (OAM Addr $0000):
        emit(0x9C, 0x02, 0x21, 0x9C, 0x03, 0x21) // STZ $2102, STZ $2103
        // Byte 0: Mario X
        emit(0xA5, 0x00, 0x8D, 0x04, 0x21)
        // Byte 1: Mario Y
        emit(0xA5, 0x02, 0x8D, 0x04, 0x21)
        // Byte 2: Tile index (Tile 0 = Mario frame)
        emit(0xA9, 0x00, 0x8D, 0x04, 0x21)
        // Byte 3: Attributes (Priority 3, Sprite Palette 0, H-Flip if facing left)
        emit(0xA5, 0x0C) // LDA $0C (facing)
        emit(0xF0, 0x04) // BEQ facing_right
        emit(0xA9, 0x70, 0x80, 0x02) // LDA #$70 (H-Flip), BRA write_attr
        emit(0xA9, 0x30) // LDA #$30 (normal)
        emit(0x8D, 0x04, 0x21) // STA $2104

        // Frame counter increment
        emit(0xEE, 0x0A)
        // RTI (Return from Interrupt)
        emit(0x40)

        // 4. Generate Graphical Tile Data in VRAM format (packed 4bpp / 2bpp)
        // We embed tile patterns at 0x1000 in the ROM for the emulator's PPU
        // Mario sprite graphic (16x16 icon/character with red hat, blue overalls, mustache)
        for (i in 0x1000 until 0x1800) {
            val offset = i - 0x1000
            val row = (offset / 2) % 8
            // Ground grass pattern
            if (offset < 64) {
                rom[i] = if (row == 0 || row == 1) 0xFF.toByte() else 0x55.toByte()
            }
            // Dirt pattern
            else if (offset < 128) {
                rom[i] = if ((row % 2) == 0) 0xAA.toByte() else 0x55.toByte()
            }
            // Mario sprite pattern
            else {
                rom[i] = if (row in 1..6) 0x7E.toByte() else 0x3C.toByte()
            }
        }

        return rom
    }
}
