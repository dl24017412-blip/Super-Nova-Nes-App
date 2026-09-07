package com.example.ui.library

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.RomEntity
import com.example.data.repository.EmulatorRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

enum class LibraryFilter {
    ALL,
    FAVORITES,
    RECENT
}

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = EmulatorRepository(application)

    val searchQuery = MutableStateFlow("")
    val activeFilter = MutableStateFlow(LibraryFilter.ALL)
    val isImporting = MutableStateFlow(false)
    val importError = MutableStateFlow<String?>(null)

    val romList: StateFlow<List<RomEntity>> = combine(
        repository.allRoms,
        searchQuery,
        activeFilter
    ) { roms, query, filter ->
        var filtered = when (filter) {
            LibraryFilter.ALL -> roms
            LibraryFilter.FAVORITES -> roms.filter { it.isFavorite }
            LibraryFilter.RECENT -> roms.filter { it.lastPlayedTimestamp > 0 }
                .sortedByDescending { it.lastPlayedTimestamp }
        }

        if (query.isNotBlank()) {
            filtered = filtered.filter {
                it.title.contains(query, ignoreCase = true) ||
                it.fileName.contains(query, ignoreCase = true)
            }
        }
        filtered
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun setFilter(filter: LibraryFilter) {
        activeFilter.value = filter
    }

    fun importRom(uri: Uri) {
        viewModelScope.launch {
            isImporting.value = true
            importError.value = null
            val result = repository.importRomFromUri(uri)
            if (result.isFailure) {
                importError.value = result.exceptionOrNull()?.message ?: "Erro ao importar ROM"
            }
            isImporting.value = false
        }
    }

    fun toggleFavorite(rom: RomEntity) {
        viewModelScope.launch {
            repository.toggleFavorite(rom)
        }
    }

    fun deleteRom(rom: RomEntity) {
        viewModelScope.launch {
            repository.deleteRom(rom)
        }
    }

    fun createSampleDemoRom() {
        viewModelScope.launch {
            isImporting.value = true
            try {
                val app = getApplication<Application>()
                val romDir = File(app.filesDir, "roms").apply { mkdirs() }
                val demoFile = File(romDir, "SuperNova_Demo.sfc")

                if (!demoFile.exists()) {
                    // Generate a valid 32KB LoROM SNES binary with standard header & color bar test
                    val data = ByteArray(0x8000)
                    // Reset vector pointing to 0x8000
                    val resetVector = 0x8000
                    data[0x7FFC] = (resetVector and 0xFF).toByte()
                    data[0x7FFD] = ((resetVector shr 8) and 0xFF).toByte()

                    // LoROM Header at $7FC0
                    val title = "SUPERNOVA DEMO ROM  "
                    for (i in title.indices) {
                        data[0x7FC0 + i] = title[i].code.toByte()
                    }
                    data[0x7FD5] = 0x20 // LoROM
                    data[0x7FD6] = 0x00 // ROM only
                    data[0x7FD7] = 0x08 // 256KB/32KB
                    data[0x7FD8] = 0x00 // No SRAM
                    data[0x7FDA] = 0x33 // Maker
                    data[0x7FDC] = 0x00 // Complement low
                    data[0x7FDD] = 0x00 // Complement high
                    data[0x7FDE] = 0xFF.toByte() // Checksum low
                    data[0x7FDF] = 0xFF.toByte() // Checksum high

                    // Entry code at 0x0000 (mapped to $8000)
                    // SEI, CLD, LDX #$01FF, TXS
                    var p = 0
                    data[p++] = 0x78.toByte() // SEI
                    data[p++] = 0xD8.toByte() // CLD
                    data[p++] = 0xA2.toByte(); data[p++] = 0xFF.toByte(); data[p++] = 0x01.toByte() // LDX #$01FF
                    data[p++] = 0x9A.toByte() // TXS
                    // Set brightness 15: LDA #$0F, STA $2100
                    data[p++] = 0xA9.toByte(); data[p++] = 0x0F.toByte() // LDA #$0F
                    data[p++] = 0x8D.toByte(); data[p++] = 0x00.toByte(); data[p++] = 0x21.toByte() // STA $2100
                    // Set CGRAM palette 0 to Color:
                    // STZ $2121
                    data[p++] = 0x9C.toByte(); data[p++] = 0x21.toByte(); data[p++] = 0x21.toByte()
                    // LDA #$1F (Blue/Cyan), STA $2122, STA $2122
                    data[p++] = 0xA9.toByte(); data[p++] = 0x1F.toByte()
                    data[p++] = 0x8D.toByte(); data[p++] = 0x22.toByte(); data[p++] = 0x21.toByte()
                    data[p++] = 0x8D.toByte(); data[p++] = 0x22.toByte(); data[p++] = 0x21.toByte()
                    // Loop forever: BRA -2
                    data[p++] = 0x80.toByte(); data[p++] = 0xFE.toByte()

                    FileOutputStream(demoFile).use { it.write(data) }
                }

                val entity = RomEntity(
                    title = "SuperNova SNES Demo",
                    filePath = demoFile.absolutePath,
                    fileName = demoFile.name,
                    fileSize = demoFile.length(),
                    lastPlayedTimestamp = System.currentTimeMillis(),
                    playTimeSeconds = 0,
                    isFavorite = true,
                    isHiRom = false,
                    hasBattery = false
                )
                val db = com.example.data.AppDatabase.getDatabase(app)
                db.romDao().insertRom(entity)
            } catch (e: Exception) {
                importError.value = e.message
            }
            isImporting.value = false
        }
    }
}
