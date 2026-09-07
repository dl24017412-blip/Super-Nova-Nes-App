package com.example.ui.emulator

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.jni.NativeBridge
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.SnesPrimary
import com.example.ui.theme.SnesSecondary

@Composable
fun EmulatorScreen(
    romId: Long,
    viewModel: EmulatorViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(romId) {
        viewModel.loadAndStartRom(romId)
    }

    // Intercept back button to show pause menu
    BackHandler {
        if (!uiState.showPauseMenu) {
            viewModel.openPauseMenu()
        } else {
            viewModel.resumeGame()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .testTag("emulator_screen")
    ) {
        // Native EGL/OpenGL ES SurfaceView
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .testTag("snes_surface_view"),
            factory = { context ->
                SurfaceView(context).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            NativeBridge.nativeSetSurface(holder.surface)
                        }

                        override fun surfaceChanged(
                            holder: SurfaceHolder,
                            format: Int,
                            width: Int,
                            height: Int
                        ) {
                            NativeBridge.nativeSurfaceChanged(width, height)
                        }

                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            NativeBridge.nativeSurfaceDestroyed()
                        }
                    })
                }
            }
        )

        // Loading or Error Overlay
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DarkBackground),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(color = SnesPrimary)
                    Text(
                        text = "Inicializando núcleo SuperNova SNES...",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        if (uiState.errorMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DarkBackground.copy(alpha = 0.95f))
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Erro na Emulação",
                        color = Color(0xFFFF6B6B),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = uiState.errorMessage ?: "",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
            }
        }

        // Real-Time FPS and CPU stats overlay (if enabled in settings)
        if (uiState.settings.showFps && uiState.isRunning) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 16.dp, top = 48.dp)
                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .testTag("fps_counter")
            ) {
                Text(
                    text = "${String.format("%.1f", uiState.fps)} FPS | ${String.format("%.1f", uiState.frameTimeMs)}ms",
                    color = SnesSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // On-Screen Touch Controls Overlay
        if (uiState.isRunning) {
            TouchControls(
                opacity = uiState.settings.buttonOpacity,
                scale = uiState.settings.buttonScale,
                onButtonEvent = { button, pressed ->
                    viewModel.setButtonState(button, pressed)
                },
                onOpenMenu = { viewModel.openPauseMenu() },
                onQuickReset = { viewModel.resetGame() }
            )
        }

        // In-Game Pause Menu Dialog
        if (uiState.showPauseMenu) {
            PauseMenuDialog(
                gameTitle = uiState.rom?.title ?: "Super Nintendo",
                fps = uiState.fps,
                frameTimeMs = uiState.frameTimeMs,
                cpuTimeMs = uiState.cpuTimeMs,
                settings = uiState.settings,
                saveMessage = uiState.saveMessage,
                onResume = { viewModel.resumeGame() },
                onReset = { viewModel.resetGame() },
                onSaveSlot = { slot -> viewModel.saveState(slot) },
                onLoadSlot = { slot -> viewModel.loadState(slot) },
                onUpdateSettings = { newSettings -> viewModel.updateSettings(newSettings) },
                onExitGame = {
                    viewModel.stopEmulation()
                    onNavigateBack()
                }
            )
        }
    }
}
