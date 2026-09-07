package com.example.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.AppDatabase
import com.example.data.model.RomEntity
import com.example.data.model.SaveStateEntity
import com.example.jni.NativeBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class EmulatorRepository(private val context: Context) {
    private val db = AppDatabase.getDatabase(context)
    private val romDao = db.romDao()
    private val saveStateDao = db.saveStateDao()

    val allRoms: Flow<List<RomEntity>> = romDao.getAllRoms()
    val favoriteRoms: Flow<List<RomEntity>> = romDao.getFavoriteRoms()

    fun getSaveStatesForRom(romId: Long): Flow<List<SaveStateEntity>> =
        saveStateDao.getSaveStatesForRom(romId)

    suspend fun getRomById(id: Long): RomEntity? = withContext(Dispatchers.IO) {
        romDao.getRomById(id)
    }

    suspend fun toggleFavorite(rom: RomEntity) = withContext(Dispatchers.IO) {
        romDao.updateFavorite(rom.id, !rom.isFavorite)
    }

    suspend fun deleteRom(rom: RomEntity) = withContext(Dispatchers.IO) {
        val file = File(rom.filePath)
        if (file.exists()) {
            file.delete()
        }
        saveStateDao.deleteAllForRom(rom.id)
        romDao.deleteRom(rom)
    }

    suspend fun recordPlaySession(romId: Long, durationSeconds: Long) = withContext(Dispatchers.IO) {
        romDao.recordPlaySession(romId, System.currentTimeMillis(), durationSeconds)
    }

    suspend fun importRomFromUri(uri: Uri): Result<RomEntity> = withContext(Dispatchers.IO) {
        try {
            var fileName = "unknown_game.sfc"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    val name = cursor.getString(nameIndex)
                    if (!name.isNullOrBlank()) {
                        fileName = name
                    }
                }
            }

            val romDir = File(context.filesDir, "roms").apply { mkdirs() }
            val destFile = File(romDir, fileName)

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext Result.failure(Exception("Não foi possível ler o arquivo"))

            val title = fileName.substringBeforeLast(".")
                .replace("_", " ")
                .replace("-", " ")
                .trim()

            val entity = RomEntity(
                title = if (title.isNotBlank()) title else "SNES Game",
                filePath = destFile.absolutePath,
                fileName = fileName,
                fileSize = destFile.length(),
                lastPlayedTimestamp = 0,
                playTimeSeconds = 0,
                isFavorite = false
            )

            val id = romDao.insertRom(entity)
            Result.success(entity.copy(id = id))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getSaveStatePath(romId: Long, slot: Int): String {
        val statesDir = File(context.filesDir, "states").apply { mkdirs() }
        return File(statesDir, "rom_${romId}_slot_${slot}.state").absolutePath
    }

    suspend fun saveState(romId: Long, slot: Int): Boolean = withContext(Dispatchers.IO) {
        val path = getSaveStatePath(romId, slot)
        val success = NativeBridge.nativeSaveState(slot, path)
        if (success) {
            val state = SaveStateEntity(
                romId = romId,
                slot = slot,
                filePath = path,
                timestamp = System.currentTimeMillis()
            )
            saveStateDao.insertSaveState(state)
        }
        success
    }

    suspend fun loadState(romId: Long, slot: Int): Boolean = withContext(Dispatchers.IO) {
        val path = getSaveStatePath(romId, slot)
        NativeBridge.nativeLoadState(slot, path)
    }

    fun getSramDirectory(): String {
        val sramDir = File(context.filesDir, "sram").apply { mkdirs() }
        return sramDir.absolutePath
    }
}
