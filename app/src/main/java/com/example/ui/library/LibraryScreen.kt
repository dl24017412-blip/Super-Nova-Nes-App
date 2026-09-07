package com.example.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.RomEntity
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onRomSelected: (Long) -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val roms by viewModel.romList.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val activeFilter by viewModel.activeFilter.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()
    val importError by viewModel.importError.collectAsState()

    var showDeleteConfirmDialog by remember { mutableStateOf<RomEntity?>(null) }

    // SAF Document Picker for ROM files (.sfc, .smc, .bin, .zip)
    val romPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importRom(it) }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("library_screen"),
        containerColor = DarkBackground,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkBackground)
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                // Header with App Title and Settings Action
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(SnesSecondary, CircleShape)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "SUPERNOVA SNES",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.5.sp,
                                color = TextPrimary
                            )
                        }
                        Text(
                            text = "Emulador Nativo de Super Nintendo",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }

                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier
                            .testTag("btn_settings")
                            .background(DarkSurfaceVariant, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Configurações",
                            tint = TextPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_rom_input"),
                    placeholder = { Text("Pesquisar jogos...", color = TextMuted) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Limpar", tint = TextSecondary)
                            }
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkSurfaceVariant,
                        unfocusedContainerColor = DarkSurface,
                        focusedBorderColor = SnesPrimary,
                        unfocusedBorderColor = DarkSurfaceHighlight,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Filter Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = activeFilter == LibraryFilter.ALL,
                        onClick = { viewModel.setFilter(LibraryFilter.ALL) },
                        label = { Text("Todos (${roms.size})") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SnesPrimary,
                            selectedLabelColor = Color.White,
                            containerColor = DarkSurfaceVariant,
                            labelColor = TextSecondary
                        ),
                        border = null
                    )

                    FilterChip(
                        selected = activeFilter == LibraryFilter.FAVORITES,
                        onClick = { viewModel.setFilter(LibraryFilter.FAVORITES) },
                        label = { Text("Favoritos") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (activeFilter == LibraryFilter.FAVORITES) Color.White else SnesBtnYellow
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SnesPrimary,
                            selectedLabelColor = Color.White,
                            containerColor = DarkSurfaceVariant,
                            labelColor = TextSecondary
                        ),
                        border = null
                    )

                    FilterChip(
                        selected = activeFilter == LibraryFilter.RECENT,
                        onClick = { viewModel.setFilter(LibraryFilter.RECENT) },
                        label = { Text("Recentes") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SnesPrimary,
                            selectedLabelColor = Color.White,
                            containerColor = DarkSurfaceVariant,
                            labelColor = TextSecondary
                        ),
                        border = null
                    )
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    romPickerLauncher.launch(
                        arrayOf(
                            "application/octet-stream",
                            "application/zip",
                            "application/x-snes-rom",
                            "*/*"
                        )
                    )
                },
                modifier = Modifier.testTag("btn_import_rom"),
                containerColor = SnesPrimary,
                contentColor = Color.White,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Importar ROM", fontWeight = FontWeight.Bold) }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isImporting) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                    color = SnesPrimary
                )
            }

            if (importError != null) {
                Snackbar(
                    modifier = Modifier
                        .padding(16.dp)
                        .align(Alignment.BottomCenter)
                ) {
                    Text(importError ?: "")
                }
            }

            if (roms.isEmpty()) {
                EmptyLibraryState(
                    onImportClick = {
                        romPickerLauncher.launch(
                            arrayOf(
                                "application/octet-stream",
                                "application/zip",
                                "application/x-snes-rom",
                                "*/*"
                            )
                        )
                    },
                    onCreateDemoClick = { viewModel.createSampleDemoRom() }
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp)
                ) {
                    items(roms, key = { it.id }) { rom ->
                        RomCard(
                            rom = rom,
                            onClick = { onRomSelected(rom.id) },
                            onToggleFavorite = { viewModel.toggleFavorite(rom) },
                            onDelete = { showDeleteConfirmDialog = rom }
                        )
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    showDeleteConfirmDialog?.let { rom ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = null },
            title = { Text("Excluir Jogo?", color = TextPrimary) },
            text = {
                Text(
                    "Deseja remover '${rom.title}' e todos os estados salvos? Esta ação não pode ser desfeita.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteRom(rom)
                        showDeleteConfirmDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentDanger)
                ) {
                    Text("Excluir")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = null }) {
                    Text("Cancelar", color = TextSecondary)
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
fun RomCard(
    rom: RomEntity,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("rom_card_${rom.id}")
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = DarkSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceHighlight),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cartridge Icon / Art Badge
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(SnesPurpleDark, DarkSurfaceVariant)
                        )
                    )
                    .border(1.dp, DarkSurfaceHighlight, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.SportsEsports,
                        contentDescription = null,
                        tint = SnesPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                    Text(
                        text = "SNES",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        color = TextMuted,
                        letterSpacing = 1.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = rom.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = formatFileSize(rom.fileSize),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )

                    if (rom.lastPlayedTimestamp > 0) {
                        Text(text = "•", color = TextMuted, fontSize = 10.sp)
                        Text(
                            text = "Jogado ${formatDate(rom.lastPlayedTimestamp)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = SnesSecondary
                        )
                    }
                }
            }

            // Actions: Favorite and Delete
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (rom.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = "Favorito",
                    tint = if (rom.isFavorite) SnesBtnYellow else TextMuted
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Excluir",
                    tint = TextMuted
                )
            }
        }
    }
}

@Composable
fun EmptyLibraryState(
    onImportClick: () -> Unit,
    onCreateDemoClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(DarkSurfaceVariant)
                .border(1.dp, DarkSurfaceHighlight, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.VideogameAsset,
                contentDescription = null,
                tint = SnesPrimary,
                modifier = Modifier.size(44.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Nenhuma ROM Encontrada",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "SuperNova SNES funciona 100% offline. Importe suas ROMs de Super Nintendo (.sfc, .smc, .zip) ou experimente a demo interativa.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onImportClick,
            colors = ButtonDefaults.buttonColors(containerColor = SnesPrimary),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(0.8f)
        ) {
            Icon(Icons.Default.FolderOpen, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Selecionar Arquivo ROM", fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedButton(
            onClick = onCreateDemoClick,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(0.8f),
            border = androidx.compose.foundation.BorderStroke(1.dp, SnesPurpleDark)
        ) {
            Icon(Icons.Default.PlayCircleOutline, contentDescription = null, tint = SnesSecondary)
            Spacer(Modifier.width(8.dp))
            Text("Carregar Demo Interativa", color = SnesSecondary, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    return String.format("%.1f MB", mb)
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd/MM", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
