package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.RomEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RomDao {
    @Query("SELECT * FROM roms ORDER BY lastPlayedTimestamp DESC, title ASC")
    fun getAllRoms(): Flow<List<RomEntity>>

    @Query("SELECT * FROM roms WHERE isFavorite = 1 ORDER BY title ASC")
    fun getFavoriteRoms(): Flow<List<RomEntity>>

    @Query("SELECT * FROM roms WHERE id = :id LIMIT 1")
    suspend fun getRomById(id: Long): RomEntity?

    @Query("SELECT * FROM roms WHERE filePath = :path LIMIT 1")
    suspend fun getRomByPath(path: String): RomEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRom(rom: RomEntity): Long

    @Update
    suspend fun updateRom(rom: RomEntity)

    @Delete
    suspend fun deleteRom(rom: RomEntity)

    @Query("UPDATE roms SET lastPlayedTimestamp = :timestamp, playTimeSeconds = playTimeSeconds + :addedPlayTime WHERE id = :id")
    suspend fun recordPlaySession(id: Long, timestamp: Long, addedPlayTime: Long)

    @Query("UPDATE roms SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: Long, isFavorite: Boolean)
}
