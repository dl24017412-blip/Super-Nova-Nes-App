package com.example.ui.emulator

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.AspectRatioMode
import com.example.model.EmulatorSettings
import com.example.model.PerformanceProfile
import com.example.model.VideoFilter
import com.example.ui.theme.*

@Composable
fun PauseMenuDialog(
    gameTitle: String,
    fps: Float,
    frameTimeMs: Float,
    cpuTimeMs: Float,
    settings: EmulatorSettings,
    saveMessage: String?,
    onResume: () -> Unit,
    onReset: () -> Unit,
    onSaveSlot: (Int) -> Unit,
    onLoadSlot: (Int) -> Unit,
    onUpdateSettings: (EmulatorSettings) -> Unit,
    onExitGame: () -> Unit
) {
    var selectedSlot by remember { mutableStateOf(1) }

    Dialog(
        onDismissRequest = onResume,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.88f)
                .testTag("pause_menu_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = DarkSurface,
            tonalElevation = 8.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceHighlight)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "PAUSA",
                            style = MaterialTheme.typography.labelSmall,
                            color = SnesPrimary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                        Text(
                            text = gameTitle,
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(
                        onClick = onResume,
                        modifier = Modifier
                            .testTag("btn_close_pause_menu")
                            .background(DarkSurfaceVariant, CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar", tint = TextPrimary)
                    }
                }

                // Status Message if state saved/loaded
                if (!saveMessage.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SnesPurpleDark.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .border(1.dp, SnesPrimary, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = saveMessage,
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    }
                }

                // Performance Monitor Card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = DarkSurfaceVariant
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        StatItem(label = "FPS", value = String.format("%.1f", fps))
                        StatItem(label = "FRAME", value = String.format("%.1f ms", frameTimeMs))
                        StatItem(label = "CPU CORE", value = String.format("%.1f ms", cpuTimeMs))
                    }
                }

                // Save States Section
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "ESTADOS SALVOS (SAVE STATES)",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary,
                        fontWeight = FontWeight.SemiBold
                    )

                    // Slot selector 1 to 10
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items((1..10).toList()) { slot ->
                            val isSelected = slot == selectedSlot
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) SnesPrimary else DarkSurfaceVariant)
                                    .clickable { selectedSlot = slot },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$slot",
                                    color = if (isSelected) Color.White else TextSecondary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }

                    // Save / Load Buttons for selected slot
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { onSaveSlot(selectedSlot) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_save_slot"),
                            colors = ButtonDefaults.buttonColors(containerColor = SnesPurpleDark),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Salvar Slot $selectedSlot")
                        }

                        OutlinedButton(
                            onClick = { onLoadSlot(selectedSlot) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_load_slot"),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, SnesPrimary)
                        ) {
                            Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Carregar Slot $selectedSlot")
                        }
                    }
                }

                // Quick Settings (Aspect Ratio & Video Filter)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "CONFIGURAÇÕES RÁPIDAS",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary,
                        fontWeight = FontWeight.SemiBold
                    )

                    // Aspect Ratio Selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Proporção de Tela", color = TextPrimary, fontSize = 14.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            AspectRatioMode.values().forEach { mode ->
                                val selected = mode == settings.aspectRatio
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selected) SnesPrimary else DarkSurfaceVariant)
                                        .clickable { onUpdateSettings(settings.copy(aspectRatio = mode)) }
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = when (mode) {
                                            AspectRatioMode.ORIGINAL_4_3 -> "4:3"
                                            AspectRatioMode.INTEGER_SCALE -> "1x/2x"
                                            AspectRatioMode.FILL_SCREEN -> "Total"
                                        },
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (selected) Color.White else TextSecondary
                                    )
                                }
                            }
                        }
                    }

                    // Video Filter Selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Filtro Gráfico", color = TextPrimary, fontSize = 14.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            VideoFilter.values().forEach { filter ->
                                val selected = filter == settings.videoFilter
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selected) SnesPrimary else DarkSurfaceVariant)
                                        .clickable { onUpdateSettings(settings.copy(videoFilter = filter)) }
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = when (filter) {
                                            VideoFilter.NEAREST -> "Nítido"
                                            VideoFilter.BILINEAR -> "Suave"
                                            VideoFilter.SCANLINE -> "CRT"
                                        },
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (selected) Color.White else TextSecondary
                                    )
                                }
                            }
                        }
                    }

                    // Audio Mute Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Áudio do Jogo", color = TextPrimary, fontSize = 14.sp)
                        Switch(
                            checked = settings.audioEnabled,
                            onCheckedChange = { onUpdateSettings(settings.copy(audioEnabled = it)) },
                            colors = SwitchDefaults.colors(checkedThumbColor = SnesPrimary)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Action Buttons: Resume, Reset, Exit
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onResume,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_resume_game"),
                        colors = ButtonDefaults.buttonColors(containerColor = SnesPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Continuar Jogando", fontWeight = FontWeight.Bold)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onReset,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("btn_reset_game"),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceHighlight)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Reiniciar")
                        }

                        Button(
                            onClick = onExitGame,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("btn_exit_game"),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentDanger),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Sair")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            fontSize = 10.sp,
            color = TextMuted,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Text(
            text = value,
            fontSize = 16.sp,
            color = SnesSecondary,
            fontWeight = FontWeight.Bold
        )
    }
}
