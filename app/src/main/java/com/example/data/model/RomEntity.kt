package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "roms")
data class RomEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val filePath: String,
    val fileName: String,
    val fileSize: Long,
    val coverPath: String? = null,
    val lastPlayedTimestamp: Long = 0,
    val playTimeSeconds: Long = 0,
    val isFavorite: Boolean = false,
    val isHiRom: Boolean = false,
    val hasBattery: Boolean = false
)
