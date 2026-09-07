package com.example.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.preferences.EmulatorPreferences
import com.example.model.AspectRatioMode
import com.example.model.EmulatorSettings
import com.example.model.PerformanceProfile
import com.example.model.VideoFilter
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = EmulatorPreferences(application)

    val settings: StateFlow<EmulatorSettings> = preferences.settingsFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        preferences.getSettings()
    )

    fun updatePerformanceProfile(profile: PerformanceProfile) {
        preferences.updateSettings(settings.value.copy(performanceProfile = profile))
    }

    fun updateAspectRatio(aspect: AspectRatioMode) {
        preferences.updateSettings(settings.value.copy(aspectRatio = aspect))
    }

    fun updateVideoFilter(filter: VideoFilter) {
        preferences.updateSettings(settings.value.copy(videoFilter = filter))
    }

    fun setAudioEnabled(enabled: Boolean) {
        preferences.updateSettings(settings.value.copy(audioEnabled = enabled))
    }

    fun setAudioVolume(volume: Float) {
        preferences.updateSettings(settings.value.copy(audioVolume = volume))
    }

    fun setShowFps(show: Boolean) {
        preferences.updateSettings(settings.value.copy(showFps = show))
    }

    fun setHapticFeedback(enabled: Boolean) {
        preferences.updateSettings(settings.value.copy(hapticFeedback = enabled))
    }

    fun setButtonOpacity(opacity: Float) {
        preferences.updateSettings(settings.value.copy(buttonOpacity = opacity))
    }

    fun setButtonScale(scale: Float) {
        preferences.updateSettings(settings.value.copy(buttonScale = scale))
    }

    fun setAutoSaveOnExit(autoSave: Boolean) {
        preferences.updateSettings(settings.value.copy(autoSaveOnExit = autoSave))
    }
}
