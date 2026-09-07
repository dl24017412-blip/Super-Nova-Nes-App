package com.example.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AspectRatioMode
import com.example.model.PerformanceProfile
import com.example.model.VideoFilter
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.settings.collectAsState()

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_screen"),
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Configurações",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Performance Profile Section
            SettingsSection(title = "DESEMPENHO & BATERIA") {
                PerformanceProfile.values().forEach { profile ->
                    val isSelected = settings.performanceProfile == profile
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { viewModel.updatePerformanceProfile(profile) },
                            colors = RadioButtonDefaults.colors(selectedColor = SnesPrimary)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text = profile.label,
                                color = if (isSelected) TextPrimary else TextSecondary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            // Video & Display Section
            SettingsSection(title = "VÍDEO & TELA") {
                // Aspect Ratio
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Proporção de Tela", color = TextPrimary, fontSize = 14.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AspectRatioMode.values().forEach { mode ->
                            val selected = mode == settings.aspectRatio
                            Button(
                                onClick = { viewModel.updateAspectRatio(mode) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (selected) SnesPrimary else DarkSurfaceVariant
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = when (mode) {
                                        AspectRatioMode.ORIGINAL_4_3 -> "4:3 SNES"
                                        AspectRatioMode.INTEGER_SCALE -> "1x / 2x"
                                        AspectRatioMode.FILL_SCREEN -> "Total"
                                    },
                                    fontSize = 12.sp,
                                    color = if (selected) Color.White else TextSecondary
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Video Filter
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Filtro de Renderização", color = TextPrimary, fontSize = 14.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        VideoFilter.values().forEach { filter ->
                            val selected = filter == settings.videoFilter
                            Button(
                                onClick = { viewModel.updateVideoFilter(filter) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (selected) SnesPrimary else DarkSurfaceVariant
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = when (filter) {
                                        VideoFilter.NEAREST -> "Nítido"
                                        VideoFilter.BILINEAR -> "Suave"
                                        VideoFilter.SCANLINE -> "CRT Scan"
                                    },
                                    fontSize = 12.sp,
                                    color = if (selected) Color.White else TextSecondary
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Show FPS
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Exibir Contador de FPS", color = TextPrimary, fontSize = 14.sp)
                        Text("Mostra taxa de quadros e tempo de frame em tempo real", color = TextMuted, fontSize = 12.sp)
                    }
                    Switch(
                        checked = settings.showFps,
                        onCheckedChange = { viewModel.setShowFps(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = SnesPrimary)
                    )
                }
            }

            // Audio Section
            SettingsSection(title = "ÁUDIO NATIVO (SPC700)") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Ativar Áudio", color = TextPrimary, fontSize = 14.sp)
                        Text("Emulação de som de 8 canais por OpenSL ES", color = TextMuted, fontSize = 12.sp)
                    }
                    Switch(
                        checked = settings.audioEnabled,
                        onCheckedChange = { viewModel.setAudioEnabled(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = SnesPrimary)
                    )
                }

                if (settings.audioEnabled) {
                    Spacer(Modifier.height(8.dp))
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Volume", color = TextPrimary, fontSize = 14.sp)
                            Text("${(settings.audioVolume * 100).toInt()}%", color = SnesSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = settings.audioVolume,
                            onValueChange = { viewModel.setAudioVolume(it) },
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = SnesPrimary,
                                activeTrackColor = SnesPrimary,
                                inactiveTrackColor = DarkSurfaceHighlight
                            )
                        )
                    }
                }
            }

            // Touch Controls Section
            SettingsSection(title = "CONTROLES POR TOQUE") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Vibração / Resposta Háptica", color = TextPrimary, fontSize = 14.sp)
                        Text("Feedback vibratório ao pressionar botões", color = TextMuted, fontSize = 12.sp)
                    }
                    Switch(
                        checked = settings.hapticFeedback,
                        onCheckedChange = { viewModel.setHapticFeedback(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = SnesPrimary)
                    )
                }

                Spacer(Modifier.height(12.dp))

                // Opacity Slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Opacidade dos Botões", color = TextPrimary, fontSize = 14.sp)
                        Text("${(settings.buttonOpacity * 100).toInt()}%", color = SnesSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = settings.buttonOpacity,
                        onValueChange = { viewModel.setButtonOpacity(it) },
                        valueRange = 0.2f..1.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = SnesPrimary,
                            activeTrackColor = SnesPrimary,
                            inactiveTrackColor = DarkSurfaceHighlight
                        )
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Scale Slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Escala dos Botões", color = TextPrimary, fontSize = 14.sp)
                        Text("${(settings.buttonScale * 100).toInt()}%", color = SnesSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = settings.buttonScale,
                        onValueChange = { viewModel.setButtonScale(it) },
                        valueRange = 0.75f..1.25f,
                        colors = SliderDefaults.colors(
                            thumbColor = SnesPrimary,
                            activeTrackColor = SnesPrimary,
                            inactiveTrackColor = DarkSurfaceHighlight
                        )
                    )
                }
            }

            // Saves & State Section
            SettingsSection(title = "ESTADOS SALVOS & DADOS") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Salvar Automaticamente ao Sair", color = TextPrimary, fontSize = 14.sp)
                        Text("Cria um save state automático ao minimizar ou fechar o jogo", color = TextMuted, fontSize = 12.sp)
                    }
                    Switch(
                        checked = settings.autoSaveOnExit,
                        onCheckedChange = { viewModel.setAutoSaveOnExit(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = SnesPrimary)
                    )
                }
            }

            // About & Offline Guarantee
            SettingsSection(title = "SOBRE & PRIVACIDADE") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "SuperNova SNES v1.0",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "• Emulação 100% Offline: Não requer conexão com internet nem contas de login.\n" +
                               "• Núcleo C++ Nativo de Alto Desempenho com 65816 CPU e SNES PPU scanline.\n" +
                               "• Renderizador OpenGL ES 2.0 acelerado por GPU com filtros CRT e Bilinear.\n" +
                               "• Áudio SPC700 de baixa latência via OpenSL ES.\n" +
                               "• Suporte a Joysticks USB/Bluetooth físicos e controles virtuais com haptics.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = DarkSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceHighlight)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = SnesPrimary,
                letterSpacing = 1.2.sp
            )
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}
