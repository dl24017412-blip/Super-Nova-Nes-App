#ifndef SNES_TYPES_H
#define SNES_TYPES_H

#include <cstdint>
#include <cstddef>
#include <android/log.h>

#define LOG_TAG "SuperNovaSNES"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#ifdef DEBUG_BUILD
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#else
#define LOGD(...) do {} while(0)
#endif

// Controller button bitmasks
constexpr uint16_t SNES_BTN_B      = 1 << 0;
constexpr uint16_t SNES_BTN_Y      = 1 << 1;
constexpr uint16_t SNES_BTN_SELECT = 1 << 2;
constexpr uint16_t SNES_BTN_START  = 1 << 3;
constexpr uint16_t SNES_BTN_UP     = 1 << 4;
constexpr uint16_t SNES_BTN_DOWN   = 1 << 5;
constexpr uint16_t SNES_BTN_LEFT   = 1 << 6;
constexpr uint16_t SNES_BTN_RIGHT  = 1 << 7;
constexpr uint16_t SNES_BTN_A      = 1 << 8;
constexpr uint16_t SNES_BTN_X      = 1 << 9;
constexpr uint16_t SNES_BTN_L      = 1 << 10;
constexpr uint16_t SNES_BTN_R      = 1 << 11;

// Video constants
constexpr int SNES_SCREEN_WIDTH = 256;
constexpr int SNES_SCREEN_HEIGHT = 224;
constexpr int SNES_TOTAL_PIXELS = SNES_SCREEN_WIDTH * SNES_SCREEN_HEIGHT;

// Audio constants
constexpr int SNES_AUDIO_SAMPLE_RATE = 32000;
constexpr int SNES_AUDIO_CHANNELS = 2;
constexpr int SNES_SAMPLES_PER_FRAME = 534; // ~32000 / 59.94

// Performance Profiles
enum class PerformanceProfile {
    LOW = 0,
    BALANCED = 1,
    QUALITY = 2
};

// Aspect Ratio Modes
enum class AspectRatioMode {
    ORIGINAL_4_3 = 0,
    INTEGER_SCALE = 1,
    FILL_SCREEN = 2
};

// Scaling Filters
enum class VideoFilter {
    NEAREST = 0,
    BILINEAR = 1,
    SCANLINE = 2
};

struct RomHeaderInfo {
    char title[22];
    uint8_t romType;
    uint8_t romSize;
    uint8_t sramSize;
    uint16_t checksum;
    uint16_t complement;
    bool isHiRom;
    bool hasBattery;
    uint32_t realRomSize;
};

#endif // SNES_TYPES_H
