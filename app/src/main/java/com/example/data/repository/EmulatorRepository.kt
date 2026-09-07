package com.example.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.AppDatabase
import com.example.data.model.RomEntity
import com.example.data.model.SaveStateEntity
import com.example.jni.NativeBridge
import com.example.util.DefaultRomProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

class EmulatorRepository(private val context: Context) {
    private val db = AppDatabase.getDatabase(context)
    private val romDao = db.romDao()
    private val saveStateDao = db.saveStateDao()

    val allRoms: Flow<List<RomEntity>> = romDao.getAllRoms()
    val favoriteRoms: Flow<List<RomEntity>> = romDao.getFavoriteRoms()

    suspend fun ensureDefaultRom(): RomEntity = withContext(Dispatchers.IO) {
        val existing = romDao.getAllRomsList()
        val defaultRom = existing.firstOrNull { it.fileName == DefaultRomProvider.DEFAULT_ROM_FILENAME }
        if (defaultRom != null) {
            return@withContext defaultRom
        }

        val romDir = File(context.filesDir, "roms").apply { mkdirs() }
        val file = DefaultRomProvider.getOrCreateDefaultRom(romDir)

        val entity = RomEntity(
            title = DefaultRomProvider.DEFAULT_ROM_TITLE,
            filePath = file.absolutePath,
            fileName = file.name,
            fileSize = file.length(),
            lastPlayedTimestamp = System.currentTimeMillis(),
            playTimeSeconds = 0,
            isFavorite = true,
            isHiRom = false,
            hasBattery = true
        )
        val id = romDao.insertRom(entity)
        entity.copy(id = id)
    }

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
            var destFile = File(romDir, fileName)

            if (fileName.endsWith(".zip", ignoreCase = true)) {
                var extractedFile: File? = null
                context.contentResolver.openInputStream(uri)?.use { inStream ->
                    ZipInputStream(inStream).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            val entryName = entry.name
                            if (!entry.isDirectory && (entryName.endsWith(".sfc", ignoreCase = true) ||
                                        entryName.endsWith(".smc", ignoreCase = true) ||
                                        entryName.endsWith(".fig", ignoreCase = true) ||
                                        entryName.endsWith(".swc", ignoreCase = true))) {
                                val cleanName = File(entryName).name
                                val target = File(romDir, cleanName)
                                FileOutputStream(target).use { out ->
                                    zis.copyTo(out)
                                }
                                extractedFile = target
                                fileName = cleanName
                                break
                            }
                            entry = zis.nextEntry
                        }
                    }
                }
                if (extractedFile != null) {
                    destFile = extractedFile!!
                } else {
                    // Fallback copy zip as is
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    } ?: return@withContext Result.failure(Exception("Não foi possível ler o arquivo"))
                }
            } else {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                } ?: return@withContext Result.failure(Exception("Não foi possível ler o arquivo"))
            }

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
