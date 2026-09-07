package com.example.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.example.model.AspectRatioMode
import com.example.model.EmulatorSettings
import com.example.model.PerformanceProfile
import com.example.model.VideoFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class EmulatorPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("supernova_prefs", Context.MODE_PRIVATE)

    private val _settingsFlow = MutableStateFlow(loadSettings())
    val settingsFlow: StateFlow<EmulatorSettings> = _settingsFlow.asStateFlow()

    fun getSettings(): EmulatorSettings = _settingsFlow.value

    private fun loadSettings(): EmulatorSettings {
        val profileId = prefs.getInt("perf_profile", PerformanceProfile.BALANCED.id)
        val aspectId = prefs.getInt("aspect_ratio", AspectRatioMode.ORIGINAL_4_3.id)
        val filterId = prefs.getInt("video_filter", VideoFilter.BILINEAR.id)
        val audioEnabled = prefs.getBoolean("audio_enabled", true)
        val audioVolume = prefs.getFloat("audio_volume", 1.0f)
        val showFps = prefs.getBoolean("show_fps", true)
        val haptic = prefs.getBoolean("haptic_feedback", true)
        val opacity = prefs.getFloat("button_opacity", 0.65f)
        val scale = prefs.getFloat("button_scale", 1.0f)
        val autoSave = prefs.getBoolean("auto_save_exit", true)

        val profile = PerformanceProfile.values().find { it.id == profileId } ?: PerformanceProfile.BALANCED
        val aspect = AspectRatioMode.values().find { it.id == aspectId } ?: AspectRatioMode.ORIGINAL_4_3
        val filter = VideoFilter.values().find { it.id == filterId } ?: VideoFilter.BILINEAR

        return EmulatorSettings(
            performanceProfile = profile,
            aspectRatio = aspect,
            videoFilter = filter,
            audioEnabled = audioEnabled,
            audioVolume = audioVolume,
            showFps = showFps,
            hapticFeedback = haptic,
            buttonOpacity = opacity,
            buttonScale = scale,
            autoSaveOnExit = autoSave
        )
    }

    fun updateSettings(newSettings: EmulatorSettings) {
        prefs.edit()
            .putInt("perf_profile", newSettings.performanceProfile.id)
            .putInt("aspect_ratio", newSettings.aspectRatio.id)
            .putInt("video_filter", newSettings.videoFilter.id)
            .putBoolean("audio_enabled", newSettings.audioEnabled)
            .putFloat("audio_volume", newSettings.audioVolume)
            .putBoolean("show_fps", newSettings.showFps)
            .putBoolean("haptic_feedback", newSettings.hapticFeedback)
            .putFloat("button_opacity", newSettings.buttonOpacity)
            .putFloat("button_scale", newSettings.buttonScale)
            .putBoolean("auto_save_exit", newSettings.autoSaveOnExit)
            .apply()

        _settingsFlow.value = newSettings
    }
}
