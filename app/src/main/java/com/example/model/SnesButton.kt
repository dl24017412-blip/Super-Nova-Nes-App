package com.example.model

enum class SnesButton(val mask: Int) {
    B(1 shl 0),
    Y(1 shl 1),
    SELECT(1 shl 2),
    START(1 shl 3),
    UP(1 shl 4),
    DOWN(1 shl 5),
    LEFT(1 shl 6),
    RIGHT(1 shl 7),
    A(1 shl 8),
    X(1 shl 9),
    L(1 shl 10),
    R(1 shl 11)
}

enum class PerformanceProfile(val id: Int, val label: String) {
    LOW(0, "Baixo (Economia de Bateria)"),
    BALANCED(1, "Equilibrado (Recomendado)"),
    QUALITY(2, "Qualidade Alta (CRT Scanlines)")
}

enum class AspectRatioMode(val id: Int, val label: String) {
    ORIGINAL_4_3(0, "4:3 Original SNES"),
    INTEGER_SCALE(1, "Escala Inteira (Pixel Perfect)"),
    FILL_SCREEN(2, "Preencher Tela")
}

enum class VideoFilter(val id: Int, val label: String) {
    NEAREST(0, "Nítido (Nearest Neighbor)"),
    BILINEAR(1, "Suave (Bilinear)"),
    SCANLINE(2, "Retrô Scanlines CRT")
}

data class EmulatorSettings(
    val performanceProfile: PerformanceProfile = PerformanceProfile.BALANCED,
    val aspectRatio: AspectRatioMode = AspectRatioMode.ORIGINAL_4_3,
    val videoFilter: VideoFilter = VideoFilter.BILINEAR,
    val audioEnabled: Boolean = true,
    val audioVolume: Float = 1.0f,
    val showFps: Boolean = true,
    val hapticFeedback: Boolean = true,
    val buttonOpacity: Float = 0.65f,
    val buttonScale: Float = 1.0f,
    val autoSaveOnExit: Boolean = true
)
