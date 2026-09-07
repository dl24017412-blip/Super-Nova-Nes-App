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

    init {
        viewModelScope.launch {
            repository.ensureDefaultRom()
        }
    }

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
                repository.ensureDefaultRom()
            } catch (e: Exception) {
                importError.value = e.message
            }
            isImporting.value = false
        }
    }
}
