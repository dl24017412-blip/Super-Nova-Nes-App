package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.SaveStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SaveStateDao {
    @Query("SELECT * FROM save_states WHERE romId = :romId ORDER BY slot ASC")
    fun getSaveStatesForRom(romId: Long): Flow<List<SaveStateEntity>>

    @Query("SELECT * FROM save_states WHERE romId = :romId AND slot = :slot LIMIT 1")
    suspend fun getSaveState(romId: Long, slot: Int): SaveStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSaveState(state: SaveStateEntity): Long

    @Delete
    suspend fun deleteSaveState(state: SaveStateEntity)

    @Query("DELETE FROM save_states WHERE romId = :romId")
    suspend fun deleteAllForRom(romId: Long)
}
