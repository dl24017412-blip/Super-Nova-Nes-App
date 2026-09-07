package com.example.ui.emulator

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.RomEntity
import com.example.data.preferences.EmulatorPreferences
import com.example.data.repository.EmulatorRepository
import com.example.jni.NativeBridge
import com.example.model.EmulatorSettings
import com.example.model.SnesButton
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class EmulatorUiState(
    val isLoading: Boolean = true,
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val isFastForward: Boolean = false,
    val errorMessage: String? = null,
    val rom: RomEntity? = null,
    val fps: Float = 0f,
    val frameTimeMs: Float = 0f,
    val cpuTimeMs: Float = 0f,
    val activeButtonsMask: Int = 0,
    val settings: EmulatorSettings = EmulatorSettings(),
    val saveMessage: String? = null,
    val showPauseMenu: Boolean = false
)

class EmulatorViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = EmulatorRepository(application)
    private val preferences = EmulatorPreferences(application)

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = application.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        manager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        application.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private val _uiState = MutableStateFlow(EmulatorUiState())
    val uiState: StateFlow<EmulatorUiState> = _uiState.asStateFlow()

    private var statsJob: Job? = null
    private var sessionStartTime: Long = 0
    private var currentButtonMask = 0

    init {
        val storagePath = repository.getSramDirectory()
        NativeBridge.nativeInit(storagePath)

        viewModelScope.launch {
            preferences.settingsFlow.collect { newSettings ->
                _uiState.value = _uiState.value.copy(settings = newSettings)
                applySettingsToNative(newSettings)
            }
        }
    }

    private fun applySettingsToNative(s: EmulatorSettings) {
        NativeBridge.nativeSetVideoOptions(s.aspectRatio.id, s.videoFilter.id, s.performanceProfile.id)
        NativeBridge.nativeSetAudioOptions(s.audioEnabled, s.audioVolume)
    }

    fun loadAndStartRom(romId: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val rom = repository.getRomById(romId)
            if (rom == null) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Arquivo ROM não encontrado no banco de dados."
                )
                return@launch
            }

            val file = File(rom.filePath)
            if (!file.exists()) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Arquivo '${file.name}' não existe no armazenamento."
                )
                return@launch
            }

            try {
                val bytes = file.readBytes()
                val result = NativeBridge.nativeLoadRom(bytes, file.name)
                if (result != 0) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Formato de ROM inválido ou arquivo corrompido."
                    )
                    return@launch
                }

                applySettingsToNative(_uiState.value.settings)
                NativeBridge.nativeStart()

                sessionStartTime = System.currentTimeMillis()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isRunning = true,
                    isPaused = false,
                    rom = rom
                )

                startStatsPolling()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Erro ao carregar ROM: ${e.message}"
                )
            }
        }
    }

    fun pauseGame() {
        if (_uiState.value.isRunning && !_uiState.value.isPaused) {
            NativeBridge.nativePause()
            _uiState.value = _uiState.value.copy(isPaused = true, showPauseMenu = true)
        }
    }

    fun resumeGame() {
        if (_uiState.value.isRunning && _uiState.value.isPaused) {
            NativeBridge.nativeResume()
            _uiState.value = _uiState.value.copy(isPaused = false, showPauseMenu = false)
        }
    }

    fun resetGame() {
        NativeBridge.nativeReset()
        _uiState.value = _uiState.value.copy(showPauseMenu = false, isPaused = false)
        NativeBridge.nativeResume()
    }

    fun setButtonState(button: SnesButton, pressed: Boolean) {
        if (pressed) {
            currentButtonMask = currentButtonMask or button.mask
            if (_uiState.value.settings.hapticFeedback) {
                triggerHaptic()
            }
        } else {
            currentButtonMask = currentButtonMask and button.mask.inv()
        }
        _uiState.value = _uiState.value.copy(activeButtonsMask = currentButtonMask)
        NativeBridge.nativeSetInputState(0, currentButtonMask)
    }

    fun setRawButtonMask(mask: Int) {
        currentButtonMask = mask
        _uiState.value = _uiState.value.copy(activeButtonsMask = currentButtonMask)
        NativeBridge.nativeSetInputState(0, currentButtonMask)
    }

    private fun triggerHaptic() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(15)
            }
        } catch (_: Exception) {}
    }

    fun toggleFastForward() {
        val newFf = !_uiState.value.isFastForward
        _uiState.value = _uiState.value.copy(isFastForward = newFf)
        NativeBridge.nativeSetFastForward(newFf)
    }

    fun quickSave() {
        saveState(0)
    }

    fun quickLoad() {
        loadState(0)
    }

    fun saveState(slot: Int) {
        val rom = _uiState.value.rom ?: return
        viewModelScope.launch {
            val ok = repository.saveState(rom.id, slot)
            _uiState.value = _uiState.value.copy(
                saveMessage = if (ok) "Estado salvo com sucesso no Slot $slot!" else "Falha ao salvar estado no Slot $slot"
            )
            delay(2500)
            _uiState.value = _uiState.value.copy(saveMessage = null)
        }
    }

    fun loadState(slot: Int) {
        val rom = _uiState.value.rom ?: return
        viewModelScope.launch {
            val ok = repository.loadState(rom.id, slot)
            _uiState.value = _uiState.value.copy(
                saveMessage = if (ok) "Estado do Slot $slot restaurado com sucesso!" else "Falha ao restaurar Slot $slot"
            )
            if (ok) {
                resumeGame()
            }
            delay(2500)
            _uiState.value = _uiState.value.copy(saveMessage = null)
        }
    }

    fun updateSettings(newSettings: EmulatorSettings) {
        preferences.updateSettings(newSettings)
    }

    fun closePauseMenu() {
        _uiState.value = _uiState.value.copy(showPauseMenu = false)
        if (_uiState.value.isPaused) {
            resumeGame()
        }
    }

    fun openPauseMenu() {
        pauseGame()
        _uiState.value = _uiState.value.copy(showPauseMenu = true)
    }

    private fun startStatsPolling() {
        statsJob?.cancel()
        statsJob = viewModelScope.launch {
            val stats = FloatArray(3)
            while (isActive) {
                if (_uiState.value.isRunning && !_uiState.value.isPaused) {
                    NativeBridge.nativeGetStats(stats)
                    _uiState.value = _uiState.value.copy(
                        fps = stats[0],
                        frameTimeMs = stats[1],
                        cpuTimeMs = stats[2]
                    )
                }
                delay(500) // Poll every 500ms
            }
        }
    }

    fun stopEmulation() {
        statsJob?.cancel()
        NativeBridge.nativeStop()

        val rom = _uiState.value.rom
        if (rom != null && sessionStartTime > 0) {
            val duration = (System.currentTimeMillis() - sessionStartTime) / 1000
            viewModelScope.launch {
                repository.recordPlaySession(rom.id, duration)
            }
        }

        _uiState.value = _uiState.value.copy(
            isRunning = false,
            isPaused = false,
            showPauseMenu = false
        )
    }

    override fun onCleared() {
        super.onCleared()
        stopEmulation()
    }
}
